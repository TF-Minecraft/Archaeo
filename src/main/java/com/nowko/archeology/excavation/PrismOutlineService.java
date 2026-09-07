package com.nowko.archeology.excavation;

import com.nowko.archeology.config.CatalogRegistry;
import com.nowko.archeology.config.LimitsSettings;
import com.nowko.archeology.model.Site;
import com.nowko.archeology.model.StratumBand;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Draws the excavation prism for one viewer for a few seconds ("Show limits" on the camp board).
 * An established dig stops answering the tracker, so the cut has to be readable on the ground:
 * where the chunk ends, how deep the work goes, and where one stratum hands over to the next.
 */
public class PrismOutlineService {
    /** Chunk edge and the floor and ceiling of the whole cut. */
    private static final Color EDGE = Color.fromRGB(255, 205, 120);
    /** Where one present stratum meets the next. */
    private static final Color SEAM = Color.fromRGB(170, 215, 255);
    /** Blocks between two dots along a ring; the corners are always drawn. */
    private static final int RING_STEP = 2;

    private final JavaPlugin plugin;
    private final CatalogRegistry catalogs;
    private final Map<UUID, BukkitTask> shows = new ConcurrentHashMap<>();

    /**
     * @param plugin scheduler owner
     * @param catalogs read live so {@code /archaeo reload} retimes running shows
     */
    public PrismOutlineService(JavaPlugin plugin, CatalogRegistry catalogs) {
        this.plugin = plugin;
        this.catalogs = catalogs;
    }

    /**
     * Cancels every running outline; called when the plugin shuts down.
     */
    public void stop() {
        for (UUID id : List.copyOf(shows.keySet())) {
            BukkitTask task = shows.remove(id);
            if (task != null) {
                task.cancel();
            }
        }
    }

    /**
     * Starts (or restarts) the outline for this viewer.
     *
     * @param player viewer; nobody else sees these particles
     * @param site excavation whose prism is drawn
     */
    public void show(Player player, Site site) {
        LimitsSettings limits = catalogs.pick().limits();
        List<Ring> rings = rings(site);
        if (rings.isEmpty()) {
            player.sendMessage("This site has no excavated strata to outline.");
            return;
        }
        cancel(player);
        int interval = Math.max(1, limits.intervalTicks());
        int frames = Math.max(1, limits.seconds() * 20 / interval);
        AtomicInteger left = new AtomicInteger(frames);
        UUID viewer = player.getUniqueId();
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || left.decrementAndGet() < 0) {
                cancelById(viewer);
                return;
            }
            draw(player, site, rings, limits);
        }, 1L, interval);
        shows.put(viewer, task);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.6f, 1.4f);
        player.sendMessage("Excavation limits shown for " + limits.seconds() + " seconds.");
    }

    /**
     * @param player viewer whose outline should stop
     */
    public void cancel(Player player) {
        cancelById(player.getUniqueId());
    }

    /**
     * @param viewer player id
     */
    private void cancelById(UUID viewer) {
        BukkitTask task = shows.remove(viewer);
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * One frame: the chunk perimeter at every ring height, plus the four vertical corners.
     *
     * @param player viewer
     * @param site excavation
     * @param rings heights to trace
     * @param limits view distance for this frame
     */
    private void draw(Player player, Site site, List<Ring> rings, LimitsSettings limits) {
        World world = player.getWorld();
        if (!world.getName().equals(site.getWorldName())) {
            return;
        }
        double reach = limits.viewDistance();
        Location eye = player.getLocation();
        if (Math.abs(eye.getX() - site.centerBlockX()) > reach
                || Math.abs(eye.getZ() - site.centerBlockZ()) > reach) {
            return;
        }
        double minX = site.getChunkX() << 4;
        double minZ = site.getChunkZ() << 4;
        double maxX = minX + 16;
        double maxZ = minZ + 16;
        for (Ring ring : rings) {
            perimeter(player, ring.y(), minX, minZ, maxX, maxZ, ring.color());
        }
        double bottom = rings.get(rings.size() - 1).y();
        double top = rings.get(0).y();
        corners(player, bottom, top, minX, minZ, maxX, maxZ);
    }

    /**
     * @param player viewer
     * @param y height of this ring
     * @param minX west edge
     * @param minZ north edge
     * @param maxX east edge
     * @param maxZ south edge
     * @param color ring colour
     */
    private void perimeter(Player player, double y, double minX, double minZ, double maxX, double maxZ, Color color) {
        for (double x = minX; x <= maxX; x += RING_STEP) {
            dot(player, x, y, minZ, color);
            dot(player, x, y, maxZ, color);
        }
        for (double z = minZ + RING_STEP; z < maxZ; z += RING_STEP) {
            dot(player, minX, y, z, color);
            dot(player, maxX, y, z, color);
        }
    }

    /**
     * @param player viewer
     * @param bottom floor of the deepest present band
     * @param top ceiling of the shallowest present band
     * @param minX west edge
     * @param minZ north edge
     * @param maxX east edge
     * @param maxZ south edge
     */
    private void corners(Player player, double bottom, double top, double minX, double minZ, double maxX, double maxZ) {
        for (double y = bottom; y <= top; y += 1) {
            dot(player, minX, y, minZ, EDGE);
            dot(player, minX, y, maxZ, EDGE);
            dot(player, maxX, y, minZ, EDGE);
            dot(player, maxX, y, maxZ, EDGE);
        }
    }

    /**
     * @param player viewer
     * @param x world X
     * @param y world Y
     * @param z world Z
     * @param color dust colour
     */
    private void dot(Player player, double x, double y, double z, Color color) {
        player.spawnParticle(
                Particle.DUST,
                x,
                y,
                z,
                1,
                0,
                0,
                0,
                0,
                new Particle.DustOptions(color, 0.9f));
    }

    /**
     * Heights worth tracing, from the surface down: the top of the shallowest band, every seam
     * between two present bands, and the floor of the deepest one.
     *
     * @param site excavation
     * @return rings ordered from the highest down, or empty when no band is present
     */
    private static List<Ring> rings(Site site) {
        List<StratumBand> present = new ArrayList<>();
        for (StratumBand band : site.getStrata().values()) {
            if (band.isPresent()) {
                present.add(band);
            }
        }
        if (present.isEmpty()) {
            return List.of();
        }
        present.sort((a, b) -> Integer.compare(b.getMaxY(), a.getMaxY()));
        List<Ring> rings = new ArrayList<>();
        rings.add(new Ring(present.get(0).getMaxY() + 1.0, EDGE));
        for (int index = 1; index < present.size(); index++) {
            rings.add(new Ring(present.get(index).getMaxY() + 1.0, SEAM));
        }
        rings.add(new Ring(present.get(present.size() - 1).getMinY(), EDGE));
        return List.copyOf(rings);
    }

    /**
     * One traced height of the prism.
     *
     * @param y world Y of the ring
     * @param color dust colour that tells an edge from a seam
     */
    private record Ring(double y, Color color) {
    }
}
