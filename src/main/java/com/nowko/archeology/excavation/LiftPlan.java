package com.nowko.archeology.excavation;

import com.nowko.archeology.config.ExcavationTool;
import com.nowko.archeology.model.Site;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Chooses which prism cubes come out of one resolved hold.
 * The aimed cube always comes out first. Further cubes follow {@code break-shape},
 * using the same pattern on time and when late (only the count changes).
 */
public final class LiftPlan {
    /**
     * 3×3 around the origin: centre first, then cardinals, then diagonals.
     */
    private static final int[][] FOOTPRINT = {
            {0, 0},
            {0, 1}, {1, 0}, {0, -1}, {-1, 0},
            {1, 1}, {1, -1}, {-1, -1}, {-1, 1}
    };

    private LiftPlan() {
    }

    /**
     * @param site excavation prism
     * @param origin cube the player held
     * @param tool lift counts, break shape, and fill gate
     * @param late whether the ready window was missed
     * @return cubes in lift order; may be shorter than the YAML count when fill runs out
     */
    public static List<Block> cells(Site site, Block origin, ExcavationTool tool, boolean late) {
        int onTime = Math.max(1, tool.cellsOnTime());
        int count = late ? Math.max(onTime, tool.cellsOnLate()) : onTime;
        Set<Long> taken = new LinkedHashSet<>();
        List<Block> out = new ArrayList<>(count);
        if (!tryAdd(out, taken, site, tool, origin)) {
            return out;
        }
        if (count <= 1) {
            return out;
        }
        int extra = count - 1;
        switch (tool.breakShape()) {
            case AROUND -> addFootprint(out, taken, site, origin, tool, extra, false);
            case RANDOM -> addFootprint(out, taken, site, origin, tool, extra, true);
            case DOWN -> addDown(out, taken, site, origin, tool, extra);
        }
        return out;
    }

    /**
     * Cubes under the aimed cell, in order Y−1, Y−2, …
     *
     * @param out cubes already chosen (includes origin)
     * @param taken packed keys of {@code out}
     * @param site excavation
     * @param origin aimed cell
     * @param tool fill gate
     * @param extra how many to add under {@code origin}
     */
    private static void addDown(
            List<Block> out,
            Set<Long> taken,
            Site site,
            Block origin,
            ExcavationTool tool,
            int extra
    ) {
        for (int i = 1; i <= extra; i++) {
            Block cell = origin.getRelative(0, -i, 0);
            if (!tryAdd(out, taken, site, tool, cell)) {
                break;
            }
        }
    }

    /**
     * Rest of the 3×3×2 after the aimed cube: same layer, then one block below.
     *
     * @param out cubes already chosen
     * @param taken packed keys of {@code out}
     * @param site excavation
     * @param origin aimed cell
     * @param tool fill gate
     * @param extra how many to add
     * @param shuffle whether to pick the pool in random order
     */
    private static void addFootprint(
            List<Block> out,
            Set<Long> taken,
            Site site,
            Block origin,
            ExcavationTool tool,
            int extra,
            boolean shuffle
    ) {
        List<Block> pool = new ArrayList<>();
        for (int down = 0; down <= 1; down++) {
            for (int[] offset : FOOTPRINT) {
                Block cell = origin.getRelative(offset[0], -down, offset[1]);
                if (accept(site, tool, cell) && !taken.contains(pack(cell))) {
                    pool.add(cell);
                }
            }
        }
        if (shuffle) {
            Collections.shuffle(pool, ThreadLocalRandom.current());
        }
        int need = extra;
        for (Block cell : pool) {
            if (need <= 0) {
                return;
            }
            if (tryAdd(out, taken, site, tool, cell)) {
                need--;
            }
        }
    }

    /**
     * @param out cubes already chosen
     * @param taken packed keys
     * @param site excavation
     * @param tool fill gate
     * @param cell candidate
     * @return whether {@code cell} was appended
     */
    private static boolean tryAdd(
            List<Block> out,
            Set<Long> taken,
            Site site,
            ExcavationTool tool,
            Block cell
    ) {
        if (!accept(site, tool, cell)) {
            return false;
        }
        if (!taken.add(pack(cell))) {
            return false;
        }
        out.add(cell);
        return true;
    }

    /**
     * @param site excavation
     * @param tool fill gate
     * @param cell world cube
     * @return whether this profile may lift that cube
     */
    private static boolean accept(Site site, ExcavationTool tool, Block cell) {
        if (!site.isInPrism(cell.getX(), cell.getY(), cell.getZ())) {
            return false;
        }
        if (!PrismFill.isTerrainFill(cell.getType())) {
            return false;
        }
        return tool.fill().allows(cell.getType());
    }

    /**
     * @param cell world cube
     * @return packed X/Y/Z for {@link Set} membership
     */
    private static long pack(Block cell) {
        return ((long) cell.getX() & 0x3FFFFFFL) << 38
                | ((long) cell.getZ() & 0x3FFFFFFL) << 12
                | (cell.getY() + 2048 & 0xFFF);
    }
}
