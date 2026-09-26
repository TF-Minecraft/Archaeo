package net.tfminecraft.archaeo.establish;

import net.tfminecraft.archaeo.item.RecoveredFindItem;
import net.tfminecraft.archaeo.model.Site;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;

/**
 * Field book handed out when a camp is closed. It is a written book so it can sit in a chest
 * or lectern, but right-click opens the excavation boards rather than vanilla pages: the
 * archive is still the site YAML, not 100 pages of flattened text.
 */
public final class CampArchiveBook {
    private final NamespacedKey siteIdKey;
    private final NamespacedKey markerKey;

    /**
     * @param plugin owner of the PDC keys
     */
    public CampArchiveBook(JavaPlugin plugin) {
        this.siteIdKey = new NamespacedKey(plugin, "archive_site_id");
        this.markerKey = new NamespacedKey(plugin, "excavation_archive");
    }

    /**
     * @param closer who took the camp down
     * @param site excavation whose record this book opens
     * @return written book that opens the archive GUI
     */
    public ItemStack create(Player closer, Site site) {
        return create(closer.getName(), site);
    }

    /**
     * Same field book as {@link #create(Player, Site)} when the closer is console or a command.
     *
     * @param authorName stamped on the cover
     * @param site excavation whose record this book opens
     * @return written book that opens the archive GUI
     */
    public ItemStack create(String authorName, Site site) {
        ItemStack stack = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) stack.getItemMeta();
        String title = RecoveredFindItem.siteName(site);
        if (title.length() > 32) {
            title = title.substring(0, 32);
        }
        meta.setTitle(title);
        String author = authorName == null || authorName.isBlank() ? "Staff" : authorName;
        if (author.length() > 32) {
            author = author.substring(0, 32);
        }
        meta.setAuthor(author);
        meta.setGeneration(BookMeta.Generation.ORIGINAL);
        meta.setDisplayName(displayName(site));
        meta.setLore(loreOf(site));
        String unfinished = site.getFinds().size() > 0 && site.completionPercent() < 100
                ? "\nThe cut was not finished (" + site.completionPercent() + "%)."
                : "\nThe cut was finished.";
        meta.addPage(
                site.publicName()
                        + "\nCompletion: " + site.completionPercent() + "%"
                        + unfinished
                        + "\n\nRight-click this book to read the excavation record.");
        var pdc = meta.getPersistentDataContainer();
        pdc.set(markerKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(siteIdKey, PersistentDataType.STRING, site.getId().toString());
        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * Vanilla book-copy recipes keep title and pages but drop custom name, lore, and PDC.
     * Stamp those back so a copy still opens the live record.
     *
     * @param original field book in the grid
     * @param crafted vanilla copy
     * @return copy that still opens the archive
     */
    public ItemStack stampCopy(ItemStack original, ItemStack crafted) {
        if (crafted == null || crafted.getType() != Material.WRITTEN_BOOK || !isArchive(original)) {
            return crafted;
        }
        ItemStack stamped = crafted.clone();
        BookMeta from = (BookMeta) original.getItemMeta();
        BookMeta to = (BookMeta) stamped.getItemMeta();
        if (from.hasDisplayName()) {
            to.setDisplayName(from.getDisplayName());
        }
        to.setLore(from.getLore());
        String siteId = from.getPersistentDataContainer().get(siteIdKey, PersistentDataType.STRING);
        to.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
        if (siteId != null) {
            to.getPersistentDataContainer().set(siteIdKey, PersistentDataType.STRING, siteId);
        }
        stamped.setItemMeta(to);
        return stamped;
    }

    /**
     * Display name and lore follow the live dossier (completion, rename) when the book is opened.
     *
     * @param stack field book in hand
     * @param site live excavation
     */
    public void refresh(ItemStack stack, Site site) {
        if (stack == null || site == null || !isArchive(stack)) {
            return;
        }
        BookMeta meta = (BookMeta) stack.getItemMeta();
        meta.setDisplayName(displayName(site));
        meta.setLore(loreOf(site));
        stack.setItemMeta(meta);
    }

    /**
     * @param site excavation
     * @return hotbar name
     */
    private static String displayName(Site site) {
        return ChatColor.GOLD + "Field book — " + site.publicName();
    }

    /**
     * @param site excavation
     * @return item lore
     */
    private static List<String> loreOf(Site site) {
        return List.of(
                ChatColor.GRAY + "Completion: " + ChatColor.WHITE + site.completionPercent() + "%",
                ChatColor.DARK_GRAY + "Right-click to open the excavation record.");
    }

    /**
     * @param stack candidate
     * @return whether this is a closed-excavation field book
     */
    public boolean isArchive(ItemStack stack) {
        if (stack == null || stack.getType() != Material.WRITTEN_BOOK || !stack.hasItemMeta()) {
            return false;
        }
        Byte marker = stack.getItemMeta().getPersistentDataContainer()
                .get(markerKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    /**
     * @param stack field book
     * @return excavation id, or {@code null}
     */
    public UUID siteIdOf(ItemStack stack) {
        if (!isArchive(stack)) {
            return null;
        }
        String raw = stack.getItemMeta().getPersistentDataContainer()
                .get(siteIdKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
