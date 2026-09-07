package com.nowko.archeology.excavation;

import com.nowko.archeology.config.CatalogRegistry;
import com.nowko.archeology.config.LimitsSettings;
import com.nowko.archeology.model.Site;
import com.nowko.archeology.model.StratumBand;
import org.bukkit.Axis;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Draws the excavation prism for one viewer for a few seconds ("Show limits" on the camp board).
 * An established dig stops answering the tracker, so the cut has to be readable on the ground:
 * where the chunk ends, how deep the work goes, and where one stratum hands over to the next.
 *
 * <p>The edges are thin {@link BlockDisplay} bars rather than particles, because a dig site is
 * never a flat lawn: particles are depth-tested and any spoil heap, wall, or hillside hides them.
 * A glowing entity, on the other hand, has its outline drawn through blocks by the vanilla client,
 * so a buried edge stays readable. The bars are spawned invisible to the world and then shown to
 * the one player who asked, and they are not persisted, so a hard shutdown leaves nothing behind.
 */
public class PrismOutlineService {
    /** Chunk edge and the floor and ceiling of the whole cut. */
    private static final Color EDGE_GLOW = Color.fromRGB(255, 205, 120);
    /** Where one present stratum meets the next. */
    private static final Color SEAM_GLOW = Color.fromRGB(170, 215, 255);
    /** Bar block seen where nothing hides it; the glow carries the colour through terrain. */
    private static final Material EDGE_BAR = Material.ORANGE_CONCRETE;
    private static final Material SEAM_BAR = Material.LIGHT_BLUE_CONCRETE;
    /** Blocks across an archaeological chunk. */
    private static final double SPAN = 16.0;
    /** Display view range is a multiple of this many blocks. */
    private static final double VIEW_RANGE_UNIT = 64.0;
    /** Slack added to the culling box so a bar seen end-on is not dropped by the client. */
    private static final float CULL_MARGIN = 2.0f;

    private final JavaPlugin plugin;
    private final CatalogRegistry catalogs;
    private final Map<UUID, List<Entity>> shows = new ConcurrentHashMap<>();

    /**
     * @param plugin scheduler owner and holder of the per-player visibility grants
     * @param catalogs read live so {@code /archaeo reload} retimes the next show
     */
    public PrismOutlineService(JavaPlugin plugin, CatalogRegistry catalogs) {
        this.plugin = plugin;
        this.catalogs = catalogs;
    }

    /**
     * Clears every running outline; called when the plugin shuts down.
     */
    public void stop() {
        for (UUID viewer : List.copyOf(shows.keySet())) {
            cancelById(viewer);
        }
    }

    /**
     * Starts (or restarts) the outline for this viewer.
     *
     * @param player viewer; nobody else is shown these bars
     * @param site excavation whose prism is drawn
     */
    public void show(Player player, Site site) {
        World world = player.getWorld();
        if (!world.getName().equals(site.getWorldName())) {
            player.sendMessage("That excavation is in another world.");
            return;
        }
        List<Ring> rings = rings(site);
        if (rings.isEmpty()) {
            player.sendMessage("This site has no excavated strata to outline.");
            return;
        }
        cancel(player);
        LimitsSettings limits = catalogs.pick().limits();
        List<Entity> bars = new ArrayList<>();
        paint(player, world, site, rings, limits, bars);
        if (bars.isEmpty()) {
            return;
        }
        shows.put(player.getUniqueId(), bars);
        UUID viewer = player.getUniqueId();
        plugin.getServer().getScheduler().runTaskLater(
                plugin, () -> cancelById(viewer), Math.max(1L, limits.seconds() * 20L));
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.6f, 1.4f);
        player.sendMessage("Excavation limits shown for " + limits.seconds() + " seconds.");
    }

    /**
     * @param player viewer whose outline should be removed now
     */
    public void cancel(Player player) {
        cancelById(player.getUniqueId());
    }

    /**
     * @param viewer player id
     */
    private void cancelById(UUID viewer) {
        List<Entity> bars = shows.remove(viewer);
        if (bars == null) {
            return;
        }
        for (Entity bar : bars) {
            bar.remove();
        }
    }

    /**
     * The box of the cut plus one ring per stratum seam: four horizontal bars at every traced
     * height, and four vertical bars joining the ceiling to the floor.
     *
     * @param player viewer
     * @param world site world
     * @param site excavation
     * @param rings heights to trace, highest first
     * @param limits bar thickness and view range
     * @param bars spawned entities, for later removal
     */
    private void paint(
            Player player,
            World world,
            Site site,
            List<Ring> rings,
            LimitsSettings limits,
            List<Entity> bars
    ) {
        double minX = site.getChunkX() << 4;
        double minZ = site.getChunkZ() << 4;
        double maxX = minX + SPAN;
        double maxZ = minZ + SPAN;
        for (Ring ring : rings) {
            bar(player, world, minX, ring.y(), minZ, Axis.X, SPAN, ring.seam(), limits, bars);
            bar(player, world, minX, ring.y(), maxZ, Axis.X, SPAN, ring.seam(), limits, bars);
            bar(player, world, minX, ring.y(), minZ, Axis.Z, SPAN, ring.seam(), limits, bars);
            bar(player, world, maxX, ring.y(), minZ, Axis.Z, SPAN, ring.seam(), limits, bars);
        }
        double bottom = rings.get(rings.size() - 1).y();
        double height = rings.get(0).y() - bottom;
        if (height <= 0) {
            return;
        }
        bar(player, world, minX, bottom, minZ, Axis.Y, height, false, limits, bars);
        bar(player, world, minX, bottom, maxZ, Axis.Y, height, false, limits, bars);
        bar(player, world, maxX, bottom, minZ, Axis.Y, height, false, limits, bars);
        bar(player, world, maxX, bottom, maxZ, Axis.Y, height, false, limits, bars);
    }

    /**
     * One edge: a block display squashed to a thread on its two short axes and stretched along the
     * third. The declared display size is what the client culls against, so it covers the whole bar
     * instead of its anchor block, and the brightness override keeps the edge lit down in the cut.
     *
     * @param player viewer the bar is shown to
     * @param world site world
     * @param x start X of the edge
     * @param y start Y of the edge
     * @param z start Z of the edge
     * @param axis direction the bar runs along
     * @param length blocks covered along {@code axis}
     * @param seam whether this edge is a stratum seam rather than the outer box
     * @param limits bar thickness and view range
     * @param bars spawned entities, for later removal
     */
    private void bar(
            Player player,
            World world,
            double x,
            double y,
            double z,
            Axis axis,
            double length,
            boolean seam,
            LimitsSettings limits,
            List<Entity> bars
    ) {
        float thin = (float) Math.max(0.01, limits.thickness());
        float span = (float) length;
        Vector3f translation;
        Vector3f scale;
        switch (axis) {
            case X -> {
                translation = new Vector3f(0, -thin / 2, -thin / 2);
                scale = new Vector3f(span, thin, thin);
            }
            case Y -> {
                translation = new Vector3f(-thin / 2, 0, -thin / 2);
                scale = new Vector3f(thin, span, thin);
            }
            default -> {
                translation = new Vector3f(-thin / 2, -thin / 2, 0);
                scale = new Vector3f(thin, thin, span);
            }
        }
        Material material = seam ? SEAM_BAR : EDGE_BAR;
        Color glow = seam ? SEAM_GLOW : EDGE_GLOW;
        float cull = span + CULL_MARGIN;
        float range = (float) (limits.viewDistance() / VIEW_RANGE_UNIT);
        BlockDisplay display = world.spawn(new Location(world, x, y, z), BlockDisplay.class, entity -> {
            entity.setVisibleByDefault(false);
            entity.setPersistent(false);
            entity.setBlock(material.createBlockData());
            entity.setGlowing(true);
            entity.setGlowColorOverride(glow);
            entity.setBrightness(new Display.Brightness(15, 15));
            entity.setShadowRadius(0f);
            entity.setShadowStrength(0f);
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setDisplayWidth(cull);
            entity.setDisplayHeight(cull);
            entity.setViewRange(range);
            entity.setTransformation(new Transformation(translation, new Quaternionf(), scale, new Quaternionf()));
        });
        player.showEntity(plugin, display);
        bars.add(display);
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
        rings.add(new Ring(present.get(0).getMaxY() + 1.0, false));
        for (int index = 1; index < present.size(); index++) {
            rings.add(new Ring(present.get(index).getMaxY() + 1.0, true));
        }
        rings.add(new Ring(present.get(present.size() - 1).getMinY(), false));
        return List.copyOf(rings);
    }

    /**
     * One traced height of the prism.
     *
     * @param y world Y of the ring
     * @param seam whether it is a stratum seam rather than the ceiling or floor of the cut
     */
    private record Ring(double y, boolean seam) {
    }
}
