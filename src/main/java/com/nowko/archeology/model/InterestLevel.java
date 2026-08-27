package com.nowko.archeology.model;

/**
 * Staff-chosen wealth of a managed ruin. YAML and command keys are lowercase English.
 */
public enum InterestLevel {
    /** Modest finds and a short tracker radius. */
    LOW,
    /** Default field site. */
    MEDIUM,
    /** Richer dossier and wider detection. */
    HIGH,
    /** Rare, high-budget ruin. */
    EXCEPTIONAL;

    /**
     * Parses command or YAML input.
     *
     * @param raw {@code low}, {@code medium}, {@code high}, or {@code exceptional}
     * @return matching level, or {@code null} if unknown
     */
    public static InterestLevel fromInput(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.trim().toLowerCase()) {
            case "low" -> LOW;
            case "medium" -> MEDIUM;
            case "high" -> HIGH;
            case "exceptional" -> EXCEPTIONAL;
            default -> null;
        };
    }

    /**
     * @return config.yml key under {@code interest-levels}
     */
    public String yamlKey() {
        return name().toLowerCase();
    }
}
