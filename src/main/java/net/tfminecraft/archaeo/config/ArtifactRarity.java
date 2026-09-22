package net.tfminecraft.archaeo.config;

import org.bukkit.ChatColor;

import java.util.Locale;

/**
 * Built-in spawn-flavour tiers used when {@code config.yml} {@code rarity:} omits a key.
 * Colours and labels can be overridden there; {@link #loreLine()} shows the tier in uppercase.
 */
public enum ArtifactRarity {
    /** Everyday scatter; white. */
    COMMON(ChatColor.WHITE),
    /** Uncommon enough to notice; blue. */
    RARE(ChatColor.BLUE),
    /** Distinctive assemblage; dark purple. */
    EPIC(ChatColor.DARK_PURPLE),
    /** Site-defining piece; gold. */
    LEGENDARY(ChatColor.GOLD);

    private final ChatColor color;

    /**
     * @param color chat colour for the tier word in lore
     */
    ArtifactRarity(ChatColor color) {
        this.color = color;
    }

    /**
     * Parses a YAML rarity key. Blank or unknown tokens become {@link #COMMON}.
     * Legacy {@code uncommon} maps to {@link #RARE} so older packs keep a blue tier.
     *
     * @param raw value from {@code artifacts.yml}, may be blank
     * @return tier used for display colour
     */
    public static ArtifactRarity fromConfig(String raw) {
        if (raw == null || raw.isBlank()) {
            return COMMON;
        }
        String key = raw.trim().toLowerCase(Locale.ROOT);
        if ("uncommon".equals(key)) {
            return RARE;
        }
        for (ArtifactRarity rarity : values()) {
            if (rarity.id().equals(key)) {
                return rarity;
            }
        }
        return COMMON;
    }

    /**
     * @return YAML key ({@code common}, {@code rare}, {@code epic}, {@code legendary})
     */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * @return colour for the tier word
     */
    public ChatColor color() {
        return color;
    }

    /**
     * Packaged style: uppercase enum name and built-in colour.
     *
     * @return style used when config does not override this id
     */
    public RarityStyle style() {
        return new RarityStyle(id(), name(), color);
    }

    /**
     * One lore line: grey label plus the coloured tier in uppercase.
     *
     * @return line such as {@code Rarity: RARE} with colours applied
     */
    public String loreLine() {
        return style().loreLine();
    }
}
