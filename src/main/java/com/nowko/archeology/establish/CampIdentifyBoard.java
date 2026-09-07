package com.nowko.archeology.establish;

import com.nowko.archeology.config.ArtifactTemplate;
import com.nowko.archeology.config.CatalogRegistry;
import com.nowko.archeology.config.InterpretationTemplate;
import com.nowko.archeology.config.InterpretationType;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Classification station using the vanilla brewing-stand window: ingredient = piece, bottles = offers.
 * The client will not let a player drop a sword into those slots; the plugin places the items.
 */
public final class CampIdentifyBoard implements InventoryHolder {
    /** Left / middle / right bottle slots. */
    static final int SLOT_OFFER_0 = 0;
    static final int SLOT_OFFER_1 = 1;
    static final int SLOT_OFFER_2 = 2;
    /** Top ingredient slot. */
    static final int SLOT_PIECE = 3;
    /** Blaze-powder slot, used as Back. */
    static final int SLOT_BACK = 4;
    private static final int[] OFFER_SLOTS = {SLOT_OFFER_0, SLOT_OFFER_1, SLOT_OFFER_2};

    private final UUID siteId;
    private final UUID findId;
    private final CatalogRegistry catalogs;
    private InterpretationType type;
    private final List<String> offerIds = new ArrayList<>();
    private Inventory inventory;

    /**
     * @param siteId excavation
     * @param findId archive row
     * @param catalogs station pools
     */
    public CampIdentifyBoard(UUID siteId, UUID findId, CatalogRegistry catalogs) {
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
     * @return question currently on the table, or {@code null} if the station did not open
     */
    InterpretationType type() {
        return type;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * @param player cataloguer
     * @param site excavation
     * @return whether the station opened
     */
    public boolean open(Player player, Site site) {
        BuriedFind find = site.findById(findId).orElse(null);
        if (find == null) {
            player.closeInventory();
            return false;
        }
        type = catalogs.nextOpenType(find);
        if (type == null) {
            player.sendMessage("Every question on this piece already has an answer.");
            new CampFindBoard(site.getId(), findId, catalogs).open(player, site);
            return false;
        }
        ArtifactTemplate template = catalogs.artifact(find.getArtifactId());
        Set<String> tags = weightTags(site, template);
        List<InterpretationTemplate> offers = catalogs.stationOffers(
                type.id(),
                find.getId(),
                find.getArtifactId(),
                tags);
        offerIds.clear();
        String title = ChatColor.DARK_GREEN + type.question();
        if (title.length() > 32) {
            title = title.substring(0, 32);
        }
        inventory = Bukkit.createInventory(this, InventoryType.BREWING, title);
        inventory.setItem(SLOT_PIECE, pieceItem(template, find, site));
        for (int i = 0; i < OFFER_SLOTS.length; i++) {
            if (i >= offers.size()) {
                continue;
            }
            InterpretationTemplate option = offers.get(i);
            offerIds.add(option.id());
            inventory.setItem(OFFER_SLOTS[i], offerItem(option));
        }
        inventory.setItem(SLOT_BACK, named(
                Material.BLAZE_POWDER,
                ChatColor.WHITE + "Back",
                ChatColor.GRAY + "Leave the station. Signed answers stay."));
        player.openInventory(inventory);
        return true;
    }

    /**
     * @param slot clicked top slot
     * @return phrase id, or {@code null}
     */
    String offerAt(int slot) {
        int index = -1;
        if (slot == SLOT_OFFER_0) {
            index = 0;
        } else if (slot == SLOT_OFFER_1) {
            index = 1;
        } else if (slot == SLOT_OFFER_2) {
            index = 2;
        }
        if (index < 0 || index >= offerIds.size()) {
            return null;
        }
        return offerIds.get(index);
    }

    /**
     * @param site excavation
     * @param template artifact, or {@code null}
     * @return tags that only weight the draw
     */
    private Set<String> weightTags(Site site, ArtifactTemplate template) {
        Set<String> tags = new HashSet<>();
        if (template != null && template.tags() != null) {
            tags.addAll(template.tags());
        }
        for (String hintId : site.getHintIds()) {
            var hint = catalogs.hint(hintId);
            if (hint != null && hint.tags() != null) {
                tags.addAll(hint.tags());
            }
        }
        return tags;
    }

    /**
     * @param template catalog row, or {@code null}
     * @param find archive row
     * @param site excavation
     * @return ingredient-slot stand-in for the piece
     */
    private ItemStack pieceItem(ArtifactTemplate template, BuriedFind find, Site site) {
        String name = template == null ? find.getArtifactId() : template.displayName();
        String number = find.publicNumber(site.getSerial());
        return named(
                CampFindsBoard.iconOf(template),
                ChatColor.WHITE + name,
                ChatColor.GOLD + (number == null ? "—" : number),
                ChatColor.DARK_GRAY + "Placed by the station. The real piece stays on you.");
    }

    /**
     * @param option one of the three
     * @return book shown in a bottle slot
     */
    private static ItemStack offerItem(InterpretationTemplate option) {
        return named(
                Material.BOOK,
                ChatColor.WHITE + option.displayName(),
                ChatColor.GRAY + "Click to sign this reading.",
                ChatColor.DARK_GRAY + "It cannot be unpicked in this version.");
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
