package net.tfminecraft.archaeo.establish;

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

import java.util.UUID;

import static org.junit.Assert.*;

public class CampArchiveBookTest {
    private JavaPlugin plugin;
    private CampArchiveBook books;
    private Site site;

    @Before public void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        books = new CampArchiveBook(plugin);
        site = new Site(); site.setId(UUID.randomUUID()); site.setName("River camp");
    }
    @After public void tearDown() { MockBukkit.unmock(); }

    @Test
    public void bookRecordsCompletionAndTruncatesVanillaTitleAndAuthorLimits() {
        site.setName("A".repeat(50));
        BuriedFind find = new BuriedFind(); site.getFinds().add(find);
        ItemStack book = books.create("B".repeat(50), site);
        BookMeta meta = (BookMeta) book.getItemMeta();
        assertEquals("A".repeat(32), meta.getTitle());
        assertEquals("B".repeat(32), meta.getAuthor());
        assertEquals(BookMeta.Generation.ORIGINAL, meta.getGeneration());
        assertTrue(meta.getPage(1).contains("not finished (0%)"));
        assertEquals(site.getId(), books.siteIdOf(book));
        find.setState(FindState.RECOVERED);
        site.setName("Renamed camp");
        books.refresh(book, site);
        assertTrue(book.getItemMeta().getDisplayName().contains("Renamed camp"));
        assertTrue(book.getItemMeta().getLore().getFirst().contains("100%"));
        assertEquals(site.getId(), books.siteIdOf(book));
        for (String author : new String[]{null, " "}) {
            BookMeta fallback = (BookMeta) books.create(author, site).getItemMeta();
            assertEquals("Staff", fallback.getAuthor());
            assertTrue(fallback.getPage(1).contains("cut was finished"));
        }
        assertEquals("Writer", ((BookMeta) books.create(MockBukkit.getMock().addPlayer("Writer"), site).getItemMeta()).getAuthor());
    }

    @Test
    public void copyingBookRetainsArchiveIdentityWithoutMutatingRecipeResult() {
        ItemStack original = books.create("Author", site);
        ItemStack crafted = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) crafted.getItemMeta();
        meta.setTitle("Vanilla copy"); meta.setAuthor("Author"); meta.addPage("Copied page");
        crafted.setItemMeta(meta);
        ItemStack copy = books.stampCopy(original, crafted);
        assertNotSame(crafted, copy);
        assertFalse(books.isArchive(crafted));
        assertTrue(books.isArchive(copy));
        assertEquals(site.getId(), books.siteIdOf(copy));
        assertEquals("Copied page", ((BookMeta) copy.getItemMeta()).getPage(1));
        assertEquals(original.getItemMeta().getDisplayName(), copy.getItemMeta().getDisplayName());
        assertEquals(original.getItemMeta().getLore(), copy.getItemMeta().getLore());
        assertSame(crafted, books.stampCopy(new ItemStack(Material.BOOK), crafted));
        ItemStack paper = new ItemStack(Material.PAPER);
        assertSame(paper, books.stampCopy(original, paper));
        assertNull(books.stampCopy(original, null));
    }

    @Test
    public void copiesOfARenamedOrDamagedFieldBookStayFieldBooks() {
        ItemStack original = books.create("Author", site);
        BookMeta cleared = (BookMeta) original.getItemMeta(); cleared.setDisplayName(null); original.setItemMeta(cleared); // Anvil name cleared.
        ItemStack copy = books.stampCopy(original, new ItemStack(Material.WRITTEN_BOOK));
        assertTrue(books.isArchive(copy)); assertEquals(site.getId(), books.siteIdOf(copy)); assertFalse(copy.getItemMeta().hasDisplayName());
        BookMeta damaged = (BookMeta) original.getItemMeta();
        damaged.getPersistentDataContainer().remove(new NamespacedKey(plugin, "archive_site_id")); original.setItemMeta(damaged);
        ItemStack orphan = books.stampCopy(original, new ItemStack(Material.WRITTEN_BOOK));
        assertTrue(books.isArchive(orphan)); assertNull(books.siteIdOf(orphan));
    }

    @Test
    public void ordinaryAndCorruptBooksDoNotResolveToAnArchiveDossier() {
        assertFalse(books.isArchive(null));
        assertFalse(books.isArchive(new ItemStack(Material.PAPER)));
        assertFalse(books.isArchive(new ItemStack(Material.WRITTEN_BOOK)));
        assertNull(books.siteIdOf(null));
        books.refresh(null, site);
        ItemStack ordinary = new ItemStack(Material.WRITTEN_BOOK);
        books.refresh(ordinary, site);
        assertFalse(books.isArchive(ordinary));
        ItemStack archive = books.create("Author", site);
        books.refresh(archive, null);
        for (String raw : new String[]{null, " ", "not-a-uuid"}) {
            BookMeta meta = (BookMeta) archive.getItemMeta();
            NamespacedKey key = new NamespacedKey(plugin, "archive_site_id");
            if (raw == null) meta.getPersistentDataContainer().remove(key);
            else meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, raw);
            archive.setItemMeta(meta);
            assertTrue(books.isArchive(archive));
            assertNull(books.siteIdOf(archive));
        }
        BookMeta meta = (BookMeta) archive.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "excavation_archive"), PersistentDataType.BYTE, (byte) 0);
        archive.setItemMeta(meta);
        assertFalse(books.isArchive(archive));
    }
}
