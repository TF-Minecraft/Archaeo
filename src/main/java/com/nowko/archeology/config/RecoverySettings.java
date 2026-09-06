package com.nowko.archeology.config;

/**
 * Field brush recovery from {@code excavation.brush} in {@code config.yml}.
 *
 * @param enabled whether the brush can lift a fully exposed find
 * @param channelTicks hold time per uncleaned fill cell ({@code hold-ticks})
 * @param maxCellsToClean most cubes that must be brushed; larger shapes lift after this many
 * @param progressBar whether a boss bar shows remaining brush time
 */
public record RecoverySettings(
        boolean enabled,
        int channelTicks,
        int maxCellsToClean,
        boolean progressBar
) {
    /**
     * @return packaged defaults matching {@code config.yml}
     */
    public static RecoverySettings defaults() {
        return new RecoverySettings(true, 40, 6, true);
    }
}
