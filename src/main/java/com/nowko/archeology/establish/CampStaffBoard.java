package com.nowko.archeology.establish;

import com.nowko.archeology.model.Site;
import com.nowko.archeology.model.SiteRole;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Roster of who may excavate. The director adds by chat name; every head opens that person's file,
 * where their contribution, their role, and their dismissal live.
 */
public final class CampStaffBoard implements InventoryHolder {
    static final int SLOT_ADD = 18;
    static final int SLOT_BACK = 22;
    private static final int ROSTER_SLOTS = 18;

    private final UUID siteId;
    private final boolean director;
    private final List<UUID> roster = new ArrayList<>();
    private Inventory inventory;

    /**
     * @param siteId excavation
     * @param director whether the viewer may change the roster
     */
    public CampStaffBoard(UUID siteId, boolean director) {
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
     * @return whether this copy includes add/remove
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
     * @param player viewer
     * @param site excavation
     */
    public void open(Player player, Site site) {
        roster.clear();
        roster.addAll(CampNames.roster(site));
        inventory = Bukkit.createInventory(this, 27, ChatColor.DARK_GREEN + "Staff");
        int shown = Math.min(roster.size(), ROSTER_SLOTS);
        for (int i = 0; i < shown; i++) {
            inventory.setItem(i, head(player, site, roster.get(i)));
        }
        if (director) {
            inventory.setItem(SLOT_ADD, named(
                    Material.NAME_TAG,
                    ChatColor.WHITE + "Add worker",
                    ChatColor.GRAY + "Type their name in chat.",
                    ChatColor.DARK_GRAY + "They may then work on the dig site."));
        }
        inventory.setItem(SLOT_BACK, named(
                Material.BARRIER,
                ChatColor.WHITE + "Back",
                ChatColor.GRAY + "Return to the excavation board."));
        player.openInventory(inventory);
    }

    /**
     * @param slot clicked top slot
     * @return roster uuid, or {@code null}
     */
    UUID playerAt(int slot) {
        if (slot < 0 || slot >= roster.size() || slot >= ROSTER_SLOTS) {
            return null;
        }
        return roster.get(slot);
    }

    /**
     * @param viewer opener
     * @param site excavation
     * @param member roster uuid
     * @return skull
     */
    private ItemStack head(Player viewer, Site site, UUID member) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        OfflinePlayer owner = Bukkit.getOfflinePlayer(member);
        meta.setOwningPlayer(owner);
        SiteRole role = site.roleOf(member);
        meta.setDisplayName(ChatColor.WHITE + CampNames.of(viewer, member));
        List<String> lore = new ArrayList<>();
        lore.add((role == SiteRole.DIRECTOR ? ChatColor.GOLD : ChatColor.GRAY) + role.displayName());
        lore.add(ChatColor.DARK_GRAY + role.duty());
        lore.add(ChatColor.DARK_GRAY + (director ? "Click to open their file." : "Click to read their file."));
        meta.setLore(lore);
        stack.setItemMeta(meta);
        return stack;
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
