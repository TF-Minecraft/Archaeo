package com.nowko.archeology.excavation;

import com.nowko.archeology.config.PickSettings;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Per-hold cue schedule: 1–3 soft clings from the first prevented vanilla break, then one clang.
 * Rolled when the player starts holding.
 */
public final class HoldCuePlan {
    private final int cueClings;
    private int vanillaBreaks;

    /**
     * @param cueClings soft clings before the clang
     */
    HoldCuePlan(int cueClings) {
        this.cueClings = cueClings;
    }

    /**
     * @param settings cling range
     * @return a new roll for this hold
     */
    public static HoldCuePlan roll(PickSettings settings) {
        int clingMin = Math.max(1, settings.cueClingsMin());
        int clingMax = Math.max(clingMin, settings.cueClingsMax());
        int clings = clingMin + ThreadLocalRandom.current().nextInt(clingMax - clingMin + 1);
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
        /** Held past the clang: this cell and the one below come out. */
        AFTER
    }
}
