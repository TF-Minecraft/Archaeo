package net.tfminecraft.archaeo.config;

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
    // Rows come from LabSettings#defaults or CatalogRegistry#loadLab, which never leave a field
    // null and never pick air for the pane (ConfigEnums#material falls back instead).

    /**
     * @param toolId cursor tool, or {@code null} when the cursor is not a rack tool
     * @return whether that tool wipes this stain; a blank {@code tool} accepts any cursor
     */
    public boolean allowsTool(String toolId) {
        if (tool.isBlank()) {
            return true;
        }
        return tool.equalsIgnoreCase(toolId);
    }

    /**
     * @return pane used on the field
     */
    public Material pane() {
        return glass;
    }

    /**
     * @return name shown on the pane; a blank {@code display-name} shows the stain key
     */
    public String label() {
        return displayName.isBlank() ? id : displayName;
    }

    /**
     * @return rack tool key, lower case
     */
    public String toolId() {
        return tool.trim().toLowerCase(Locale.ROOT);
    }
}
