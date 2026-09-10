package com.nowko.archeology.config;

import org.bukkit.Material;

import java.util.List;
import java.util.Locale;

/**
 * One entry from {@code materials.yml}: field-trace label, burial survival, and the lab chain.
 * The first cabinet action is always a wipe; later {@code steps} are listed for later.
 *
 * @param id catalog key such as {@code ceramic}
 * @param displayName English name shown in chat and HUD
 * @param survival multiplier on the buried-condition roll; {@code 1.0} keeps the roll, lower rots
 * @param steps lab chain from {@code materials.yml}; first entry is the cabinet wipe
 * @param cleanGlass pane colour of a cleaned field cell
 * @param stains stain ids from {@code sketch.lab.stains} that may appear on this material
 */
public record FindMaterial(
        String id,
        String displayName,
        double survival,
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
     * @return infinitive for lore
     */
    public String firstStepVerb() {
        return "clean";
    }

    /**
     * @return present-tense HUD verb
     */
    public String firstStepGerund() {
        return "Cleaning";
    }

    /**
     * @return short completion line after the wipe
     */
    public String firstStepDone() {
        String name = displayName == null || displayName.isBlank() ? "piece" : displayName.toLowerCase(Locale.ROOT);
        return "The " + name + " is clean.";
    }

    /**
     * @return pane used for a cleaned cell
     */
    public Material cleanPane() {
        return cleanGlass == null || cleanGlass.isAir() ? Material.WHITE_STAINED_GLASS_PANE : cleanGlass;
    }
}
