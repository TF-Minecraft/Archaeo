package com.nowko.archeology.excavation;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Whitelist of main-hand stacks that may run the excavation clock on prism fill.
 * {@link Material#AIR} means an empty hand. Outside the dig site these items stay vanilla.
 */
public class DigTools {
    private Set<Material> allowed = defaultMaterials();

    /**
     * @return every pickaxe and shovel Bukkit currently tags, plus an empty hand
     */
    public static Set<Material> defaultMaterials() {
        Set<Material> materials = new LinkedHashSet<>();
        materials.add(Material.AIR);
        materials.addAll(Tag.ITEMS_PICKAXES.getValues());
        materials.addAll(Tag.ITEMS_SHOVELS.getValues());
        return Collections.unmodifiableSet(materials);
    }

    /**
     * @param tokens YAML names: {@code AIR}/{@code HAND}, {@code PICKAXES}, {@code SHOVELS}, or a {@link Material}
     * @return resolved whitelist; empty tokens yield {@link #defaultMaterials()}
     */
    public static Set<Material> parse(Collection<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return defaultMaterials();
        }
        Set<Material> materials = new LinkedHashSet<>();
        for (String token : tokens) {
            addToken(materials, token);
        }
        return materials.isEmpty() ? defaultMaterials() : Collections.unmodifiableSet(materials);
    }

    /**
     * @param materials allowed types, including {@link Material#AIR} for an empty hand
     */
    public void setAllowed(Set<Material> materials) {
        this.allowed = materials == null || materials.isEmpty()
                ? defaultMaterials()
                : Collections.unmodifiableSet(new LinkedHashSet<>(materials));
    }

    /**
     * @param stack main-hand stack, or {@code null}
     * @return whether this stack may excavate on the dig site
     */
    public boolean isAllowed(ItemStack stack) {
        Material type = stack == null || stack.getType().isAir() ? Material.AIR : stack.getType();
        return allowed.contains(type);
    }

    /**
     * @return a vanilla stack from the whitelist (first pickaxe, else first non-air) for {@code /archaeo pick give}
     */
    public ItemStack sampleStack() {
        for (Material material : allowed) {
            if (material != Material.AIR && Tag.ITEMS_PICKAXES.isTagged(material)) {
                return new ItemStack(material);
            }
        }
        for (Material material : allowed) {
            if (!material.isAir()) {
                return new ItemStack(material);
            }
        }
        return new ItemStack(Material.STONE_PICKAXE);
    }

    /**
     * Clears an older Archaeo mine-lock on a whitelist tool so {@code getBreakSpeed} can be sampled.
     *
     * @param stack main-hand stack, or {@code null}
     */
    public void restoreVanillaSpeedIfSealed(ItemStack stack) {
        if (!isAllowed(stack) || stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || !needsSpeedRepair(meta)) {
            return;
        }
        Collection<AttributeModifier> modifiers = meta.getAttributeModifiers(Attribute.BLOCK_BREAK_SPEED);
        if (modifiers != null) {
            for (AttributeModifier modifier : new ArrayList<>(modifiers)) {
                meta.removeAttributeModifier(Attribute.BLOCK_BREAK_SPEED, modifier);
            }
        }
        ItemMeta vanilla = new ItemStack(stack.getType()).getItemMeta();
        if (vanilla != null) {
            meta.setTool(vanilla.getTool());
        }
        stack.setItemMeta(meta);
    }

    /**
     * @param materials target set
     * @param raw YAML token
     */
    private static void addToken(Set<Material> materials, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        String token = raw.trim().toUpperCase(Locale.ROOT);
        if ("AIR".equals(token) || "HAND".equals(token) || "EMPTY".equals(token) || "EMPTY_HAND".equals(token)) {
            materials.add(Material.AIR);
            return;
        }
        if ("PICKAXES".equals(token) || "PICKAXE".equals(token)) {
            materials.addAll(Tag.ITEMS_PICKAXES.getValues());
            return;
        }
        if ("SHOVELS".equals(token) || "SHOVEL".equals(token)) {
            materials.addAll(Tag.ITEMS_SHOVELS.getValues());
            return;
        }
        Material material = Material.matchMaterial(token);
        if (material != null) {
            materials.add(material.isAir() ? Material.AIR : material);
        }
    }

    /**
     * @param meta item meta
     * @return whether an older zero-speed lock is still on this stack
     */
    private static boolean needsSpeedRepair(ItemMeta meta) {
        Collection<AttributeModifier> modifiers = meta.getAttributeModifiers(Attribute.BLOCK_BREAK_SPEED);
        if (modifiers != null && !modifiers.isEmpty()) {
            return true;
        }
        return meta.getTool().getDefaultMiningSpeed() <= 0f;
    }
}
