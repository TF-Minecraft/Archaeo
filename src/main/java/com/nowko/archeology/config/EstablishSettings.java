package com.nowko.archeology.config;

import org.bukkit.Material;

/**
 * Establishment kit (camp) rules from {@code config.yml}. Item id is {@code establish.item}.
 *
 * @param enabled whether the kit can claim a confirmed ruin
 * @param protectDigSite whether terrain fill in every present stratum band is locked against vanilla damage
 * @param invalidBlock client-only block for template cells that cannot be planted
 * @param ruinOutlineBlock client-only glass for the dig chunk perimeter
 */
public record EstablishSettings(
        boolean enabled,
        boolean protectDigSite,
        Material invalidBlock,
        Material ruinOutlineBlock
) {
    /**
     * @return packaged defaults matching {@code config.yml}
     */
    public static EstablishSettings defaults() {
        return new EstablishSettings(
                true,
                true,
                Material.RED_STAINED_GLASS,
                Material.LIGHT_BLUE_STAINED_GLASS
        );
    }
}
