package com.nowko.archeology.establish;

import com.nowko.archeology.config.ArtifactTemplate;
import com.nowko.archeology.config.CatalogRegistry;
import com.nowko.archeology.config.InterpretationTemplate;
import com.nowko.archeology.model.BuriedFind;
import com.nowko.archeology.model.InterpretationConfidence;
import com.nowko.archeology.model.Site;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Lets a cataloguer file up to two readings on a studied find. Suggested tags come first.
 */
public final class CampInterpretBoard implements InventoryHolder {
    static final int SLOT_LOW = 18;
    static final int SLOT_MEDIUM = 20;
    static final int SLOT_HIGH = 22;
    static final int SLOT_BACK = 26;
    private static final int LIST_SLOTS = 18;

    private final UUID siteId;
    private final UUID findId;
    private final CatalogRegistry catalogs;
    private InterpretationConfidence confidence = InterpretationConfidence.MEDIUM;
    private final List<String> optionIds = new ArrayList<>();
    private Inventory inventory;

    /**
     * @param siteId excavation
     * @param findId archive row
     * @param catalogs interpretation catalog
     */
    public CampInterpretBoard(UUID siteId, UUID findId, CatalogRegistry catalogs) {
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
     * @return confidence used for the next add
     */
    InterpretationConfidence confidence() {
        return confidence;
    }

    /**
     * @param confidence band chosen for the next add
     */
    void setConfidence(InterpretationConfidence confidence) {
        this.confidence = confidence == null ? InterpretationConfidence.MEDIUM : confidence;
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
        Set<String> tags = template == null ? Set.of() : template.tags();
        optionIds.clear();
        inventory = Bukkit.createInventory(this, 27, ChatColor.DARK_GREEN + "Interpret");
        int slot = 0;
        for (InterpretationTemplate option : catalogs.interpretationsFor(tags)) {
            if (slot >= LIST_SLOTS) {
                break;
            }
            optionIds.add(option.id());
            inventory.setItem(slot, optionItem(option, find, tags));
            slot++;
        }
        inventory.setItem(SLOT_LOW, confidenceItem(InterpretationConfidence.LOW, Material.LIGHT_GRAY_DYE));
        inventory.setItem(SLOT_MEDIUM, confidenceItem(InterpretationConfidence.MEDIUM, Material.YELLOW_DYE));
        inventory.setItem(SLOT_HIGH, confidenceItem(InterpretationConfidence.HIGH, Material.LIME_DYE));
        inventory.setItem(SLOT_BACK, named(
                Material.BARRIER,
                ChatColor.WHITE + "Back",
                ChatColor.GRAY + "Return to the fiche."));
        player.openInventory(inventory);
    }

    /**
     * @param slot clicked top slot
     * @return interpretation id, or {@code null}
     */
    String optionAt(int slot) {
        if (slot < 0 || slot >= optionIds.size() || slot >= LIST_SLOTS) {
            return null;
        }
        return optionIds.get(slot);
    }

    /**
     * @param slot clicked top slot
     * @return confidence band for that dye, or {@code null}
     */
    static InterpretationConfidence confidenceAt(int slot) {
        if (slot == SLOT_LOW) {
            return InterpretationConfidence.LOW;
        }
        if (slot == SLOT_MEDIUM) {
            return InterpretationConfidence.MEDIUM;
        }
        if (slot == SLOT_HIGH) {
            return InterpretationConfidence.HIGH;
        }
        return null;
    }

    /**
     * @param option catalog reading
     * @param find archive row
     * @param tags artifact tags
     * @return list icon
     */
    private ItemStack optionItem(InterpretationTemplate option, BuriedFind find, Set<String> tags) {
        boolean selected = find.hasInterpretation(option.id());
        boolean suggested = option.suggestedBy(tags);
        List<String> lore = new ArrayList<>();
        if (suggested) {
            lore.add(ChatColor.AQUA + "Suggested by the piece.");
        }
        if (selected) {
            lore.add(ChatColor.GOLD + "Filed. Click to remove.");
        } else if (find.getInterpretations().size() >= 2) {
            lore.add(ChatColor.DARK_GRAY + "Two readings already. Remove one first.");
        } else {
            lore.add(ChatColor.DARK_GRAY + "Click to file at " + confidence.displayName() + " confidence.");
        }
        ItemStack stack = named(
                selected ? Material.WRITTEN_BOOK : Material.BOOK,
                (selected ? ChatColor.GOLD : ChatColor.WHITE) + option.displayName(),
                lore.toArray(String[]::new));
        if (suggested || selected) {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                stack.setItemMeta(meta);
            }
        }
        return stack;
    }

    /**
     * @param band confidence this dye selects
     * @param dye icon
     * @return dye button
     */
    private ItemStack confidenceItem(InterpretationConfidence band, Material dye) {
        boolean active = band == confidence;
        return named(
                dye,
                (active ? ChatColor.GOLD : ChatColor.WHITE) + "Confidence: " + band.displayName(),
                active
                        ? ChatColor.DARK_GRAY + "Selected for the next reading."
                        : ChatColor.DARK_GRAY + "Click to use this band.");
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
