package net.tfminecraft.archaeo.config;

/**
 * Prospecting kit (cata) rules from {@code config.yml}. Item id is {@code prospect.item}.
 *
 * @param enabled whether the kit can sample ground
 * @param pointsRequired unique blocks needed to confirm a hidden site
 * @param useTicks channel time per sample and action-bar duration
 * @param minSampleDistance minimum blocks between a player's sample points
 */
public record ProspectSettings(
        boolean enabled,
        int pointsRequired,
        int useTicks,
        int minSampleDistance
) {
    /**
     * @return packaged defaults matching {@code config.yml}
     */
    public static ProspectSettings defaults() {
        return new ProspectSettings(true, 4, 40, 3);
    }
}
