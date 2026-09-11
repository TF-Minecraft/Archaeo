package com.nowko.archeology.config;

import com.nowko.archeology.item.ItemRef;
import org.bukkit.Material;

/**
 * Field-sketch rules from {@code sketch:} in {@code config.yml}.
 *
 * @param pencilUses sketches one pencil survives; {@code 0} means it never wears and has no bar
 * @param cabinet vanilla block or ItemsAdder furniture that opens the register window
 * @param lab first cleaning step at the cabinet
 */
public record SketchSettings(int pencilUses, ItemRef cabinet, LabSettings lab) {
    /**
     * @return packaged defaults matching {@code config.yml}
     */
    public static SketchSettings defaults() {
        return new SketchSettings(64, ItemRef.vanilla(Material.CARTOGRAPHY_TABLE), LabSettings.defaults());
    }
}
