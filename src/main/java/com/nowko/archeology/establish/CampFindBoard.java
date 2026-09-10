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
 * One find's consultation page in the excavation archive: identity, provenience, condition, and signed readings.
 */
public final class CampFindBoard implements InventoryHolder {
    static final int SLOT_IDENTITY = 4;
    static final int SLOT_BACK = CampGui.SLOT_BACK;
    static final int SLOT_PROVENIENCE = 11;
    static final int SLOT_CONDITION = 13;
    static final int SLOT_READINGS = 15;

    private final UUID siteId;
    private final UUID findId;
    private final CatalogRegistry catalogs;
    private final boolean museum;
    private Inventory inventory;

    /**
     * Consultation page opened from the excavation finds register.
     *
     * @param siteId excavation
     * @param findId archive row
     * @param catalogs materials, rarity, and interpretation labels
     */
    public CampFindBoard(UUID siteId, UUID findId, CatalogRegistry catalogs) {
        this(siteId, findId, catalogs, false);
    }

    /**
     * @param siteId excavation
     * @param findId archive row
     * @param catalogs materials, rarity, and interpretation labels
     * @param museum when true, this is a world plaque: no cabinet hint, and Back closes
     */
    public CampFindBoard(UUID siteId, UUID findId, CatalogRegistry catalogs, boolean museum) {
        this.siteId = siteId;
        this.findId = findId;
        this.catalogs = catalogs;
        this.museum = museum;
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
     * @return whether this window was opened from a displayed piece, not the camp register
     */
    public boolean isMuseum() {
        return museum;
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
        String title = number == null ? "Find" : number;
        if (title.length() > 32) {
            title = title.substring(0, 32);
        }
        inventory = Bukkit.createInventory(this, 27, title);
        ItemStack filler = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < 27; slot++) {
            inventory.setItem(slot, filler);
        }
        inventory.setItem(SLOT_IDENTITY, identityItem(template, find, number));
        inventory.setItem(SLOT_PROVENIENCE, provenienceItem(player, site, find));
        inventory.setItem(SLOT_CONDITION, conditionItem(find));
        inventory.setItem(SLOT_READINGS, readingsItem(find));
        if (museum) {
            inventory.setItem(SLOT_BACK, named(
                    Material.BARRIER,
                    ChatColor.WHITE + "Close",
                    ChatColor.GRAY + "Leave the plaque."));
        } else {
            inventory.setItem(SLOT_BACK, named(
                    Material.BARRIER,
                    ChatColor.WHITE + "Back",
                    ChatColor.GRAY + "Return to the finds register."));
        }
        player.openInventory(inventory);
    }

    /**
     * Catalog identity that grows with cabinet work: material after cleaning, rarity after a filed sketch.
     *
     * @param template catalog row, or {@code null}
     * @param find archive row
     * @param number public inventory number
     * @return identity icon
     */
    private ItemStack identityItem(ArtifactTemplate template, BuriedFind find, String number) {
        String name = find.shownName(template == null ? null : template.displayName());
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GOLD + (number == null ? "—" : number));
        lore.add(ChatColor.AQUA + find.catalogStatusLabel());
        if (template != null && RecoveredFindItem.conditionKnown(find)) {
            lore.add(ChatColor.GRAY + "Material: " + ChatColor.WHITE
                    + catalogs.materialDisplayName(template.material()));
        }
        if (find.hasFieldSketch() && template != null
                && template.rarity() != null && !template.rarity().isBlank()) {
            lore.add(ChatColor.GRAY + "Rarity: " + ChatColor.WHITE + template.rarity());
        }
        if (!museum) {
            String hint = RecoveredFindItem.nextCabinetHint(template, find, catalogs);
            if (hint != null) {
                lore.add(hint);
            }
        }
        return named(Material.NAME_TAG, ChatColor.WHITE + name, lore.toArray(String[]::new));
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
        return named(Material.MAP, ChatColor.GOLD + "Provenience", lore.toArray(String[]::new));
    }

    /**
     * @param find archive row
     * @return condition card
     */
    private ItemStack conditionItem(BuriedFind find) {
        String grade = catalogs.pick().conservation().gradeLabel(find.getConservation());
        List<String> lore = new ArrayList<>();
        if (RecoveredFindItem.conditionKnown(find)) {
            lore.add(ChatColor.GRAY + "Conservation: " + ChatColor.WHITE + find.getConservation() + "%"
                    + (grade == null || grade.isBlank() ? "" : ChatColor.GRAY + " · " + grade));
        } else if (find.getState() == FindState.RECOVERED) {
            lore.add(ChatColor.DARK_GRAY + "Clean the piece to read its condition.");
        }
        if (find.isDisturbedBeforeDig()) {
            lore.add(ChatColor.GOLD + "Disturbed before the dig");
        }
        if (find.isFieldDamaged()) {
            lore.add(ChatColor.RED + "Hurt while digging");
        }
        if (find.getState() == FindState.LOST) {
            lore.add(ChatColor.RED + "Nothing could be recovered.");
        }
        return named(Material.SPYGLASS, ChatColor.GOLD + "Condition", lore.toArray(String[]::new));
    }

    /**
     * @param find archive row
     * @return readings card
     */
    private ItemStack readingsItem(BuriedFind find) {
        List<String> lore = new ArrayList<>();
        if (find.getInterpretations().isEmpty()) {
            lore.add(ChatColor.DARK_GRAY + "No readings yet.");
            return named(Material.WRITABLE_BOOK, ChatColor.WHITE + "Readings", lore.toArray(String[]::new));
        }
        for (FindInterpretation reading : find.getInterpretations()) {
            lore.add(ChatColor.WHITE + RecoveredFindItem.readingPhrase(reading, catalogs));
            lore.add(ChatColor.GRAY + "  " + CampNames.of(null, reading.author()));
        }
        return named(Material.WRITTEN_BOOK, ChatColor.GOLD + "Readings", lore.toArray(String[]::new));
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
