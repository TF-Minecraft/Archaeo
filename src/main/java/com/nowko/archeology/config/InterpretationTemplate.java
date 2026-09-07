package com.nowko.archeology.config;

import java.util.Set;

/**
 * One phrase from {@code interpretations.yml}: a possible answer to one station question.
 *
 * @param id catalog key such as {@code combat_edge}
 * @param typeId question this phrase answers ({@code function}, {@code agency}, {@code formation})
 * @param displayName English phrase shown on the station and archive
 * @param suggestedBy tags that raise this phrase's chance of appearing among the three offers
 */
public record InterpretationTemplate(String id, String typeId, String displayName, Set<String> suggestedBy) {
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
}
