package com.nowko.archeology.site;

import com.nowko.archeology.config.CatalogRegistry;
import com.nowko.archeology.config.StratumDefinition;
import com.nowko.archeology.excavation.PrismFill;
import org.bukkit.Chunk;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Arrays;

/**
 * Cheap surface and burial checks that decide whether a newly generated chunk may host a ruin.
 * Reuses the same ground rules as {@link GroundDatum} and the same soil tag as prospecting.
 * Columns with water (or lava) on top of the ground count as flooded so open ocean floors fail.
 */
public final class ChunkRuinFitness {
    private ChunkRuinFitness() {
    }

    /**
     * Samples every column once: relief, soil cover, flood cover, median datum, and buryable cells.
     *
     * @param chunk loaded chunk
     * @param catalog strata depths and {@code find-min-cover}
     * @return metrics for the auto-spawner filters
     */
    public static Sample sample(Chunk chunk, CatalogRegistry catalog) {
        World world = chunk.getWorld();
        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;
        int[] heights = new int[256];
        int soil = 0;
        int flooded = 0;
        int i = 0;
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = baseX + lx;
                int z = baseZ + lz;
                int y = GroundDatum.columnGroundY(world, x, z);
                heights[i++] = y;
                Block ground = world.getBlockAt(x, y, z);
                if (Tag.MINEABLE_SHOVEL.isTagged(ground.getType())) {
                    soil++;
                }
                if (isFloodedColumn(world, x, y, z)) {
                    flooded++;
                }
            }
        }
        int[] sorted = Arrays.copyOf(heights, heights.length);
        Arrays.sort(sorted);
        int median = GroundDatum.median(heights);
        int p10 = sorted[Math.max(0, (int) Math.floor(0.10 * (sorted.length - 1)))];
        int p90 = sorted[Math.min(sorted.length - 1, (int) Math.ceil(0.90 * (sorted.length - 1)))];
        int relief = p90 - p10;
        double soilFraction = soil / 256.0;
        double floodedFraction = flooded / 256.0;
        int buried = countBuriedCells(world, chunk.getX(), chunk.getZ(), median, catalog);
        return new Sample(median, relief, soilFraction, floodedFraction, buried);
    }

    /**
     * A column is flooded when liquid sits on the ground, or within a short stack above it before air.
     * That catches seafloor sand under open ocean without rejecting a dry beach that only touches the tide.
     *
     * @param world ruin world
     * @param x block X
     * @param groundY solid ground Y from {@link GroundDatum}
     * @param z block Z
     * @return whether this column is underwater at the surface
     */
    private static boolean isFloodedColumn(World world, int x, int groundY, int z) {
        int maxY = Math.min(world.getMaxHeight() - 1, groundY + 8);
        for (int y = groundY + 1; y <= maxY; y++) {
            Block block = world.getBlockAt(x, y, z);
            if (block.isLiquid()) {
                return true;
            }
            if (!block.getType().isAir() && !Tag.REPLACEABLE.isTagged(block.getType())) {
                return false;
            }
        }
        return false;
    }

    /**
     * Counts cells in always-present strata bands that are fill under enough cover.
     * Stratum IV is ignored here so the fitness gate does not depend on a random IV roll.
     *
     * @param world ruin world
     * @param chunkX chunk X
     * @param chunkZ chunk Z
     * @param datumY median ground Y
     * @param catalog strata and cover
     * @return buried cell count across always-present bands
     */
    static int countBuriedCells(World world, int chunkX, int chunkZ, int datumY, CatalogRegistry catalog) {
        int cover = catalog.findMinCover();
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        int count = 0;
        for (StratumDefinition definition : catalog.strataInOrder()) {
            if (!definition.alwaysPresent()) {
                continue;
            }
            int maxY = datumY - definition.depthMin();
            int minY = datumY - definition.depthMax();
            for (int x = minX; x <= minX + 15; x++) {
                for (int z = minZ; z <= minZ + 15; z++) {
                    for (int y = minY; y <= maxY; y++) {
                        if (isBuried(world, x, y, z, cover)) {
                            count++;
                        }
                    }
                }
            }
        }
        return count;
    }

    /**
     * @param world ruin world
     * @param x block X
     * @param y block Y
     * @param z block Z
     * @param cover fill blocks required above the cell
     * @return whether the cell is excavation fill under enough fill
     */
    private static boolean isBuried(World world, int x, int y, int z, int cover) {
        if (!PrismFill.isTerrainFill(world.getBlockAt(x, y, z).getType())) {
            return false;
        }
        for (int step = 1; step <= cover; step++) {
            if (!PrismFill.isTerrainFill(world.getBlockAt(x, y + step, z).getType())) {
                return false;
            }
        }
        return true;
    }

    /**
     * One fitness pass over a chunk.
     *
     * @param medianY ground datum
     * @param reliefBlocks {@code p90 − p10} of column heights
     * @param soilFraction columns with shovel-mineable surface
     * @param floodedFraction columns with liquid above the ground (open water)
     * @param buriedCells cells that could hide a find in always-present bands
     */
    public record Sample(
            int medianY,
            int reliefBlocks,
            double soilFraction,
            double floodedFraction,
            int buriedCells
    ) {
    }
}
