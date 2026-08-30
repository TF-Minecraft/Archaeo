package com.nowko.archeology.excavation;

import com.nowko.archeology.site.SiteRepository;
import org.bukkit.block.Block;

/**
 * Shared test for the open excavation cut (terrain in the prism with an open face).
 */
public final class DigCut {
    private DigCut() {
    }

    /**
     * @param sites established excavations
     * @param block world cell
     * @return whether this is the working face
     */
    public static boolean isWorkingFace(SiteRepository sites, Block block) {
        if (!PrismFill.isTerrainFill(block.getType())) {
            return false;
        }
        if (sites.findEstablishedPrism(
                block.getWorld().getName(),
                block.getX(),
                block.getY(),
                block.getZ()).isEmpty()) {
            return false;
        }
        return PrismFill.hasOpenFace(block);
    }
}
