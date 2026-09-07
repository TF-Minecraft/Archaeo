package com.nowko.archeology.model;

import java.util.List;
import java.util.Locale;

/**
 * What one person on the excavation staff is allowed to do in the field.
 *
 * <p>The dig splits into two hands: whoever opens the cut with the pick, and whoever records and
 * lifts the piece with the brush. A role decides which of the two this person is trusted with, so a
 * newcomer can move spoil without being handed a fragile find.
 *
 * <p>There is deliberately no "may look but not touch" role. Anyone can walk up to the camp sign and
 * read the record, the dossier, the roster and the prism outline without being on the staff at all,
 * so a role that only takes things away from someone who had nothing would mean nothing.
 *
 * <p>{@link #ARCHAEOLOGIST} is the default: it is exactly what every excavator could do before roles
 * existed, so old dossiers keep behaving the way their director left them.
 */
public enum SiteRole {
    /** Owns the project. Never assigned by hand; it follows the camp. */
    DIRECTOR("director", "Director", true, "Runs the project and the staff."),
    /** Full field work: opens the cut and lifts pieces. */
    ARCHAEOLOGIST("archaeologist", "Archaeologist", true, "May dig the cut and lift finds."),
    /** Moves spoil only; the brush stays with the archaeologists. */
    EXCAVATOR("excavator", "Excavator", false, "May dig the cut, but not lift finds.");

    private final String yamlKey;
    private final String displayName;
    private final boolean mayRecover;
    private final String duty;

    /**
     * @param yamlKey stable id written to the dossier
     * @param displayName label shown on boards
     * @param mayRecover whether the brush may clean and lift a find
     * @param duty one-line description for board lore
     */
    SiteRole(String yamlKey, String displayName, boolean mayRecover, String duty) {
        this.yamlKey = yamlKey;
        this.displayName = displayName;
        this.mayRecover = mayRecover;
        this.duty = duty;
    }

    /**
     * @return stable id written to the dossier
     */
    public String yamlKey() {
        return yamlKey;
    }

    /**
     * @return label shown on boards
     */
    public String displayName() {
        return displayName;
    }

    /**
     * @return whether this role may brush a find clean and lift it
     */
    public boolean mayRecover() {
        return mayRecover;
    }

    /**
     * @return one-line description for board lore
     */
    public String duty() {
        return duty;
    }

    /**
     * The director role follows the camp, so it is never on the picker.
     *
     * @return roles the director may hand out, in board order
     */
    public static List<SiteRole> assignable() {
        return List.of(ARCHAEOLOGIST, EXCAVATOR);
    }

    /**
     * @return role given to staff whose dossier predates roles
     */
    public static SiteRole defaultRole() {
        return ARCHAEOLOGIST;
    }

    /**
     * @param raw yaml key, case-insensitive
     * @return matching role, or {@link #defaultRole()} when the key is missing or unknown
     */
    public static SiteRole fromYaml(String raw) {
        if (raw == null || raw.isBlank()) {
            return defaultRole();
        }
        String needle = raw.trim().toLowerCase(Locale.ROOT);
        for (SiteRole role : values()) {
            if (role.yamlKey.equals(needle)) {
                return role;
            }
        }
        return defaultRole();
    }
}
