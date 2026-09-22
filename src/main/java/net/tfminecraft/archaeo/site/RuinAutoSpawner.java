package net.tfminecraft.archaeo.site;

import net.tfminecraft.archaeo.config.AutoRuinSettings;
import net.tfminecraft.archaeo.config.CatalogRegistry;
import net.tfminecraft.archaeo.config.InterestSettings;
import net.tfminecraft.archaeo.model.InterestLevel;
import net.tfminecraft.archaeo.model.Site;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Trial auto-spawn on chunk load: each chunk is considered at most once by Archaeo, whether the
 * terrain is brand new or was generated months before the plugin was installed.
 * A small {@code max-pending} window caps queued work so exploration cannot build a huge backlog
 * of chunks that unload before they are decided. Each drain tick drops unloaded queue entries
 * without marking them (budgeted by {@code max-unload-purge-per-tick} so a large pending cap
 * cannot scan the whole queue in one tick) and then runs fitness only on chunks that are still
 * loaded. Staff get a chat line with a [tp] link.
 *
 * <p>Changing {@code chance-per-chunk} (or {@code /archaeo ruin auto reset}) wipes the ledger so
 * chunks can retry at the new threshold. A normal reload only updates settings and keeps the queue.
 */
public class RuinAutoSpawner implements Listener {
    private final JavaPlugin plugin;
    private final CatalogRegistry catalogs;
    private final SiteRepository sites;
    private final SiteGenerator generator;
    private final AutoRuinEvaluationLedger ledger;
    private final Path policyFile;
    private AutoRuinSettings settings;
    private final Deque<Pending> queue = new ArrayDeque<>();
    /** Chunks in the drain queue, so reloads do not enqueue the same chunk twice. */
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
        this.policyFile = plugin.getDataFolder().toPath().resolve("auto-ruins").resolve("evaluation-policy.txt");
        this.settings = settings == null ? AutoRuinSettings.defaults() : settings;
    }

    /**
     * Applies latest {@code auto-ruins} values. Clears the evaluation ledger only when
     * {@code chance-per-chunk} actually changed; a plain reload must not drop the pending queue
     * (loaded chunks will not fire {@code ChunkLoadEvent} again until they unload).
     *
     * @param settings latest values after reload
     */
    public void setSettings(AutoRuinSettings settings) {
        AutoRuinSettings next = settings == null ? AutoRuinSettings.defaults() : settings;
        boolean chanceChanged = Double.compare(this.settings.chancePerChunk(), next.chancePerChunk()) != 0;
        this.settings = next;
        if (chanceChanged) {
            clearEvaluated("chance-per-chunk changed to " + next.chancePerChunk());
        }
        writePolicyChance(next.chancePerChunk());
    }

    /**
     * Replaces RAM with whatever is under {@code auto-ruins/evaluated/} now, then re-marks sites.
     * Does not clear the pending queue: callers that need a full wipe should use {@link #clearEvaluated}.
     */
    public void resyncLedgerFromDisk() {
        ledger.load();
        seedExistingSites();
    }

    /**
     * Loads the evaluation ledger, seeds it from existing sites, and starts the drain/flush loops.
     * Wipes the ledger only when a stored policy chance disagrees with config — never on first run
     * merely because {@code evaluation-policy.txt} is missing (that was wiping progress every boot).
     */
    public void start() {
        stop();
        ledger.load();
        Double stored = readPolicyChance();
        double live = settings.chancePerChunk();
        if (stored != null && Double.compare(stored, live) != 0) {
            clearEvaluated("stored chance " + stored + " ≠ config " + live);
        } else {
            seedExistingSites();
        }
        writePolicyChance(live);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::drain, 1L, 1L);
        flushTask = plugin.getServer().getScheduler().runTaskTimer(plugin, ledger::flush, 100L, 100L);
    }

    /**
     * Staff wipe of {@code auto-ruins/evaluated/} so the next chunk loads re-run the lottery.
     * Existing ruin dossiers stay; their chunks are re-marked so duplicates are not attempted.
     *
     * @param reason short log / chat explanation
     */
    public void clearEvaluated(String reason) {
        queue.clear();
        inflight.clear();
        ledger.clearAll();
        seedExistingSites();
        writePolicyChance(settings.chancePerChunk());
        plugin.getLogger().info("Auto-ruin evaluation ledger cleared (" + reason + ").");
    }

    /**
     * @return one-line staff summary of whether auto-spawn can still see new chunks
     */
    public String statusLine() {
        return "auto-ruins enabled=" + settings.enabled()
                + " chance=" + settings.chancePerChunk()
                + " pending=" + inflight.size() + "/" + Math.max(1, settings.maxPending())
                + " queue=" + queue.size()
                + " evaluatedChunks≈" + ledger.evaluatedChunkCount()
                + " regions=" + ledger.loadedRegionCount()
                + " sites=" + sites.all().size();
    }

    /**
     * Marks every known site chunk so auto-spawn never tries to place a second ruin there.
     */
    private void seedExistingSites() {
        for (Site site : sites.all()) {
            ledger.markEvaluated(site.getWorldName(), site.getChunkX(), site.getChunkZ());
        }
    }

    /**
     * Forgets that this chunk was evaluated so a later load may roll auto-spawn again.
     *
     * @param worldName world id
     * @param chunkX chunk X
     * @param chunkZ chunk Z
     */
    public void forgetChunk(String worldName, int chunkX, int chunkZ) {
        ledger.forgetEvaluated(worldName, chunkX, chunkZ);
    }

    /**
     * @return last chance written under {@code auto-ruins/evaluation-policy.txt}, or {@code null}
     */
    private Double readPolicyChance() {
        if (!Files.isRegularFile(policyFile)) {
            return null;
        }
        try {
            String raw = Files.readString(policyFile, StandardCharsets.UTF_8).trim();
            if (raw.isEmpty()) {
                return null;
            }
            return Double.parseDouble(raw.replace(',', '.'));
        } catch (IOException | NumberFormatException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not read " + policyFile, exception);
            return null;
        }
    }

    /**
     * @param chance live {@code chance-per-chunk} to persist for the next boot/reload compare
     */
    private void writePolicyChance(double chance) {
        try {
            Files.createDirectories(policyFile.getParent());
            Files.writeString(policyFile, Double.toString(chance) + "\n", StandardCharsets.UTF_8);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not write " + policyFile, exception);
        }
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
     * Accepts a chunk into the pending drain queue when Archaeo first sees it loaded.
     * When {@code max-pending} is full, the load is ignored (not burned) so a later load can retry
     * once a slot frees. The chunk is readable on {@link ChunkLoadEvent}; no populate delay.
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
        int maxPending = Math.max(1, settings.maxPending());
        if (inflight.size() >= maxPending) {
            return;
        }
        String key = key(worldName, chunkX, chunkZ);
        if (!inflight.add(key)) {
            return;
        }
        queue.offer(new Pending(worldName, chunkX, chunkZ));
    }

    /**
     * Drops unloaded queue entries (budgeted) then runs up to {@code max-evaluations-per-tick}
     * full attempts on chunks that are still present.
     */
    private void drain() {
        if (!settings.enabled()) {
            queue.clear();
            inflight.clear();
            return;
        }
        int[] unloadBudget = {Math.max(1, settings.maxUnloadPurgePerTick())};
        dropUnloadedFromQueue(unloadBudget);
        int evalBudget = Math.max(1, settings.maxEvaluationsPerTick());
        for (int i = 0; i < evalBudget; i++) {
            Pending pending = pollLoadedPending(unloadBudget);
            if (pending == null) {
                return;
            }
            evaluate(pending);
        }
    }

    /**
     * Rotates up to the remaining unload budget through the queue: loaded entries go to the back,
     * unloaded ones are dropped without marking so a later load may retry.
     *
     * @param unloadBudget single-element remaining {@code isChunkLoaded} checks for this drain tick
     */
    private void dropUnloadedFromQueue(int[] unloadBudget) {
        int n = Math.min(unloadBudget[0], queue.size());
        for (int i = 0; i < n; i++) {
            Pending pending = queue.poll();
            if (pending == null) {
                return;
            }
            unloadBudget[0]--;
            if (isChunkLoaded(pending)) {
                queue.offer(pending);
            } else {
                inflight.remove(key(pending.worldName(), pending.chunkX(), pending.chunkZ()));
            }
        }
    }

    /**
     * @param unloadBudget remaining checks for dropping unloaded entries this tick
     * @return next queued chunk that is still loaded, or {@code null} when none found within budget
     */
    private Pending pollLoadedPending(int[] unloadBudget) {
        while (!queue.isEmpty()) {
            Pending pending = queue.poll();
            if (pending == null) {
                return null;
            }
            if (isChunkLoaded(pending)) {
                return pending;
            }
            if (unloadBudget[0] <= 0) {
                queue.addFirst(pending);
                return null;
            }
            unloadBudget[0]--;
            inflight.remove(key(pending.worldName(), pending.chunkX(), pending.chunkZ()));
        }
        return null;
    }

    /**
     * @param pending queued coordinates
     * @return whether that chunk is currently loaded in its world
     */
    private boolean isChunkLoaded(Pending pending) {
        World world = plugin.getServer().getWorld(pending.worldName());
        return world != null && world.isChunkLoaded(pending.chunkX(), pending.chunkZ());
    }

    /**
     * Lottery, spacing, fitness, then {@link SiteGenerator#createManagedRuin}.
     * Marks the chunk evaluated once a decision is made so old maps are not re-scanned forever.
     *
     * @param pending queued chunk coordinates
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
            if (ChunkRuinFitness.isExcludedWaterBiome(chunk, settings.excludedBiomes())) {
                return;
            }
            ChunkRuinFitness.Sample sample = ChunkRuinFitness.sample(chunk);
            if (sample.reliefBlocks() > settings.maxReliefBlocks()) {
                return;
            }
            if (sample.soilFraction() + 1e-9 < settings.minSoilFraction()) {
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
                "[Archaeo] Auto-ruin %s · rarity %s · chunk %d,%d (%s) · finds %d · relief %d · soil %.0f%%",
                site.displayLabel(),
                rarity,
                site.getChunkX(),
                site.getChunkZ(),
                site.getWorldName(),
                site.getFinds().size(),
                sample.reliefBlocks(),
                sample.soilFraction() * 100.0);
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
     * Chunk waiting for a fitness pass on the drain queue.
     *
     * @param worldName world id
     * @param chunkX chunk X
     * @param chunkZ chunk Z
     */
    private record Pending(String worldName, int chunkX, int chunkZ) {
    }
}
