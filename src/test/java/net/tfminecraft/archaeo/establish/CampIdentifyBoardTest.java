package net.tfminecraft.archaeo.establish;

import net.tfminecraft.archaeo.config.*;
import net.tfminecraft.archaeo.item.RecoveredFindItem;
import net.tfminecraft.archaeo.model.*;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CampIdentifyBoardTest {
    private PlayerMock player;
    private Site site;
    private BuriedFind find;
    private CatalogRegistry catalogs;
    private RecoveredFindItem recovered;
    private InterpretationType question;

    @Before public void setUp() {
        MockBukkit.mock(); player = MockBukkit.getMock().addPlayer();
        recovered = new RecoveredFindItem(MockBukkit.createMockPlugin());
        catalogs = mock(CatalogRegistry.class); when(catalogs.pick()).thenReturn(PickSettings.defaults());
        ArtifactTemplate pot = new ArtifactTemplate("pot", "Clay vessel", 1, 1, "ceramic", "", false, 1,
                Set.of(), Set.of("clay"), FindProfile.OBJECT, List.of(), "");
        when(catalogs.artifact("pot")).thenReturn(pot);
        when(catalogs.materialDisplayName("ceramic")).thenReturn("Ceramic");
        when(catalogs.rarityLoreLine(pot)).thenReturn("Common");
        site = new Site(); site.setId(UUID.randomUUID()); site.setName("River camp");
        find = new BuriedFind(); find.setId(UUID.randomUUID()); find.setArtifactId("pot"); find.setStratumId("I");
        find.setState(FindState.RECOVERED); find.setLabCleaned(true); find.setFieldSketch(true); site.getFinds().add(find);
        question = new InterpretationType("function", "Function", "What purpose did this ancient object serve?", List.of());
        when(catalogs.nextOpenType(find)).thenReturn(question);
    }
    @After public void tearDown() { MockBukkit.unmock(); }

    @Test public void stationCombinesEvidenceTagsAndMapsVisibleOffersWithoutTouchingRealItem() {
        site.getHintIds().add("river"); site.getHintIds().add("removed");
        when(catalogs.hint("river")).thenReturn(new HintTemplate("river", "River bank", 1, Set.of("water"),
                Set.of(), Set.of(), Set.of(), null, null, null, null, null));
        List<InterpretationTemplate> offers = List.of(option("vessel", "Storage vessel"), option("ritual", "Ritual offering"), option("trade", "Trade ware"));
        when(catalogs.stationOffers("function", find.getId(), "pot", Set.of("clay", "water"))).thenReturn(offers);
        ItemStack held = new ItemStack(Material.BRICK, 2); player.getInventory().setItemInMainHand(held);
        CampIdentifyBoard board = new CampIdentifyBoard(site.getId(), find.getId(), catalogs, recovered, true);
        assertTrue(board.open(player, site));
        assertSame(board.getInventory(), player.getOpenInventory().getTopInventory());
        assertEquals(InventoryType.BREWING, board.getInventory().getType());
        assertEquals(question.question().substring(0, 32), player.getOpenInventory().getTitle());
        assertEquals(question, board.type());
        for (int i = 0; i < offers.size(); i++) {
            assertEquals(offers.get(i).id(), board.offerAt(i));
            assertEquals(offers.get(i).displayName(), ChatColor.stripColor(board.getInventory().getItem(i).getItemMeta().getDisplayName()));
        }
        assertNull(board.offerAt(CampIdentifyBoard.SLOT_PIECE)); assertNull(board.offerAt(CampIdentifyBoard.SLOT_BACK));
        assertNull(recovered.findIdOf(board.getInventory().getItem(CampIdentifyBoard.SLOT_PIECE)));
        assertTrue(String.join(" ", board.getInventory().getItem(CampIdentifyBoard.SLOT_PIECE).getItemMeta().getLore()).contains("real artifact stays"));
        assertEquals(held, player.getInventory().getItemInMainHand()); assertTrue(find.getInterpretations().isEmpty());
    }

    @Test public void reopeningAfterCatalogReloadClearsOldOfferClickTargets() {
        when(catalogs.stationOffers(anyString(), any(), anyString(), anySet())).thenReturn(List.of(option("a", "A"), option("b", "B"), option("c", "C")));
        CampIdentifyBoard board = new CampIdentifyBoard(site.getId(), find.getId(), catalogs, recovered);
        assertTrue(board.open(player, site)); assertEquals("c", board.offerAt(2));
        when(catalogs.artifact("pot")).thenReturn(null);
        when(catalogs.stationOffers(anyString(), any(), anyString(), anySet())).thenReturn(List.of(option("new", "New reading")));
        assertTrue(board.open(player, site));
        assertEquals("new", board.offerAt(0)); assertNull(board.offerAt(1)); assertNull(board.offerAt(2));
        assertNull(board.getInventory().getItem(1)); assertNull(board.getInventory().getItem(2));
        verify(catalogs).stationOffers("function", find.getId(), "pot", Set.of());
    }

    @Test public void completedRecordReturnsToArchiveOrClosesCabinet() {
        when(catalogs.nextOpenType(find)).thenReturn(null);
        CampIdentifyBoard archive = new CampIdentifyBoard(site.getId(), find.getId(), catalogs, recovered);
        assertFalse(archive.open(player, site));
        assertTrue(player.getOpenInventory().getTopInventory().getHolder() instanceof CampFindBoard);
        assertTrue(ChatColor.stripColor(player.nextMessage()).contains("record on this piece is complete"));
        var archiveInventory = player.getOpenInventory().getTopInventory();
        CampIdentifyBoard cabinet = new CampIdentifyBoard(site.getId(), find.getId(), catalogs, recovered, true);
        assertFalse(cabinet.open(player, site));
        assertNotSame(archiveInventory, player.getOpenInventory().getTopInventory());
    }

    @Test public void deletedFindClosesExistingStation() {
        when(catalogs.stationOffers(anyString(), any(), anyString(), anySet())).thenReturn(List.of(option("a", "A")));
        CampIdentifyBoard board = new CampIdentifyBoard(site.getId(), find.getId(), catalogs, recovered);
        assertTrue(board.open(player, site)); site.getFinds().clear(); assertFalse(board.open(player, site));
        assertNotSame(board.getInventory(), player.getOpenInventory().getTopInventory());
    }

    private static InterpretationTemplate option(String id, String label) {
        return new InterpretationTemplate(id, "function", label, Set.of(), Set.of(), Set.of());
    }
}
