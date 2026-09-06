package com.nowko.archeology.excavation;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Per-hold cue schedule: 1–3 soft clings from the first prevented vanilla break, then one clang.
 * Rolled when the player starts holding. The range is fixed so Soon never becomes a HUD number.
 */
public final class HoldCuePlan {
    private static final int CLING_MIN = 1;
    private static final int CLING_MAX = 3;

    private final int cueClings;
    private int vanillaBreaks;

    /**
     * @param cueClings soft clings before the clang
     */
    HoldCuePlan(int cueClings) {
        this.cueClings = cueClings;
    }

    /**
     * @return a new roll for this hold
     */
    public static HoldCuePlan roll() {
        int clings = CLING_MIN + ThreadLocalRandom.current().nextInt(CLING_MAX - CLING_MIN + 1);
        return new HoldCuePlan(clings);
    }

    /**
     * Consumes one simulated vanilla break ({@link org.bukkit.block.Block#getBreakSpeed} reaching 1.0).
     *
     * @return which audible cue this break should play
     */
    public BreakCue nextCue() {
        vanillaBreaks++;
        if (vanillaBreaks <= cueClings) {
            return BreakCue.CLING;
        }
        if (vanillaBreaks == cueClings + 1) {
            return BreakCue.CLANG;
        }
        return BreakCue.AFTER;
    }

    /**
     * What to play on a prevented vanilla break.
     */
    public enum BreakCue {
        /** Soft cling: the clang is coming, beat count unknown. */
        CLING,
        /** Ready clang: ideal release. */
        CLANG,
        /** Held past the clang: extra cells come out. */
        AFTER
    }
}
