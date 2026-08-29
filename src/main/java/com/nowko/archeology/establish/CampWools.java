package com.nowko.archeology.establish;

import org.bukkit.DyeColor;
import org.bukkit.Material;

/**
 * Wool color colours for the camp template (the {@code R} cells).
 */
public final class CampWools {
    private static final DyeColor[] ORDER = DyeColor.values();

    private CampWools() {
    }

    /**
     * Vanilla wool colours in DyeColor order.
     *
     * @return a copy of the palette
     */
    public static DyeColor[] palette() {
        return ORDER.clone();
    }


    /**
     * @param name DyeColor name, or {@code null}
     * @return wool block, default red
     */
    public static Material woolOf(String name) {
        DyeColor color = parse(name);
        Material material = Material.matchMaterial(color.name() + "_WOOL");
        return material != null ? material : Material.RED_WOOL;
    }

    /**
     * @param current DyeColor name
     * @return next colour in the vanilla list
     */
    public static DyeColor next(String current) {
        DyeColor color = parse(current);
        return ORDER[(color.ordinal() + 1) % ORDER.length];
    }

    /**
     * @param color accent
     * @return short English label
     */
    public static String label(DyeColor color) {
        return color.name().toLowerCase().replace('_', ' ') + " wool";
    }

    /**
     * @param name DyeColor name
     * @return parsed colour, or red
     */
    public static DyeColor parse(String name) {
        if (name == null || name.isBlank()) {
            return DyeColor.RED;
        }
        try {
            return DyeColor.valueOf(name.trim().toUpperCase().replace(' ', '_'));
        } catch (IllegalArgumentException ignored) {
            return DyeColor.RED;
        }
    }
}
