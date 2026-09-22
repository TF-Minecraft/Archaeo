package net.tfminecraft.archaeo.excavation;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Per-hold cue schedule: 1–3 soft clings, then one clang (Release).
 * Rolled when the player starts holding. Each beat is one cue-clock interval
 * ({@code cue-ticks} or a legacy vanilla break sample).
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
     * Consumes one cue-clock beat (one {@code cue-ticks} interval, or one legacy vanilla break).
     *
     * @return which audible cue this beat should play
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
