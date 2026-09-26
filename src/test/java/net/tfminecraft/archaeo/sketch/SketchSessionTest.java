package net.tfminecraft.archaeo.sketch;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class SketchSessionTest {
    /** Verifies one immediate step followed by deterministic held-key repeats. */
    @Test
    public void tapMovesOnceAndHeldDirectionRepeatsAfterDelay() {
        SketchSession session = new SketchSession(UUID.randomUUID(), null, new SketchSheet());

        session.moveFromInput(1, 0);
        assertEquals(17, session.cursorX());
        for (int tick = 0; tick < 4; tick++) {
            session.moveFromInput(1, 0);
        }
        assertEquals(17, session.cursorX());

        session.moveFromInput(1, 0);
        assertEquals(18, session.cursorX());
        session.moveFromInput(1, 0);
        assertEquals(18, session.cursorX());
        session.moveFromInput(1, 0);
        assertEquals(19, session.cursorX());

        session.moveFromInput(0, 0);
        session.moveFromInput(1, 0);
        assertEquals(20, session.cursorX());
        session.moveFromInput(-1, 0);
        assertEquals(19, session.cursorX());

        session.moveFromInput(-1, -1);
        assertEquals(19, session.cursorX());
        assertEquals(15, session.cursorY());
    }

    /** Verifies that erasing fills every cell crossed between cursor steps. */
    @Test
    public void eraserFillsEveryCellBetweenCursorSteps() {
        SketchSheet sheet = new SketchSheet();
        for (int x = 16; x <= 19; x++) {
            sheet.set(x, 16, SketchInk.RED);
        }
        SketchSession session = new SketchSession(UUID.randomUUID(), null, sheet);

        session.beginEraseStroke();
        session.move(1, 0);
        session.updateEraseStroke();
        session.move(1, 0);
        session.updateEraseStroke();
        session.move(1, 0);
        session.updateEraseStroke();

        for (int x = 16; x <= 19; x++) {
            assertEquals("eraser skipped cell " + x, SketchInk.PAPER, sheet.at(x, 16));
        }
    }
}
