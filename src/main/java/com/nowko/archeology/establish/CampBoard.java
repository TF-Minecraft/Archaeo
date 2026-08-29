package com.nowko.archeology.establish;

import com.nowko.archeology.model.InterestLevel;
import com.nowko.archeology.model.Site;
import com.nowko.archeology.model.SiteStatus;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Chest GUI for an excavation sign. Director sees rename, move, and primary / secondary wool.
 */
public final class CampBoard implements InventoryHolder {
    static final int SLOT_INFO = 13;
    static final int SLOT_RENAME = 11;
    static final int SLOT_MOVE = 15;
    static final int SLOT_WOOL_PRIMARY = 20;
    static final int SLOT_WOOL_SECONDARY = 24;

    private final UUID siteId;
    private final boolean director;
    private Inventory inventory;

    /**
     * @param siteId excavation
     * @param director whether the viewer may edit
     */
    public CampBoard(UUID siteId, boolean director) {
        this.siteId = siteId;
        this.director = director;
    }

    /**
     * @return excavation id
     */
    public UUID siteId() {
        return siteId;
    }

    /**
     * @return whether this copy includes director actions
     */
    public boolean director() {
        return director;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * Opens the board for {@code player}.
     *
     * @param player viewer
     * @param site excavation
     */
    public void open(Player player, Site site) {
        String title = ChatColor.DARK_GREEN + "Excavation " + site.displayLabel();
        if (title.length() > 32) {
            title = title.substring(0, 32);
        }
        inventory = Bukkit.createInventory(this, 27, title);
        ItemStack filler = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < 27; slot++) {
            inventory.setItem(slot, filler);
        }
        inventory.setItem(SLOT_INFO, infoItem(player, site));
        if (director) {
            inventory.setItem(SLOT_RENAME, named(
                    Material.NAME_TAG,
                    ChatColor.WHITE + "Rename",
                    ChatColor.GRAY + "Type the new name in chat.",
                    ChatColor.DARK_GRAY + "The camp sign updates."));
            inventory.setItem(SLOT_MOVE, named(
                    Material.ENDER_PEARL,
                    ChatColor.WHITE + "Move camp",
                    ChatColor.GRAY + "Right-click to place the ghost you see.",
                    ChatColor.DARK_GRAY + "Left-click or type cancel to abort."));
            inventory.setItem(SLOT_WOOL_PRIMARY, named(
                    CampWools.woolOf(site.getCampWoolPrimary(), DyeColor.WHITE),
                    ChatColor.WHITE + "Primary color",
                    ChatColor.GRAY + CampWools.label(CampWools.parse(site.getCampWoolPrimary(), DyeColor.WHITE)),
                    ChatColor.DARK_GRAY + "Replaces the white-wool cells."));
            inventory.setItem(SLOT_WOOL_SECONDARY, named(
                    CampWools.woolOf(site.getCampWoolSecondary(), DyeColor.RED),
                    ChatColor.WHITE + "Secondary color",
                    ChatColor.GRAY + CampWools.label(CampWools.parse(site.getCampWoolSecondary(), DyeColor.RED)),
                    ChatColor.DARK_GRAY + "Replaces the red-wool cells."));
        }
        player.openInventory(inventory);
    }

    /**
     * @param player viewer (for director name lookup)
     * @param site excavation
     * @return summary item
     */
    private ItemStack infoItem(Player player, Site site) {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Name: " + ChatColor.WHITE + site.displayLabel());
        lore.add(ChatColor.GRAY + "Director: " + ChatColor.WHITE + directorName(player, site));
        lore.add(ChatColor.GRAY + "Status: " + ChatColor.WHITE + statusLabel(site.getStatus()));
        InterestLevel interest = site.getInterest();
        if (interest != null) {
            lore.add(ChatColor.GRAY + "Interest: " + ChatColor.WHITE + interest.yamlKey());
        }
        if (director) {
            lore.add("");
            lore.add(ChatColor.DARK_GRAY + "You are the director.");
        }
        return named(Material.WRITABLE_BOOK, ChatColor.GOLD + "Record", lore.toArray(String[]::new));
    }

    /**
     * @param player viewer
     * @param site excavation
     * @return last known director name
     */
    private static String directorName(Player player, Site site) {
        if (site.getDirector() == null) {
            return "—";
        }
        if (site.getDirector().equals(player.getUniqueId())) {
            return player.getName();
        }
        String name = player.getServer().getOfflinePlayer(site.getDirector()).getName();
        return name != null ? name : site.getDirector().toString().substring(0, 8);
    }

    /**
     * @param status lifecycle
     * @return English label
     */
    static String statusLabel(SiteStatus status) {
        return switch (status) {
            case HIDDEN -> "Hidden";
            case ESTABLISHED -> "Active";
            case EXHAUSTED -> "Finished";
        };
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
