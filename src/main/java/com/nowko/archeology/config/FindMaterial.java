package com.nowko.archeology.config;

import org.bukkit.Material;

import java.util.List;
import java.util.Locale;

/**
 * One entry from {@code materials.yml}: field-trace label, burial survival, and the lab chain.
 * The first step is a short wipe in the cabinet inventory.
 *
 * @param id catalog key such as {@code ceramic}
 * @param displayName English name shown in chat and HUD
 * @param survival multiplier on the buried-condition roll; {@code 1.0} keeps the roll, lower rots
 * @param wash whether water is an appropriate lab gesture for this material
 * @param steps lab chain from {@code materials.yml}; first entry is the cabinet wipe
 * @param cleanGlass pane colour of a cleaned field cell
 * @param stains stain ids from {@code sketch.lab.stains} that may appear on this material
 */
public record FindMaterial(
        String id,
        String displayName,
        double survival,
        boolean wash,
        List<String> steps,
        Material cleanGlass,
        List<String> stains
) {
    /**
     * @return first lab step id, or {@code clean} when the list is empty
     */
    public String firstStep() {
        if (steps == null || steps.isEmpty()) {
            return "clean";
        }
        String step = steps.get(0);
        return step == null || step.isBlank() ? "clean" : step.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * @return whether water belongs on this first step (ceramic wash, never metal)
     */
    public boolean firstStepUsesWater() {
        String step = firstStep();
        return "wash".equals(step) || ("clean".equals(step) && wash);
    }

    /**
     * @return infinitive for lore, such as {@code wash} or {@code dry}
     */
    public String firstStepVerb() {
        return switch (firstStep()) {
            case "wash" -> "wash";
            case "dry" -> "dry";
            case "stabilize" -> "stabilize";
            case "conserve" -> "conserve";
            default -> wash ? "wash" : "clean";
        };
    }

    /**
     * @return present-tense HUD verb
     */
    public String firstStepGerund() {
        return switch (firstStep()) {
            case "wash" -> "Washing";
            case "dry" -> "Drying";
            case "stabilize" -> "Stabilizing";
            case "conserve" -> "Conserving";
            default -> wash ? "Washing" : "Cleaning";
        };
    }

    /**
     * @return short completion line after the wipe
     */
    public String firstStepDone() {
        String name = displayName == null || displayName.isBlank() ? "piece" : displayName.toLowerCase(Locale.ROOT);
        return switch (firstStep()) {
            case "wash" -> "The " + name + " is washed.";
            case "dry" -> "The " + name + " is dry.";
            case "stabilize" -> "The " + name + " is stable.";
            case "conserve" -> "The " + name + " is conserved.";
            default -> wash ? "The " + name + " is washed." : "The " + name + " is clean.";
        };
    }

    /**
     * @return pane used for a cleaned cell
     */
    public Material cleanPane() {
        return cleanGlass == null || cleanGlass.isAir() ? Material.WHITE_STAINED_GLASS_PANE : cleanGlass;
    }
}
