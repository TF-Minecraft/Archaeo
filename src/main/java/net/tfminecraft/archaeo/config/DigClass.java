package net.tfminecraft.archaeo.config;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

/**
 * Whether an excavation tool prefers stone-like or soil-like fill for the cue clock.
 * Inferred from the held stack's vanilla material so one YAML profile can list both a pick
 * and a shovel (knife vs spoon) without splitting the profile.
 */
public enum DigClass {
    /** Faster on {@code excavation.cues.pick-faster} blocks. */
    PICK,
    /** Faster on {@code excavation.cues.shovel-faster} blocks. */
    SHOVEL,
    /** No affinity; always uses the profile's base {@code cue-ticks}. */
    NONE;

    /**
     * @param stack main-hand stack, or {@code null}
     * @return pick / shovel from vanilla tags, or {@link #NONE}
     */
    public static DigClass of(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return NONE;
        }
        return of(stack.getType());
    }

    /**
     * @param material held item type
     * @return pick / shovel from vanilla tags, or {@link #NONE}
     */
    public static DigClass of(Material material) {
        if (material == null || material.isAir()) {
            return NONE;
        }
        if (Tag.ITEMS_PICKAXES.isTagged(material)) {
            return PICK;
        }
        if (Tag.ITEMS_SHOVELS.isTagged(material)) {
            return SHOVEL;
        }
        return NONE;
    }

    /**
     * @param raw YAML {@code dig-class} token
     * @return parsed class, or {@code null} when blank / unknown
     */
    public static DigClass parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "pick", "pickaxe", "pico" -> PICK;
            case "shovel", "spade", "pala" -> SHOVEL;
            case "none", "any", "hand" -> NONE;
            default -> null;
        };
    }
}
