package com.nowko.archeology.config;

import java.util.Set;

/**
 * One phrase from {@code interpretations.yml}: a possible answer to one station question.
 *
 * @param id catalog key such as {@code combat_edge}
 * @param typeId question this phrase answers ({@code function}, {@code formation}, {@code epoch})
 * @param displayName English phrase shown on the station and archive
 * @param suggestedBy tags that raise this phrase's chance of appearing among the three offers
 * @param suggestedFor artifact ids that must see at least one of these phrases in the three offers
 */
public record InterpretationTemplate(
        String id,
        String typeId,
        String displayName,
        Set<String> suggestedBy,
        Set<String> suggestedFor
) {
    /**
     * Weighting only: never hides the phrase and never paints it as correct.
     *
     * @param tags artifact and site-hint tags
     * @return whether this reading should be more likely in the station draw
     */
    public boolean suggestedBy(Set<String> tags) {
        if (tags == null || tags.isEmpty() || suggestedBy == null || suggestedBy.isEmpty()) {
            return false;
        }
        for (String tag : suggestedBy) {
            if (tags.contains(tag)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param artifactId catalog artifact key
     * @return whether this phrase is a sensible floor for that template
     */
    public boolean suggestedFor(String artifactId) {
        return artifactId != null
                && !artifactId.isBlank()
                && suggestedFor != null
                && suggestedFor.contains(artifactId);
    }
}
