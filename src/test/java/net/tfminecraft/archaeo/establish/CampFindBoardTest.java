package net.tfminecraft.archaeo.establish;

import net.tfminecraft.archaeo.config.*;
import net.tfminecraft.archaeo.model.*;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
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

public class CampFindBoardTest {
    private PlayerMock viewer;
    private Site site;
    private BuriedFind find;
    private CatalogRegistry catalogs;

    @Before public void setUp() {
        MockBukkit.mock(); viewer = MockBukkit.getMock().addPlayer("Curator");
        catalogs = mock(CatalogRegistry.class);
        when(catalogs.pick()).thenReturn(PickSettings.defaults());
        when(catalogs.materialOf(any())).thenReturn(new FindMaterial("ceramic", "Ceramic", 1, null, List.of()));
        when(catalogs.materialDisplayName("ceramic")).thenReturn("Ceramic");
        ArtifactTemplate pot = new ArtifactTemplate("pot", "Clay vessel", 1, 2, "ceramic", "rare", false, 1,
                Set.of(), Set.of(), FindProfile.OBJECT, List.of(), "");
        when(catalogs.artifact("pot")).thenReturn(pot);
        when(catalogs.rarityLoreLine(pot)).thenReturn("Rare");
        site = new Site(); site.setId(UUID.randomUUID()); site.setName("River camp");
        find = new BuriedFind(); find.setId(UUID.randomUUID()); find.setArtifactId("pot");
        find.setStratumId("II"); find.setState(FindState.RECOVERED); find.setFindNumber(7);
        find.setRecoveredBy(viewer.getUniqueId()); site.getFinds().add(find);
    }
    @After public void tearDown() { MockBukkit.unmock(); }

    @Test public void archiveRevealsConditionThenRarityAsCabinetWorkProgresses() {
        CampFindBoard board = new CampFindBoard(site.getId(), find.getId(), catalogs);
        board.open(viewer, site);
        assertSame(board, viewer.getOpenInventory().getTopInventory().getHolder());
        assertEquals("#River camp-7", viewer.getOpenInventory().getTitle());
        String identity = lore(board.getInventory(), CampFindBoard.SLOT_IDENTITY);
        assertTrue(identity.contains("(1/3)")); assertFalse(identity.contains("Material:")); assertFalse(identity.contains("Rare"));
        assertTrue(lore(board.getInventory(), CampFindBoard.SLOT_CONDITION).contains("Clean the piece"));
        String origin = lore(board.getInventory(), CampFindBoard.SLOT_PROVENIENCE);
        assertTrue(origin.contains("River camp")); assertTrue(origin.contains("II")); assertTrue(origin.contains("Curator"));
        assertEquals(Material.WRITABLE_BOOK, board.getInventory().getItem(CampFindBoard.SLOT_READINGS).getType());
        find.setLabCleaned(true); board.open(viewer, site);
        identity = lore(board.getInventory(), CampFindBoard.SLOT_IDENTITY);
        assertTrue(identity.contains("Material: Ceramic")); assertTrue(identity.contains("(2/3)")); assertFalse(identity.contains("Rare"));
        assertTrue(lore(board.getInventory(), CampFindBoard.SLOT_CONDITION).contains("Conservation:"));
        find.setFieldSketch(true); board.open(viewer, site);
        assertTrue(lore(board.getInventory(), CampFindBoard.SLOT_IDENTITY).contains("Rare"));
    }

    @Test public void museumPlaqueShowsSignedReadingsAndClosesWithoutCabinetInstructions() {
        find.setGivenName("River offering"); find.setLabCleaned(true); find.setFieldSketch(true);
        find.addInterpretation(new FindInterpretation("function", "vessel", viewer.getUniqueId(), Instant.now()));
        when(catalogs.interpretation("vessel")).thenReturn(new InterpretationTemplate("vessel", "function", "Storage vessel", Set.of(), Set.of(), Set.of()));
        CampFindBoard board = new CampFindBoard(site.getId(), find.getId(), catalogs, true); board.open(viewer, site);
        assertTrue(board.isMuseum());
        assertEquals("River offering", name(board.getInventory(), CampFindBoard.SLOT_IDENTITY));
        assertFalse(lore(board.getInventory(), CampFindBoard.SLOT_IDENTITY).contains("cabinet"));
        assertEquals(Material.WRITTEN_BOOK, board.getInventory().getItem(CampFindBoard.SLOT_READINGS).getType());
        assertTrue(lore(board.getInventory(), CampFindBoard.SLOT_READINGS).contains("Storage vessel"));
        assertTrue(lore(board.getInventory(), CampFindBoard.SLOT_READINGS).contains("Curator"));
        assertEquals("Close", name(board.getInventory(), CampFindBoard.SLOT_BACK));
    }

    @Test public void lostPiecesRetainSeparatePriorDisturbanceAndExcavationDamageEvidence() {
        BlockCell cell = new BlockCell(1, 2, 3); BlockCell struck = new BlockCell(2, 2, 3);
        find.getCells().addAll(List.of(cell, struck));
        find.woundBeforeDig(cell); find.woundDirect(struck); find.setState(FindState.LOST);
        CampFindBoard board = new CampFindBoard(site.getId(), find.getId(), catalogs); board.open(viewer, site);
        String condition = lore(board.getInventory(), CampFindBoard.SLOT_CONDITION);
        assertTrue(condition.contains("Conservation:")); assertTrue(condition.contains("Disturbed before the dig"));
        assertTrue(condition.contains("Hurt while digging")); assertTrue(condition.contains("Nothing could be recovered"));
        assertFalse(lore(board.getInventory(), CampFindBoard.SLOT_IDENTITY).contains("cabinet"));
    }

    @Test public void removedCatalogAndLongSiteNameStillProduceReadableArchiveAndDeletedFindClosesIt() {
        when(catalogs.artifact("pot")).thenReturn(null); site.setName("Long excavation name ".repeat(4));
        CampFindBoard board = new CampFindBoard(site.getId(), find.getId(), catalogs); board.open(viewer, site);
        assertEquals(32, viewer.getOpenInventory().getTitle().length());
        assertFalse(name(board.getInventory(), CampFindBoard.SLOT_IDENTITY).isBlank());
        assertTrue(lore(board.getInventory(), CampFindBoard.SLOT_PROVENIENCE).contains(site.getName()));
        site.getFinds().clear(); board.open(viewer, site);
        assertNotSame(board.getInventory(), viewer.getOpenInventory().getTopInventory());
    }

    @Test public void sketchedPiecesOfARetiredCatalogEntryAndDestroyedPiecesShowOnlyWhatIsKnown() {
        when(catalogs.artifact("pot")).thenReturn(null); find.setFieldSketch(true);
        CampFindBoard board = new CampFindBoard(site.getId(), find.getId(), catalogs); board.open(viewer, site);
        assertFalse(lore(board.getInventory(), CampFindBoard.SLOT_IDENTITY).contains("Rare")); verify(catalogs, never()).rarityLoreLine(any(ArtifactTemplate.class));
        find.setConservation(0); find.setState(FindState.LOST); board.open(viewer, site);
        String condition = lore(board.getInventory(), CampFindBoard.SLOT_CONDITION);
        assertTrue(condition, condition.startsWith("Conservation: 0%\n")); assertFalse(condition.contains("·"));
    }

    private static String name(Inventory inv, int slot) { return ChatColor.stripColor(inv.getItem(slot).getItemMeta().getDisplayName()); }
    private static String lore(Inventory inv, int slot) { return ChatColor.stripColor(String.join("\n", inv.getItem(slot).getItemMeta().getLore())); }
}
