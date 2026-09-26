package net.tfminecraft.archaeo.excavation;

import org.junit.Test;

import static net.tfminecraft.archaeo.excavation.HoldCuePlan.BreakCue.*;
import static org.junit.Assert.*;

public class HoldCuePlanTest {
    @Test
    public void rolledPlansPlayOneToThreeClingsThenExactlyOneClang() {
        for (int sample = 0; sample < 100; sample++) {
            HoldCuePlan plan = HoldCuePlan.roll();
            int clings = 0;
            HoldCuePlan.BreakCue cue;
            while ((cue = plan.nextCue()) == CLING) {
                clings++;
                assertTrue("A ready cue must follow at most three clings", clings <= 3);
            }
            assertTrue(clings >= 1);
            assertEquals(CLANG, cue);
            for (int beat = 0; beat < 10; beat++) assertEquals(AFTER, plan.nextCue());
        }
    }

    @Test
    public void eachHoldHasAnIndependentCueClock() {
        HoldCuePlan finished = HoldCuePlan.roll();
        HoldCuePlan untouched = HoldCuePlan.roll();
        assertNotSame(finished, untouched);
        for (int beat = 0; beat < 4; beat++) finished.nextCue();
        assertEquals(AFTER, finished.nextCue());
        assertEquals(CLING, untouched.nextCue());
        assertEquals(CLING, HoldCuePlan.roll().nextCue());
    }
}
