package net.tfminecraft.archaeo.establish;

import net.tfminecraft.archaeo.config.*;
import net.tfminecraft.archaeo.model.*;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class FindReportBookTest {
    private JavaPlugin plugin;
    private PlayerMock director;
    private Site site;
    private CatalogRegistry catalogs;
    private FindReportBook reports;

    @Before public void setUp() {
        MockBukkit.mock(); plugin = MockBukkit.createMockPlugin(); director = MockBukkit.getMock().addPlayer("Director");
        reports = new FindReportBook(plugin); catalogs = mock(CatalogRegistry.class);
        site = new Site(); site.setId(UUID.randomUUID()); site.setName("River excavation"); site.setSerial(23);
        site.setInterest(InterestLevel.LOW); site.establish(director.getUniqueId(), 0, 0, 10, 4, 0);
        site.setStatus(SiteStatus.EXHAUSTED);
    }
    @After public void tearDown() { MockBukkit.unmock(); }

    @Test public void reportIsSignedSnapshotOfFiledFindsWithProvenienceAndReadings() {
        BuriedFind pot = find(FindState.RECOVERED, 1); pot.setGivenName("Storage jar"); pot.setRecoveredBy(director.getUniqueId());
        pot.setStudied(true); pot.setStudyNotes("Incised maker mark");
        pot.addInterpretation(new FindInterpretation("function", "storage", director.getUniqueId(), Instant.now()));
        BuriedFind lost = find(FindState.LOST, 2); lost.setGivenName("Lost rim");
        BuriedFind hidden = find(FindState.HIDDEN, 0); hidden.setGivenName("Buried secret");
        ArtifactTemplate template = new ArtifactTemplate("pot", "Clay vessel", 1, 1, "ceramic", "", false, 1,
                Set.of(), Set.of(), FindProfile.OBJECT, List.of(), "Catalog note");
        when(catalogs.artifact("pot")).thenReturn(template);
        when(catalogs.rarityLoreLine(template)).thenReturn("Rare");
        site.getHintIds().add("missing-old-note"); site.getHintIds().add("river");
        when(catalogs.hint("river")).thenReturn(new HintTemplate("river", "River deposits", 1, Set.of(), Set.of(), Set.of(), Set.of(), null, null, null, null, null));
        ItemStack report = reports.create(director, site, catalogs); BookMeta meta = (BookMeta) report.getItemMeta();
        assertTrue(reports.isReport(report)); assertEquals(site.getId(), reports.siteIdOf(report));
        assertEquals(site.getName(), meta.getTitle()); assertEquals("Director", meta.getAuthor());
        assertEquals(BookMeta.Generation.ORIGINAL, meta.getGeneration());
        String pages = String.join("\n", meta.getPages());
        for (String text : List.of("Excavation #23", "Signed by Director", "River deposits", "missing-old-note", "Storage jar", "Stratum II", "Rare", "Incised maker mark", "According to Director", "storage", "Lost rim")) {
            assertTrue(text + " missing from " + pages, pages.contains(text));
        }
        assertFalse(pages.contains("Buried secret")); assertFalse(pages.contains("Catalog note"));
        pot.setGivenName("Renamed after signing"); site.setName("New camp name");
        assertEquals(pages, String.join("\n", ((BookMeta) report.getItemMeta()).getPages()));
    }

    @Test public void emptyArchiveExplainsMissingNotesAndFinds() {
        BookMeta meta = (BookMeta) reports.create(director, site, catalogs).getItemMeta();
        assertEquals(3, meta.getPageCount());
        assertEquals("Field notes\nNo field notes were filed.", meta.getPage(2));
        assertEquals("No finds were filed.", meta.getPage(3));
    }

    @Test public void lengthyReportsRespectClientPageLimitsAndRetainTextAcrossSplits() {
        site.setName("Long excavation name ".repeat(5));
        BuriedFind find = find(FindState.RECOVERED, 1); find.setStudied(true);
        String notes = "Field observation ".repeat(100); find.setStudyNotes(notes);
        BookMeta meta = (BookMeta) reports.create(director, site, catalogs).getItemMeta();
        assertEquals(32, meta.getTitle().length()); assertTrue(meta.getPageCount() > 3);
        assertTrue(String.join("", meta.getPages()).contains(notes.trim()));
        assertTrue(meta.getPages().stream().allMatch(page -> page.length() <= 800));
        find.setStudyNotes("Long note ".repeat(10000));
        BookMeta capped = (BookMeta) reports.create(director, site, catalogs).getItemMeta();
        assertEquals(100, capped.getPageCount()); assertTrue(capped.getPages().stream().allMatch(page -> page.length() <= 800));
    }

    @Test public void reportNamesTheConfiguredInterestAndFallsBackToCatalogNotesForLegacyRows() {
        when(catalogs.interest(InterestLevel.LOW)).thenReturn(new InterestSettings(InterestLevel.LOW, "Modest scatter", 1, 0, 1, 2, 0, 0, 1, 0, 0));
        ArtifactTemplate pot = new ArtifactTemplate("pot", "Clay vessel", 1, 1, "ceramic", "", false, 1,
                Set.of(), Set.of(), FindProfile.OBJECT, List.of(), "Catalog note");
        ArtifactTemplate bead = new ArtifactTemplate("bead", "Glass bead", 1, 1, "glass", "", false, 1,
                Set.of(), Set.of(), FindProfile.OBJECT, List.of(), " ");
        when(catalogs.artifact("pot")).thenReturn(pot); when(catalogs.artifact("bead")).thenReturn(bead);
        when(catalogs.rarityLoreLine(any(ArtifactTemplate.class))).thenReturn("Common");
        BuriedFind legacy = find(FindState.RECOVERED, 0); legacy.setStudied(true); legacy.setStudyNotes(" "); // Recovered before numbering.
        BuriedFind plain = find(FindState.RECOVERED, 2); plain.setArtifactId("bead"); plain.setStudied(true);
        BuriedFind removed = find(FindState.RECOVERED, 3); removed.setArtifactId("retired"); removed.setStudied(true);
        BuriedFind waiting = find(FindState.RECOVERED, 4); waiting.setArtifactId("retired");
        List<String> pages = ((BookMeta) reports.create(director, site, catalogs).getItemMeta()).getPages();
        assertTrue(pages.getFirst().contains("Interest: Modest scatter"));
        assertTrue(pages.get(2), pages.get(2).startsWith("Find\nClay vessel")); assertTrue(pages.get(2).contains("Catalog note"));
        String kept = "Conservation " + plain.getConservation() + "%";
        assertEquals(List.of("Stratum II", kept, "Studied", "Common", "No readings filed."), pages.get(3).lines().skip(2).toList());
        assertEquals(List.of("Stratum II", kept, "Studied", "No readings filed."), pages.get(4).lines().skip(2).toList());
        assertEquals(List.of("Stratum II", kept, "Field catalog", "Not studied.", "No readings filed."), pages.get(5).lines().skip(2).toList());
        site.setInterest(null); // An unrated ruin.
        assertFalse(((BookMeta) reports.create(director, site, catalogs).getItemMeta()).getPage(1).contains("Interest"));
    }

    @Test public void damagedBookTagsCannotResolveAnUnrelatedExcavation() {
        assertFalse(reports.isReport(null)); assertFalse(reports.isReport(new ItemStack(Material.BOOK)));
        assertFalse(reports.isReport(new ItemStack(Material.WRITTEN_BOOK)));
        ItemStack report = reports.create(director, site, catalogs);
        for (String id : List.of("", "not-a-uuid")) {
            BookMeta meta = (BookMeta) report.getItemMeta();
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "site_id"), PersistentDataType.STRING, id);
            report.setItemMeta(meta); assertTrue(reports.isReport(report)); assertNull(reports.siteIdOf(report));
        }
        BookMeta meta = (BookMeta) report.getItemMeta();
        meta.getPersistentDataContainer().remove(new NamespacedKey(plugin, "site_id")); report.setItemMeta(meta);
        assertNull(reports.siteIdOf(report));
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "excavation_report"), PersistentDataType.BYTE, (byte) 0);
        report.setItemMeta(meta); assertFalse(reports.isReport(report)); assertNull(reports.siteIdOf(report));
    }

    private BuriedFind find(FindState state, int number) {
        BuriedFind find = new BuriedFind(); find.setId(UUID.randomUUID()); find.setArtifactId("pot"); find.setStratumId("II");
        find.setFindNumber(number); find.setState(state); site.getFinds().add(find); return find;
    }
}
