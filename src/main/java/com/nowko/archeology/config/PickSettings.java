package com.nowko.archeology.config;

import java.util.List;

/**
 * Hand Pick from {@code config.yml}: cadence, empty-fill cue and window, find risk, and item copy.
 *
 * @param enabled whether the pick is issued and recognized
 * @param blockStages strikes to remove a find-cell fill (hidden; not shown on the HUD)
 * @param jornadaActions pick cycles restored each Minecraft day
 * @param strikeIntervalTicks ticks between counted strikes while left-click is held
 * @param cueClingsMin inclusive minimum soft clings before the ready ting (empty fill)
 * @param cueClingsMax inclusive maximum soft clings before the ready ting (empty fill)
 * @param readyWindowTicks ticks after the ting in which release is on time
 * @param visualCues particles and subtitles that mirror clings for players without sound
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
        int cueClingsMin,
        int cueClingsMax,
        int readyWindowTicks,
        boolean visualCues,
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
                1,
                3,
                20,
                true,
                8,
                20,
                70,
                "Hand Pick",
                List.of(
                        "Hold left-click on the open cut. Soft chimes, then release on the ready chime.",
                        "Not a mining pick."
                )
        );
    }
}
