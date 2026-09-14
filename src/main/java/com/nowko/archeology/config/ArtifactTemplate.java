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
 * @param rarity optional display tier override; blank means derive from {@code weight} via {@code rarity-from-weight}
 * @param relic whether this counts toward the relic budget
 * @param weight generation weight
 * @param strata stratum ids this template may spawn in
 * @param tags matching tags for hints
 * @param profile which station questions this template uses
 * @param item Bukkit material name for the recovered item
 * @param studyNotes English note revealed when the piece is studied at camp; may be blank
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
        FindProfile profile,
        String item,
        String studyNotes
) {
    /**
     * Missing profile is treated as an object so older YAML still loads.
     */
    public ArtifactTemplate {
        profile = profile == null ? FindProfile.OBJECT : profile;
    }

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
