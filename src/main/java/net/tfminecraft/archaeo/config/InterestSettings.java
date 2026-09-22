package net.tfminecraft.archaeo.config;

import net.tfminecraft.archaeo.model.InterestLevel;

/**
 * Generation budget for one {@link InterestLevel}, loaded from {@code interest.yml}.
 *
 * @param level enum this row belongs to
 * @param displayName English label
 * @param baseWealth wealth used by hint filters
 * @param variation extra wealth used only for prospecting flavour (never below {@code baseWealth})
 * @param minFinds inclusive lower find count
 * @param maxFinds inclusive upper find count
 * @param minRelics inclusive lower relic count
 * @param maxRelics inclusive upper relic count
 * @param hintCount how many hints to attach
 * @param stratumIvChance chance that stratum IV exists
 * @param disturbedChance chance a present band is marked disturbed
 */
public record InterestSettings(
        InterestLevel level,
        String displayName,
        int baseWealth,
        int variation,
        int minFinds,
        int maxFinds,
        int minRelics,
        int maxRelics,
        int hintCount,
        double stratumIvChance,
        double disturbedChance
) {
}
