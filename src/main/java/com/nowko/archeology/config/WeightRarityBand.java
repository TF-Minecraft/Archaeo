package com.nowko.archeology.config;

/**
 * One weight band used when an artifact omits {@code rarity:}.
 * Lower {@code maxWeight} bands are checked first; the first match wins.
 *
 * @param id rarity key such as {@code rare}
 * @param maxWeight inclusive upper weight for this tier
 */
public record WeightRarityBand(String id, int maxWeight) {
}
