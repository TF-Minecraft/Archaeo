package com.nowko.archeology.config;

import com.nowko.archeology.model.InterestLevel;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Trial auto-spawn of hidden ruins when chunks load ({@code auto-ruins} in {@code config.yml}).
 * Each chunk is considered at most once (persisted), including terrain generated before the plugin existed.
 * Density knobs keep the map sparse; surface checks keep bad terrain out.
 *
 * @param enabled whether loaded chunks may receive an auto ruin
 * @param worlds world names that participate; empty means every world
 * @param chancePerChunk deterministic share of evaluated chunks that attempt a spawn (0–1)
 * @param minChunkDistance minimum Chebyshev distance in chunks to any existing site
 * @param maxSitesPerWorld hard cap per world; {@code 0} disables the cap
 * @param excludeSpawnChunks Chebyshev radius in chunks around world spawn that never auto-spawns
 * @param maxReliefBlocks allowed {@code p90 − p10} of a sparse surface-height grid
 * @param minSoilFraction minimum share of sparse surface samples with shovel-mineable ground
 * @param excludedBiomes biome path keys that reject a chunk (oceans/rivers by default); empty disables the gate
 * @param interestWeights relative weights for rolling {@link InterestLevel}
 * @param maxPending maximum chunks waiting in the drain queue; further loads are skipped until a slot frees
 * @param maxUnloadPurgePerTick max queue entries checked per drain tick for “still loaded?” (drops unloaded
 *     without marking). Caps TPS cost if {@code max-pending} is set very high
 * @param maxEvaluationsPerTick how many full fitness checks may run in one server tick
 * @param notifyStaff whether online staff receive a chat line with a clickable teleport on each spawn
 */
public record AutoRuinSettings(
        boolean enabled,
        List<String> worlds,
        double chancePerChunk,
        int minChunkDistance,
        int maxSitesPerWorld,
        int excludeSpawnChunks,
        int maxReliefBlocks,
        double minSoilFraction,
        Set<String> excludedBiomes,
        Map<InterestLevel, Integer> interestWeights,
        int maxPending,
        int maxUnloadPurgePerTick,
        int maxEvaluationsPerTick,
        boolean notifyStaff
) {
    /**
     * Packaged ocean and river biome ids. Beaches and shores are intentionally omitted.
     *
     * @return default excluded biome path keys
     */
    public static Set<String> defaultExcludedBiomes() {
        return Set.of(
                "ocean",
                "deep_ocean",
                "warm_ocean",
                "lukewarm_ocean",
                "deep_lukewarm_ocean",
                "cold_ocean",
                "deep_cold_ocean",
                "frozen_ocean",
                "deep_frozen_ocean",
                "deep_warm_ocean",
                "river",
                "frozen_river");
    }

    /**
     * @return packaged trial defaults matching {@code config.yml}
     */
    public static AutoRuinSettings defaults() {
        Map<InterestLevel, Integer> weights = new EnumMap<>(InterestLevel.class);
        weights.put(InterestLevel.LOW, 50);
        weights.put(InterestLevel.MEDIUM, 35);
        weights.put(InterestLevel.HIGH, 12);
        weights.put(InterestLevel.EXCEPTIONAL, 3);
        return new AutoRuinSettings(
                true,
                List.of(),
                0.004,
                16,
                60,
                32,
                6,
                0.35,
                defaultExcludedBiomes(),
                Map.copyOf(weights),
                4,
                32,
                1,
                true);
    }

    /**
     * Normalizes a YAML biome id to the path key used by Bukkit ({@code ocean}, not {@code minecraft:ocean}).
     *
     * @param raw config entry
     * @return lowercase path key, or {@code null} if blank
     */
    public static String normalizeBiomeId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim().toLowerCase();
        int colon = trimmed.indexOf(':');
        if (colon >= 0) {
            trimmed = trimmed.substring(colon + 1);
        }
        return trimmed.isBlank() ? null : trimmed;
    }

    /**
     * @param raw YAML list, or {@code null}
     * @param fallback used when the list is missing
     * @return normalized unique biome keys in config order
     */
    public static Set<String> normalizeBiomeList(List<String> raw, Set<String> fallback) {
        if (raw == null) {
            return fallback;
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String entry : raw) {
            String id = normalizeBiomeId(entry);
            if (id != null) {
                normalized.add(id);
            }
        }
        return Set.copyOf(normalized);
    }
}
