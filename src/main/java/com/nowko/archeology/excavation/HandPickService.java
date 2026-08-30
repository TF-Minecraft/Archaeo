package com.nowko.archeology.excavation;

import com.nowko.archeology.config.CatalogRegistry;
import com.nowko.archeology.config.PickSettings;
import com.nowko.archeology.config.StratumDefinition;
import com.nowko.archeology.item.HandPickItem;
import com.nowko.archeology.model.BlockCell;
import com.nowko.archeology.model.BuriedFind;
import com.nowko.archeology.model.FindState;
import com.nowko.archeology.model.Site;
import com.nowko.archeology.model.StratumBand;
import com.nowko.archeology.site.SiteRepository;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Counts Hand Pick strikes on a plugin timer and applies them when the cycle ends.
 * Vanilla mining never completes. Crack overlay follows the current cycle; on resume it matches saved fill damage.
 */
public class HandPickService {
    private static final long WARN_MS = 3000L;
    /** Mining packets stop shortly after release; this delay treats the button as up. */
    private static final int STALE_TICKS = 16;
    /** Extra strikes only while packets still arrive; must be less than {@link #STALE_TICKS}. */
    private static final int ACTIVE_HOLD_TICKS = 8;
    /** Vanilla crack overlay never reaches 1.0; Archaeo removes the block itself. */
    private static final float MAX_CRACK = 0.9f;

    private final JavaPlugin plugin;
    private final SiteRepository sites;
    private final CatalogRegistry catalogs;
    private final HandPickItem item;
    private PickSettings settings;
    private BukkitTask task;
    private int gameTick;
    private final Map<UUID, Cycle> cycles = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastWarn = new ConcurrentHashMap<>();

    /**
     * @param plugin scheduler
     * @param sites excavation dossiers
     * @param catalogs strata labels for the HUD
     * @param item Hand Pick recognition
     * @param settings jornada, stages, and strike interval
     */
    public HandPickService(
            JavaPlugin plugin,
            SiteRepository sites,
            CatalogRegistry catalogs,
            HandPickItem item,
            PickSettings settings
    ) {
        this.plugin = plugin;
        this.sites = sites;
        this.catalogs = catalogs;
        this.item = item;
        this.settings = settings;
    }

    /**
     * @param settings after reload
     */
    public void setSettings(PickSettings settings) {
        this.settings = settings;
    }

    /**
     * Starts the HUD and cycle tick.
     */
    public void start() {
        stop();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    /**
     * Ends open cycles and stops the tick.
     */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (UUID id : new ArrayList<>(cycles.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                finish(player);
            } else {
                cycles.remove(id);
            }
        }
    }

    /**
     * Starts or keeps a strike cycle on the working-face cell being mined.
     *
     * @param player holder
     * @param block open-cut cell
     */
    public void noteMining(Player player, Block block) {
        if (!settings.enabled()) {
            return;
        }
        if (!DigCut.isWorkingFace(sites, block)) {
            return;
        }
        Site site = sites.findEstablishedPrism(
                block.getWorld().getName(),
                block.getX(),
                block.getY(),
                block.getZ()).orElse(null);
        if (site == null) {
            return;
        }
        ensureJornada(site, player.getWorld());
        if (site.getJornadaPickLeft() <= 0) {
            warn(player, "The excavation work day is over.");
            return;
        }
        Cycle existing = cycles.get(player.getUniqueId());
        if (existing != null && !existing.sameCell(block)) {
            finish(player);
            existing = null;
        }
        if (existing == null) {
            cycles.put(player.getUniqueId(), new Cycle(site.getId(), block.getX(), block.getY(), block.getZ(), gameTick));
            showCracks(player, site.getId(), block, 0);
            return;
        }
        existing.lastActiveTick = gameTick;
    }

    /**
     * Puts the real block back and shows plugin cracks so vanilla never finishes the cell.
     *
     * @param player miner
     * @param block cell the client tried to finish
     */
    public void suppressVanillaBreak(Player player, Block block) {
        player.sendBlockChange(block.getLocation(), block.getBlockData());
        if (!DigCut.isWorkingFace(sites, block)) {
            player.sendBlockDamage(block.getLocation(), 0f);
            return;
        }
        noteMining(player, block);
        Cycle cycle = cycles.get(player.getUniqueId());
        int extra = cycle != null && cycle.sameCell(block) ? cycle.hits : 0;
        UUID siteId = cycle != null ? cycle.siteId : sites.findEstablishedPrism(
                block.getWorld().getName(),
                block.getX(),
                block.getY(),
                block.getZ()).map(Site::getId).orElse(null);
        if (siteId != null) {
            showCracks(player, siteId, block, extra);
        }
    }

    /**
     * Applies counted strikes (item swap, quit, or explicit release).
     *
     * @param player holder
     */
    public void finish(Player player) {
        Cycle cycle = cycles.remove(player.getUniqueId());
        if (cycle == null) {
            return;
        }
        apply(player, cycle);
    }

    /**
     * Action-bar hint when the pick is used off the cut.
     *
     * @param player holder
     */
    public void warnOffCut(Player player) {
        warn(player, "This tool is only for the open cut of an excavation.");
    }

    /**
     * @param player holder
     * @return whether a cycle is open
     */
    public boolean isCycling(Player player) {
        return cycles.containsKey(player.getUniqueId());
    }

    /**
     * Counts strikes on the configured interval while the button is down; a packet gap ends the cycle.
     */
    private void tick() {
        gameTick++;
        for (Map.Entry<UUID, Cycle> entry : new ArrayList<>(cycles.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            Cycle cycle = entry.getValue();
            if (player == null || !player.isOnline() || !item.isPick(player.getInventory().getItemInMainHand())) {
                if (player != null) {
                    finish(player);
                } else {
                    cycles.remove(entry.getKey());
                }
                continue;
            }
            if (gameTick - cycle.lastActiveTick > STALE_TICKS) {
                finish(player);
                continue;
            }
            Block block = player.getWorld().getBlockAt(cycle.x, cycle.y, cycle.z);
            if (gameTick - cycle.lastActiveTick <= ACTIVE_HOLD_TICKS) {
                int interval = Math.max(1, settings.strikeIntervalTicks());
                if (gameTick - cycle.lastHitTick >= interval) {
                    cycle.lastHitTick = gameTick;
                    cycle.hits++;
                    Site site = sites.findById(cycle.siteId).orElse(null);
                    if (site != null) {
                        onStrike(player, site, block);
                    } else {
                        playHit(block);
                    }
                }
            }
            showCracks(player, cycle.siteId, block, cycle.hits);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (item.isPick(player.getInventory().getItemInMainHand())) {
                showHud(player);
            }
        }
    }

    /**
     * Spends one jornada action and adds this cycle's hits to the cell.
     *
     * @param player striker
     * @param cycle cell and hit count
     */
    private void apply(Player player, Cycle cycle) {
        Block block = player.getWorld().getBlockAt(cycle.x, cycle.y, cycle.z);
        if (cycle.hits < 1) {
            return;
        }
        World world = player.getWorld();
        Site site = sites.findById(cycle.siteId).orElse(null);
        if (site == null || !DigCut.isWorkingFace(sites, block)) {
            warn(player, "Use this on the open cut of an excavation.");
            return;
        }
        ensureJornada(site, world);
        if (site.getJornadaPickLeft() <= 0) {
            warn(player, "The excavation work day is over.");
            return;
        }
        BlockCell cell = new BlockCell(cycle.x, cycle.y, cycle.z);
        int stages = site.addFillDamage(cell, cycle.hits);
        site.setJornadaPickLeft(site.getJornadaPickLeft() - 1);
        if (stages >= settings.blockStages()) {
            Material broken = block.getType();
            site.clearFillDamage(cell);
            block.setType(Material.AIR, false);
            world.playSound(block.getLocation(), fillSound(broken, true), SoundCategory.BLOCKS, 1f, 1f);
            player.sendBlockDamage(block.getLocation(), 0f);
            site.findAt(cell).ifPresent(find -> harmFind(find, settings.conservationLossOnRemove()));
        }
        sites.save(site);
    }

    /**
     * One counted strike: fill sound, or find detect / conservation loss if this cell is a find.
     *
     * @param player striker
     * @param site excavation
     * @param block cell
     */
    private void onStrike(Player player, Site site, Block block) {
        BuriedFind find = site.findAt(new BlockCell(block.getX(), block.getY(), block.getZ())).orElse(null);
        if (find == null) {
            playHit(block);
            return;
        }
        if (find.getState() == FindState.HIDDEN) {
            find.setState(FindState.PARTIAL);
            player.sendMessage("Archaeological material detected. Extent unknown.");
            block.getWorld().playSound(
                    block.getLocation(),
                    Sound.BLOCK_AMETHYST_BLOCK_HIT,
                    SoundCategory.BLOCKS,
                    1f,
                    1.2f);
            sites.save(site);
            return;
        }
        harmFind(find, settings.conservationLossPerStrike());
        warn(player, "The archaeological material may be being altered.");
        block.getWorld().playSound(
                block.getLocation(),
                Sound.BLOCK_CHAIN_HIT,
                SoundCategory.BLOCKS,
                0.9f,
                0.8f);
        sites.save(site);
    }

    /**
     * Lowers conservation and marks the find damaged below the configured threshold.
     *
     * @param find artifact
     * @param loss points to subtract
     */
    private void harmFind(BuriedFind find, int loss) {
        if (loss <= 0) {
            return;
        }
        find.setConservation(find.getConservation() - loss);
        if (find.getConservation() < settings.damagedBelowPercent()) {
            find.setDamaged(true);
        }
    }

    /**
     * Overlay cracks from persisted fill plus strikes in the current cycle.
     * Not sent on release: the client fades the last stage on its own.
     *
     * @param player miner
     * @param siteId excavation
     * @param block cell
     * @param extraHits strikes not yet written to the site
     */
    private void showCracks(Player player, UUID siteId, Block block, int extraHits) {
        player.sendBlockDamage(block.getLocation(), crackProgress(siteId, block, extraHits));
    }

    /**
     * @param siteId excavation
     * @param block cell
     * @param extraHits uncommitted strikes
     * @return crack overlay in {@code (0, MAX_CRACK]}
     */
    private float crackProgress(UUID siteId, Block block, int extraHits) {
        int stages = Math.max(1, settings.blockStages());
        int stored = 0;
        if (siteId != null) {
            Site site = sites.findById(siteId).orElse(null);
            if (site != null) {
                stored = site.getFillDamage().getOrDefault(new BlockCell(block.getX(), block.getY(), block.getZ()), 0);
            }
        }
        float progress = (stored + extraHits) / (float) stages;
        if (progress <= 0f) {
            return 0f;
        }
        return Math.min(MAX_CRACK, progress);
    }

    /**
     * Refills today's Hand Pick budget on an established excavation.
     *
     * @param site established site
     * @param world used for the current Minecraft day id
     * @return actions after refill
     */
    public int refillJornada(Site site, World world) {
        ensureJornada(site, world);
        site.setJornadaPickLeft(settings.jornadaActions());
        sites.save(site);
        return site.getJornadaPickLeft();
    }

    /**
     * Restores pick actions when the world day rolls over.
     *
     * @param site excavation
     * @param world site world
     */
    void ensureJornada(Site site, World world) {
        long day = world.getFullTime() / 24000L;
        if (site.getJornadaWorldDay() != day) {
            site.setJornadaWorldDay(day);
            site.setJornadaPickLeft(settings.jornadaActions());
        }
    }

    /**
     * @param player viewer
     */
    private void showHud(Player player) {
        Cycle cycle = cycles.get(player.getUniqueId());
        Block target = player.getTargetBlockExact(6);
        if (target == null && cycle == null) {
            return;
        }
        if (cycle != null) {
            target = player.getWorld().getBlockAt(cycle.x, cycle.y, cycle.z);
        }
        Site site = sites.findEstablishedPrism(
                target.getWorld().getName(),
                target.getX(),
                target.getY(),
                target.getZ()).orElse(null);
        if (site == null) {
            return;
        }
        ensureJornada(site, player.getWorld());
        StratumBand band = site.stratumAt(target.getY());
        String layer = band == null ? "—" : band.getId();
        StratumDefinition definition = band == null ? null : catalogs.stratum(band.getId());
        String antiquity = definition == null ? "" : " · " + definition.antiquity();
        int extra = 0;
        if (cycle != null && cycle.sameCell(target)) {
            extra = cycle.hits;
        }
        BlockCell cell = new BlockCell(target.getX(), target.getY(), target.getZ());
        int stored = site.getFillDamage().getOrDefault(cell, 0);
        int stages = Math.max(1, settings.blockStages());
        String text = "STRATUM " + layer + antiquity
                + "  ⛏ " + site.getJornadaPickLeft()
                + "  ·  " + (stored + extra) + "/" + stages;
        BuriedFind aimed = site.findAt(cell).orElse(null);
        if (aimed != null && aimed.getState() != FindState.HIDDEN) {
            text = text + "  ·  " + aimed.getConservation() + "%";
        }
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(text));
    }

    /**
     * @param block struck cell
     */
    private void playHit(Block block) {
        block.getWorld().playSound(
                block.getLocation(),
                fillSound(block.getType(), false),
                SoundCategory.BLOCKS,
                0.85f,
                1f);
        block.getWorld().spawnParticle(
                Particle.BLOCK,
                block.getLocation().add(0.5, 0.5, 0.5),
                8,
                0.25,
                0.25,
                0.25,
                0.04,
                block.getBlockData());
    }

    /**
     * @param material fill type
     * @param broken whether the cell was removed
     * @return vanilla-like hit or break sound
     */
    private static Sound fillSound(Material material, boolean broken) {
        boolean soft = Tag.DIRT.isTagged(material)
                || Tag.SAND.isTagged(material)
                || material == Material.GRAVEL
                || material == Material.CLAY
                || material == Material.MUD;
        if (broken) {
            return soft ? Sound.BLOCK_GRAVEL_BREAK : Sound.BLOCK_STONE_BREAK;
        }
        return soft ? Sound.BLOCK_GRAVEL_HIT : Sound.BLOCK_STONE_HIT;
    }

    /**
     * @param player viewer
     * @param message English line
     */
    private void warn(Player player, String message) {
        long now = System.currentTimeMillis();
        Long previous = lastWarn.get(player.getUniqueId());
        if (previous != null && now - previous < WARN_MS) {
            return;
        }
        lastWarn.put(player.getUniqueId(), now);
        player.sendMessage(message);
    }

    /**
     * One player's current left-click hold on a cell.
     */
    private static final class Cycle {
        private final UUID siteId;
        private final int x;
        private final int y;
        private final int z;
        private int hits;
        private int lastActiveTick;
        private int lastHitTick;

        /**
         * @param siteId excavation
         * @param x block X
         * @param y block Y
         * @param z block Z
         * @param tick current service tick
         */
        private Cycle(UUID siteId, int x, int y, int z, int tick) {
            this.siteId = siteId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.lastActiveTick = tick;
            this.lastHitTick = tick;
        }

        /**
         * @param block world cell
         * @return whether this cycle is still on that cell
         */
        private boolean sameCell(Block block) {
            return block.getX() == x && block.getY() == y && block.getZ() == z;
        }
    }
}
