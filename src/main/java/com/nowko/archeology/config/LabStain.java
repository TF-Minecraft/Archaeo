package com.nowko.archeology.config;

import org.bukkit.Material;

import java.util.Locale;

/**
 * One dirt kind in the cabinet lab, from {@code sketch.lab.stains}.
 * Colour and name live here; the material only lists which stains may appear.
 *
 * @param id key such as {@code soil} or {@code rust}
 * @param displayName English name on the dirty pane
 * @param glass pane colour of this stain
 * @param tool rack tool id that wipes it
 */
public record LabStain(String id, String displayName, Material glass, String tool) {
    /**
     * @param toolId cursor tool
     * @return whether that tool wipes this stain
     */
    public boolean allowsTool(String toolId) {
        if (tool == null || tool.isBlank()) {
            return true;
        }
        return tool.equalsIgnoreCase(toolId == null ? "" : toolId);
    }

    /**
     * @return pane used on the field
     */
    public Material pane() {
        return glass == null || glass.isAir() ? Material.BROWN_STAINED_GLASS_PANE : glass;
    }

    /**
     * @return name shown on the pane
     */
    public String label() {
        if (displayName == null || displayName.isBlank()) {
            return id == null || id.isBlank() ? "Dirt" : id;
        }
        return displayName;
    }

    /**
     * @return rack tool key, lower case
     */
    public String toolId() {
        return tool == null ? "" : tool.trim().toLowerCase(Locale.ROOT);
    }
}
