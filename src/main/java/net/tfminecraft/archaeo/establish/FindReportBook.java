package net.tfminecraft.archaeo.establish;

import net.tfminecraft.archaeo.config.ArtifactTemplate;
import net.tfminecraft.archaeo.config.CatalogRegistry;
import net.tfminecraft.archaeo.config.HintTemplate;
import net.tfminecraft.archaeo.config.InterestSettings;
import net.tfminecraft.archaeo.item.RecoveredFindItem;
import net.tfminecraft.archaeo.model.BuriedFind;
import net.tfminecraft.archaeo.model.FindInterpretation;
import net.tfminecraft.archaeo.model.FindState;
import net.tfminecraft.archaeo.model.Site;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds the director's signed report: a readable snapshot of the live excavation archive.
 */
public final class FindReportBook {
    /** Vanilla written-book page cap used when splitting long reports. */
    private static final int MAX_PAGES = 100;
    private static final int PAGE_CHARS = 800;

    private final NamespacedKey siteIdKey;
    private final NamespacedKey markerKey;

    /**
     * @param plugin owner of the PDC keys
     */
    public FindReportBook(JavaPlugin plugin) {
        this.siteIdKey = new NamespacedKey(plugin, "site_id");
        this.markerKey = new NamespacedKey(plugin, "excavation_report");
    }

    /**
     * @param director signer
     * @param site excavation
     * @param catalogs hints, artifacts, and interpretation labels
     * @return signed written book
     */
    public ItemStack create(Player director, Site site, CatalogRegistry catalogs) {
        ItemStack stack = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        String title = RecoveredFindItem.siteName(site);
        if (title.length() > 32) {
            title = title.substring(0, 32);
        }
        meta.setTitle(title);
        String author = director.getName() == null ? "Director" : director.getName();
        if (author.length() > 32) {
            author = author.substring(0, 32);
        }
        meta.setAuthor(author);
        meta.setGeneration(BookMeta.Generation.ORIGINAL);
        addPages(meta, buildPages(director, site, catalogs));
        var pdc = meta.getPersistentDataContainer();
        pdc.set(markerKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(siteIdKey, PersistentDataType.STRING, site.getId().toString());
        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * @param stack candidate
     * @return whether this is a director's signed report
     */
    public boolean isReport(ItemStack stack) {
        if (stack == null || stack.getType() != Material.WRITTEN_BOOK || !stack.hasItemMeta()) {
            return false;
        }
        Byte marker = stack.getItemMeta().getPersistentDataContainer()
                .get(markerKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    /**
     * @param stack signed report
     * @return excavation id, or {@code null}
     */
    public UUID siteIdOf(ItemStack stack) {
        if (!isReport(stack)) {
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

    /**
     * @param director signer
     * @param site excavation
     * @param catalogs labels
     * @return page texts without colour
     */
    private static List<String> buildPages(Player director, Site site, CatalogRegistry catalogs) {
        List<String> pages = new ArrayList<>();
        StringBuilder cover = new StringBuilder();
        cover.append(RecoveredFindItem.siteName(site)).append('\n');
        cover.append("Excavation #").append(site.getSerial()).append('\n');
        cover.append("Director: ").append(CampNames.of(director, site.getDirector())).append('\n');
        cover.append("Status: ").append(CampBoard.statusLabel(site.getStatus())).append('\n');
        if (site.getInterest() != null) {
            InterestSettings interest = catalogs.interest(site.getInterest());
            String label = interest == null ? site.getInterest().yamlKey() : interest.displayName();
            cover.append("Interest: ").append(label).append('\n');
        }
        cover.append("Signed by ").append(director.getName()).append('.');
        pages.add(cover.toString());

        StringBuilder dossier = new StringBuilder("Field notes\n");
        if (site.getHintIds().isEmpty()) {
            dossier.append("No field notes were filed.");
        } else {
            for (String hintId : site.getHintIds()) {
                HintTemplate hint = catalogs.hint(hintId);
                dossier.append("- ").append(hint == null ? hintId : hint.text()).append('\n');
            }
        }
        pages.add(dossier.toString().trim());

        List<BuriedFind> finds = site.cataloguedFinds();
        if (finds.isEmpty()) {
            pages.add("No finds were filed.");
            return pages;
        }
        for (BuriedFind find : finds) {
            pages.add(findPage(director, site, find, catalogs));
        }
        return pages;
    }

    /**
     * @param director signer
     * @param site excavation
     * @param find archive row
     * @param catalogs labels
     * @return one find page
     */
    private static String findPage(Player director, Site site, BuriedFind find, CatalogRegistry catalogs) {
        ArtifactTemplate template = catalogs.artifact(find.getArtifactId());
        String name = find.shownName(template == null ? null : template.displayName());
        String number = find.publicNumber(site);
        StringBuilder page = new StringBuilder();
        page.append(number == null ? "Find" : number).append('\n');
        page.append(name).append('\n');
        page.append("Stratum ").append(find.getStratumId()).append('\n');
        page.append("Conservation ").append(find.getConservation()).append("%\n");
        page.append(find.catalogStatusLabel()).append('\n');
        if (find.getRecoveredBy() != null) {
            page.append("Recovered by ").append(CampNames.of(director, find.getRecoveredBy())).append('\n');
        }
        if (find.isStudied()) {
            if (template != null) {
                page.append(catalogs.rarityLoreLine(template)).append('\n');
            }
            String notes = find.getStudyNotes();
            if (notes == null || notes.isBlank()) {
                notes = template == null ? null : template.studyNotes();
            }
            if (notes != null && !notes.isBlank()) {
                page.append(notes).append('\n');
            }
        } else if (find.getState() != FindState.LOST) {
            page.append("Not studied.\n");
        }
        if (find.getInterpretations().isEmpty()) {
            page.append("No readings filed.");
        } else {
            for (FindInterpretation reading : find.getInterpretations()) {
                page.append("According to ").append(CampNames.of(director, reading.author()))
                        .append(": ").append(RecoveredFindItem.readingPhrase(reading, catalogs)).append('\n');
            }
        }
        return page.toString().trim();
    }

    /**
     * Splits overlong pages so the client can still open the book.
     *
     * @param meta book being filled
     * @param pages raw pages
     */
    private static void addPages(BookMeta meta, List<String> pages) {
        int count = 0;
        for (String raw : pages) {
            String text = ChatColor.stripColor(raw);
            if (text.isBlank()) {
                continue;
            }
            int start = 0;
            while (start < text.length() && count < MAX_PAGES) {
                int end = Math.min(text.length(), start + PAGE_CHARS);
                meta.addPage(text.substring(start, end));
                count++;
                start = end;
            }
            if (count >= MAX_PAGES) {
                return;
            }
        }
        if (count == 0) {
            meta.addPage("Empty report.");
        }
    }
}
