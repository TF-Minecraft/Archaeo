package com.nowko.archeology.sketch;

import com.nowko.archeology.config.ArtifactTemplate;
import com.nowko.archeology.config.CatalogRegistry;
import com.nowko.archeology.item.RecoveredFindItem;
import com.nowko.archeology.model.BuriedFind;
import com.nowko.archeology.model.Site;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.UUID;

/**
 * Furnace-shaped cabinet: top = drawing, fuel = the piece they clicked with, result = Register.
 * The real recovered find stays in the player's hand.
 */
public final class SketchCabinet implements InventoryHolder {
    /** Where the signed drawing goes (furnace ingredient). */
    public static final int SLOT_SKETCH = 0;
    /** Stand-in for the piece (furnace fuel). */
    public static final int SLOT_FIND = 1;
    /** Files the drawing (furnace result). */
    public static final int SLOT_REGISTER = 2;

    private UUID siteId;
    private UUID findId;
    private Inventory inventory;

    /**
     * {@inheritDoc}
     */
    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * @return excavation this window was opened for
     */
    public UUID siteId() {
        return siteId;
    }

    /**
     * @return archive row this window was opened for
     */
    public UUID findId() {
        return findId;
    }

    /**
     * @param player cataloguer
     * @param site excavation
     * @param find archive row
     * @param template catalog row, or {@code null}
     * @param recovered recovered-find tags and lore
     * @param catalogs materials, grades, and readings
     */
    public void open(
            Player player,
            Site site,
            BuriedFind find,
            ArtifactTemplate template,
            RecoveredFindItem recovered,
            CatalogRegistry catalogs
    ) {
        this.siteId = site.getId();
        this.findId = find.getId();
        inventory = Bukkit.createInventory(this, InventoryType.FURNACE, "Register");
        inventory.setItem(SLOT_FIND, recovered.standIn(template, site, find, catalogs));
        inventory.setItem(SLOT_REGISTER, registerControl());
        player.openInventory(inventory);
    }

    /**
     * Hands the drawing back so closing cannot eat it. The fuel slot is a stand-in, not the real piece.
     *
     * @param player cataloguer
     */
    public void returnContents(Player player) {
        if (inventory == null || player == null) {
            return;
        }
        give(player, inventory.getItem(SLOT_SKETCH));
        inventory.setItem(SLOT_SKETCH, null);
        inventory.setItem(SLOT_FIND, null);
        inventory.setItem(SLOT_REGISTER, null);
    }

    /**
     * @param slot furnace index
     * @return whether that cell is the piece stand-in or Register
     */
    public static boolean locked(int slot) {
        return slot == SLOT_FIND || slot == SLOT_REGISTER;
    }

    /**
     * @return Register control
     */
    private static ItemStack registerControl() {
        ItemStack stack = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.WHITE + "Register");
            meta.setLore(List.of(
                    ChatColor.GRAY + "Place the drawing in the top slot.",
                    ChatColor.GRAY + "Then click here."));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /**
     * @param player receiver
     * @param stack drawing, or empty
     */
    private static void give(Player player, ItemStack stack) {
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            return;
        }
        player.getInventory().addItem(stack).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }
}
