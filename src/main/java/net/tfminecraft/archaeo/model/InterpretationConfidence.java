package net.tfminecraft.archaeo.model;

import java.util.Locale;

/**
 * How sure the author is of a reading. It is their opinion, not a truth score.
 */
public enum InterpretationConfidence {
    /** Tentative. */
    LOW("low", "low"),
    /** Working hypothesis. */
    MEDIUM("medium", "medium"),
    /** The author would defend it. */
    HIGH("high", "high");

    private final String yamlKey;
    private final String displayName;

    /**
     * @param yamlKey stable id written to the dossier
     * @param displayName English label on boards and books
     */
    InterpretationConfidence(String yamlKey, String displayName) {
        this.yamlKey = yamlKey;
        this.displayName = displayName;
    }

    /**
     * @return stable id written to the dossier
     */
    public String yamlKey() {
        return yamlKey;
    }

    /**
     * @return English label on boards and books
     */
    public String displayName() {
        return displayName;
    }

    /**
     * @param raw yaml key, case-insensitive
     * @return matching band, or {@link #MEDIUM} when missing or unknown
     */
    public static InterpretationConfidence fromYaml(String raw) {
        if (raw == null || raw.isBlank()) {
            return MEDIUM;
        }
        String needle = raw.trim().toLowerCase(Locale.ROOT);
        for (InterpretationConfidence value : values()) {
            if (value.yamlKey.equals(needle)) {
                return value;
            }
        }
        return MEDIUM;
    }
}
