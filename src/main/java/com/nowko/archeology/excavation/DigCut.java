package com.nowko.archeology.excavation;

import com.nowko.archeology.site.SiteRepository;
import org.bukkit.block.Block;

/**
 * Shared test for terrain fill inside an established stratum prism (every present band).
 */
public final class DigCut {
    private DigCut() {
    }

    /**
     * Whether this cell is excavation substrate in a live prism. Does not care if a face is open.
     *
     * @param sites established excavations
     * @param block world cell
     * @return whether the cell is prism fill
     */
    public static boolean isPrismFill(SiteRepository sites, Block block) {
        if (!PrismFill.isTerrainFill(block.getType())) {
            return false;
        }
        return sites.findEstablishedPrism(
                block.getWorld().getName(),
                block.getX(),
                block.getY(),
                block.getZ()).isPresent();
    }
}
