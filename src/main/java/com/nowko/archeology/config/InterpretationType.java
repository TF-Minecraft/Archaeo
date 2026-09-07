package com.nowko.archeology.config;

import java.util.List;

/**
 * One question the classification station asks about a find ({@code interpretations.yml} type).
 *
 * @param id YAML key such as {@code function} or {@code species}
 * @param displayName short English label
 * @param question player-facing prompt on the station
 * @param options phrases in this pool, at least a handful so three offers are a subset
 */
public record InterpretationType(
        String id,
        String displayName,
        String question,
        List<InterpretationTemplate> options
) {
}
