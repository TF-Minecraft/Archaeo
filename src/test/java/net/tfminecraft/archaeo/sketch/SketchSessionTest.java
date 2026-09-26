package net.tfminecraft.archaeo.sketch;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

import java.util.*;
import org.bukkit.map.MapView;
import org.junit.Test;

public class SketchSessionTest {
    /** Verifies one immediate step followed by deterministic held-key repeats. */
    @Test
    public void tapMovesOnceAndHeldDirectionRepeatsAfterDelay() {
        SketchSession session = new SketchSession(null, new SketchSheet());

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
        SketchSession session = new SketchSession(null, sheet);

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

    @Test public void holdingAMovementKeyStopsTheCursorAtEveryEdgeAndPaintsTheCornerCells() {
        SketchSheet sheet = new SketchSheet(); SketchSession session = new SketchSession(mock(MapView.class), sheet);
        assertEquals(16, session.cursorX()); assertEquals(16, session.cursorY());
        for (int step = 0; step < 40; step++) session.move(-1, -1);
        assertEquals(0, session.cursorX()); assertEquals(0, session.cursorY()); assertFalse(session.move(-1, -1));
        assertTrue(session.paint()); assertEquals(SketchInk.CHARCOAL, sheet.at(0, 0));
        for (int step = 0; step < 40; step++) session.move(1, 1);
        assertEquals(SketchSheet.SIZE - 1, session.cursorX()); assertEquals(SketchSheet.SIZE - 1, session.cursorY()); assertFalse(session.move(1, 1));
        assertTrue(session.paint()); assertEquals(SketchInk.CHARCOAL, sheet.at(SketchSheet.SIZE - 1, SketchSheet.SIZE - 1));
        assertTrue(session.erase()); assertFalse(session.erase()); assertEquals(SketchInk.PAPER, sheet.at(SketchSheet.SIZE - 1, SketchSheet.SIZE - 1));
    }

    @Test public void spaceWalksAllFiveStrokesThenWrapsWithoutEverSelectingEraserPaper() {
        SketchSession session = new SketchSession(mock(MapView.class), new SketchSheet());
        List<SketchInk> seen = new ArrayList<>(List.of(session.ink()));
        for (int press = 0; press < 5; press++) { session.cycleInk(); seen.add(session.ink()); }
        assertEquals(List.of(SketchInk.CHARCOAL, SketchInk.RED, SketchInk.OCHRE, SketchInk.SLATE, SketchInk.MOSS, SketchInk.CHARCOAL), seen);
    }

    /** Verifies a held eraser, re-clicked every four ticks as vanilla repeats use, clears exactly the cells the cursor passes. */
    @Test public void heldEraserFollowsTheCursorDiagonallyUpAndLeftThenStraightUpWithoutGapsOrStrayCells() {
        SketchSheet sheet = new SketchSheet();
        for (int x = 0; x < SketchSheet.SIZE; x++) for (int y = 0; y < SketchSheet.SIZE; y++) sheet.set(x, y, SketchInk.RED);
        SketchSession session = new SketchSession(null, sheet); Set<List<Integer>> passed = new HashSet<>(List.of(List.of(16, 16)));
        // Same order as the input loop: a right-click lands, then the tick moves the cursor and extends the stroke.
        for (int tick = 0; tick < 24; tick++) {
            if (tick % 4 == 0) session.beginEraseStroke();
            session.moveFromInput(tick < 12 ? -1 : 0, -1); session.updateEraseStroke();
            passed.add(List.of(session.cursorX(), session.cursorY()));
        }
        assertEquals(11, session.cursorX()); assertEquals(5, session.cursorY()); assertEquals(12, passed.size());
        // Cells reached between right-clicks are only cleared by the stroke interpolation.
        assertTrue(passed.containsAll(List.of(List.of(14, 14), List.of(11, 10), List.of(11, 8))));
        for (int x = 0; x < SketchSheet.SIZE; x++) for (int y = 0; y < SketchSheet.SIZE; y++)
            assertEquals(x + "," + y, passed.contains(List.of(x, y)) ? SketchInk.PAPER : SketchInk.RED, sheet.at(x, y));
    }

    /** Verifies the stroke outlives five input-loop ticks after a right-click and no more. */
    @Test public void eraserStrokeEndsFiveTicksAfterTheLastRightClickSoALaterMoveKeepsItsInk() {
        for (int idle : new int[]{4, 5}) {
            SketchSheet sheet = new SketchSheet(); sheet.set(16, 16, SketchInk.RED); sheet.set(16, 17, SketchInk.RED);
            SketchSession session = new SketchSession(null, sheet);
            session.beginEraseStroke();
            for (int tick = 0; tick < idle; tick++) { session.moveFromInput(0, 0); session.updateEraseStroke(); }
            session.moveFromInput(0, 1); session.updateEraseStroke();
            assertEquals(SketchInk.PAPER, sheet.at(16, 16));
            assertEquals("after " + idle + " idle ticks", idle < 5 ? SketchInk.PAPER : SketchInk.RED, sheet.at(16, 17));
        }
    }
}
