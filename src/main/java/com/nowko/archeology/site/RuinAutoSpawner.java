package com.nowko.archeology.site;

import com.nowko.archeology.config.AutoRuinSettings;
import com.nowko.archeology.config.CatalogRegistry;
import com.nowko.archeology.config.InterestSettings;
import com.nowko.archeology.model.InterestLevel;
import com.nowko.archeology.model.Site;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Trial auto-spawn on chunk load: each chunk is considered at most once by Archaeo, whether the
 * terrain is brand new or was generated months before the plugin was installed.
 * Queues work so fitness checks never pile up in one tick; staff get a chat line with a [tp] link.
 */
public class RuinAutoSpawner implements Listener {
    private final JavaPlugin plugin;
    private final CatalogRegistry catalogs;
    private final SiteRepository sites;
    private final SiteGenerator generator;
    private final AutoRuinEvaluationLedger ledger;
    private AutoRuinSettings settings;
    private final Queue<Pending> queue = new ArrayDeque<>();
    /** Chunks waiting in the queue or on a delayed schedule, so reloads do not enqueue twice. */
    private final Set<String> inflight = ConcurrentHashMap.newKeySet();
    private BukkitTask task;
    private BukkitTask flushTask;

    /**
     * @param plugin scheduler and logger owner
     * @param catalogs live settings and interest display names
     * @param sites dossier store and spacing lookups
     * @param generator creates the ruin when a chunk passes filters
     * @param ledger persistent “already considered” mask
     * @param settings initial {@code auto-ruins} block
     */
    public RuinAutoSpawner(
            JavaPlugin plugin,
            CatalogRegistry catalogs,
            SiteRepository sites,
            SiteGenerator generator,
            AutoRuinEvaluationLedger ledger,
            AutoRuinSettings settings
    ) {
        this.plugin = plugin;
        this.catalogs = catalogs;
        this.sites = sites;
        this.generator = generator;
        this.ledger = ledger;
        this.settings = settings == null ? AutoRuinSettings.defaults() : settings;
    }

    /**
     * @param settings latest values after reload
     */
    public void setSettings(AutoRuinSettings settings) {
        this.settings = settings == null ? AutoRuinSettings.defaults() : settings;
    }

    /**
     * Loads the evaluation ledger, seeds it from existing sites, and starts the drain/flush loops.
     */
    public void start() {
        stop();
        ledger.load();
        for (Site site : sites.all()) {
            ledger.markEvaluated(site.getWorldName(), site.getChunkX(), site.getChunkZ());
        }
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::drain, 1L, 1L);
        flushTask = plugin.getServer().getScheduler().runTaskTimer(plugin, ledger::flush, 100L, 100L);
    }

    /**
     * Cancels loops, drops the queue, and flushes the ledger.
     */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
        queue.clear();
        inflight.clear();
        ledger.flush();
    }

    /**
     * Enqueues a chunk the first time Archaeo sees it loaded, after a short delay for populate.
     *
     * @param event any chunk load; Minecraft's {@code isNewChunk} is intentionally ignored
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!settings.enabled()) {
            return;
        }
        Chunk chunk = event.getChunk();
        World world = chunk.getWorld();
        if (!worldAllowed(world.getName())) {
            return;
        }
        if (settings.maxSitesPerWorld() > 0
                && countSitesInWorld(world.getName()) >= settings.maxSitesPerWorld()) {
            return;
        }
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        String worldName = world.getName();
        if (ledger.isEvaluated(worldName, chunkX, chunkZ)) {
            return;
        }
        String key = key(worldName, chunkX, chunkZ);
        if (!inflight.add(key)) {
            return;
        }
        // New terrain may still be populating; old chunks are ready, and a short delay is harmless.
        long delay = Math.max(1L, settings.evaluateDelayTicks());
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!settings.enabled()) {
                inflight.remove(key);
                return;
            }
            World live = plugin.getServer().getWorld(worldName);
            if (live == null || !live.isChunkLoaded(chunkX, chunkZ)) {
                inflight.remove(key);
                return;
            }
            if (ledger.isEvaluated(worldName, chunkX, chunkZ)) {
                inflight.remove(key);
                return;
            }
            queue.offer(new Pending(worldName, chunkX, chunkZ));
        }, delay);
    }

    /**
     * Runs up to {@code max-evaluations-per-tick} full attempts from the queue.
     */
    private void drain() {
        if (!settings.enabled()) {
            queue.clear();
            inflight.clear();
            return;
        }
        int budget = Math.max(1, settings.maxEvaluationsPerTick());
        for (int i = 0; i < budget && !queue.isEmpty(); i++) {
            Pending pending = queue.poll();
            if (pending != null) {
                evaluate(pending);
            }
        }
    }

    /**
     * Lottery, spacing, fitness, then {@link SiteGenerator#createManagedRuin}.
     * Marks the chunk evaluated once a decision is made so old maps are not re-scanned forever.
     *
     * @param pending delayed chunk coordinates
     */
    private void evaluate(Pending pending) {
        String key = key(pending.worldName(), pending.chunkX(), pending.chunkZ());
        World world = plugin.getServer().getWorld(pending.worldName());
        if (world == null || !world.isChunkLoaded(pending.chunkX(), pending.chunkZ())) {
            inflight.remove(key);
            return;
        }
        if (settings.maxSitesPerWorld() > 0
                && countSitesInWorld(pending.worldName()) >= settings.maxSitesPerWorld()) {
            // Under the cap later this chunk may still be eligible; do not burn the evaluation.
            inflight.remove(key);
            return;
        }

        try {
            if (!passesLottery(world, pending.chunkX(), pending.chunkZ())) {
                return;
            }
            if (sites.findByChunk(pending.worldName(), pending.chunkX(), pending.chunkZ()).isPresent()) {
                return;
            }
            if (tooCloseToSpawn(world, pending.chunkX(), pending.chunkZ())) {
                return;
            }
            if (tooCloseToSite(pending.worldName(), pending.chunkX(), pending.chunkZ())) {
                return;
            }

            Chunk chunk = world.getChunkAt(pending.chunkX(), pending.chunkZ());
            ChunkRuinFitness.Sample sample = ChunkRuinFitness.sample(chunk, catalogs);
            if (sample.reliefBlocks() > settings.maxReliefBlocks()) {
                return;
            }
            if (sample.soilFraction() + 1e-9 < settings.minSoilFraction()) {
                return;
            }
            if (sample.floodedFraction() - 1e-9 > settings.maxFloodedFraction()) {
                return;
            }
            if (sample.buriedCells() < settings.minBuriedCells()) {
                return;
            }

            InterestLevel interest = rollInterest(world, pending.chunkX(), pending.chunkZ());
            try {
                Site site = generator.createManagedRuin(chunk, interest, null, null);
                if (site.getFinds().isEmpty()) {
                    sites.delete(site);
                    plugin.getLogger().info("Auto-ruin skipped (no finds): chunk "
                            + pending.chunkX() + "," + pending.chunkZ() + " in " + pending.worldName());
                    return;
                }
                notifySpawn(site, sample);
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Auto-ruin failed at "
                        + pending.chunkX() + "," + pending.chunkZ() + " in " + pending.worldName(), exception);
            }
        } finally {
            ledger.markEvaluated(pending.worldName(), pending.chunkX(), pending.chunkZ());
            inflight.remove(key);
        }
    }

    /**
     * @param worldName world id
     * @param chunkX chunk X
     * @param chunkZ chunk Z
     * @return stable in-memory key for the inflight set
     */
    private static String key(String worldName, int chunkX, int chunkZ) {
        return worldName + '|' + chunkX + '|' + chunkZ;
    }

    /**
     * @param worldName world id
     * @return whether this world is in the whitelist, or the list is empty (all worlds)
     */
    private boolean worldAllowed(String worldName) {
        if (settings.worlds().isEmpty()) {
            return true;
        }
        for (String allowed : settings.worlds()) {
            if (allowed.equalsIgnoreCase(worldName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Deterministic roll so the same chunk never changes its mind after a restart.
     *
     * @param world chunk world
     * @param chunkX chunk X
     * @param chunkZ chunk Z
     * @return whether this chunk may attempt a spawn
     */
    private boolean passesLottery(World world, int chunkX, int chunkZ) {
        double chance = Math.max(0.0, Math.min(1.0, settings.chancePerChunk()));
        if (chance <= 0.0) {
            return false;
        }
        if (chance >= 1.0) {
            return true;
        }
        long mixed = mix(world.getSeed(), chunkX, chunkZ);
        double roll = (mixed >>> 11) * 0x1.0p-53;
        return roll < chance;
    }

    /**
     * @param worldName world id
     * @param chunkX candidate X
     * @param chunkZ candidate Z
     * @return whether another site is closer than {@code min-chunk-distance} (Chebyshev)
     */
    private boolean tooCloseToSite(String worldName, int chunkX, int chunkZ) {
        int min = Math.max(0, settings.minChunkDistance());
        if (min <= 0) {
            return false;
        }
        for (Site site : sites.all()) {
            if (!site.getWorldName().equals(worldName)) {
                continue;
            }
            int dx = Math.abs(site.getChunkX() - chunkX);
            int dz = Math.abs(site.getChunkZ() - chunkZ);
            if (Math.max(dx, dz) < min) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param world chunk world
     * @param chunkX candidate X
     * @param chunkZ candidate Z
     * @return whether the chunk sits inside the spawn exclusion square
     */
    private boolean tooCloseToSpawn(World world, int chunkX, int chunkZ) {
        int radius = Math.max(0, settings.excludeSpawnChunks());
        if (radius <= 0) {
            return false;
        }
        Location spawn = world.getSpawnLocation();
        int spawnChunkX = spawn.getBlockX() >> 4;
        int spawnChunkZ = spawn.getBlockZ() >> 4;
        int dx = Math.abs(chunkX - spawnChunkX);
        int dz = Math.abs(chunkZ - spawnChunkZ);
        return Math.max(dx, dz) < radius;
    }

    /**
     * @param worldName world id
     * @return how many dossiers already live in that world
     */
    private int countSitesInWorld(String worldName) {
        int count = 0;
        for (Site site : sites.all()) {
            if (site.getWorldName().equals(worldName)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Weighted interest pick; uses a chunk-stable seed when generation asks for world seeds.
     *
     * @param world chunk world
     * @param chunkX chunk X
     * @param chunkZ chunk Z
     * @return rolled interest level
     */
    private InterestLevel rollInterest(World world, int chunkX, int chunkZ) {
        Map<InterestLevel, Integer> weights = settings.interestWeights();
        int total = 0;
        for (InterestLevel level : InterestLevel.values()) {
            total += Math.max(0, weights.getOrDefault(level, 0));
        }
        if (total <= 0) {
            return InterestLevel.MEDIUM;
        }
        int roll;
        if (catalogs.useWorldSeed()) {
            long mixed = mix(world.getSeed() ^ 0xA11CE, chunkX, chunkZ);
            roll = (int) Math.floorMod(mixed, total);
        } else {
            roll = ThreadLocalRandom.current().nextInt(total);
        }
        int cursor = 0;
        for (InterestLevel level : InterestLevel.values()) {
            cursor += Math.max(0, weights.getOrDefault(level, 0));
            if (roll < cursor) {
                return level;
            }
        }
        return InterestLevel.MEDIUM;
    }

    /**
     * Logs and optionally chats staff about the new ruin, with a clickable teleport to the chunk.
     *
     * @param site persisted ruin
     * @param sample surface metrics from the fitness pass
     */
    private void notifySpawn(Site site, ChunkRuinFitness.Sample sample) {
        InterestSettings interest = catalogs.interest(site.getInterest());
        String rarity = interest == null ? site.getInterest().yamlKey() : interest.displayName();
        String line = String.format(
                Locale.ROOT,
                "[Archaeo] Auto-ruin %s · rarity %s · chunk %d,%d (%s) · finds %d · relief %d · soil %.0f%% · flooded %.0f%% · buried %d",
                site.displayLabel(),
                rarity,
                site.getChunkX(),
                site.getChunkZ(),
                site.getWorldName(),
                site.getFinds().size(),
                sample.reliefBlocks(),
                sample.soilFraction() * 100.0,
                sample.floodedFraction() * 100.0,
                sample.buriedCells());
        plugin.getLogger().info(line);
        if (!settings.notifyStaff()) {
            return;
        }
        String permission = catalogs.staffPermission();
        TextComponent prefix = new TextComponent(line + " ");
        TextComponent tp = new TextComponent("[tp]");
        tp.setColor(net.md_5.bungee.api.ChatColor.AQUA);
        tp.setClickEvent(new ClickEvent(
                ClickEvent.Action.RUN_COMMAND,
                "/archaeo ruin tp #" + site.getSerial()));
        tp.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new Text("Teleport to this ruin chunk")));
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.hasPermission(permission)) {
                player.spigot().sendMessage(prefix, tp);
            }
        }
    }

    /**
     * Mixes world seed and chunk coords into a 64-bit value for lottery and interest rolls.
     *
     * @param seed world seed (or a salted variant)
     * @param chunkX chunk X
     * @param chunkZ chunk Z
     * @return scrambled bits
     */
    private static long mix(long seed, int chunkX, int chunkZ) {
        long mixed = seed;
        mixed ^= (long) chunkX * 0x9E3779B97F4A7C15L;
        mixed ^= (long) chunkZ * 0xC2B2AE3D27D4EB4FL;
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= mixed >>> 33;
        mixed *= 0xc4ceb9fe1a85ec53L;
        mixed ^= mixed >>> 33;
        return mixed;
    }

    /**
     * Chunk waiting for a fitness pass after the load delay.
     *
     * @param worldName world id
     * @param chunkX chunk X
     * @param chunkZ chunk Z
     */
    private record Pending(String worldName, int chunkX, int chunkZ) {
    }
}
