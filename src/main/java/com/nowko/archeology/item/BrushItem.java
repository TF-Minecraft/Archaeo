package com.nowko.archeology.item;

import com.nowko.archeology.config.RecoverySettings;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Field brush used to lift exposed finds. Any stack of {@code items.brush} counts, including vanilla.
 */
public class BrushItem {
    private RecoverySettings settings;
    private Material material;

    /**
     * @param settings display name and lore for staff give
     * @param material Bukkit type from {@code items.brush}
     */
    public BrushItem(RecoverySettings settings, Material material) {
        this.settings = settings;
        this.material = material;
    }

    /**
     * @param settings copy after reload
     * @param material type after reload
     */
    public void update(RecoverySettings settings, Material material) {
        this.settings = settings;
        this.material = material;
    }

    /**
     * @return a named brush stack; recovery itself matches by material, not PDC
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
        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * @param stack main-hand stack, or {@code null}
     * @return whether this stack is the configured recovery tool
     */
    public boolean isBrush(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        return stack.getType() == material;
    }
}
