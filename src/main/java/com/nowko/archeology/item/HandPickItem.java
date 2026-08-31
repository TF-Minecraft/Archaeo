package com.nowko.archeology.item;

import com.nowko.archeology.config.PickSettings;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Builds and recognizes the Hand Pick (PDC). Appearance uses {@code items.pick}.
 * Vanilla tool speed stays on the item so {@code Block.getBreakSpeed} can be sampled;
 * the client is stopped from mining via the player's {@code BLOCK_BREAK_SPEED}.
 */
public class HandPickItem {
    private static final byte MARKER = 1;

    private final NamespacedKey key;
    private PickSettings settings;
    private Material material;

    /**
     * @param plugin owner of the PDC key
     * @param settings display name and lore
     * @param material Bukkit type from {@code items.pick}
     */
    public HandPickItem(JavaPlugin plugin, PickSettings settings, Material material) {
        this.key = new NamespacedKey(plugin, "hand_pick");
        this.settings = settings;
        this.material = material;
    }

    /**
     * @param settings copy after reload
     * @param material type after reload
     */
    public void update(PickSettings settings, Material material) {
        this.settings = settings;
        this.material = material;
    }

    /**
     * @return a new Hand Pick stack
     */
    public ItemStack create() {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.setDisplayName(ChatColor.WHITE + settings.itemName());
        List<String> lore = new ArrayList<>();
        for (String line : settings.itemLore()) {
            lore.add(ChatColor.GRAY + line);
        }
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, MARKER);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * @param stack item in a hand, or {@code null}
     * @return whether this is an Archaeo Hand Pick
     */
    public boolean isPick(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return false;
        }
        Byte mark = stack.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return mark != null && mark == MARKER;
    }

    /**
     * Restores vanilla tool speed on older Hand Picks so the break clock can sample them.
     *
     * @param stack main-hand stack, or {@code null}
     */
    public void sealVanillaMining(ItemStack stack) {
        if (!isPick(stack)) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || !needsSpeedRepair(meta)) {
            return;
        }
        restoreVanillaToolSpeed(meta);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        stack.setItemMeta(meta);
    }

    /**
     * @param meta Hand Pick meta
     * @return whether an older mine-lock is still on this stack
     */
    private boolean needsSpeedRepair(ItemMeta meta) {
        Collection<AttributeModifier> modifiers = meta.getAttributeModifiers(Attribute.BLOCK_BREAK_SPEED);
        if (modifiers != null && !modifiers.isEmpty()) {
            return true;
        }
        return meta.getTool().getDefaultMiningSpeed() <= 0f;
    }

    /**
     * Removes the previous mine-lock (zero tool speed / {@code BLOCK_BREAK_SPEED} on the item).
     *
     * @param meta Hand Pick meta
     */
    private void restoreVanillaToolSpeed(ItemMeta meta) {
        Collection<AttributeModifier> modifiers = meta.getAttributeModifiers(Attribute.BLOCK_BREAK_SPEED);
        if (modifiers != null) {
            for (AttributeModifier modifier : new ArrayList<>(modifiers)) {
                meta.removeAttributeModifier(Attribute.BLOCK_BREAK_SPEED, modifier);
            }
        }
        ItemMeta vanilla = new ItemStack(material).getItemMeta();
        if (vanilla != null) {
            meta.setTool(vanilla.getTool());
        }
    }
}
