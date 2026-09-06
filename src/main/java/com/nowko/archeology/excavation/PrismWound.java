package com.nowko.archeology.excavation;

import com.nowko.archeology.model.BlockCell;
import com.nowko.archeology.model.BuriedFind;
import com.nowko.archeology.model.FindState;
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
     * @param site ruin or established excavation
     * @param x block X
     * @param y block Y
     * @param z block Z
     * @param damagedBelowPercent conservation at which the find is stamped damaged
     * @return whether the dossier changed and whether a live find cube was newly smashed
     */
    public static Removal onCellRemoved(Site site, int x, int y, int z, int damagedBelowPercent) {
        if (!site.isInPrism(x, y, z)) {
            return Removal.none();
        }
        boolean changed = false;
        StratumBand band = site.stratumAt(y);
        if (band != null && band.isPresent() && !band.isDisturbed()) {
            band.setDisturbed(true);
            changed = true;
        }
        boolean findSmashed = smashFindAt(site, x, y, z, damagedBelowPercent);
        return new Removal(changed || findSmashed, findSmashed);
    }

    /**
     * Direct or vanilla hit on a still-recoverable find cube.
     *
     * @param site dossier
     * @param x block X
     * @param y block Y
     * @param z block Z
     * @param damagedBelowPercent conservation at which the find is stamped damaged
     * @param aimed whether the miner was looking at this cube (Hand Pick) rather than collapsing onto it
     * @return whether conservation changed
     */
    public static boolean smashFindAt(
            Site site,
            int x,
            int y,
            int z,
            int damagedBelowPercent,
            boolean aimed
    ) {
        BuriedFind find = site.findAt(new BlockCell(x, y, z)).orElse(null);
        if (find == null || find.getState() == FindState.RECOVERED || find.getState() == FindState.LOST) {
            return false;
        }
        BlockCell cell = new BlockCell(x, y, z);
        boolean changed = aimed ? find.woundDirect(cell) : find.woundFromAbove(cell);
        if (!changed) {
            return false;
        }
        if (find.getConservation() < damagedBelowPercent) {
            find.setDamaged(true);
        }
        return true;
    }

    /**
     * Vanilla removal is a graze from above.
     *
     * @param site dossier
     * @param x block X
     * @param y block Y
     * @param z block Z
     * @param damagedBelowPercent conservation at which the find is stamped damaged
     * @return whether conservation changed
     */
    public static boolean smashFindAt(Site site, int x, int y, int z, int damagedBelowPercent) {
        return smashFindAt(site, x, y, z, damagedBelowPercent, false);
    }

    /**
     * Outcome of removing one prism cell.
     *
     * @param dossierChanged whether the site should be saved
     * @param findSmashed whether a live find cube was newly wounded
     */
    public record Removal(boolean dossierChanged, boolean findSmashed) {
        /**
         * @return no dossier write and no smash cue
         */
        public static Removal none() {
            return new Removal(false, false);
        }
    }
}
