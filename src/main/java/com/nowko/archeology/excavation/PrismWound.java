package com.nowko.archeology.excavation;

import com.nowko.archeology.model.BlockCell;
import com.nowko.archeology.model.BuriedFind;
import com.nowko.archeology.model.Site;
import com.nowko.archeology.model.StratumBand;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Marks finds and layers wounded when the world no longer holds terrain at a find cell.
 */
public final class PrismWound {
    private PrismWound() {
    }

    /**
     * At first claim: any find whose cells are already air, water, or builds is damaged.
     *
     * @param world ruin world
     * @param site excavation being planted
     * @return how many finds were newly damaged
     */
    public static int markMissingTerrain(World world, Site site) {
        int count = 0;
        for (BuriedFind find : site.getFinds()) {
            boolean wounded = false;
            for (BlockCell cell : find.getCells()) {
                Block block = world.getBlockAt(cell.x(), cell.y(), cell.z());
                Material type = block.getType();
                if (type.isAir() || block.isLiquid() || !PrismFill.isTerrainFill(type)) {
                    wounded |= find.woundFromAbove(cell);
                }
            }
            if (wounded) {
                count++;
            }
        }
        return count;
    }

    /**
     * Vanilla (or explosion) removed this prism cell: that layer is disturbed and overlapping finds are damaged.
     *
     * @param site established excavation
     * @param x block X
     * @param y block Y
     * @param z block Z
     * @return whether dossier data changed
     */
    public static boolean onCellRemoved(Site site, int x, int y, int z) {
        if (!site.isInPrism(x, y, z)) {
            return false;
        }
        boolean changed = false;
        StratumBand band = site.stratumAt(y);
        if (band != null && band.isPresent() && !band.isDisturbed()) {
            band.setDisturbed(true);
            changed = true;
        }
        BlockCell cell = new BlockCell(x, y, z);
        for (BuriedFind find : site.getFinds()) {
            if (!find.getCells().contains(cell)) {
                continue;
            }
            if (find.woundFromAbove(cell)) {
                changed = true;
            }
        }
        return changed;
    }
}
