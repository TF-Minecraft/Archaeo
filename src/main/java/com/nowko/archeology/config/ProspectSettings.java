package com.nowko.archeology.config;

import java.util.List;

/**
 * Prospecting kit (cata) from {@code config.yml}.
 *
 * @param enabled whether the kit can sample ground
 * @param pointsRequired unique blocks needed to confirm a hidden site
 * @param useTicks channel time per sample and action-bar duration
 * @param minSampleDistance minimum blocks between a player's sample points
 * @param itemName English display name
 * @param itemLore English lore lines
 */
public record ProspectSettings(
        boolean enabled,
        int pointsRequired,
        int useTicks,
        int minSampleDistance,
        String itemName,
        List<String> itemLore
) {
    /**
     * @return packaged defaults matching {@code config.yml}
     */
    public static ProspectSettings defaults() {
        return new ProspectSettings(
                true,
                4,
                40,
                3,
                "Prospecting kit",
                List.of(
                        "Right-click several ground points in a suspected chunk.",
                        "Confirm the site before you can plant a camp."
                )
        );
    }
}
