package net.tfminecraft.archaeo.config;

import java.util.Set;

/**
 * One phrase from {@code interpretations.yml}: a possible answer to one station question.
 *
 * @param id catalog key such as {@code combat_edge}
 * @param typeId question this phrase answers ({@code function}, {@code species}, {@code epoch})
 * @param displayName English phrase shown on the station and archive
 * @param suggestedBy tags that raise this phrase's chance of appearing among the three offers
 * @param suggestedFor artifact ids that must see at least one of these phrases in the three offers
 * @param profiles station paths that may draw this phrase; empty means every path
 */
public record InterpretationTemplate(
        String id,
        String typeId,
        String displayName,
        Set<String> suggestedBy,
        Set<String> suggestedFor,
        Set<FindProfile> profiles
) {
    /**
     * Missing path list means the phrase is offered on every classification path.
     */
    public InterpretationTemplate {
        profiles = profiles == null ? Set.of() : Set.copyOf(profiles);
    }

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

    /**
     * Empty {@code profiles} means the phrase is in every path (epoch, and legacy files).
     *
     * @param profile find path
     * @return whether this phrase may be offered
     */
    public boolean appliesTo(FindProfile profile) {
        if (profiles == null || profiles.isEmpty()) {
            return true;
        }
        return profile != null && profiles.contains(profile);
    }
}
