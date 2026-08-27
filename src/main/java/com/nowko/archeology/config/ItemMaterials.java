package com.nowko.archeology.config;

import org.bukkit.Material;

/**
 * Bukkit materials for tools the plugin issues or will bind to later.
 *
 * @param tracker held scanner
 * @param pick excavation pick (minigame, later)
 * @param shovel excavation shovel (minigame, later)
 * @param hammer excavation hammer / mace (minigame, later)
 * @param brush excavation brush (minigame, later)
 */
public record ItemMaterials(
        Material tracker,
        Material pick,
        Material shovel,
        Material hammer,
        Material brush
) {
    /**
     * @return packaged defaults (recovery compass, iron pick/shovel, mace, brush)
     */
    public static ItemMaterials defaults() {
        return new ItemMaterials(
                Material.RECOVERY_COMPASS,
                Material.IRON_PICKAXE,
                Material.IRON_SHOVEL,
                Material.MACE,
                Material.BRUSH
        );
    }
}
