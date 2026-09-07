package com.nowko.archeology.sketch;

/**
 * In-memory 32×32 cells for one prototype map. Nothing is written to disk.
 */
final class SketchSheet {
    static final int SIZE = 32;
    static final int PIXEL_SCALE = 4;

    private final SketchInk[] cells = new SketchInk[SIZE * SIZE];

    /**
     * Fills the sheet with paper.
     */
    SketchSheet() {
        for (int i = 0; i < cells.length; i++) {
            cells[i] = SketchInk.PAPER;
        }
    }

    /**
     * @param x cell column
     * @param y cell row
     * @return ink in that cell
     */
    SketchInk at(int x, int y) {
        return cells[index(x, y)];
    }

    /**
     * @param x cell column
     * @param y cell row
     * @param ink colour to stamp
     * @return whether the cell changed
     */
    boolean set(int x, int y, SketchInk ink) {
        int i = index(x, y);
        if (cells[i] == ink) {
            return false;
        }
        cells[i] = ink;
        return true;
    }

    /**
     * @param x cell column
     * @param y cell row
     * @return flat index
     */
    private static int index(int x, int y) {
        return y * SIZE + x;
    }
}
