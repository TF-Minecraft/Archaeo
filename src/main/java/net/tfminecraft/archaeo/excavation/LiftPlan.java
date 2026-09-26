package net.tfminecraft.archaeo.excavation;

import net.tfminecraft.archaeo.config.ExcavationTool;
import net.tfminecraft.archaeo.model.Site;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Chooses which prism cubes come out of one resolved hold.
 * The aimed cube always comes out first. Further cubes stay inside {@code break-shape}
 * and must share a face with the aimed cube or with a cube already chosen this lift.
 */
public final class LiftPlan {
    /**
     * 3×3 around the origin: centre first, then cardinals, then diagonals.
     * Diagonals are only lifted after a cardinal (or another face neighbour) is already in the set.
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
     * @param tool lift counts and break shape
     * @param late whether the ready window was missed
     * @return cubes in lift order; may be shorter than the YAML count when fill runs out
     */
    public static List<Block> cells(Site site, Block origin, ExcavationTool tool, boolean late) {
        int onTime = Math.max(1, tool.cellsOnTime());
        int count = late ? Math.max(onTime, tool.cellsOnLate()) : onTime;
        Set<Long> taken = new LinkedHashSet<>();
        List<Block> out = new ArrayList<>(count);
        if (!tryAdd(out, taken, site, origin)) {
            return out;
        }
        if (count <= 1) {
            return out;
        }
        int extra = count - 1;
        switch (tool.breakShape()) {
            case AROUND -> addFootprint(out, taken, site, origin, extra, false);
            case RANDOM -> addFootprint(out, taken, site, origin, extra, true);
            case DOWN -> addDown(out, taken, site, origin, extra);
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
     * @param extra how many to add under {@code origin}
     */
    private static void addDown(
            List<Block> out,
            Set<Long> taken,
            Site site,
            Block origin,
            int extra
    ) {
        for (int i = 1; i <= extra; i++) {
            Block cell = origin.getRelative(0, -i, 0);
            if (!tryAdd(out, taken, site, cell)) {
                break;
            }
        }
    }

    /**
     * Grows through the 3×3×2 around the aim, face by face. {@code around} prefers
     * cardinals then diagonals then the layer below; {@code random} picks among
     * currently attached fill.
     *
     * @param out cubes already chosen
     * @param taken packed keys of {@code out}
     * @param site excavation
     * @param origin aimed cell
     * @param extra how many to add
     * @param shuffle whether to pick the next attached cube at random
     */
    private static void addFootprint(
            List<Block> out,
            Set<Long> taken,
            Site site,
            Block origin,
            int extra,
            boolean shuffle
    ) {
        List<Block> pool = new ArrayList<>();
        for (int down = 0; down <= 1; down++) {
            for (int[] offset : FOOTPRINT) {
                Block cell = origin.getRelative(offset[0], -down, offset[1]);
                if (accept(site, cell) && !taken.contains(pack(cell))) {
                    pool.add(cell);
                }
            }
        }
        int need = extra;
        while (need > 0) {
            List<Block> frontier = attached(pool, taken);
            if (frontier.isEmpty()) {
                return;
            }
            Block next = shuffle
                    ? frontier.get(ThreadLocalRandom.current().nextInt(frontier.size()))
                    : frontier.get(0);
            // Pool cubes were accepted when the pool was built and leave it once chosen.
            taken.add(pack(next));
            out.add(next);
            pool.remove(next);
            need--;
        }
    }

    /**
     * Pool cubes that share a face with a cube already chosen, in pool order.
     *
     * @param pool remaining 3×3×2 candidates; never holds a cube already taken
     * @param taken packed keys already lifting
     * @return attached candidates
     */
    private static List<Block> attached(List<Block> pool, Set<Long> taken) {
        List<Block> frontier = new ArrayList<>();
        for (Block cell : pool) {
            if (touchesTaken(cell, taken)) {
                frontier.add(cell);
            }
        }
        return frontier;
    }

    /**
     * @param cell candidate
     * @param taken packed keys of cubes already in this lift
     * @return whether {@code cell} shares a face with any taken cube
     */
    private static boolean touchesTaken(Block cell, Set<Long> taken) {
        return taken.contains(pack(cell.getRelative(0, 1, 0)))
                || taken.contains(pack(cell.getRelative(0, -1, 0)))
                || taken.contains(pack(cell.getRelative(0, 0, 1)))
                || taken.contains(pack(cell.getRelative(0, 0, -1)))
                || taken.contains(pack(cell.getRelative(1, 0, 0)))
                || taken.contains(pack(cell.getRelative(-1, 0, 0)));
    }

    /**
     * @param out cubes already chosen
     * @param taken packed keys
     * @param site excavation
     * @param cell candidate that is not yet in {@code out}
     * @return whether {@code cell} was appended
     */
    private static boolean tryAdd(
            List<Block> out,
            Set<Long> taken,
            Site site,
            Block cell
    ) {
        if (!accept(site, cell)) {
            return false;
        }
        taken.add(pack(cell));
        out.add(cell);
        return true;
    }

    /**
     * @param site excavation
     * @param cell world cube
     * @return whether this cube is prism fill
     */
    private static boolean accept(Site site, Block cell) {
        if (!site.isInPrism(cell.getX(), cell.getY(), cell.getZ())) {
            return false;
        }
        return PrismFill.isTerrainFill(cell.getType());
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
