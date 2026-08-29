package com.nowko.archeology.establish;

import org.bukkit.DyeColor;
import org.bukkit.Material;

/**
 * Wool colours for the camp template ({@code W} primary and {@code R} secondary).
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
        return woolOf(name, DyeColor.RED);
    }

    /**
     * @param name DyeColor name, or {@code null}
     * @param fallback when {@code name} is missing or invalid
     * @return wool block for that dye
     */
    public static Material woolOf(String name, DyeColor fallback) {
        DyeColor color = parse(name, fallback);
        Material material = Material.matchMaterial(color.name() + "_WOOL");
        if (material != null) {
            return material;
        }
        Material fallbackWool = Material.matchMaterial(fallback.name() + "_WOOL");
        return fallbackWool != null ? fallbackWool : Material.RED_WOOL;
    }

    /**
     * @param current DyeColor name
     * @return next colour in the vanilla list
     */
    public static DyeColor next(String current) {
        DyeColor color = parse(current, DyeColor.RED);
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
        return parse(name, DyeColor.RED);
    }

    /**
     * @param name DyeColor name
     * @param fallback when {@code name} is missing or invalid
     * @return parsed colour
     */
    public static DyeColor parse(String name, DyeColor fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        try {
            return DyeColor.valueOf(name.trim().toUpperCase().replace(' ', '_'));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
