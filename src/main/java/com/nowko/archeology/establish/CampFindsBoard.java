package com.nowko.archeology.establish;

import com.nowko.archeology.config.ArtifactTemplate;
import com.nowko.archeology.config.CatalogRegistry;
import com.nowko.archeology.model.BuriedFind;
import com.nowko.archeology.model.FindState;
import com.nowko.archeology.model.Site;
import com.nowko.archeology.model.SiteStatus;
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
 * Finds register for one excavation: every piece that has left the cut, including those lost in it.
 */
public final class CampFindsBoard implements InventoryHolder {
    static final int SLOT_REPORT = 18;
    static final int SLOT_BACK = CampGui.SLOT_BACK;
    private static final int LIST_SLOTS = CampGui.LIST_SLOTS;

    private final UUID siteId;
    private final boolean director;
    private final CatalogRegistry catalogs;
    private final List<UUID> finds = new ArrayList<>();
    private Inventory inventory;

    /**
     * @param siteId excavation
     * @param director whether the viewer may issue the closing report
     * @param catalogs artifact icons and labels
     */
    public CampFindsBoard(UUID siteId, boolean director, CatalogRegistry catalogs) {
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
        finds.clear();
        for (BuriedFind find : site.cataloguedFinds()) {
            finds.add(find.getId());
        }
        inventory = Bukkit.createInventory(this, 27, "Finds");
        int shown = Math.min(finds.size(), LIST_SLOTS);
        for (int i = 0; i < shown; i++) {
            BuriedFind find = site.findById(finds.get(i)).orElse(null);
            if (find != null) {
                inventory.setItem(i, rowItem(site, find));
            }
        }
        if (director && site.getStatus() == SiteStatus.EXHAUSTED) {
            inventory.setItem(SLOT_REPORT, named(
                    Material.WRITTEN_BOOK,
                    ChatColor.GOLD + "Issue report",
                    ChatColor.GRAY + "A signed book of this excavation.",
                    ChatColor.DARK_GRAY + "Reprints read the live register."));
        }
        inventory.setItem(SLOT_BACK, named(
                Material.BARRIER,
                ChatColor.WHITE + "Back",
                ChatColor.GRAY + "Return to the excavation board."));
        player.openInventory(inventory);
    }

    /**
     * @param slot clicked top slot
     * @return find id, or {@code null}
     */
    UUID findAt(int slot) {
        if (slot < 0 || slot >= finds.size() || slot >= LIST_SLOTS) {
            return null;
        }
        return finds.get(slot);
    }

    /**
     * @param site excavation
     * @param find archive row
     * @return list icon
     */
    private ItemStack rowItem(Site site, BuriedFind find) {
        ArtifactTemplate template = catalogs.artifact(find.getArtifactId());
        Material icon = iconOf(template, find);
        String name = find.shownName(template == null ? null : template.displayName());
        String number = find.publicNumber(site.getSerial());
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GOLD + (number == null ? "—" : number));
        lore.add(ChatColor.GRAY + "Stratum " + find.getStratumId());
        lore.add(statusColor(find) + find.catalogStatusLabel());
        lore.add(ChatColor.DARK_GRAY + "Click to open the fiche.");
        return named(icon, ChatColor.WHITE + name, lore.toArray(String[]::new));
    }

    /**
     * @param template catalog row, or {@code null}
     * @return inventory icon from the first catalog item
     */
    public static Material iconOf(ArtifactTemplate template) {
        return iconOf(template, null);
    }

    /**
     * @param template catalog row, or {@code null}
     * @param find archive row whose rolled material wins when present
     * @return inventory icon
     */
    public static Material iconOf(ArtifactTemplate template, BuriedFind find) {
        if (template == null) {
            return Material.BRICK;
        }
        return template.resolveItem(find == null ? null : find.getItem());
    }

    /**
     * @param find archive row
     * @return colour for the status line
     */
    private static ChatColor statusColor(BuriedFind find) {
        if (find.getState() == FindState.LOST) {
            return ChatColor.RED;
        }
        if (find.isCatalogued()) {
            return ChatColor.GOLD;
        }
        if (find.isStudied() || find.hasFieldSketch() || find.isLabCleaned()) {
            return ChatColor.AQUA;
        }
        return ChatColor.GRAY;
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
