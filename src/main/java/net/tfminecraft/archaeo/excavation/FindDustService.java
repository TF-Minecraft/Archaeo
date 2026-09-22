package net.tfminecraft.archaeo.excavation;

import net.tfminecraft.archaeo.config.PickSettings;
import net.tfminecraft.archaeo.model.BlockCell;
import net.tfminecraft.archaeo.model.BuriedFind;
import net.tfminecraft.archaeo.model.FindState;
import net.tfminecraft.archaeo.model.Site;
import net.tfminecraft.archaeo.model.SiteStatus;
import net.tfminecraft.archaeo.site.SiteRepository;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

/**
 * Sheds motes from find cells that already have an open face, without changing the block.
 * The tick loop is started only while at least one established ruin chunk is loaded.
 */
public class FindDustService implements Listener {
    private static final double OFFSET = 0.3;
    private static final double EXTRA = 0.1;
    private final JavaPlugin plugin;
    private final SiteRepository sites;
    private PickSettings settings;
    private BukkitTask task;
    private int tick;

    /**
     * @param plugin scheduler and event owner
     * @param sites established excavations
     * @param settings leak on/off, cadence, and mote count
     */
    public FindDustService(JavaPlugin plugin, SiteRepository sites, PickSettings settings) {
        this.plugin = plugin;
        this.sites = sites;
        this.settings = settings;
    }

    /**
     * @param settings after reload
     */
    public void setSettings(PickSettings settings) {
        this.settings = settings;
        syncTimer();
    }

    /**
     * Starts or stops the leak loop according to loaded ruin chunks.
     */
    public void start() {
        syncTimer();
    }

    /**
     * Cancels the leak loop.
     */
    public void stop() {
        stopTimer();
    }

    /**
     * Turns the scheduler on when an established ruin chunk is loaded and dust is enabled;
     * turns it off when none remain.
     */
    public void syncTimer() {
        if (settings.findDust() && hasLoadedRuinChunk()) {
            startTimer();
            return;
        }
        stopTimer();
    }

    /**
     * @param event a chunk entering memory
     */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (isEstablishedRuin(event.getChunk())) {
            syncTimer();
        }
    }

    /**
     * @param event a chunk leaving memory; {@link Chunk#isLoaded()} is still true during the event
     */
    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        if (!isEstablishedRuin(event.getChunk())) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, this::syncTimer);
    }

    /**
     * Walks loaded find cells and sheds from those that face an opening.
     */
    private void pulse() {
        if (!settings.findDust()) {
            stopTimer();
            return;
        }
        tick++;
        int interval = settings.findDustIntervalTicks();
        if (interval < 1 || tick % interval != 0) {
            return;
        }
        boolean anyLoaded = false;
        for (Site site : sites.all()) {
            if (site.getStatus() != SiteStatus.ESTABLISHED) {
                continue;
            }
            World world = Bukkit.getWorld(site.getWorldName());
            if (world == null || !world.isChunkLoaded(site.getChunkX(), site.getChunkZ())) {
                continue;
            }
            anyLoaded = true;
            boolean dirty = false;
            for (BuriedFind find : site.getFinds()) {
                if (find.getState() == FindState.LOST || find.getState() == FindState.RECOVERED) {
                    continue;
                }
                FindState next = dustFind(world, find);
                if (next != find.getState()) {
                    find.setState(next);
                    dirty = true;
                }
            }
            if (dirty) {
                sites.touch(site);
            }
        }
        if (!anyLoaded) {
            stopTimer();
        }
    }

    /**
     * Sheds from every still-present fill cell of this shape that has an open face.
     *
     * @param world ruin world
     * @param find shape in the cut
     * @return HIDDEN, PARTIAL, DISCOVERED, or LOST from what is still fill
     */
    private FindState dustFind(World world, BuriedFind find) {
        for (BlockCell cell : find.getCells()) {
            if (!world.isChunkLoaded(cell.x() >> 4, cell.z() >> 4)) {
                return find.getState();
            }
        }
        int fill = 0;
        int exposed = 0;
        List<Block> leaking = new ArrayList<>();
        for (BlockCell cell : find.getCells()) {
            Block block = world.getBlockAt(cell.x(), cell.y(), cell.z());
            if (!PrismFill.isTerrainFill(block.getType())) {
                continue;
            }
            fill++;
            if (!PrismFill.hasOpenFace(block)) {
                continue;
            }
            exposed++;
            if (find.isCleaned(cell)) {
                continue;
            }
            leaking.add(block);
        }
        if (fill == 0) {
            return FindState.LOST;
        }
        if (exposed == 0) {
            return FindState.HIDDEN;
        }
        boolean discoveredLook = exposed == fill;
        for (Block block : leaking) {
            shed(block);
        }
        return discoveredLook ? FindState.DISCOVERED : FindState.PARTIAL;
    }

    /**
     * Wax sparkles around the cube and on each open face. No dust overlay and no End Rod.
     *
     * @param block find cell that still exists as fill
     */
    private void shed(Block block) {
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        int count = Math.max(1, settings.findDustCount());
        burst(block.getWorld(), center, count, OFFSET);
        int faceCount = Math.max(1, count / 2);
        for (BlockFace face : PrismFill.openingFaces(block)) {
            Location at = block.getLocation().add(
                    0.5 + face.getModX() * 0.55,
                    0.5 + face.getModY() * 0.55,
                    0.5 + face.getModZ() * 0.55);
            burst(block.getWorld(), at, faceCount, 0.08);
        }
    }

    /**
     * {@link Particle#WAX_ON} only. Offset and extra match the look that was tuned in config.
     *
     * @param world ruin world
     * @param at spawn origin
     * @param count motes
     * @param offset spread in blocks
     */
    private void burst(World world, Location at, int count, double offset) {
        world.spawnParticle(Particle.WAX_ON, at, count, offset, offset, offset, EXTRA);
    }

    /**
     * @return whether any established site's ruin chunk is in memory
     */
    private boolean hasLoadedRuinChunk() {
        for (Site site : sites.all()) {
            if (site.getStatus() != SiteStatus.ESTABLISHED) {
                continue;
            }
            World world = Bukkit.getWorld(site.getWorldName());
            if (world != null && world.isChunkLoaded(site.getChunkX(), site.getChunkZ())) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param chunk world chunk
     * @return whether it is the ruin chunk of an established excavation
     */
    private boolean isEstablishedRuin(Chunk chunk) {
        return sites.findByChunk(chunk.getWorld().getName(), chunk.getX(), chunk.getZ())
                .filter(site -> site.getStatus() == SiteStatus.ESTABLISHED)
                .isPresent();
    }

    /**
     * Starts the 1-tick loop if it is not already running.
     */
    private void startTimer() {
        if (task != null) {
            return;
        }
        tick = 0;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::pulse, 1L, 1L);
    }

    /**
     * Cancels the 1-tick loop.
     */
    private void stopTimer() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        tick = 0;
    }
}
