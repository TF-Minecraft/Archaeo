package com.nowko.archeology.config;

import java.util.List;

/**
 * Field brush recovery from {@code config.yml}: channel time, tedium cap, and item copy.
 *
 * @param enabled whether the brush can lift a fully exposed find
 * @param channelTicks hold time per uncleaned fill cell
 * @param maxCellsToClean most cubes that must be brushed; larger shapes lift after this many
 * @param itemName English display name for {@code /archaeo brush give}
 * @param itemLore English lore lines
 */
public record RecoverySettings(
        boolean enabled,
        int channelTicks,
        int maxCellsToClean,
        String itemName,
        List<String> itemLore
) {
    /**
     * @return packaged defaults matching {@code config.yml}
     */
    public static RecoverySettings defaults() {
        return new RecoverySettings(
                true,
                20,
                6,
                "Field brush",
                List.of(
                        "Right-click a fully exposed find. Dust leaves each cube you clean.",
                        "The last cube lifts the piece onto the ground."
                )
        );
    }
}
