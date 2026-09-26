package net.tfminecraft.archaeo.establish;

import net.tfminecraft.archaeo.config.EstablishSettings;
import net.tfminecraft.archaeo.model.Site;
import net.tfminecraft.archaeo.model.SiteRole;
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
 *
 * <p>The first two chest rows are the roster. That many heads is the hard cap per excavation; the
 * YAML may only lower it. There is no second page.
 */
public final class CampStaffBoard implements InventoryHolder {
    static final int SLOT_ADD = 18;
    static final int SLOT_BACK = CampGui.SLOT_BACK;
    private static final int ROSTER_SLOTS = EstablishSettings.STAFF_BOARD_SLOTS;

    private final UUID siteId;
    private final boolean director;
    private final int maxStaff;
    private final List<UUID> roster = new ArrayList<>();
    private Inventory inventory;

    /**
     * @param siteId excavation
     * @param director whether the viewer may change the roster
     * @param maxStaff configured cap, already clamped to the board
     */
    public CampStaffBoard(UUID siteId, boolean director, int maxStaff) {
        this.siteId = siteId;
        this.director = director;
        this.maxStaff = maxStaff;
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
     * @param player viewer
     * @param site excavation
     */
    public void open(Player player, Site site) {
        roster.clear();
        roster.addAll(CampNames.roster(site));
        inventory = Bukkit.createInventory(this, 27, "Staff");
        int shown = Math.min(roster.size(), ROSTER_SLOTS);
        for (int i = 0; i < shown; i++) {
            inventory.setItem(i, head(player, site, roster.get(i)));
        }
        if (director) {
            inventory.setItem(SLOT_ADD, addItem());
        }
        inventory.setItem(SLOT_BACK, named(
                Material.BARRIER,
                ChatColor.WHITE + "Back",
                ChatColor.GRAY + "Return to the excavation board."));
        player.openInventory(inventory);
    }

    /**
     * @return whether this excavation already holds as many people as the cap allows
     */
    boolean staffFull() {
        return roster.size() >= maxStaff;
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
     * @return add-worker button, or a full-crew notice when the cap is reached
     */
    private ItemStack addItem() {
        if (staffFull()) {
            return named(
                    Material.ARMOR_STAND,
                    ChatColor.WHITE + "Staff is full",
                    ChatColor.GRAY + "This excavation holds " + maxStaff
                            + (maxStaff == 1 ? " person." : " people."),
                    ChatColor.DARK_GRAY + "Remove someone before adding.");
        }
        return named(
                Material.ARMOR_STAND,
                ChatColor.WHITE + "Add worker",
                ChatColor.GRAY + "Type their name in chat.",
                ChatColor.DARK_GRAY + "They may then work on the dig site.");
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
        meta.setDisplayName(name);
        meta.setLore(List.of(lore));
        stack.setItemMeta(meta);
        return stack;
    }
}
