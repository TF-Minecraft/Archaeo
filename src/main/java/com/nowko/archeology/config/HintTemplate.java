package com.nowko.archeology.config;

import java.util.Set;

/**
 * One entry from {@code hints.yml}: English text plus filters against the generated dossier.
 *
 * @param id template key
 * @param text player-facing hint
 * @param weight pick weight
 * @param tags flavor tags
 * @param requireTagsAny find tags: at least one must match
 * @param requireTagsAll find tags: all must match
 * @param requireStrataAll strata that must be present
 * @param requireMissingStratum stratum that must be absent, or {@code null}
 * @param minWealth minimum interest wealth, or {@code null}
 * @param maxWealth maximum interest wealth, or {@code null}
 * @param minStrata minimum present stratum count, or {@code null}
 * @param requireDisturbed if non-null, whether a disturbed band is required
 */
public record HintTemplate(
        String id,
        String text,
        int weight,
        Set<String> tags,
        Set<String> requireTagsAny,
        Set<String> requireTagsAll,
        Set<String> requireStrataAll,
        String requireMissingStratum,
        Integer minWealth,
        Integer maxWealth,
        Integer minStrata,
        Boolean requireDisturbed
) {
}
