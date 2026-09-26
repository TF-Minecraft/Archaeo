package net.tfminecraft.archaeo.sketch;

import org.bukkit.map.MapView;

/**
 * One player frozen in front of one prototype map: cursor, current ink, and the sheet they stamp.
 */
final class SketchSession {
    private static final int REPEAT_DELAY_TICKS = 5;
    private static final int REPEAT_INTERVAL_TICKS = 2;
    private static final int ERASE_STROKE_GRACE_TICKS = 5;

    private final MapView view;
    private final SketchSheet sheet;
    private int cursorX = SketchSheet.SIZE / 2;
    private int cursorY = SketchSheet.SIZE / 2;
    private SketchInk ink = SketchInk.CHARCOAL;
    private int heldHorizontal;
    private int heldVertical;
    private int horizontalTicks;
    private int verticalTicks;
    private long autosavedRevision;
    private int eraseStrokeTicks;
    private int lastEraseX = -1;
    private int lastEraseY = -1;
    private boolean jumpHeld;
    private boolean awaitingSign;

    /**
     * @param view locked map the renderer owns
     * @param sheet pixels for that view
     */
    SketchSession(MapView view, SketchSheet sheet) {
        this.view = view;
        this.sheet = sheet;
        this.autosavedRevision = sheet.revision();
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
     * @return ink that sneak will stamp
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
     * @return whether chat is waiting for sign / cancel
     */
    boolean awaitingSign() {
        return awaitingSign;
    }

    /**
     * @param awaiting chat confirm for locking the sheet
     */
    void setAwaitingSign(boolean awaiting) {
        this.awaitingSign = awaiting;
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
     * Moves once on key press, then repeats at a fixed rate while held. Each axis
     * has its own clock so adding a second direction responds immediately.
     *
     * @param dx horizontal input (-1, 0, or 1)
     * @param dy vertical input (-1, 0, or 1)
     */
    void moveFromInput(int dx, int dy) {
        int stepX = 0;
        int stepY = 0;
        if (dx != heldHorizontal) {
            heldHorizontal = dx;
            horizontalTicks = 0;
            stepX = dx;
        } else if (dx != 0 && repeats(++horizontalTicks)) {
            stepX = dx;
        }
        if (dy != heldVertical) {
            heldVertical = dy;
            verticalTicks = 0;
            stepY = dy;
        } else if (dy != 0 && repeats(++verticalTicks)) {
            stepY = dy;
        }
        if (stepX != 0 || stepY != 0) {
            move(stepX, stepY);
        }
    }

    /**
     * @param heldTicks ticks since the direction was first pressed
     * @return whether this tick is a cursor repeat
     */
    private static boolean repeats(int heldTicks) {
        return heldTicks >= REPEAT_DELAY_TICKS
                && (heldTicks - REPEAT_DELAY_TICKS) % REPEAT_INTERVAL_TICKS == 0;
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

    /** Starts a short eraser stroke that follows subsequent cursor steps. */
    void beginEraseStroke() {
        eraseStrokeTicks = ERASE_STROKE_GRACE_TICKS;
        lastEraseX = cursorX;
        lastEraseY = cursorY;
        erase();
    }

    /** Erases the cells between the previous and current cursor positions. */
    void updateEraseStroke() {
        if (eraseStrokeTicks <= 0) {
            return;
        }
        eraseLine(lastEraseX, lastEraseY, cursorX, cursorY);
        lastEraseX = cursorX;
        lastEraseY = cursorY;
        if (--eraseStrokeTicks == 0) {
            lastEraseX = -1;
            lastEraseY = -1;
        }
    }

    /**
     * Erases every cell on a discrete line between two cursor positions.
     *
     * @param x0 starting column
     * @param y0 starting row
     * @param x1 ending column
     * @param y1 ending row
     */
    private void eraseLine(int x0, int y0, int x1, int y1) {
        int dx = Math.abs(x1 - x0);
        int sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0);
        int sy = y0 < y1 ? 1 : -1;
        int error = dx + dy;
        while (true) {
            sheet.set(x0, y0, SketchInk.PAPER);
            if (x0 == x1 && y0 == y1) {
                return;
            }
            int doubledError = 2 * error;
            if (doubledError >= dy) {
                error += dy;
                x0 += sx;
            }
            if (doubledError <= dx) {
                error += dx;
                y0 += sy;
            }
        }
    }

    /**
     * @return whether the sheet changed since its last successful checkpoint
     */
    boolean needsAutosave() {
        return autosavedRevision != sheet.revision();
    }

    /** Marks the current sheet revision as safely checkpointed. */
    void markAutosaved() {
        autosavedRevision = sheet.revision();
    }

    /**
     * Walks the five stroke colours. Paper stays erase-only.
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
