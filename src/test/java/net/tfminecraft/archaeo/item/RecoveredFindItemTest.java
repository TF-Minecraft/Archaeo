package net.tfminecraft.archaeo.item;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.*;
import net.tfminecraft.archaeo.config.*;
import net.tfminecraft.archaeo.model.*;
import net.tfminecraft.archaeo.site.SiteRepository;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

public class RecoveredFindItemTest {
    private ServerMock server;
    private JavaPlugin plugin;
    private RecoveredFindItem items;
    private CatalogRegistry catalogs;
    private ArtifactTemplate template;
    private Site site;
    private BuriedFind find;

    @Before public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        items = new RecoveredFindItem(plugin);
        catalogs = mock(CatalogRegistry.class);
        when(catalogs.pick()).thenReturn(PickSettings.defaults());
        when(catalogs.materialDisplayName("ceramic")).thenReturn("Ceramic");
        when(catalogs.materialOf("ceramic")).thenReturn(new FindMaterial("ceramic", "Ceramic", 1, Material.WHITE_STAINED_GLASS_PANE, List.of("soil")));
        template = new ArtifactTemplate("pot", "Ancient pot", 1, 3, "ceramic", null, false, 5, Set.of("I"), Set.of(), FindProfile.OBJECT,
                List.of(ItemRef.vanilla(Material.BRICK), ItemRef.vanilla(Material.FLOWER_POT)), "A vessel shaped for carrying water over long journeys through the old countryside.");
        when(catalogs.artifact("pot")).thenReturn(template);
        when(catalogs.rarityLoreLine(template)).thenReturn(ChatColor.LIGHT_PURPLE + "Rarity: EPIC");
        site = new Site();
        site.setId(UUID.randomUUID()); site.setName("Old quarry");
        find = new BuriedFind();
        find.setId(UUID.randomUUID()); find.setArtifactId("pot"); find.setStratumId("I");
        find.setState(FindState.RECOVERED); find.setFindNumber(3);
        find.setBuriedConservation(80); find.setConservation(70);
        site.getFinds().add(find);
    }
    @After public void tearDown() { MockBukkit.unmock(); }

    @Test public void recoveredPieceStampsArchiveProvenanceAndCannotStack() {
        UUID recoverer = UUID.randomUUID();
        find.setRecoveredBy(recoverer); find.setRecoveredAt(Instant.EPOCH);
        find.getPriorCells().add(new BlockCell(1, 2, 3));
        ItemStack stack = items.create(template, site, find, recoverer, "Good", true, catalogs);
        assertTrue(items.isRecovered(stack));
        assertTrue(items.isThisFind(stack, find.getId()));
        assertFalse(items.isThisFind(stack, UUID.randomUUID()));
        assertEquals(find.getId(), items.findIdOf(stack));
        assertEquals(site.getId(), items.siteIdOf(stack));
        assertEquals("Old quarry", items.siteNameOf(stack));
        assertEquals("Ancient pot", items.labelOf(stack));
        assertEquals(1, stack.getAmount()); assertEquals(1, stack.getItemMeta().getMaxStackSize());
        var pdc = stack.getItemMeta().getPersistentDataContainer();
        assertEquals(Integer.valueOf(3), pdc.get(key("find_number"), PersistentDataType.INTEGER));
        assertEquals(Integer.valueOf(70), pdc.get(key("conservation"), PersistentDataType.INTEGER));
        assertEquals(Integer.valueOf(80), pdc.get(key("buried_conservation"), PersistentDataType.INTEGER));
        assertEquals("Good", pdc.get(key("conservation_grade"), PersistentDataType.STRING));
        assertEquals(recoverer.toString(), pdc.get(key("recovered_by"), PersistentDataType.STRING));
        assertEquals(Instant.EPOCH.toString(), pdc.get(key("recovered_at"), PersistentDataType.STRING));
        assertEquals(Byte.valueOf((byte) 1), pdc.get(key("field_damaged"), PersistentDataType.BYTE));
        assertEquals(Byte.valueOf((byte) 1), pdc.get(key("disturbed_before_dig"), PersistentDataType.BYTE));
        List<String> lore = plain(stack.getItemMeta().getLore());
        assertTrue(lore.contains("#Old quarry-3"));
        assertTrue(lore.contains("Disturbed before the dig")); assertTrue(lore.contains("Hurt while digging"));
        assertFalse(lore.stream().anyMatch(line -> line.startsWith("Conservation:")));
    }

    @Test public void legacyItemSelectionIsStableAndUnavailableCustomItemsFallBackToBrick() {
        ItemStack first = items.baseStack(template, find);
        String chosen = find.getItem();
        assertNotNull(chosen);
        assertEquals(first.getType(), items.baseStack(template, find).getType());
        find.setItem(null);
        assertEquals(first.getType(), items.baseStack(template, find).getType());
        assertEquals(chosen, find.getItem());
        find.setItem("FLOWER_POT");
        assertEquals(Material.FLOWER_POT, items.baseStack(template, find).getType());
        assertEquals(Material.BRICK, items.baseStack(null, find).getType());
        assertNotNull(items.baseStack(template, null));
        ItemMatcher missingPack = mock(ItemMatcher.class);
        items.setMatcher(missingPack);
        assertEquals(Material.BRICK, items.baseStack(template, find).getType());
        when(missingPack.create(any())).thenReturn(new ItemStack(Material.AIR));
        assertEquals(Material.BRICK, items.baseStack(template, find).getType());
        when(missingPack.create(any())).thenReturn(new ItemStack(Material.DIAMOND, 12));
        ItemStack custom = items.baseStack(template, find);
        assertEquals(Material.DIAMOND, custom.getType()); assertEquals(1, custom.getAmount());
        items.setMatcher(null);
        assertEquals(Material.FLOWER_POT, items.baseStack(template, find).getType());
    }

    @Test public void cabinetProgressRevealsConditionThenRarityThenSignedStudy() {
        assertFalse(RecoveredFindItem.conditionKnown(find));
        List<String> initial = plain(items.lore(template, site, find, "Good", false, catalogs));
        assertTrue(initial.stream().anyMatch(line -> line.contains("(1/3)")));
        assertFalse(initial.stream().anyMatch(line -> line.startsWith("Material:")));
        find.setLabCleaned(true);
        assertTrue(RecoveredFindItem.conditionKnown(find));
        List<String> cleaned = plain(items.lore(template, site, find, "Good", false, catalogs));
        assertTrue(cleaned.contains("Material: Ceramic"));
        assertTrue(cleaned.contains("Conservation: 70% · Good"));
        assertTrue(cleaned.stream().anyMatch(line -> line.contains("(2/3)")));
        find.setFieldSketch(true);
        when(catalogs.nextOpenType(find)).thenReturn(new InterpretationType("function", "Function", "Purpose?", List.of()));
        List<String> sketched = plain(items.lore(template, site, find, "", false, catalogs));
        assertTrue(sketched.contains("Conservation: 70%")); assertTrue(sketched.contains("Rarity: EPIC"));
        assertTrue(sketched.stream().anyMatch(line -> line.contains("(3/3)")));
        var author = server.addPlayer("Historian");
        FindInterpretation reading = new FindInterpretation("function", "vessel", author.getUniqueId(), Instant.EPOCH);
        find.addInterpretation(reading); find.setStudied(true);
        when(catalogs.interpretation("vessel")).thenReturn(new InterpretationTemplate("vessel", "function", "Water vessel", Set.of(), Set.of(), Set.of()));
        when(catalogs.interpretationType("function")).thenReturn(new InterpretationType("function", "Function", "Purpose?", List.of()));
        when(catalogs.nextOpenType(find)).thenReturn(null);
        List<String> complete = plain(items.lore(template, site, find, null, false, catalogs));
        assertTrue(complete.contains("The record on this piece is complete."));
        assertTrue(complete.contains("According to Historian: Function: Water vessel"));
        assertTrue(complete.contains("A vessel shaped for carrying water"));
        assertTrue(complete.stream().anyMatch(line -> line.startsWith("  ")));
        find.setStudyNotes("Personal observation");
        List<String> personal = plain(items.lore(template, site, find, null, false, catalogs));
        assertTrue(personal.contains("Personal observation"));
        assertFalse(personal.contains("A vessel shaped for carrying water"));
    }

    @Test public void lostAndUnregisteredFindsDoNotInviteCabinetWork() {
        assertFalse(RecoveredFindItem.conditionKnown(null));
        assertNull(RecoveredFindItem.nextCabinetHint(null, null, null));
        find.setState(FindState.LOST); find.setFindNumber(0);
        assertTrue(RecoveredFindItem.conditionKnown(find));
        assertNull(RecoveredFindItem.nextCabinetHint(template, find, catalogs));
        List<String> lore = plain(items.lore(null, site, find, null, false, null));
        assertTrue(lore.contains("Lost in the cut")); assertTrue(lore.contains("Conservation: 70%"));
        assertFalse(lore.stream().anyMatch(line -> line.startsWith("#")));
        find.setState(FindState.RECOVERED);
        assertTrue(RecoveredFindItem.nextCabinetHint(null, find, null).contains("(1/3)"));
        find.setLabCleaned(true); find.setFieldSketch(true);
        assertTrue(RecoveredFindItem.nextCabinetHint(null, find, null).contains("(3/3)"));
        find.addInterpretation(new FindInterpretation("function", "vessel", null, Instant.EPOCH));
        assertTrue(RecoveredFindItem.nextCabinetHint(null, find, null).contains("complete"));
    }

    @Test public void damagedOrForeignTagsCannotMasqueradeAsRecoveredArchiveRows() {
        for (ItemStack ordinary : new ItemStack[] {null, new ItemStack(Material.AIR), new ItemStack(Material.BRICK)}) {
            assertFalse(items.isRecovered(ordinary)); assertFalse(items.isThisFind(ordinary, find.getId()));
            assertNull(items.findIdOf(ordinary)); assertNull(items.siteIdOf(ordinary)); assertNull(items.siteNameOf(ordinary));
            assertEquals("recovered find", items.labelOf(ordinary));
        }
        ItemStack stack = create();
        assertFalse(items.isThisFind(stack, null));
        for (String value : new String[] {"", " ", "not-a-uuid", null}) {
            ItemMeta meta = stack.getItemMeta();
            for (String field : List.of("find_id", "site_id")) {
                if (value == null) meta.getPersistentDataContainer().remove(key(field));
                else meta.getPersistentDataContainer().set(key(field), PersistentDataType.STRING, value);
            }
            stack.setItemMeta(meta);
            assertNull(items.findIdOf(stack)); assertNull(items.siteIdOf(stack)); assertFalse(items.isRecovered(stack));
        }
        ItemMeta meta = stack.getItemMeta(); meta.setDisplayName(ChatColor.RED + " "); stack.setItemMeta(meta);
        assertEquals("recovered find", items.labelOf(stack));
    }

    @Test public void refreshingCarriedCopiesIncludesCursorAndPreservesUnrelatedItems() {
        var player = server.addPlayer();
        ItemStack original = create();
        player.getInventory().setItem(0, original.clone());
        player.getInventory().setItem(8, original.clone());
        ItemStack unrelated = new ItemStack(Material.STONE, 23);
        player.getInventory().setItem(4, unrelated);
        player.getOpenInventory().setCursor(original.clone());
        assertTrue(items.isInMainHand(player, find.getId()));
        assertTrue(items.isCarrying(player, find.getId()));
        assertFalse(items.isCarrying(player, UUID.randomUUID()));
        find.setGivenName("Family urn"); find.setLabCleaned(true); find.setFieldSketch(true); find.setStudied(true);
        items.refreshCarried(player, site, find, template, "Good", catalogs);
        for (ItemStack copy : List.of(player.getInventory().getItem(0), player.getInventory().getItem(8), player.getOpenInventory().getCursor())) {
            assertEquals("Family urn", items.labelOf(copy));
            var pdc = copy.getItemMeta().getPersistentDataContainer();
            assertEquals(Byte.valueOf((byte) 1), pdc.get(key("studied"), PersistentDataType.BYTE));
            assertEquals(Byte.valueOf((byte) 1), pdc.get(key("lab_cleaned"), PersistentDataType.BYTE));
            assertEquals(Byte.valueOf((byte) 1), pdc.get(key("field_sketch"), PersistentDataType.BYTE));
            assertEquals(player.getUniqueId().toString(), pdc.get(key("recovered_by"), PersistentDataType.STRING));
        }
        assertEquals(unrelated, player.getInventory().getItem(4));
        // Someone else carrying the piece does not become its recoverer.
        UUID digger = UUID.randomUUID(); find.setRecoveredBy(digger);
        items.refreshCarried(player, site, find, template, "Good", catalogs);
        assertEquals(digger.toString(), player.getInventory().getItem(0).getItemMeta().getPersistentDataContainer().get(key("recovered_by"), PersistentDataType.STRING));
        player.getInventory().clear();
        assertTrue(items.isCarrying(player, find.getId()));
        assertFalse(items.isInMainHand(player, find.getId()));
        player.getOpenInventory().setCursor(null);
        assertFalse(items.isCarrying(player, find.getId()));
        assertFalse(items.isCarrying(null, find.getId())); assertFalse(items.isCarrying(player, null));
        assertFalse(items.isInMainHand(null, find.getId()));
    }

    @Test public void cabinetRefreshUsesArchivedRecovererAndRejectsOtherFinds() {
        ItemStack stack = create();
        ItemStack unrelated = new ItemStack(Material.BRICK);
        ItemMeta personal = unrelated.getItemMeta();
        personal.setDisplayName("Building brick"); personal.setLore(List.of("Keep for repairs")); unrelated.setItemMeta(personal);
        ItemStack before = unrelated.clone();
        UUID recoverer = UUID.randomUUID(); find.setRecoveredBy(recoverer); find.setGivenName("Urn");
        items.refresh(unrelated, site, find, template, "Good", catalogs);
        assertEquals(before, unrelated);
        items.refresh(stack, site, find, template, "Good", catalogs);
        assertEquals("Urn", items.labelOf(stack));
        assertEquals(recoverer.toString(), stack.getItemMeta().getPersistentDataContainer().get(key("recovered_by"), PersistentDataType.STRING));
        items.refresh(null, site, find, template, null, catalogs);
        items.refresh(stack, site, null, template, null, catalogs);
        assertEquals("Urn", items.labelOf(stack));
    }

    @Test public void retitlingUpdatesOnlyPiecesWhoseLiveSiteNameChanged() {
        SiteRepository sites = mock(SiteRepository.class);
        when(sites.findById(site.getId())).thenReturn(Optional.of(site));
        ItemStack stack = create();
        assertFalse(items.retitleIfStale(stack, sites, catalogs));
        site.setName("River camp");
        assertTrue(items.retitleIfStale(stack, sites, catalogs));
        assertEquals("River camp", items.siteNameOf(stack));
        assertTrue(plain(stack.getItemMeta().getLore()).contains("#River camp-3"));
        assertFalse(items.retitleIfStale(stack, sites, catalogs));
        assertFalse(items.retitleIfStale(stack, null, catalogs));
        when(sites.findById(site.getId())).thenReturn(Optional.empty());
        assertFalse(items.retitleIfStale(stack, sites, catalogs));
        Site other = new Site(); other.setId(UUID.randomUUID());
        assertFalse(items.retitle(stack, other, catalogs));
        assertFalse(items.retitle(stack, null, catalogs));
        site.getFinds().clear();
        assertFalse(items.retitle(stack, site, catalogs));
        site.getFinds().add(find);
        ItemMeta meta = stack.getItemMeta(); meta.getPersistentDataContainer().remove(key("site_name")); stack.setItemMeta(meta);
        when(sites.findById(site.getId())).thenReturn(Optional.of(site));
        assertTrue(items.retitleIfStale(stack, sites, null));
    }

    @Test public void cabinetStandInShowsProgressWithoutGrantingAnotherRecoveredItem() {
        find.setGivenName("Family urn");
        ItemStack standIn = items.standIn(template, site, find, catalogs);
        assertEquals("Family urn", items.labelOf(standIn));
        assertTrue(plain(standIn.getItemMeta().getLore()).contains("The real artifact stays in your hand."));
        assertFalse(items.isRecovered(standIn));
        assertNull(items.siteIdOf(standIn));
        assertEquals(Material.BRICK, items.standIn(null, site, find, null).getType());
    }

    @Test public void readingLabelsResolveLegacyTypesAndKeepUnknownArchivedPhrasesReadable() {
        when(catalogs.interpretation("vessel")).thenReturn(new InterpretationTemplate("vessel", "function", "Water vessel", Set.of(), Set.of(), Set.of()));
        when(catalogs.interpretationType("function")).thenReturn(new InterpretationType("function", "Function", "Purpose?", List.of()));
        assertEquals("Function: Water vessel", RecoveredFindItem.readingPhrase(new FindInterpretation(null, "vessel", null, null), catalogs));
        assertEquals("Function: Water vessel", RecoveredFindItem.readingPhrase(new FindInterpretation(" ", "vessel", null, null), catalogs));
        assertEquals("old-type: old-answer", RecoveredFindItem.readingPhrase(new FindInterpretation("old-type", "old-answer", null, null), catalogs));
        assertEquals("old-answer", RecoveredFindItem.readingPhrase(new FindInterpretation(null, "old-answer", null, null), null));
        assertEquals("", RecoveredFindItem.readingPhrase(null, catalogs));
    }

    @Test public void studiedFindKeepsItsDossierNoteAfterItsArtifactLeavesTheCatalog() {
        // Regression: lore once skipped every study note when the catalog row was gone.
        find.setState(FindState.RECOVERED); find.setStudied(true); find.setFieldSketch(true); find.setStudyNotes("Scratched owner's mark on the base");
        // CatalogRegistry.materialOf never returns null: an unknown or missing id gets a placeholder material.
        when(catalogs.materialOf(null)).thenReturn(new FindMaterial("unknown", "Unknown", 1, Material.WHITE_STAINED_GLASS_PANE, List.of()));
        List<String> orphan = plain(items.lore(null, site, find, null, false, catalogs));
        assertTrue(orphan.contains("Scratched owner's mark on the base"));
        assertFalse(orphan.stream().anyMatch(line -> line.startsWith("Rarity")));
        find.setStudyNotes(null);
        List<String> bare = plain(items.lore(null, site, find, null, false, catalogs));
        assertFalse(bare.stream().anyMatch(line -> line.startsWith("  ") || line.contains("vessel shaped")));
        // A hand-edited dossier may store a blank note; the catalog note then applies.
        find.setStudyNotes("  ");
        assertTrue(plain(items.lore(template, site, find, null, false, catalogs)).contains("A vessel shaped for carrying water"));
        ArtifactTemplate silent = new ArtifactTemplate("pot", "Ancient pot", 1, 3, "ceramic", null, false, 5, Set.of("I"), Set.of(), FindProfile.OBJECT,
                List.of(ItemRef.vanilla(Material.BRICK)), "");
        when(catalogs.rarityLoreLine(silent)).thenReturn("Rarity: COMMON");
        List<String> silentLore = plain(items.lore(silent, site, find, null, false, catalogs));
        // Blank dossier note and blank catalog note: only the rarity line differs from the bare lore.
        assertEquals(bare.size() + 1, silentLore.size()); assertTrue(silentLore.contains("Rarity: COMMON"));
    }

    @Test public void legacyReadingsWithoutAUsableTypeShowTheirStoredAnswer() {
        assertEquals("old-answer", RecoveredFindItem.readingPhrase(new FindInterpretation(null, "old-answer", null, null), catalogs));
        assertEquals("old-answer", RecoveredFindItem.readingPhrase(new FindInterpretation(" ", "old-answer", null, null), catalogs));
    }

    private ItemStack create() { return items.create(template, site, find, find.getRecoveredBy(), "Good", false, catalogs); }
    private NamespacedKey key(String id) { return new NamespacedKey(plugin, id); }
    private static List<String> plain(List<String> lines) { return lines.stream().map(ChatColor::stripColor).toList(); }
}
