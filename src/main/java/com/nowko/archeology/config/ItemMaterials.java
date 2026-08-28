package com.nowko.archeology.config;

import org.bukkit.Material;

/**
 * Bukkit materials for tools the plugin issues or will bind to later.
 *
 * @param tracker held scanner
 * @param prospect soil probe / cata (not the excavation brush)
 * @param establish camp kit (not a vanilla stick)
 * @param pick excavation pick (minigame, later)
 * @param shovel excavation shovel (minigame, later)
 * @param hammer excavation hammer / mace (minigame, later)
 * @param brush excavation brush (minigame, later)
 */
public record ItemMaterials(
        Material tracker,
        Material prospect,
        Material establish,
        Material pick,
        Material shovel,
        Material hammer,
        Material brush
) {
    /**
     * @return packaged defaults
     */
    public static ItemMaterials defaults() {
        return new ItemMaterials(
                Material.RECOVERY_COMPASS,
                Material.STONE_HOE,
                Material.STICK,
                Material.IRON_PICKAXE,
                Material.IRON_SHOVEL,
                Material.MACE,
                Material.BRUSH
        );
    }
}
