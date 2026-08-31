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
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Hand Pick cycles: empty fill uses cue clings then a ready ting; finds still warn on contact.
 * Vanilla mining and crack overlay never advance.
 */
public class HandPickService {
    private static final long WARN_MS = 3000L;
    /** Mining packets stop shortly after release; this delay treats the button as up. */
    private static final int STALE_TICKS = 16;
    /** Extra strikes only while packets still arrive; must be less than {@link #STALE_TICKS}. */
    private static final int ACTIVE_HOLD_TICKS = 8;

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
     * @param settings jornada, empty-fill window, and find risk
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
            cycles.put(
                    player.getUniqueId(),
                    new Cycle(site.getId(), block.getX(), block.getY(), block.getZ(), gameTick, rollCueClings()));
            clearCrack(player, block);
            return;
        }
        existing.lastActiveTick = gameTick;
    }

    /**
     * Puts the real block back and clears crack overlay so vanilla never finishes the cell.
     *
     * @param player miner
     * @param block cell the client tried to finish
     */
    public void suppressVanillaBreak(Player player, Block block) {
        player.sendBlockChange(block.getLocation(), block.getBlockData());
        clearCrack(player, block);
        if (!DigCut.isWorkingFace(sites, block)) {
            return;
        }
        noteMining(player, block);
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
        clearCrack(player, player.getWorld().getBlockAt(cycle.x, cycle.y, cycle.z));
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
            clearCrack(player, block);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (item.isPick(player.getInventory().getItemInMainHand())) {
                showHud(player);
            }
        }
    }

    /**
     * Spends jornada when the cycle did work: find-cell stages, or empty fill lifted (on time or late).
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
        if (site.findAt(cell).isPresent()) {
            applyFindFill(player, site, block, cell, cycle);
            return;
        }
        applyEmptyFill(player, site, block, cycle);
    }

    /**
     * Find cells still use hidden fill stages; the pick does not drop the artifact.
     *
     * @param player striker
     * @param site excavation
     * @param block cell
     * @param cell coordinates
     * @param cycle this hold
     */
    private void applyFindFill(Player player, Site site, Block block, BlockCell cell, Cycle cycle) {
        int stages = site.addFillDamage(cell, cycle.hits);
        site.setJornadaPickLeft(site.getJornadaPickLeft() - 1);
        if (stages >= settings.blockStages()) {
            liftBlock(player, site, block, cell, true);
        }
        sites.save(site);
    }

    /**
     * Empty fill: early does nothing; on-time lifts this cell; late also breaks the block below.
     *
     * @param player striker
     * @param site excavation
     * @param block cell
     * @param cycle this hold
     */
    private void applyEmptyFill(Player player, Site site, Block block, Cycle cycle) {
        if (!cycle.ready) {
            return;
        }
        // lastActiveTick is last mining packet (release); gameTick here is delayed by STALE_TICKS.
        boolean overdue = cycle.lastActiveTick - cycle.readyTick > settings.readyWindowTicks();
        boolean late = cycle.late || overdue;
        site.setJornadaPickLeft(site.getJornadaPickLeft() - 1);
        BlockCell cell = new BlockCell(cycle.x, cycle.y, cycle.z);
        liftBlock(player, site, block, cell, false);
        if (late) {
            smashBelow(player, site, block);
        }
        sites.save(site);
    }

    /**
     * Turns the cell to air and plays a break sound.
     *
     * @param player miner
     * @param site excavation
     * @param block cell
     * @param cell coordinates
     * @param findCell whether a find occupies this cell (extra conservation)
     */
    private void liftBlock(Player player, Site site, Block block, BlockCell cell, boolean findCell) {
        Material broken = block.getType();
        site.clearFillDamage(cell);
        block.setType(Material.AIR, false);
        block.getWorld().playSound(block.getLocation(), fillSound(broken, true), SoundCategory.BLOCKS, 1f, 1f);
        player.sendBlockDamage(block.getLocation(), 0f, player);
        if (findCell) {
            site.findAt(cell).ifPresent(find -> {
                harmFind(find, settings.conservationLossOnRemove());
                noteIfFindDestroyed(player, block.getWorld(), find);
            });
        }
    }

    /**
     * Late release: the blow goes through and breaks the cell under the one that was lifted.
     *
     * @param player miner
     * @param site excavation
     * @param lifted cell that just became air
     */
    private void smashBelow(Player player, Site site, Block lifted) {
        Block below = lifted.getRelative(BlockFace.DOWN);
        if (!site.isInPrism(below.getX(), below.getY(), below.getZ())
                || !PrismFill.isTerrainFill(below.getType())) {
            playOverforce(below);
            return;
        }
        BlockCell cell = new BlockCell(below.getX(), below.getY(), below.getZ());
        BuriedFind find = site.findAt(cell).orElse(null);
        Material broken = below.getType();
        site.clearFillDamage(cell);
        below.setType(Material.AIR, false);
        below.getWorld().playSound(below.getLocation(), fillSound(broken, true), SoundCategory.BLOCKS, 1f, 1f);
        below.getWorld().spawnParticle(
                Particle.BLOCK,
                below.getLocation().add(0.5, 0.5, 0.5),
                28,
                0.3,
                0.3,
                0.3,
                0.08,
                broken.createBlockData());
        player.sendBlockDamage(below.getLocation(), 0f, player);
        if (find == null) {
            playOverforce(below);
            return;
        }
        playFindShatter(below);
        harmFind(find, settings.conservationLossOnRemove());
        if (!noteIfFindDestroyed(player, below.getWorld(), find)) {
            warn(player, "The archaeological material may be being altered.");
        }
    }

    /**
     * Extra smash after the vanilla break so overshoot feels like too much force.
     *
     * @param block cell that broke (or would have)
     */
    private void playOverforce(Block block) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!plugin.isEnabled()) {
                return;
            }
            block.getWorld().playSound(
                    block.getLocation(),
                    Sound.ITEM_MACE_SMASH_GROUND,
                    SoundCategory.BLOCKS,
                    0.9f,
                    0.7f);
        }, 2L);
    }

    /**
     * Artifact cell smashed: shatter after the fill break.
     *
     * @param block cell that held a find
     */
    private void playFindShatter(Block block) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!plugin.isEnabled()) {
                return;
            }
            block.getWorld().playSound(
                    block.getLocation(),
                    Sound.BLOCK_DECORATED_POT_SHATTER,
                    SoundCategory.BLOCKS,
                    1f,
                    0.85f);
            block.getWorld().playSound(
                    block.getLocation(),
                    Sound.ENTITY_ITEM_BREAK,
                    SoundCategory.BLOCKS,
                    0.85f,
                    0.65f);
        }, 2L);
    }

    /**
     * If every fill cell of the shape is gone, the find cannot be recovered.
     *
     * @param player miner
     * @param world site world
     * @param find shape
     * @return whether this call marked the find lost
     */
    private boolean noteIfFindDestroyed(Player player, World world, BuriedFind find) {
        if (find.getState() == FindState.LOST) {
            return false;
        }
        if (!allFillGone(world, find)) {
            return false;
        }
        find.setState(FindState.LOST);
        find.setConservation(0);
        find.setDamaged(true);
        player.sendMessage("Those remains were destroyed. Nothing can be recovered from them.");
        return true;
    }

    /**
     * @param world site world
     * @param find shape
     * @return whether no cell still holds natural fill
     */
    private static boolean allFillGone(World world, BuriedFind find) {
        for (BlockCell cell : find.getCells()) {
            if (PrismFill.isTerrainFill(world.getBlockAt(cell.x(), cell.y(), cell.z()).getType())) {
                return false;
            }
        }
        return true;
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
            noteEmptyFillStrike(player, block);
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
     * Empty fill: 1–N soft clings, then the ready chime; extra strikes after the window mark late.
     *
     * @param player miner
     * @param block cell
     */
    private void noteEmptyFillStrike(Player player, Block block) {
        Cycle cycle = cycles.get(player.getUniqueId());
        if (cycle == null || !cycle.sameCell(block)) {
            playHit(block);
            return;
        }
        if (!cycle.ready && cycle.hits <= cycle.cueClings) {
            playHit(block);
            playSoftCling(block);
            return;
        }
        if (!cycle.ready) {
            cycle.ready = true;
            cycle.readyTick = gameTick;
            playReadyCling(block);
            return;
        }
        playHit(block);
        if (gameTick - cycle.readyTick > settings.readyWindowTicks()) {
            cycle.late = true;
        }
    }

    /**
     * Soft cue: the ready chime is coming, but not which beat.
     *
     * @param block struck cell
     */
    private void playSoftCling(Block block) {
        block.getWorld().playSound(
                block.getLocation(),
                Sound.BLOCK_NOTE_BLOCK_CHIME,
                SoundCategory.BLOCKS,
                0.4f,
                0.85f);
    }

    /**
     * Louder chime: this cube can come out if released in the window.
     *
     * @param block struck cell
     */
    private void playReadyCling(Block block) {
        block.getWorld().playSound(
                block.getLocation(),
                Sound.BLOCK_NOTE_BLOCK_CHIME,
                SoundCategory.BLOCKS,
                1f,
                1.45f);
    }

    /**
     * @return how many soft clings this hold will play before ready
     */
    private int rollCueClings() {
        int min = Math.max(1, settings.cueClingsMin());
        int max = Math.max(min, settings.cueClingsMax());
        return min + ThreadLocalRandom.current().nextInt(max - min + 1);
    }

    /**
     * Stops vanilla and plugin crack overlay so break stages cannot be read from the texture.
     *
     * @param player viewer
     * @param block cell
     */
    private void clearCrack(Player player, Block block) {
        player.sendBlockDamage(block.getLocation(), 0f, player);
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
        BlockCell cell = new BlockCell(target.getX(), target.getY(), target.getZ());
        String text = "STRATUM " + layer + antiquity + "  ⛏ " + site.getJornadaPickLeft();
        BuriedFind aimed = site.findAt(cell).orElse(null);
        if (aimed != null && aimed.getState() != FindState.HIDDEN && aimed.getState() != FindState.LOST) {
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
        private boolean ready;
        private int readyTick = -1;
        private boolean late;
        private final int cueClings;

        /**
         * @param siteId excavation
         * @param x block X
         * @param y block Y
         * @param z block Z
         * @param tick current service tick
         * @param cueClings soft clings before ready on this hold
         */
        private Cycle(UUID siteId, int x, int y, int z, int tick, int cueClings) {
            this.siteId = siteId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.lastActiveTick = tick;
            this.lastHitTick = tick;
            this.cueClings = cueClings;
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
