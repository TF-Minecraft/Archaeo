package com.nowko.archeology.config;

import com.nowko.archeology.model.InterestLevel;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Trial auto-spawn of hidden ruins when chunks load ({@code auto-ruins} in {@code config.yml}).
 * Each chunk is considered at most once (persisted), including terrain generated before the plugin existed.
 * Density knobs keep the map sparse; surface and burial checks keep bad terrain out.
 *
 * @param enabled whether loaded chunks may receive an auto ruin
 * @param worlds world names that participate; empty means every world
 * @param chancePerChunk deterministic share of evaluated chunks that attempt a spawn (0–1)
 * @param minChunkDistance minimum Chebyshev distance in chunks to any existing site
 * @param maxSitesPerWorld hard cap per world; {@code 0} disables the cap
 * @param excludeSpawnChunks Chebyshev radius in chunks around world spawn that never auto-spawns
 * @param maxReliefBlocks allowed {@code p90 − p10} of column ground Y (gentle slope OK, mountains not)
 * @param minSoilFraction share of columns whose ground block is shovel-mineable soil
 * @param maxFloodedFraction share of columns that may have liquid above the ground (rejects open ocean)
 * @param minBuriedCells minimum cells that could hide a find before {@code createManagedRuin} runs
 * @param interestWeights relative weights for rolling {@link InterestLevel}
 * @param evaluateDelayTicks ticks to wait after chunk load so populate can finish on new terrain
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
        double maxFloodedFraction,
        int minBuriedCells,
        Map<InterestLevel, Integer> interestWeights,
        int evaluateDelayTicks,
        int maxEvaluationsPerTick,
        boolean notifyStaff
) {
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
                0.15,
                32,
                Map.copyOf(weights),
                20,
                1,
                true);
    }
}
