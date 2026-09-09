package com.nowko.archeology.config;

import org.bukkit.Material;
import org.bukkit.Sound;

/**
 * One rack tool in the cabinet lab window, from {@code sketch.lab.tools}.
 * Lore and sound apply only to these GUI copies, never to world stacks of the same item.
 *
 * @param id key such as {@code water} or {@code brush}
 * @param item stack shown on the rack
 * @param displayName English name on the item
 * @param description what this tool is for; shown only in the lab window
 * @param sound played on pick-up and on each click that cleans a pane
 */
public record LabTool(String id, Material item, String displayName, String description, Sound sound) {
}
