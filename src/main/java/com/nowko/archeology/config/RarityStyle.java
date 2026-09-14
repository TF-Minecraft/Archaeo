package com.nowko.archeology.config;

import org.bukkit.ChatColor;

/**
 * How one {@code artifacts.yml} rarity key is shown on pieces and camp plaques.
 *
 * @param id YAML key such as {@code common}
 * @param label text after {@code Rarity:} (usually uppercase)
 * @param color chat colour for the label
 */
public record RarityStyle(String id, String label, ChatColor color) {
    /**
     * @return grey prefix plus the coloured label
     */
    public String loreLine() {
        String text = label == null || label.isBlank() ? id : label;
        ChatColor tint = color == null ? ChatColor.WHITE : color;
        return ChatColor.GRAY + "Rarity: " + tint + text;
    }
}
