package com.nowko.archeology.establish;

import com.nowko.archeology.config.CatalogRegistry;
import com.nowko.archeology.config.HintTemplate;
import com.nowko.archeology.config.InterestSettings;
import com.nowko.archeology.config.StratumDefinition;
import com.nowko.archeology.model.BuriedFind;
import com.nowko.archeology.model.FindState;
import com.nowko.archeology.model.Site;
import com.nowko.archeology.model.SiteRole;
import com.nowko.archeology.model.SiteStatus;
import com.nowko.archeology.model.StratumBand;
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
 * Chest GUI for an excavation sign: dossier, staff access, and director camp tools.
 */
public final class CampBoard implements InventoryHolder {
    static final int SLOT_INFO = 4;
    static final int SLOT_PERSONAL = 11;
    static final int SLOT_INFORMATION = 13;
    static final int SLOT_LIMITS = 15;
    static final int SLOT_RENAME = 19;
    static final int SLOT_WOOL_PRIMARY = 21;
    static final int SLOT_WOOL_SECONDARY = 23;
    static final int SLOT_MOVE = 25;
    /** Characters per lore line before a field note is broken. */
    private static final int LINE_WIDTH = 34;

    private final UUID siteId;
    private final boolean director;
    private final CatalogRegistry catalogs;
    private Inventory inventory;

    /**
     * @param siteId excavation
     * @param director whether the viewer may edit camp and staff
     * @param catalogs strata labels and work-day size for the record
     */
    public CampBoard(UUID siteId, boolean director, CatalogRegistry catalogs) {
        this.siteId = siteId;
        this.director = director;
        this.catalogs = catalogs;
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
        inventory.setItem(SLOT_PERSONAL, staffItem(site));
        inventory.setItem(SLOT_INFORMATION, dossierItem(site));
        inventory.setItem(SLOT_LIMITS, limitsItem());
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
     * @return dossier item
     */
    private ItemStack infoItem(Player player, Site site) {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Name: " + ChatColor.WHITE + site.displayLabel());
        lore.add(ChatColor.GRAY + "Director: " + ChatColor.WHITE + CampNames.of(player, site.getDirector()));
        lore.add(ChatColor.GRAY + "Status: " + ChatColor.WHITE + statusLabel(site.getStatus()));
        lore.add(ChatColor.GRAY + "Work day: " + ChatColor.WHITE
                + site.getJornadaPickLeft() + " / " + catalogs.pick().jornadaActions());
        lore.add(ChatColor.GRAY + "Progress: " + ChatColor.WHITE + progressBar(site));
        lore.add("");
        addStratumLines(lore, site);
        if (site.getStatus() == SiteStatus.EXHAUSTED) {
            lore.add("");
            lore.add(ChatColor.DARK_GRAY + "The cut is closed; field work is over.");
        }
        if (director) {
            lore.add("");
            lore.add(ChatColor.DARK_GRAY + "You are the director.");
        } else if (site.getExcavators().contains(player.getUniqueId())) {
            SiteRole role = site.roleOf(player.getUniqueId());
            lore.add("");
            lore.add(ChatColor.DARK_GRAY + "You are " + role.displayName() + " here.");
            lore.add(ChatColor.DARK_GRAY + role.duty());
        }
        return named(Material.WRITABLE_BOOK, ChatColor.GOLD + "Record", lore.toArray(String[]::new));
    }

    /**
     * @param site excavation
     * @return staff button
     */
    private ItemStack staffItem(Site site) {
        int count = CampNames.roster(site).size();
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Who may work on the dig site.");
        lore.add(ChatColor.WHITE + String.valueOf(count) + ChatColor.GRAY + (count == 1 ? " person." : " people."));
        if (director) {
            lore.add(ChatColor.DARK_GRAY + "Open to read files, hire, and dismiss.");
        } else {
            lore.add(ChatColor.DARK_GRAY + "Open to read the roster and its files.");
        }
        return named(Material.PLAYER_HEAD, ChatColor.WHITE + "Staff", lore.toArray(String[]::new));
    }

    /**
     * The prospecting dossier: what the survey suggested and the field notes it produced.
     * They live here rather than in the record so the record stays a progress sheet.
     *
     * @param site excavation
     * @return information item
     */
    private ItemStack dossierItem(Site site) {
        List<String> lore = new ArrayList<>();
        if (site.getInterest() != null) {
            InterestSettings interest = catalogs.interest(site.getInterest());
            String label = interest == null ? site.getInterest().yamlKey() : interest.displayName();
            lore.add(ChatColor.GRAY + "Approximate interest: " + ChatColor.WHITE + label);
            lore.add("");
        }
        if (site.getHintIds().isEmpty()) {
            lore.add(ChatColor.DARK_GRAY + "No field notes were filed for this dossier.");
            return named(Material.BOOK, ChatColor.AQUA + "Information", lore.toArray(String[]::new));
        }
        lore.add(ChatColor.GRAY + "Field notes:");
        for (String hintId : site.getHintIds()) {
            HintTemplate hint = catalogs.hint(hintId);
            String text = hint == null ? hintId : hint.text();
            wrap(lore, text);
        }
        return named(Material.BOOK, ChatColor.AQUA + "Information", lore.toArray(String[]::new));
    }

    /**
     * @return button that traces the prism for the viewer alone
     */
    private ItemStack limitsItem() {
        return named(
                Material.SPYGLASS,
                ChatColor.WHITE + "Show limits",
                ChatColor.GRAY + "Traces the dig chunk and each stratum,",
                ChatColor.GRAY + "through spoil heaps and walls.",
                ChatColor.DARK_GRAY + "Only you see it, for "
                        + catalogs.pick().limits().seconds() + " seconds.");
    }

    /**
     * Field notes are sentences, and lore lines are not: this breaks one note on word
     * boundaries and marks the continuation lines so the note still reads as one note.
     *
     * @param lore lore being built
     * @param text one field note
     */
    private static void wrap(List<String> lore, String text) {
        StringBuilder line = new StringBuilder();
        String prefix = "- ";
        for (String word : text.split("\\s+")) {
            if (line.length() > 0 && line.length() + word.length() + 1 > LINE_WIDTH) {
                lore.add(ChatColor.WHITE + prefix + line);
                line.setLength(0);
                prefix = "  ";
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0) {
            lore.add(ChatColor.WHITE + prefix + line);
        }
    }

    /**
     * One lore line per present band: recovered, generated total, and destroyed finds.
     *
     * @param lore record lore
     * @param site excavation
     */
    private void addStratumLines(List<String> lore, Site site) {
        boolean any = false;
        for (StratumBand band : site.getStrata().values()) {
            if (!band.isPresent()) {
                continue;
            }
            any = true;
            lore.add(ChatColor.GRAY + stratumLabel(band) + ": " + ChatColor.WHITE + findCounts(site, band.getId()));
        }
        if (!any) {
            lore.add(ChatColor.DARK_GRAY + "No strata recorded.");
        }
    }

    /**
     * @param band present stratum
     * @return catalog display name, falling back to the roman id
     */
    private String stratumLabel(StratumBand band) {
        StratumDefinition definition = catalogs.stratum(band.getId());
        if (definition == null || definition.displayName().isBlank()) {
            return band.getId();
        }
        return definition.displayName();
    }

    /**
     * @param site excavation
     * @param stratumId band id
     * @return recovered, total, and lost counts for that layer
     */
    private static String findCounts(Site site, String stratumId) {
        int recovered = 0;
        int lost = 0;
        int total = 0;
        for (BuriedFind find : site.getFinds()) {
            if (find.getStratumId() == null || !find.getStratumId().equals(stratumId)) {
                continue;
            }
            total++;
            if (find.getState() == FindState.RECOVERED) {
                recovered++;
            } else if (find.getState() == FindState.LOST) {
                lost++;
            }
        }
        return recovered + " recovered · " + total + " total · " + lost + " lost";
    }

    /**
     * @param site excavation
     * @return recovered share of generated finds
     */
    private static String progressBar(Site site) {
        int total = site.getFinds().size();
        if (total <= 0) {
            return "—";
        }
        int recovered = 0;
        for (BuriedFind find : site.getFinds()) {
            if (find.getState() == FindState.RECOVERED) {
                recovered++;
            }
        }
        int filled = Math.round(10f * recovered / total);
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            bar.append(i < filled ? "█" : "░");
        }
        return bar + " " + recovered + "/" + total;
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
