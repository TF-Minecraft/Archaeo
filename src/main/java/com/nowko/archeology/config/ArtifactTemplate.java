package com.nowko.archeology.config;

import org.bukkit.Material;

import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * One entry from {@code artifacts.yml}: size range, tags, relic flag, and item(s) to give on recovery.
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
 * @param items Bukkit material names; generation picks one when there are several
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
        List<String> items,
        String studyNotes
) {
    /**
     * Missing profile is treated as an object so older YAML still loads. An empty item pool
     * becomes brick so recovery always has a vanilla stack.
     */
    public ArtifactTemplate {
        profile = profile == null ? FindProfile.OBJECT : profile;
        items = items == null || items.isEmpty() ? List.of("BRICK") : List.copyOf(items);
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

    /**
     * Picks the Bukkit material this instance will use. One catalog entry returns that name;
     * several entries pick uniformly.
     *
     * @param random site RNG; {@code null} uses the first entry
     * @return catalog material name
     */
    public String pickItem(Random random) {
        if (items.size() == 1 || random == null) {
            return items.getFirst();
        }
        return items.get(random.nextInt(items.size()));
    }

    /**
     * Resolves a stored or catalog material name to a Bukkit item. Unknown names become brick.
     *
     * @param chosen name stored on the find, or {@code null} to use the first catalog entry
     * @return item material
     */
    public Material resolveItem(String chosen) {
        String name = chosen == null || chosen.isBlank() ? items.getFirst() : chosen;
        Material material = Material.matchMaterial(name);
        if (material == null || material.isAir() || !material.isItem()) {
            return Material.BRICK;
        }
        return material;
    }
}
