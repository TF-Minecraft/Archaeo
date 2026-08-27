package com.nowko.archeology.config;

/**
 * Catalog row from {@code strata.yml}: depth below surface and whether the layer is mandatory.
 *
 * @param id roman id such as {@code I}
 * @param order sort key, smallest first (most recent)
 * @param displayName English label
 * @param antiquity flavor text
 * @param depthMin blocks below surface for the top of the band
 * @param depthMax blocks below surface for the bottom of the band
 * @param alwaysPresent if {@code false}, generation may omit this layer
 */
public record StratumDefinition(
        String id,
        int order,
        String displayName,
        String antiquity,
        int depthMin,
        int depthMax,
        boolean alwaysPresent
) {
}
