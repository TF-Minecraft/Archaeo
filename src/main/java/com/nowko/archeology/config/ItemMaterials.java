package com.nowko.archeology.config;

import org.bukkit.Material;

/**
 * Bukkit materials for tools the plugin issues or will bind to later.
 *
 * @param tracker held scanner
 * @param prospect soil probe / cata (not the excavation brush)
 * @param establish camp kit (not a vanilla stick)
 * @param pick excavation Hand Pick
 * @param shovel excavation shovel (later)
 * @param hammer unused for now
 * @param brush excavation brush (later)
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
                Material.STONE_PICKAXE,
                Material.IRON_SHOVEL,
                Material.MACE,
                Material.BRUSH
        );
    }
}
