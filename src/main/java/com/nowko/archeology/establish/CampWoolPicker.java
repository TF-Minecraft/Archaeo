package com.nowko.archeology.establish;

import com.nowko.archeology.model.Site;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.UUID;

/**
 * Director palette for camp wool color. Slots 0–15 are the vanilla colors.
 */
public final class CampWoolPicker implements InventoryHolder {
    static final int SLOT_BACK = 22;

    private final UUID siteId;
    private Inventory inventory;

    /**
     * @param siteId excavation
     */
    public CampWoolPicker(UUID siteId) {
        this.siteId = siteId;
    }

    /**
     * @return excavation id
     */
    public UUID siteId() {
        return siteId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * @param player director
     * @param site excavation
     */
    public void open(Player player, Site site) {
        inventory = Bukkit.createInventory(this, 27, ChatColor.DARK_GREEN + "Camp wool");
        DyeColor current = CampWools.parse(site.getCampWool());
        DyeColor[] palette = CampWools.palette();
        for (int i = 0; i < palette.length; i++) {
            DyeColor color = palette[i];
            String name = ChatColor.WHITE + CampWools.label(color);
            if (color == current) {
                name = ChatColor.GOLD + CampWools.label(color);
            }
            inventory.setItem(i, named(
                    CampWools.woolOf(color.name()),
                    name,
                    color == current
                            ? ChatColor.DARK_GRAY + "Current accent."
                            : ChatColor.GRAY + "Click to apply."));
        }
        inventory.setItem(SLOT_BACK, named(
                Material.BARRIER,
                ChatColor.WHITE + "Back",
                ChatColor.GRAY + "Return to the excavation board."));
        player.openInventory(inventory);
    }

    /**
     * @param slot clicked top slot
     * @return color for that palette cell, or {@code null} if it is not a wool choice
     */
    static DyeColor colorAt(int slot) {
        DyeColor[] palette = CampWools.palette();
        if (slot < 0 || slot >= palette.length) {
            return null;
        }
        return palette[slot];
    }

    /**
     * @param material icon
     * @param name display name
     * @param lore extra lines
     * @return stack
     */
    private static ItemStack named(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                meta.setLore(List.of(lore));
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
