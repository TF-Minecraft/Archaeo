package com.nowko.archeology.config;

import java.util.List;

/**
 * Hand Pick from {@code config.yml}: fill stages, strike cadence, find risk, and item copy.
 *
 * @param enabled whether the pick is issued and recognized
 * @param blockStages accumulated plugin strikes needed to remove a fill block
 * @param jornadaActions pick cycles restored each Minecraft day
 * @param strikeIntervalTicks ticks between counted strikes while left-click is held
 * @param conservationLossPerStrike conservation lost per extra strike on a detected find
 * @param conservationLossOnRemove extra loss when the pick fully removes a find cell
 * @param damagedBelowPercent mark the find damaged when conservation falls below this
 * @param itemName English display name
 * @param itemLore English lore lines
 */
public record PickSettings(
        boolean enabled,
        int blockStages,
        int jornadaActions,
        int strikeIntervalTicks,
        int conservationLossPerStrike,
        int conservationLossOnRemove,
        int damagedBelowPercent,
        String itemName,
        List<String> itemLore
) {
    /**
     * @return packaged defaults matching {@code config.yml}
     */
    public static PickSettings defaults() {
        return new PickSettings(
                true,
                6,
                8,
                25,
                8,
                20,
                70,
                "Hand Pick",
                List.of(
                        "Hold left-click on the open cut. Release to end the strike cycle.",
                        "Not a mining pick."
                )
        );
    }
}
