package com.nowko.archeology.config;

/**
 * {@code excavation.limits}: the temporary outline the camp board draws for one viewer, which is
 * how an established dig is found again once the tracker stops calling it.
 *
 * @param seconds how long the outline stays up after the board button
 * @param intervalTicks ticks between redraws; particles are short-lived, so this is the frame rate
 * @param viewDistance blocks from the dig beyond which the outline is not drawn for that player
 */
public record LimitsSettings(
        int seconds,
        int intervalTicks,
        int viewDistance
) {
    /**
     * @return packaged defaults matching {@code config.yml}
     */
    public static LimitsSettings defaults() {
        return new LimitsSettings(12, 10, 96);
    }
}
