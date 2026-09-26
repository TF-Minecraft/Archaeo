package net.tfminecraft.archaeo.sketch;

import static org.junit.Assert.*;

import org.junit.Test;

public class SketchSheetTest {
    @Test public void inksFromANewerReleaseLoadAsPaperAfterADowngradeWhileKnownStrokesSurvive() {
        byte[] stored = new SketchSheet().toBytes();
        stored[0] = (byte) SketchInk.RED.ordinal(); stored[1] = 42; stored[2] = (byte) 200;
        SketchSheet sheet = SketchSheet.fromBytes(stored);
        assertEquals(SketchInk.RED, sheet.at(0, 0)); assertEquals(SketchInk.PAPER, sheet.at(1, 0)); assertEquals(SketchInk.PAPER, sheet.at(2, 0));
        byte[] shortData = {(byte) SketchInk.MOSS.ordinal()};
        SketchSheet truncated = SketchSheet.fromBytes(shortData);
        assertEquals(SketchInk.MOSS, truncated.at(0, 0)); assertEquals(SketchInk.PAPER, truncated.at(SketchSheet.SIZE - 1, SketchSheet.SIZE - 1));
    }
}
