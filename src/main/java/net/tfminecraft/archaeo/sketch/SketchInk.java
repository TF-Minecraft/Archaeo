package net.tfminecraft.archaeo.sketch;

import java.awt.Color;

/**
 * Small field-notebook palette: paper plus five strokes. Jump cycles the strokes; paper is erase.
 */
public enum SketchInk {
    PAPER(247, 236, 214, "paper"),
    CHARCOAL(36, 32, 30, "charcoal"),
    RED(168, 42, 36, "red"),
    OCHRE(186, 122, 48, "ochre"),
    SLATE(62, 92, 138, "slate"),
    MOSS(72, 108, 58, "moss");

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
     * Walks charcoal → red → ochre → slate → moss. Paper is not in the cycle.
     *
     * @return the next stroke colour
     */
    public SketchInk next() {
        SketchInk[] values = values();
        int firstStroke = CHARCOAL.ordinal();
        if (ordinal() < firstStroke) {
            return CHARCOAL;
        }
        int next = ordinal() + 1;
        if (next >= values.length) {
            return CHARCOAL;
        }
        return values[next];
    }
}
