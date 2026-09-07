package com.nowko.archeology.config;

/**
 * {@code excavation.limits}: the temporary outline the camp board draws for one viewer, which is
 * how an established dig is found again once the tracker stops calling it.
 *
 * @param seconds how long the outline stays up after the board button
 * @param thickness bar width in blocks; a thread reads as a line, a fat bar reads as scaffolding
 * @param viewDistance blocks from which the client still renders the bars
 */
public record LimitsSettings(
        int seconds,
        double thickness,
        int viewDistance
) {
    /**
     * @return packaged defaults matching {@code config.yml}
     */
    public static LimitsSettings defaults() {
        return new LimitsSettings(12, 0.08, 96);
    }
}
