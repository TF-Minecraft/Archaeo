package net.tfminecraft.archaeo.sketch;

import java.awt.Color;

/**
 * Field-notebook pigments. Paper is erase-only; the inventory palette selects strokes.
 * Existing entries must keep their order: saved sketches store these ordinals.
 */
public enum SketchInk {
    PAPER(247, 236, 214, "paper"),
    CHARCOAL(36, 32, 30, "charcoal"),
    RED(168, 42, 36, "red"),
    OCHRE(186, 122, 48, "ochre"),
    SLATE(62, 92, 138, "slate"),
    MOSS(72, 108, 58, "moss"),

    // Append new pigments so existing map items and autosaves retain their colours.
    LIGHT_CHARCOAL(145, 140, 133, "light charcoal"),
    DARK_CHARCOAL(15, 14, 13, "dark charcoal"),
    LIGHT_RED(214, 120, 99, "light red"),
    DARK_RED(105, 26, 25, "dark red"),
    LIGHT_OCHRE(229, 190, 111, "light ochre"),
    DARK_OCHRE(120, 76, 31, "dark ochre"),
    LIGHT_SLATE(137, 167, 195, "light slate"),
    DARK_SLATE(38, 46, 66, "dark slate"),
    LIGHT_MOSS(139, 163, 102, "light moss"),
    DARK_MOSS(41, 59, 35, "dark moss"),
    BROWN(117, 79, 49, "brown"),
    LIGHT_BROWN(182, 139, 100, "light brown"),
    DARK_BROWN(68, 45, 30, "dark brown"),
    IVORY(225, 214, 180, "ivory"),
    LIGHT_IVORY(255, 255, 255, "light ivory"),
    DARK_IVORY(180, 178, 172, "dark ivory"),
    TURQUOISE(48, 139, 139, "turquoise"),
    LIGHT_TURQUOISE(121, 190, 179, "light turquoise"),
    DARK_TURQUOISE(28, 80, 83, "dark turquoise"),
    VIOLET(126, 82, 145, "violet"),
    LIGHT_VIOLET(190, 125, 205, "light violet"),
    DARK_VIOLET(78, 41, 88, "dark violet");

    private final Color color;
    private final String label;

    /**
     * @param red 0–255
     * @param green 0–255
     * @param blue 0–255
     * @param label palette and HUD name
     */
    SketchInk(int red, int green, int blue, String label) {
        this.color = new Color(red, green, blue);
        this.label = label;
    }

    /**
     * @return nearest-match RGB for {@code MapCanvas#setPixelColor}
     */
    public Color color() {
        return color;
    }

    /**
     * @return short English name
     */
    public String label() {
        return label;
    }
}
