package com.nowko.archeology.config;

import java.util.Locale;

/**
 * How {@code lift-on-ready} and {@code lift-if-late} cubes are chosen around the aimed cell.
 */
public enum BreakShape {
    /** Aimed cube, then straight down. */
    DOWN,
    /** Aimed 3×3, then the 3×3 one block below (centre of that layer is the cube under the aim). */
    AROUND,
    /** Same 3×3×2 as {@link #AROUND}, shuffled (aimed cube still first). */
    RANDOM;

    /**
     * @param raw YAML {@code break-shape} token
     * @return parsed shape; blank or unknown means {@link #DOWN}
     */
    public static BreakShape parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return DOWN;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "around", "area", "ring", "plus", "cross" -> AROUND;
            case "random", "shuffle" -> RANDOM;
            default -> DOWN;
        };
    }
}
