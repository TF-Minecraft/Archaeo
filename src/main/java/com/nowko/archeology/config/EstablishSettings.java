package com.nowko.archeology.config;

import org.bukkit.Material;

import java.util.List;

/**
 * Establishment kit (camp) from {@code config.yml}.
 *
 * @param enabled whether the kit can claim a confirmed ruin
 * @param protectDigSite whether terrain fill in every present stratum band is locked against vanilla damage
 * @param campBlock block placed as the first camp landmark
 * @param invalidBlock client-only block for template cells that cannot be planted
 * @param ruinOutlineBlock client-only glass for the dig chunk perimeter
 * @param itemName English display name
 * @param itemLore English lore lines
 */
public record EstablishSettings(
        boolean enabled,
        boolean protectDigSite,
        Material campBlock,
        Material invalidBlock,
        Material ruinOutlineBlock,
        String itemName,
        List<String> itemLore
) {
    /**
     * @return packaged defaults matching {@code config.yml}
     */
    public static EstablishSettings defaults() {
        return new EstablishSettings(
                true,
                true,
                Material.LECTERN,
                Material.RED_STAINED_GLASS,
                Material.LIGHT_BLUE_STAINED_GLASS,
                "Establishment kit",
                List.of(
                        "Aim at the ground next to a confirmed ruin.",
                        "The ghost turns red where the camp cannot sit. Right-click to plant."
                )
        );
    }
}
