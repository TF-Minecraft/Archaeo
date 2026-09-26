package net.tfminecraft.archaeo.sketch;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.awt.Color;
import java.lang.reflect.Proxy;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapView;
import org.junit.*;
import org.mockbukkit.mockbukkit.MockBukkit;

public class SketchRendererTest {
    private SketchService service;
    private SketchRenderer renderer;
    private MapView view;
    private Player viewer;
    private SketchSheet sheet;
    @Before public void setUp() {
        MockBukkit.mock(); service = mock(SketchService.class); renderer = new SketchRenderer(service);
        view = mock(MapView.class); when(view.getId()).thenReturn(7); viewer = mock(Player.class);
        sheet = new SketchSheet(); when(service.sheetOf(view)).thenReturn(sheet);
    }
    @After public void tearDown() { MockBukkit.unmock(); }

    @Test public void everySheetCellFillsItsFourByFourMapPixelsIncludingOuterEdges() {
        SketchInk[] palette = SketchInk.values();
        for (int x = 0; x < SketchSheet.SIZE; x++) for (int y = 0; y < SketchSheet.SIZE; y++) sheet.set(x, y, palette[(x + y) % palette.length]);
        Pixels pixels = new Pixels(); renderer.render(view, pixels.canvas, viewer);
        for (int x = 0; x < 128; x++) for (int y = 0; y < 128; y++)
            assertEquals("Pixel " + x + "," + y, palette[(x / 4 + y / 4) % palette.length].color(), pixels.colors[x][y]);
        assertTrue(renderer.isContextual());
    }

    @Test public void onlyTheMatchingEditorSeesAContrastingCursorOutline() {
        SketchSession session = new SketchSession(view, sheet);
        when(service.session(viewer)).thenReturn(session);
        for (SketchInk ink : new SketchInk[]{SketchInk.PAPER, SketchInk.CHARCOAL}) {
            sheet.set(16, 16, ink); Pixels editor = new Pixels(); renderer.render(view, editor.canvas, viewer);
            Color ring = ink == SketchInk.PAPER ? new Color(20, 20, 20) : Color.WHITE;
            for (int dx = 0; dx < 4; dx++) for (int dy = 0; dy < 4; dy++)
                assertEquals(dx == 0 || dx == 3 || dy == 0 || dy == 3 ? ring : ink.color(), editor.colors[64 + dx][64 + dy]);
            Player spectator = mock(Player.class); Pixels publicView = new Pixels(); renderer.render(view, publicView.canvas, spectator);
            assertEquals(ink.color(), publicView.colors[64][64]); assertEquals(ink.color(), publicView.colors[65][65]);
        }
        MapView other = mock(MapView.class); when(other.getId()).thenReturn(8);
        when(service.session(viewer)).thenReturn(new SketchSession(other, sheet));
        Pixels differentMap = new Pixels(); renderer.render(view, differentMap.canvas, viewer);
        assertEquals(SketchInk.CHARCOAL.color(), differentMap.colors[64][64]);
    }

    @Test public void movingAndClosingEditorClearsOldCursorPixelsWhileNewInkAndErasureRender() {
        SketchSession session = new SketchSession(view, sheet); when(service.session(viewer)).thenReturn(session);
        Pixels pixels = new Pixels(); renderer.render(view, pixels.canvas, viewer);
        session.paint(); session.move(1, 0); renderer.render(view, pixels.canvas, viewer);
        assertEquals(SketchInk.CHARCOAL.color(), pixels.colors[64][64]);
        assertEquals(new Color(20, 20, 20), pixels.colors[68][64]);
        session.paint(); session.erase(); when(service.session(viewer)).thenReturn(null);
        renderer.render(view, pixels.canvas, viewer);
        assertEquals(SketchInk.CHARCOAL.color(), pixels.colors[64][64]);
        for (int x = 68; x < 72; x++) for (int y = 64; y < 68; y++) assertEquals(SketchInk.PAPER.color(), pixels.colors[x][y]);
    }

    @Test public void unrelatedMapsKeepTheirExistingCanvas() {
        when(service.sheetOf(view)).thenReturn(null);
        MapCanvas canvas = mock(MapCanvas.class); renderer.render(view, canvas, viewer);
        verifyNoInteractions(canvas);
    }

    @Test public void paintingUnderAStillCursorReframesItAndAVerticalStepRestoresTheOldCellsTrueInk() {
        SketchSession session = new SketchSession(view, sheet); when(service.session(viewer)).thenReturn(session);
        Pixels pixels = new Pixels(); renderer.render(view, pixels.canvas, viewer);
        assertEquals(new Color(20, 20, 20), pixels.colors[64][64]);
        session.paint(); renderer.render(view, pixels.canvas, viewer);
        for (int dx = 0; dx < 4; dx++) for (int dy = 0; dy < 4; dy++)
            assertEquals(dx == 0 || dx == 3 || dy == 0 || dy == 3 ? Color.WHITE : SketchInk.CHARCOAL.color(), pixels.colors[64 + dx][64 + dy]);
        session.move(0, 1); renderer.render(view, pixels.canvas, viewer);
        for (int dx = 0; dx < 4; dx++) for (int dy = 0; dy < 4; dy++) assertEquals(SketchInk.CHARCOAL.color(), pixels.colors[64 + dx][64 + dy]);
        assertEquals(new Color(20, 20, 20), pixels.colors[64][68]); assertEquals(SketchInk.PAPER.color(), pixels.colors[65][69]);
    }

    private static final class Pixels {
        final Color[][] colors = new Color[128][128];
        final MapCanvas canvas = mock(MapCanvas.class);
        Pixels() {
            doAnswer(call -> {
                int x = call.getArgument(0), y = call.getArgument(1);
                assertTrue("x=" + x, x >= 0 && x < 128); assertTrue("y=" + y, y >= 0 && y < 128);
                colors[x][y] = call.getArgument(2); return null;
            }).when(canvas).setPixelColor(anyInt(), anyInt(), any(Color.class));
        }
    }

    /** Verifies that stable cells are untouched and old cursors are cleared. */
    @Test
    public void redrawsOnlyChangedCellsAndClearsTheOldCursor() {
        Color[][] pixels = new Color[128][128];
        int[] writes = {0};
        MapCanvas canvas = (MapCanvas) Proxy.newProxyInstance(
                MapCanvas.class.getClassLoader(), new Class<?>[]{MapCanvas.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("hashCode")) {
                        return System.identityHashCode(proxy);
                    }
                    if (method.getName().equals("equals")) {
                        return proxy == args[0];
                    }
                    if (method.getName().equals("setPixelColor")) {
                        pixels[(int) args[0]][(int) args[1]] = (Color) args[2];
                        writes[0]++;
                    }
                    return null;
                });
        SketchSheet sheet = new SketchSheet();
        SketchRenderer renderer = new SketchRenderer(null);

        renderer.paint(canvas, sheet, null);
        assertEquals(128 * 128, writes[0]);
        renderer.paint(canvas, sheet, null);
        assertEquals(128 * 128, writes[0]);

        sheet.set(0, 0, SketchInk.CHARCOAL);
        renderer.paint(canvas, sheet, null);
        assertEquals(128 * 128 + 16, writes[0]);
        assertEquals(SketchInk.CHARCOAL.color(), pixels[0][0]);

        SketchSession session = new SketchSession(null, sheet);
        renderer.paint(canvas, sheet, session);
        assertEquals(new Color(20, 20, 20), pixels[64][64]);
        int withCursor = writes[0];
        renderer.paint(canvas, sheet, session);
        assertEquals(withCursor, writes[0]);

        session.move(1, 0);
        renderer.paint(canvas, sheet, session);
        assertEquals(SketchInk.PAPER.color(), pixels[64][64]);
        assertEquals(new Color(20, 20, 20), pixels[68][64]);
        renderer.paint(canvas, sheet, null);
        assertEquals(SketchInk.PAPER.color(), pixels[68][64]);
    }
}
