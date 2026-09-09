package com.nowko.archeology.config;

import org.bukkit.Material;
import org.bukkit.Sound;

import java.util.List;

/**
 * Cabinet lab window: dirt budget, rack tools, and the stain catalogue.
 *
 * @param dirtyCount dirty panes scattered through the field
 * @param tools rack tools in file order
 * @param stains dirt kinds; colour and matching tool live here
 */
public record LabSettings(int dirtyCount, List<LabTool> tools, List<LabStain> stains) {
    /**
     * Field cells above the tool rack in the 27-slot lab window.
     */
    public static final int FIELD_SLOTS = 18;

    /**
     * @return packaged defaults matching {@code config.yml}
     */
    public static LabSettings defaults() {
        return new LabSettings(6, List.of(
                new LabTool(
                        "water",
                        Material.WATER_BUCKET,
                        "Water",
                        "Washes mineral crust from ceramic and stone. Do not soak metal.",
                        Sound.ITEM_BUCKET_EMPTY),
                new LabTool(
                        "brush",
                        Material.BRUSH,
                        "Brush",
                        "Dry-cleans rust and soil. Safe on metal and bone.",
                        Sound.ITEM_BRUSH_BRUSHING_GENERIC),
                new LabTool(
                        "air",
                        Material.FEATHER,
                        "Air",
                        "Dries mud on organic finds. Do not wet these pieces.",
                        Sound.ITEM_BRUSH_BRUSHING_SAND)),
                List.of(
                        new LabStain("limescale", "Limescale", Material.ORANGE_STAINED_GLASS_PANE, "water"),
                        new LabStain("soil", "Soil", Material.BROWN_STAINED_GLASS_PANE, "brush"),
                        new LabStain("rust", "Rust", Material.RED_STAINED_GLASS_PANE, "brush"),
                        new LabStain("mud", "Mud", Material.BROWN_STAINED_GLASS_PANE, "air")));
    }

    /**
     * @param id tool key
     * @return row, or {@code null}
     */
    public LabTool tool(String id) {
        if (id == null || id.isBlank() || tools == null) {
            return null;
        }
        for (LabTool tool : tools) {
            if (id.equalsIgnoreCase(tool.id())) {
                return tool;
            }
        }
        return null;
    }

    /**
     * @param id stain key
     * @return row, or {@code null}
     */
    public LabStain stain(String id) {
        if (id == null || id.isBlank() || stains == null) {
            return null;
        }
        for (LabStain stain : stains) {
            if (id.equalsIgnoreCase(stain.id())) {
                return stain;
            }
        }
        return null;
    }
}
