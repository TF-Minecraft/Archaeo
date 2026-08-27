package com.nowko.archeology.config;

import java.util.Set;

/**
 * One entry from {@code artifacts.yml}: size range, tags, relic flag, and item to give on recovery.
 *
 * @param id template key
 * @param displayName English name shown to players
 * @param sizeMin minimum connected cells
 * @param sizeMax maximum connected cells
 * @param material flavor tag
 * @param rarity flavor tag
 * @param relic whether this counts toward the relic budget
 * @param weight generation weight
 * @param strata stratum ids this template may spawn in
 * @param tags matching tags for hints
 * @param item Bukkit material name for the recovered item
 */
public record ArtifactTemplate(
        String id,
        String displayName,
        int sizeMin,
        int sizeMax,
        String material,
        String rarity,
        boolean relic,
        int weight,
        Set<String> strata,
        Set<String> tags,
        String item
) {
    /**
     * Clamps a requested cell count into this template's size range.
     *
     * @param requested desired size
     * @return value between {@code sizeMin} and {@code sizeMax}
     */
    public int clampSize(int requested) {
        return Math.max(sizeMin, Math.min(sizeMax, requested));
    }
}
