package net.tfminecraft.archaeo.establish;

import net.tfminecraft.archaeo.model.Site;
import net.tfminecraft.archaeo.model.SiteRole;
import net.tfminecraft.archaeo.model.WorkerRecord;
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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One person's page in the excavation dossier: what they have contributed, what it cost the finds,
 * and — for the director — their role and the door out.
 *
 * <p>The roster used to remove a worker on a single click, which put the most destructive action in
 * the plugin one misclick away. Reading the person first and dismissing them from inside their own
 * page makes the removal deliberate, and gives the numbers somewhere to live.
 */
public final class CampWorkerBoard implements InventoryHolder {
    static final int SLOT_HEAD = 4;
    static final int SLOT_BACK = CampGui.SLOT_BACK;
    static final int SLOT_WORK = 10;
    static final int SLOT_FINDS = 12;
    static final int SLOT_ROLE = 14;
    static final int SLOT_REMOVE = 16;
    /** Role buttons sit spaced out across the bottom row, centred on however many roles exist. */
    private static final int ROLE_ROW = 18;
    private static final int ROLE_ROW_WIDTH = 9;
    private static final int ROLE_STRIDE = 2;

    private final UUID siteId;
    private final UUID member;
    private final boolean director;
    private Inventory inventory;

    /**
     * @param siteId excavation
     * @param member staff member being read
     * @param director whether the viewer may change the role or dismiss this person
     */
    public CampWorkerBoard(UUID siteId, UUID member, boolean director) {
        this.siteId = siteId;
        this.member = member;
        this.director = director;
    }

    /**
     * @return excavation id
     */
    public UUID siteId() {
        return siteId;
    }

    /**
     * @return staff member on this page
     */
    public UUID member() {
        return member;
    }

    /**
     * @return whether this copy includes role buttons and the dismissal
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
        String name = CampNames.of(player, member);
        String title = name;
        if (title.length() > 32) {
            title = title.substring(0, 32);
        }
        inventory = Bukkit.createInventory(this, 27, title);
        ItemStack filler = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < 27; slot++) {
            inventory.setItem(slot, filler);
        }
        WorkerRecord record = site.workerRecord(member);
        SiteRole role = site.roleOf(member);
        boolean lead = site.isDirector(member);
        inventory.setItem(SLOT_HEAD, headItem(name, role, record));
        inventory.setItem(SLOT_WORK, workItem(record));
        inventory.setItem(SLOT_FINDS, findsItem(record));
        inventory.setItem(SLOT_ROLE, roleCard(role, lead));
        inventory.setItem(SLOT_BACK, named(
                Material.BARRIER,
                ChatColor.WHITE + "Back",
                ChatColor.GRAY + "Return to the staff list."));
        if (director && !lead) {
            List<SiteRole> options = SiteRole.assignable();
            for (int i = 0; i < options.size(); i++) {
                inventory.setItem(roleSlot(i, options.size()), roleItem(options.get(i), role));
            }
            inventory.setItem(SLOT_REMOVE, named(
                    Material.IRON_DOOR,
                    ChatColor.RED + "Remove from the excavation",
                    ChatColor.GRAY + "They lose access to the cut.",
                    ChatColor.DARK_GRAY + "Their record stays in the dossier."));
        }
        player.openInventory(inventory);
    }

    /**
     * Keeps the row balanced whatever the role list looks like, so adding or dropping a role never
     * leaves the buttons hanging off one side of the board.
     *
     * @param index position in {@link SiteRole#assignable()}
     * @param count how many roles are on offer
     * @return slot for that button
     */
    private static int roleSlot(int index, int count) {
        int span = count * ROLE_STRIDE - 1;
        return ROLE_ROW + (ROLE_ROW_WIDTH - span) / 2 + index * ROLE_STRIDE;
    }

    /**
     * @param slot clicked top slot
     * @return role that button hands out, or {@code null} when the slot is not a role button
     */
    static SiteRole roleAt(int slot) {
        List<SiteRole> options = SiteRole.assignable();
        for (int index = 0; index < options.size(); index++) {
            if (roleSlot(index, options.size()) == slot) {
                return options.get(index);
            }
        }
        return null;
    }

    /**
     * @param name display name
     * @param role current role
     * @param record tally, or {@code null} when they have never been filed
     * @return skull with the identity lines
     */
    private ItemStack headItem(String name, SiteRole role, WorkerRecord record) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        OfflinePlayer owner = Bukkit.getOfflinePlayer(member);
        meta.setOwningPlayer(owner);
        meta.setDisplayName(ChatColor.WHITE + name);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Role: " + roleColor(role) + role.displayName());
        lore.add(ChatColor.GRAY + "On the staff since: " + ChatColor.WHITE
                + (record == null ? "—" : ago(record.getJoinedAt())));
        lore.add(ChatColor.GRAY + "Last worked: " + ChatColor.WHITE
                + (record == null ? "never" : ago(record.getLastActiveAt())));
        meta.setLore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * @param record tally, or {@code null}
     * @return how much ground this person has moved
     */
    private static ItemStack workItem(WorkerRecord record) {
        int blocks = record == null ? 0 : record.getBlocksRemoved();
        int brushed = record == null ? 0 : record.getCellsBrushed();
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Spoil removed: " + ChatColor.WHITE + blocks + ChatColor.GRAY + " blocks");
        lore.add(ChatColor.GRAY + "Cubes brushed: " + ChatColor.WHITE + brushed);
        if (blocks == 0 && brushed == 0) {
            lore.add("");
            lore.add(ChatColor.DARK_GRAY + "Has not worked the cut yet.");
        }
        return named(Material.COARSE_DIRT, ChatColor.GOLD + "Field work", lore.toArray(String[]::new));
    }

    /**
     * Recovered and ruined sit on the same item on purpose: a dig is judged by both, and reading
     * one without the other flatters a careless worker.
     *
     * @param record tally, or {@code null}
     * @return what this person's hands did to the pieces
     */
    private static ItemStack findsItem(WorkerRecord record) {
        int recovered = record == null ? 0 : record.getFindsRecovered();
        int damaged = record == null ? 0 : record.getFindsDamaged();
        int lost = record == null ? 0 : record.getFindsLost();
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Recovered: " + ChatColor.WHITE + recovered);
        lore.add(ChatColor.GRAY + "Chipped while digging: " + ChatColor.WHITE + damaged);
        lore.add(ChatColor.GRAY + "Destroyed: " + ChatColor.WHITE + lost);
        lore.add("");
        lore.add(ChatColor.DARK_GRAY + careLine(recovered, damaged, lost));
        return named(Material.DECORATED_POT, ChatColor.GOLD + "Finds", lore.toArray(String[]::new));
    }

    /**
     * @param role role shown
     * @param lead whether this person is the director
     * @return what the role lets them do
     */
    private ItemStack roleCard(SiteRole role, boolean lead) {
        List<String> lore = new ArrayList<>();
        lore.add(roleColor(role) + role.displayName());
        lore.add(ChatColor.GRAY + role.duty());
        lore.add("");
        if (lead) {
            lore.add(ChatColor.DARK_GRAY + "The director cannot be reassigned or removed.");
        } else if (director) {
            lore.add(ChatColor.DARK_GRAY + "Pick a role below.");
        } else {
            lore.add(ChatColor.DARK_GRAY + "Only the director may change this.");
        }
        return named(Material.PAPER, ChatColor.WHITE + "Role", lore.toArray(String[]::new));
    }

    /**
     * @param option role this button hands out
     * @param current role right now
     * @return role button
     */
    private static ItemStack roleItem(SiteRole option, SiteRole current) {
        boolean active = option == current;
        String name = (active ? ChatColor.GOLD : ChatColor.WHITE) + option.displayName();
        return named(
                roleIcon(option),
                name,
                true,
                ChatColor.GRAY + option.duty(),
                active ? ChatColor.DARK_GRAY + "Current role." : ChatColor.DARK_GRAY + "Click to assign.");
    }

    /**
     * @param role current role
     * @return icon that reads as the tool of that role
     */
    private static Material roleIcon(SiteRole role) {
        return switch (role) {
            case DIRECTOR -> Material.WRITABLE_BOOK;
            case ARCHAEOLOGIST -> Material.BRUSH;
            case EXCAVATOR -> Material.IRON_PICKAXE;
        };
    }

    /**
     * @param role current role
     * @return colour used for that role wherever it is named
     */
    private static ChatColor roleColor(SiteRole role) {
        return switch (role) {
            case DIRECTOR -> ChatColor.GOLD;
            case ARCHAEOLOGIST -> ChatColor.AQUA;
            case EXCAVATOR -> ChatColor.WHITE;
        };
    }

    /**
     * A verdict the director can read at a glance, so the numbers do not have to be compared by eye.
     *
     * @param recovered pieces lifted
     * @param damaged pieces chipped
     * @param lost pieces destroyed
     * @return one-line judgement of this person's hands
     */
    private static String careLine(int recovered, int damaged, int lost) {
        if (recovered == 0 && damaged == 0 && lost == 0) {
            return "No piece has passed through their hands.";
        }
        if (lost > 0) {
            return "Has taken pieces past saving.";
        }
        if (damaged > recovered) {
            return "Breaks more than they bring out.";
        }
        if (damaged == 0) {
            return "Has not hurt a single piece.";
        }
        return "Steady hands, with the odd chip.";
    }

    /**
     * Board lore has no room for a timestamp, and a date would not tell the director what they want
     * to know: whether this person is still on the dig.
     *
     * @param moment recorded instant, or {@code null}
     * @return coarse "how long ago", or a dash when nothing was recorded
     */
    private static String ago(Instant moment) {
        if (moment == null) {
            return "—";
        }
        Duration elapsed = Duration.between(moment, Instant.now());
        long days = elapsed.toDays();
        if (days >= 2) {
            return days + " days ago";
        }
        long hours = elapsed.toHours();
        if (hours >= 2) {
            return hours + " hours ago";
        }
        long minutes = elapsed.toMinutes();
        if (minutes >= 2) {
            return minutes + " minutes ago";
        }
        return "just now";
    }

    /**
     * @param material icon
     * @param name display name
     * @param lore extra lines
     * @return stack
     */
    private static ItemStack named(Material material, String name, String... lore) {
        return named(material, name, false, lore);
    }

    /**
     * @param material icon
     * @param name display name
     * @param glint whether this is an action, not a read-only card
     * @param lore extra lines
     * @return stack
     */
    private static ItemStack named(Material material, String name, boolean glint, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                meta.setLore(List.of(lore));
            }
            if (glint) {
                meta.setEnchantmentGlintOverride(true);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
