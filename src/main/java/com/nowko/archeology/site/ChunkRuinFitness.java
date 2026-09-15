package com.nowko.archeology.site;

import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;

import java.util.Arrays;
import java.util.Set;

/**
 * Cheap surface checks that decide whether a chunk may host an auto-ruin.
 * Heights and soil use a sparse grid that only peeks a few blocks down from the heightmap
 * (canopy / trunks), never a full column walk. Burial fitness is left to
 * {@link SiteGenerator#createManagedRuin}. Open water is rejected by biome id.
 */
public final class ChunkRuinFitness {
    /** Local X/Z step for surface samples (4 → 4×4 = 16 columns). */
    private static final int SURFACE_STEP = 4;
    /**
     * How far below the heightmap we may walk to skip leaves/logs before reading “surface” ground.
     * Enough for a canopy; not a cave shaft search.
     */
    private static final int SURFACE_DROP = 6;

    private ChunkRuinFitness() {
    }

    /**
     * True when the chunk sits in an excluded water biome from config. Samples the centre and four
     * corners at sea level so a mixed coastal chunk that is mostly ocean still fails.
     * An empty {@code excluded} set disables the gate.
     *
     * @param chunk loaded chunk
     * @param excluded biome path keys from {@code auto-ruins.excluded-biomes}
     * @return whether auto-spawn should skip this chunk for biome
     */
    public static boolean isExcludedWaterBiome(Chunk chunk, Set<String> excluded) {
        if (excluded == null || excluded.isEmpty()) {
            return false;
        }
        World world = chunk.getWorld();
        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;
        int y = world.getSeaLevel();
        return isExcludedWaterBiome(world, baseX + 8, y, baseZ + 8, excluded)
                || isExcludedWaterBiome(world, baseX, y, baseZ, excluded)
                || isExcludedWaterBiome(world, baseX + 15, y, baseZ, excluded)
                || isExcludedWaterBiome(world, baseX, y, baseZ + 15, excluded)
                || isExcludedWaterBiome(world, baseX + 15, y, baseZ + 15, excluded);
    }

    /**
     * Sparse surface pass: relief from shallow height samples, soil as the share of those samples
     * whose top ground is shovel-mineable.
     *
     * @param chunk loaded chunk
     * @return metrics for the auto-spawner filters
     */
    public static Sample sample(Chunk chunk) {
        World world = chunk.getWorld();
        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;
        int count = ((16 + SURFACE_STEP - 1) / SURFACE_STEP) * ((16 + SURFACE_STEP - 1) / SURFACE_STEP);
        int[] heights = new int[count];
        int soil = 0;
        int usable = 0;
        int i = 0;
        for (int lx = 0; lx < 16; lx += SURFACE_STEP) {
            for (int lz = 0; lz < 16; lz += SURFACE_STEP) {
                int x = baseX + lx;
                int z = baseZ + lz;
                int y = surfaceGroundY(world, x, z);
                heights[i++] = y;
                if (y < world.getMinHeight()) {
                    continue;
                }
                usable++;
                Block ground = world.getBlockAt(x, y, z);
                if (Tag.MINEABLE_SHOVEL.isTagged(ground.getType())) {
                    soil++;
                }
            }
        }
        int[] sorted = Arrays.copyOf(heights, heights.length);
        Arrays.sort(sorted);
        int median = GroundDatum.median(heights);
        int p10 = sorted[Math.max(0, (int) Math.floor(0.10 * (sorted.length - 1)))];
        int p90 = sorted[Math.min(sorted.length - 1, (int) Math.ceil(0.90 * (sorted.length - 1)))];
        int relief = p90 - p10;
        double soilFraction = usable == 0 ? 0.0 : soil / (double) usable;
        return new Sample(median, relief, soilFraction);
    }

    /**
     * Top solid ground near the heightmap: at most {@link #SURFACE_DROP} steps down, skipping the
     * same non-ground materials as {@link GroundDatum} (leaves, logs, bamboo, …).
     *
     * @param world ruin world
     * @param x block X
     * @param z block Z
     * @return surface ground Y, or {@code minHeight - 1} when none was found in the shallow window
     */
    static int surfaceGroundY(World world, int x, int z) {
        int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        int minY = world.getMinHeight();
        int floor = Math.max(minY, y - SURFACE_DROP);
        while (y >= floor) {
            if (GroundDatum.isGround(world.getBlockAt(x, y, z))) {
                return y;
            }
            y--;
        }
        return minY - 1;
    }

    /**
     * @param world ruin world
     * @param x block X
     * @param y block Y
     * @param z block Z
     * @param excluded biome path keys from config
     * @return whether that cell's biome is in {@code excluded}
     */
    private static boolean isExcludedWaterBiome(World world, int x, int y, int z, Set<String> excluded) {
        NamespacedKey key = biomeKey(world.getBiome(x, y, z));
        return key != null && excluded.contains(key.getKey());
    }

    /**
     * Same Paper 1.21.10-safe key read as {@link SiteGenerator}.
     *
     * @param biome chunk biome
     * @return namespaced key, or {@code null} if unregistered
     */
    @SuppressWarnings("deprecation")
    private static NamespacedKey biomeKey(Biome biome) {
        try {
            Object value = biome.getClass().getMethod("getKeyOrNull").invoke(biome);
            if (value instanceof NamespacedKey key) {
                return key;
            }
        } catch (ReflectiveOperationException ignored) {
            // Paper 1.21.10: RegistryAware helpers are absent
        }
        return biome.getKey();
    }

    /**
     * One cheap fitness pass over a chunk.
     *
     * @param medianY sparse surface median
     * @param reliefBlocks {@code p90 − p10} of the sparse surface heights
     * @param soilFraction share of sparse surface samples with shovel-mineable ground
     */
    public record Sample(int medianY, int reliefBlocks, double soilFraction) {
    }
}
