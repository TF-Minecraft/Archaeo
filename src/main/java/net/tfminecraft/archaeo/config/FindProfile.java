package net.tfminecraft.archaeo.config;

import java.util.Locale;
import java.util.Optional;

/**
 * Classification path for one find template: which station questions apply.
 * Individual is whoever the lore treats as a person, including fantasy races.
 */
public enum FindProfile {
    /** Made things: tools, vessels, ornaments. */
    OBJECT,
    /** Remains of a person (or a people the staff configured). */
    INDIVIDUAL,
    /** Faunal remains. */
    ANIMAL;

    /**
     * Reads a YAML profile key on an artifact. Blank or unknown values become {@link #OBJECT}.
     *
     * @param raw {@code object}, {@code individual}, or {@code animal}
     * @return profile the station will use
     */
    public static FindProfile fromConfig(String raw) {
        return parseListed(raw).orElse(OBJECT);
    }

    /**
     * Parses one token from an option's {@code profiles} list. Unknown tokens are ignored
     * so a typo does not silently move a species phrase onto objects.
     *
     * @param raw YAML token
     * @return profile, or empty if the token is not a known key
     */
    public static Optional<FindProfile> parseListed(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String key = raw.trim().toLowerCase(Locale.ROOT);
        for (FindProfile profile : values()) {
            if (profile.id().equals(key)) {
                return Optional.of(profile);
            }
        }
        return Optional.empty();
    }

    /**
     * @return YAML key ({@code object}, {@code individual}, {@code animal})
     */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
