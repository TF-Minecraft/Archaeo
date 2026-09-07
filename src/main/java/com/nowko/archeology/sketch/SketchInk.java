package com.nowko.archeology.sketch;

import java.awt.Color;

/**
 * Field-sketch palette: paper plus seven crayon hues the map palette can actually show.
 */
public enum SketchInk {
    PAPER(247, 236, 214, "paper"),
    CHARCOAL(45, 42, 38, "charcoal"),
    OCHRE(176, 112, 48, "ochre"),
    SANGUINE(138, 44, 36, "sanguine"),
    TERRA(92, 64, 51, "terra"),
    MOSS(62, 92, 48, "moss"),
    SLATE(68, 84, 108, "slate"),
    BONE(232, 220, 196, "bone");

    private final Color color;
    private final String label;

    /**
     * @param red 0–255
     * @param green 0–255
     * @param blue 0–255
     * @param label action-bar name
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

    /**
     * @return next swatch, wrapping
     */
    public SketchInk next() {
        SketchInk[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
