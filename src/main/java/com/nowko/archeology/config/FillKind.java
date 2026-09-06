package com.nowko.archeology.config;

import com.nowko.archeology.excavation.PrismFill;
import org.bukkit.Material;

import java.util.Locale;

/**
 * Which prism substrate a tool profile may lift.
 */
public enum FillKind {
    /** Dirt, sand, gravel, clay, and other loose ground. */
    SOFT,
    /** Stone, ore, and other compact fill. */
    HARD,
    /** Any {@link PrismFill#isTerrainFill(Material)}. */
    ANY;

    /**
     * @param material live block type
     * @return whether this profile may work that cell
     */
    public boolean allows(Material material) {
        if (!PrismFill.isTerrainFill(material)) {
            return false;
        }
        return switch (this) {
            case ANY -> true;
            case SOFT -> PrismFill.isSoftFill(material);
            case HARD -> !PrismFill.isSoftFill(material);
        };
    }

    /**
     * @param raw YAML token such as {@code soft}
     * @return parsed kind; blank means {@link #ANY}
     */
    public static FillKind parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return ANY;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "soft", "loose", "dirt" -> SOFT;
            case "hard", "compact", "stone" -> HARD;
            case "any", "all", "both" -> ANY;
            default -> ANY;
        };
    }
}
