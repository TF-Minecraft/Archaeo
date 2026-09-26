package net.tfminecraft.archaeo.sketch;

import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.awt.Color;
import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Blits one sheet onto a 128×128 map, four pixels per cell, and frames the editor's cursor.
 */
final class SketchRenderer extends MapRenderer {
    private static final Color CURSOR_LIGHT = new Color(255, 255, 255);
    private static final Color CURSOR_DARK = new Color(20, 20, 20);

    private final SketchService sketches;
    private final Map<MapCanvas, CanvasState> canvases = new WeakHashMap<>();

    private static final class CanvasState {
        private final byte[] cells = new byte[SketchSheet.SIZE * SketchSheet.SIZE];
        private SketchSheet sheet;
        private long revision = -1;
        private int cursorX = -1;
        private int cursorY = -1;
    }

    /**
     * @param sketches live sessions; the sheet lives there so the map stays after the player leaves
     */
    SketchRenderer(SketchService sketches) {
        super(true);
        this.sketches = sketches;
    }

    /**
     * Copies dirty sheets onto the canvas. Other viewers see the drawing without a cursor.
     *
     * @param view map being sent
     * @param canvas pixel target
     * @param player who is looking
     */
    @Override
    public void render(MapView view, MapCanvas canvas, Player player) {
        SketchSheet sheet = sketches.sheetOf(view);
        if (sheet == null) {
            return;
        }
        SketchSession session = sketches.session(player);
        paint(canvas, sheet, session != null && session.view().getId() == view.getId() ? session : null);
    }

    void paint(MapCanvas canvas, SketchSheet sheet, SketchSession session) {
        CanvasState state = canvases.computeIfAbsent(canvas, ignored -> new CanvasState());
        if (state.sheet != sheet) {
            state.sheet = sheet;
            state.revision = -1;
            Arrays.fill(state.cells, (byte) -1);
        }
        int cursorX = session != null ? session.cursorX() : -1;
        int cursorY = cursorX >= 0 ? session.cursorY() : -1;
        boolean redrawCursor = cursorX >= 0 && (state.cursorX != cursorX || state.cursorY != cursorY
                || state.cells[cursorY * SketchSheet.SIZE + cursorX]
                != (byte) sheet.at(cursorX, cursorY).ordinal());
        if (state.revision != sheet.revision()) {
            blitChanges(canvas, sheet, state);
            state.revision = sheet.revision();
        }
        if (state.cursorX != cursorX || state.cursorY != cursorY) {
            if (state.cursorX >= 0) {
                fillCell(canvas, state.cursorX, state.cursorY,
                        sheet.at(state.cursorX, state.cursorY).color());
            }
            state.cursorX = cursorX;
            state.cursorY = cursorY;
        }
        if (redrawCursor) {
            frameCursor(canvas, session, sheet);
        }
    }

    /**
     * @param canvas map pixels
     * @param sheet cells
     */
    private static void blitChanges(MapCanvas canvas, SketchSheet sheet, CanvasState state) {
        for (int cellX = 0; cellX < SketchSheet.SIZE; cellX++) {
            for (int cellY = 0; cellY < SketchSheet.SIZE; cellY++) {
                SketchInk ink = sheet.at(cellX, cellY);
                int index = cellY * SketchSheet.SIZE + cellX;
                if (state.cells[index] != (byte) ink.ordinal()) {
                    fillCell(canvas, cellX, cellY, ink.color());
                    state.cells[index] = (byte) ink.ordinal();
                }
            }
        }
    }

    /**
     * Draws a 4×4 outline so the active cell reads on any ink.
     *
     * @param canvas map pixels
     * @param session editor
     * @param sheet cells under the cursor
     */
    private static void frameCursor(MapCanvas canvas, SketchSession session, SketchSheet sheet) {
        Color fill = sheet.at(session.cursorX(), session.cursorY()).color();
        Color ring = contrast(fill);
        int x0 = session.cursorX() * SketchSheet.PIXEL_SCALE;
        int y0 = session.cursorY() * SketchSheet.PIXEL_SCALE;
        int x1 = x0 + SketchSheet.PIXEL_SCALE - 1;
        int y1 = y0 + SketchSheet.PIXEL_SCALE - 1;
        for (int x = x0; x <= x1; x++) {
            canvas.setPixelColor(x, y0, ring);
            canvas.setPixelColor(x, y1, ring);
        }
        for (int y = y0; y <= y1; y++) {
            canvas.setPixelColor(x0, y, ring);
            canvas.setPixelColor(x1, y, ring);
        }
    }

    /**
     * @param canvas map pixels
     * @param cellX column
     * @param cellY row
     * @param color fill
     */
    private static void fillCell(MapCanvas canvas, int cellX, int cellY, Color color) {
        int x0 = cellX * SketchSheet.PIXEL_SCALE;
        int y0 = cellY * SketchSheet.PIXEL_SCALE;
        for (int dx = 0; dx < SketchSheet.PIXEL_SCALE; dx++) {
            for (int dy = 0; dy < SketchSheet.PIXEL_SCALE; dy++) {
                canvas.setPixelColor(x0 + dx, y0 + dy, color);
            }
        }
    }

    /**
     * @param fill cell colour
     * @return white or black outline that stays visible on that fill
     */
    private static Color contrast(Color fill) {
        int luminance = (fill.getRed() * 3 + fill.getGreen() * 6 + fill.getBlue()) / 10;
        return luminance < 140 ? CURSOR_LIGHT : CURSOR_DARK;
    }
}
