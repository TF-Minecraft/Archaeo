package net.tfminecraft.archaeo.config;

/**
 * Catalog row from {@code strata.yml}: depth below surface and whether the layer is mandatory.
 *
 * @param id roman id such as {@code I}
 * @param order sort key, smallest first (most recent)
 * @param displayName English label
 * @param depthMin blocks below the median datum for the top of the band
 * @param depthMax blocks below the median datum for the bottom of the band
 * @param alwaysPresent if {@code false}, generation may omit this layer
 */
public record StratumDefinition(
        String id,
        int order,
        String displayName,
        int depthMin,
        int depthMax,
        boolean alwaysPresent
) {
}
