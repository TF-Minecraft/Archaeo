package com.nowko.archeology.site;

import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Arrays;

/**
 * Median ground Y of a ruin chunk, used as the stratum datum when finds are generated.
 */
public final class GroundDatum {
    private GroundDatum() {
    }

    /**
     * Samples all 256 columns and returns the median terrain Y. Water, leaves, snow layers,
     * replaceable plants, logs, bamboo, and giant mushroom blocks are skipped so a pond or
     * tree does not lift or drop the whole site.
     *
     * @param chunk loaded archaeological chunk
     * @return median ground Y
     */
    public static int medianY(Chunk chunk) {
        World world = chunk.getWorld();
        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;
        int[] samples = new int[256];
        int i = 0;
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                samples[i++] = columnGroundY(world, baseX + lx, baseZ + lz);
            }
        }
        return median(samples);
    }

    /**
     * @param values unsorted samples (copied internally)
     * @return median; for an even length, the integer mean of the two central values
     */
    static int median(int[] values) {
        int[] sorted = Arrays.copyOf(values, values.length);
        Arrays.sort(sorted);
        int mid = sorted.length / 2;
        if (sorted.length % 2 == 0) {
            return (sorted[mid - 1] + sorted[mid]) / 2;
        }
        return sorted[mid];
    }

    /**
     * Walks down from the motion-blocking surface until a solid ground block is found.
     * Tree trunks, bamboo, and giant mushroom flesh are skipped the same way leaves are, so a
     * forest canopy does not lift the datum or the auto-ruin relief reading.
     *
     * @param world world
     * @param x block X
     * @param z block Z
     * @return ground Y, or the world minimum if the column is empty
     */
    public static int columnGroundY(World world, int x, int z) {
        int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        int minY = world.getMinHeight();
        while (y >= minY) {
            if (isGround(world.getBlockAt(x, y, z))) {
                return y;
            }
            y--;
        }
        return minY;
    }

    /**
     * @param block column cell
     * @return whether this is terrain the datum should sit on
     */
    static boolean isGround(Block block) {
        Material type = block.getType();
        if (type.isAir() || block.isLiquid()) {
            return false;
        }
        if (Tag.LEAVES.isTagged(type) || Tag.REPLACEABLE.isTagged(type)) {
            return false;
        }
        if (Tag.LOGS.isTagged(type)) {
            return false;
        }
        if (type == Material.SNOW || type == Material.POWDER_SNOW) {
            return false;
        }
        if (type == Material.BAMBOO || type == Material.BAMBOO_SAPLING) {
            return false;
        }
        if (type == Material.MUSHROOM_STEM
                || type == Material.BROWN_MUSHROOM_BLOCK
                || type == Material.RED_MUSHROOM_BLOCK) {
            return false;
        }
        if (type == Material.MANGROVE_ROOTS || type == Material.MUDDY_MANGROVE_ROOTS) {
            return false;
        }
        return type.isSolid();
    }
}
