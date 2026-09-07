package com.nowko.archeology.config;

import java.util.Set;

/**
 * One entry from {@code interpretations.yml}: a reading a player may attach after studying a find.
 *
 * @param id catalog key such as {@code conflict}
 * @param displayName English phrase shown on boards and books
 * @param suggestedBy artifact tags that should list this reading first; they never hide the rest
 */
public record InterpretationTemplate(String id, String displayName, Set<String> suggestedBy) {
    /**
     * @param tags artifact tags revealed by study
     * @return whether this reading should be offered before the unsorted remainder
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
