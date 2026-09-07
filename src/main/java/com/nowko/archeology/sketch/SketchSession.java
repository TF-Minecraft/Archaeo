package com.nowko.archeology.sketch;

import org.bukkit.map.MapView;

import java.util.UUID;

/**
 * One player frozen in front of one prototype map: cursor, current ink, and the sheet they stamp.
 */
final class SketchSession {
    private final UUID playerId;
    private final MapView view;
    private final SketchSheet sheet;
    private int cursorX = SketchSheet.SIZE / 2;
    private int cursorY = SketchSheet.SIZE / 2;
    private SketchInk ink = SketchInk.CHARCOAL;
    private boolean jumpHeld;

    /**
     * @param playerId editor
     * @param view locked map the renderer owns
     * @param sheet pixels for that view
     */
    SketchSession(UUID playerId, MapView view, SketchSheet sheet) {
        this.playerId = playerId;
        this.view = view;
        this.sheet = sheet;
    }

    /**
     * @return editor
     */
    UUID playerId() {
        return playerId;
    }

    /**
     * @return map shown in hand
     */
    MapView view() {
        return view;
    }

    /**
     * @return cells being drawn
     */
    SketchSheet sheet() {
        return sheet;
    }

    /**
     * @return cursor column
     */
    int cursorX() {
        return cursorX;
    }

    /**
     * @return cursor row
     */
    int cursorY() {
        return cursorY;
    }

    /**
     * @return ink that sneak / left-click will stamp
     */
    SketchInk ink() {
        return ink;
    }

    /**
     * @return whether jump was down last tick (edge-detect for cycling ink)
     */
    boolean jumpHeld() {
        return jumpHeld;
    }

    /**
     * @param held jump key this tick
     */
    void setJumpHeld(boolean held) {
        this.jumpHeld = held;
    }

    /**
     * @param dx cell columns
     * @param dy cell rows
     * @return whether the cursor moved
     */
    boolean move(int dx, int dy) {
        int nextX = clamp(cursorX + dx);
        int nextY = clamp(cursorY + dy);
        if (nextX == cursorX && nextY == cursorY) {
            return false;
        }
        cursorX = nextX;
        cursorY = nextY;
        return true;
    }

    /**
     * Stamps the current ink at the cursor.
     *
     * @return whether the cell changed
     */
    boolean paint() {
        return sheet.set(cursorX, cursorY, ink);
    }

    /**
     * Clears the cursor cell back to paper.
     *
     * @return whether the cell changed
     */
    boolean erase() {
        return sheet.set(cursorX, cursorY, SketchInk.PAPER);
    }

    /**
     * Walks the palette, including paper so erase is reachable without a right-click.
     */
    void cycleInk() {
        ink = ink.next();
    }

    /**
     * @param value raw cursor axis
     * @return value kept on the 32×32 sheet
     */
    private static int clamp(int value) {
        if (value < 0) {
            return 0;
        }
        if (value >= SketchSheet.SIZE) {
            return SketchSheet.SIZE - 1;
        }
        return value;
    }
}
