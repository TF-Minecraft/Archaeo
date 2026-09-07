package com.nowko.archeology.establish;

import com.nowko.archeology.config.ArtifactTemplate;
import com.nowko.archeology.config.CatalogRegistry;
import com.nowko.archeology.item.RecoveredFindItem;
import com.nowko.archeology.model.BuriedFind;
import com.nowko.archeology.model.FindInterpretation;
import com.nowko.archeology.model.FindState;
import com.nowko.archeology.model.Site;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
 * One find's page in the excavation archive: provenience, condition, and signed readings.
 */
public final class CampFindBoard implements InventoryHolder {
    static final int SLOT_IDENTITY = 4;
    static final int SLOT_BACK = 8;
    static final int SLOT_PROVENIENCE = 10;
    static final int SLOT_CONDITION = 12;
    static final int SLOT_STUDY = 14;
    static final int SLOT_READINGS = 16;
    static final int SLOT_IDENTIFY_ACTION = 22;
    private static final int LINE_WIDTH = 34;

    private final UUID siteId;
    private final UUID findId;
    private final CatalogRegistry catalogs;
    private Inventory inventory;

    /**
     * @param siteId excavation
     * @param findId archive row
     * @param catalogs artifact notes and interpretation labels
     */
    public CampFindBoard(UUID siteId, UUID findId, CatalogRegistry catalogs) {
        this.siteId = siteId;
        this.findId = findId;
        this.catalogs = catalogs;
    }

    /**
     * @return excavation id
     */
    public UUID siteId() {
        return siteId;
    }

    /**
     * @return archive row id
     */
    public UUID findId() {
        return findId;
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
        BuriedFind find = site.findById(findId).orElse(null);
        if (find == null) {
            player.closeInventory();
            return;
        }
        ArtifactTemplate template = catalogs.artifact(find.getArtifactId());
        String number = find.publicNumber(site.getSerial());
        String title = ChatColor.DARK_GREEN + (number == null ? "Find" : number);
        if (title.length() > 32) {
            title = title.substring(0, 32);
        }
        inventory = Bukkit.createInventory(this, 27, title);
        ItemStack filler = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < 27; slot++) {
            inventory.setItem(slot, filler);
        }
        boolean cataloguer = site.mayCatalog(player.getUniqueId());
        inventory.setItem(SLOT_IDENTITY, identityItem(template, find, number));
        inventory.setItem(SLOT_PROVENIENCE, provenienceItem(player, site, find));
        inventory.setItem(SLOT_CONDITION, conditionItem(find));
        inventory.setItem(SLOT_STUDY, studyItem(template, find));
        inventory.setItem(SLOT_READINGS, readingsItem(find));
        inventory.setItem(SLOT_BACK, named(
                Material.BARRIER,
                ChatColor.WHITE + "Back",
                ChatColor.GRAY + "Return to the finds register."));
        if (cataloguer && find.getState() == FindState.RECOVERED && catalogs.nextOpenType(find) != null) {
            inventory.setItem(SLOT_IDENTIFY_ACTION, named(
                    Material.ENCHANTING_TABLE,
                    ChatColor.WHITE + "Identify",
                    ChatColor.GRAY + "Opens the station. Bring the piece.",
                    ChatColor.DARK_GRAY + "Three readings; pick one per question."));
        }
        player.openInventory(inventory);
    }

    /**
     * @param template catalog row, or {@code null}
     * @param find archive row
     * @param number public inventory number
     * @return identity icon
     */
    private ItemStack identityItem(ArtifactTemplate template, BuriedFind find, String number) {
        String name = template == null ? find.getArtifactId() : template.displayName();
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GOLD + (number == null ? "—" : number));
        lore.add(ChatColor.AQUA + find.catalogStatusLabel());
        if (template != null) {
            lore.add(ChatColor.GRAY + "Material: " + ChatColor.WHITE
                    + catalogs.materialDisplayName(template.material()));
        }
        return named(CampFindsBoard.iconOf(template), ChatColor.WHITE + name, lore.toArray(String[]::new));
    }

    /**
     * @param player viewer
     * @param site excavation
     * @param find archive row
     * @return provenience card
     */
    private static ItemStack provenienceItem(Player player, Site site, BuriedFind find) {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Excavation: " + ChatColor.WHITE + RecoveredFindItem.siteName(site));
        lore.add(ChatColor.GRAY + "Stratum: " + ChatColor.WHITE + find.getStratumId());
        lore.add(ChatColor.GRAY + "Recovered by: " + ChatColor.WHITE
                + CampNames.of(player, find.getRecoveredBy()));
        return named(Material.COMPASS, ChatColor.GOLD + "Provenience", lore.toArray(String[]::new));
    }

    /**
     * @param find archive row
     * @return condition card
     */
    private ItemStack conditionItem(BuriedFind find) {
        String grade = catalogs.pick().conservation().gradeLabel(find.getConservation());
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Conservation: " + ChatColor.WHITE + find.getConservation() + "%"
                + (grade == null || grade.isBlank() ? "" : ChatColor.GRAY + " · " + grade));
        if (find.isDisturbedBeforeDig()) {
            lore.add(ChatColor.GOLD + "Disturbed before the dig");
        }
        if (find.isFieldDamaged()) {
            lore.add(ChatColor.RED + "Hurt while digging");
        }
        if (find.getState() == FindState.LOST) {
            lore.add(ChatColor.RED + "Nothing could be recovered.");
        }
        return named(Material.GLASS_PANE, ChatColor.GOLD + "Condition", lore.toArray(String[]::new));
    }

    /**
     * @param template catalog row, or {@code null}
     * @param find archive row
     * @return study card
     */
    private static ItemStack studyItem(ArtifactTemplate template, BuriedFind find) {
        List<String> lore = new ArrayList<>();
        if (!find.isStudied()) {
            lore.add(ChatColor.DARK_GRAY + "Not studied.");
            if (find.getState() == FindState.LOST) {
                lore.add(ChatColor.DARK_GRAY + "The piece was lost before it could be examined.");
            } else {
                lore.add(ChatColor.DARK_GRAY + "Bring the piece and a field brush.");
            }
            return named(Material.PAPER, ChatColor.WHITE + "Study", lore.toArray(String[]::new));
        }
        if (template != null && template.rarity() != null && !template.rarity().isBlank()) {
            lore.add(ChatColor.GRAY + "Rarity: " + ChatColor.WHITE + template.rarity());
        }
        String notes = find.getStudyNotes();
        if (notes == null || notes.isBlank()) {
            notes = template == null ? null : template.studyNotes();
        }
        if (notes == null || notes.isBlank()) {
            lore.add(ChatColor.DARK_GRAY + "No further notes were filed.");
        } else {
            wrap(lore, notes);
        }
        return named(Material.PAPER, ChatColor.AQUA + "Study", lore.toArray(String[]::new));
    }

    /**
     * @param find archive row
     * @return readings card
     */
    private ItemStack readingsItem(BuriedFind find) {
        List<String> lore = new ArrayList<>();
        if (find.getInterpretations().isEmpty()) {
            lore.add(ChatColor.DARK_GRAY + "No readings yet.");
            if (find.getState() == FindState.RECOVERED) {
                lore.add(ChatColor.DARK_GRAY + "Identify at the station with the piece in hand.");
            }
            return named(Material.WRITABLE_BOOK, ChatColor.WHITE + "Readings", lore.toArray(String[]::new));
        }
        for (FindInterpretation reading : find.getInterpretations()) {
            lore.add(ChatColor.WHITE + RecoveredFindItem.readingPhrase(reading, catalogs));
            lore.add(ChatColor.GRAY + "  " + CampNames.of(null, reading.author()));
        }
        return named(Material.WRITABLE_BOOK, ChatColor.GOLD + "Readings", lore.toArray(String[]::new));
    }

    /**
     * @param lore lore being built
     * @param text one study note
     */
    private static void wrap(List<String> lore, String text) {
        StringBuilder line = new StringBuilder();
        String prefix = "";
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
