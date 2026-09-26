package net.tfminecraft.archaeo.sketch;

import org.bukkit.map.MapCanvas;
import org.junit.Test;

import java.awt.Color;
import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class SketchRendererTest {
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

        SketchSession session = new SketchSession(UUID.randomUUID(), null, sheet);
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
