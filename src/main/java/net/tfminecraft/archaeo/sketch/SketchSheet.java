package net.tfminecraft.archaeo.sketch;

/**
 * In-memory 32×32 cells for one field sketch. Persistence lives on the map item, not here.
 */
final class SketchSheet {
    static final int SIZE = 32;
    static final int PIXEL_SCALE = 4;

    private final SketchInk[] cells = new SketchInk[SIZE * SIZE];
    private long revision;

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
        revision++;
        return true;
    }

    /**
     * @return number of cell changes since this sheet was loaded
     */
    long revision() {
        return revision;
    }

    /**
     * @param x cell column
     * @param y cell row
     * @return flat index
     */
    private static int index(int x, int y) {
        return y * SIZE + x;
    }

    /**
     * @return whether at least one cell has been painted
     */
    boolean hasInk() {
        for (SketchInk cell : cells) {
            if (cell != SketchInk.PAPER) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return one ordinal per cell, for the map item PDC
     */
    byte[] toBytes() {
        byte[] data = new byte[cells.length];
        for (int i = 0; i < cells.length; i++) {
            data[i] = (byte) cells[i].ordinal();
        }
        return data;
    }

    /**
     * @param data stored ordinals, or {@code null}
     * @return sheet; unknown or short data becomes paper
     */
    static SketchSheet fromBytes(byte[] data) {
        return fromBytes(data, 0);
    }

    /**
     * Restores cells and their persisted revision.
     *
     * @param data stored cell ordinals, or {@code null}
     * @param revision persisted revision number
     * @return restored sheet
     */
    static SketchSheet fromBytes(byte[] data, long revision) {
        SketchSheet sheet = new SketchSheet();
        sheet.revision = Math.max(0, revision);
        if (data == null) {
            return sheet;
        }
        SketchInk[] palette = SketchInk.values();
        int n = Math.min(sheet.cells.length, data.length);
        for (int i = 0; i < n; i++) {
            int ordinal = data[i] & 0xFF;
            if (ordinal < palette.length) {
                sheet.cells[i] = palette[ordinal];
            }
        }
        return sheet;
    }
}
