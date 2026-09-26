package net.tfminecraft.archaeo.sketch;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.*;
import net.tfminecraft.archaeo.config.*;
import net.tfminecraft.archaeo.item.*;
import net.tfminecraft.archaeo.model.*;
import net.tfminecraft.archaeo.site.SiteRepository;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

public class CabinetLabTest {
    private ServerMock server;
    private JavaPlugin plugin;
    private PlayerMock player;
    private CabinetLab lab;
    private SiteRepository sites;
    private CatalogRegistry catalogs;
    private RecoveredFindItem recovered;
    private ArtifactTemplate template;
    private Site site;
    private BuriedFind find;
    private ItemStack piece;

    @Before public void setUp() {
        server = MockBukkit.mock(); plugin = MockBukkit.createMockPlugin(); player = server.addPlayer();
        sites = mock(SiteRepository.class); catalogs = mock(CatalogRegistry.class); recovered = new RecoveredFindItem(plugin);
        when(catalogs.pick()).thenReturn(PickSettings.defaults());
        template = new ArtifactTemplate("pot", "Old pot", 1, 1, "ceramic", null, false, 1, Set.of(), Set.of(), FindProfile.OBJECT, List.of(ItemRef.vanilla(Material.BRICK)), "");
        when(catalogs.artifact("pot")).thenReturn(template);
        when(catalogs.materialDisplayName("ceramic")).thenReturn("Ceramic");
        when(catalogs.materialOf("ceramic")).thenReturn(new FindMaterial("ceramic", "Ceramic", 1, Material.WHITE_STAINED_GLASS_PANE, List.of("soil")));
        site = new Site(); site.setId(UUID.randomUUID()); site.setName("Quarry");
        find = new BuriedFind(); find.setId(UUID.randomUUID()); find.setArtifactId("pot"); find.setStratumId("I"); find.setState(FindState.RECOVERED); find.setFindNumber(1);
        find.setBuriedConservation(77); find.setConservation(77); find.setRecoveredBy(player.getUniqueId()); find.setRecoveredAt(Instant.EPOCH);
        site.getFinds().add(find); when(sites.findById(site.getId())).thenReturn(Optional.of(site));
        piece = recovered.create(template, site, find, player.getUniqueId(), "Good", false, catalogs); player.getInventory().setItemInMainHand(piece);
        lab = new CabinetLab(plugin, sites, catalogs, recovered, SketchSettings.defaults());
        // Match SketchListener's close forwarding so cancellation exercises real event cleanup.
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler public void close(InventoryCloseEvent event) {
                if (event.getPlayer() instanceof org.bukkit.entity.Player worker && event.getInventory().getHolder() instanceof CabinetLabBoard board) lab.handleClose(worker, board);
            }
        }, plugin);
    }
    @After public void tearDown() { lab.stop(); MockBukkit.unmock(); }

    @Test public void opensOnlyUncleanedRecoveredArchivePiecesAndDoesNotRestartAnOpenWipe() {
        assertTrue(lab.tryStart(player, null, site, find)); CabinetLabBoard board = board();
        assertEquals(site.getId(), board.siteId()); assertEquals(find.getId(), board.findId());
        assertEquals(6, board.dirtyLeft()); assertEquals(player.getLocation(), board.cabinet());
        lab.handleClick(player, board, dirty(board), board.copyTool(22));
        assertEquals(5, board.dirtyLeft());
        assertTrue(lab.tryStart(player, null, site, find)); assertSame(board, board()); assertEquals(5, board.dirtyLeft());
        lab.cancel(player);
        for (FindState state : List.of(FindState.HIDDEN, FindState.LOST)) { find.setState(state); assertFalse(lab.tryStart(player, null, site, find)); }
        find.setState(FindState.RECOVERED); find.setLabCleaned(true); assertFalse(lab.tryStart(player, null, site, find));
        find.setLabCleaned(false); find.addInterpretation(new FindInterpretation("function", "pot", null, Instant.EPOCH));
        assertFalse(lab.tryStart(player, null, site, find));
    }

    @Test public void toolRackTogglesCursorCopiesWithoutConsumingRealToolsOrAwardingProgress() {
        start(); CabinetLabBoard board = board(); int dirty = board.dirtyLeft();
        ItemStack brush = lab.handleClick(player, board, 22, new ItemStack(Material.AIR));
        assertTrue(board.isTool(brush)); assertEquals("brush", board.toolId(brush));
        assertNotSame(board.getInventory().getItem(22), brush);
        assertTrue(lab.handleClick(player, board, 22, brush).getType().isAir());
        ItemStack water = lab.handleClick(player, board, 21, brush); assertEquals("water", board.toolId(water));
        assertEquals(dirty, board.dirtyLeft()); assertEquals(1, board.getInventory().getItem(22).getAmount());
        assertEquals(piece, player.getInventory().getItemInMainHand()); verify(sites, never()).save(any());
    }

    @Test public void wrongToolDoesNotDamageFindOrCleanAndWarnsOnlyOncePerWindow() {
        start(); CabinetLabBoard board = board(); int slot = dirty(board); int count = board.dirtyLeft(); messages();
        ItemStack realBrush = new ItemStack(Material.BRUSH);
        assertSame(realBrush, lab.handleClick(player, board, slot, realBrush));
        assertTrue(messages().stream().anyMatch(line -> line.contains("Pick a tool")));
        ItemStack water = board.copyTool(21); lab.handleClick(player, board, slot, water); lab.handleClick(player, board, slot, water);
        assertEquals(1, messages().stream().filter(line -> line.contains("That tool is not for")).count());
        assertTrue(board.warnedWrongTool()); assertEquals(count, board.dirtyLeft()); assertEquals(77, find.getConservation());
        assertFalse(find.isLabCleaned()); verify(sites, never()).save(any());
    }

    @Test public void cleaningEveryStainPersistsOnceRefreshesRealArtifactAndClosesNextTick() {
        start(); CabinetLabBoard board = board(); ItemStack brush = board.copyTool(22); int initial = board.dirtyLeft();
        for (int i = 0; i < initial - 1; i++) lab.handleClick(player, board, dirty(board), brush);
        assertEquals(1, board.dirtyLeft()); assertFalse(find.isLabCleaned()); verify(sites, never()).save(any());
        lab.handleClick(player, board, dirty(board), brush);
        assertEquals(0, board.dirtyLeft()); assertTrue(board.finished()); assertTrue(find.isLabCleaned());
        assertFalse(find.hasFieldSketch()); assertFalse(find.isStudied()); assertEquals(77, find.getConservation());
        verify(sites).save(site);
        ItemStack refreshed = player.getInventory().getItemInMainHand(); assertEquals(find.getId(), recovered.findIdOf(refreshed));
        assertEquals(Byte.valueOf((byte) 1), refreshed.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, "lab_cleaned"), PersistentDataType.BYTE));
        assertTrue(refreshed.getItemMeta().getLore().stream().map(ChatColor::stripColor).anyMatch(line -> line.startsWith("Conservation: 77%")));
        assertTrue(messages().stream().anyMatch(line -> line.contains("(2/3)")));
        lab.handleClick(player, board, 22, brush); verify(sites, times(1)).save(site);
        server.getScheduler().performOneTick(); assertNotSame(board.getInventory(), player.getOpenInventory().getTopInventory());
        assertEquals(1, player.getInventory().getItemInMainHand().getAmount());
    }

    @Test public void cancellationDiscardsProgressAndStripsFakeStacksImmediatelyAndNextTick() {
        start(); CabinetLabBoard board = board(); lab.handleClick(player, board, dirty(board), board.copyTool(22));
        ItemStack tool = board.copyTool(22); ItemStack pane = board.getInventory().getItem(0).clone();
        player.getInventory().setItem(5, tool); player.getInventory().setItemInOffHand(pane); player.setItemOnCursor(tool.clone());
        player.getInventory().setItem(6, new ItemStack(Material.BRUSH, 2));
        assertTrue(lab.isLabItem(tool)); assertTrue(lab.isLabItem(pane)); assertFalse(lab.isLabItem(player.getInventory().getItem(6)));
        lab.cancel(player);
        assertFalse(find.isLabCleaned()); verify(sites, never()).save(any());
        assertEmpty(player.getInventory().getItem(5)); assertEmpty(player.getInventory().getItemInOffHand()); assertEmpty(player.getItemOnCursor());
        assertEquals(2, player.getInventory().getItem(6).getAmount()); assertEquals(find.getId(), recovered.findIdOf(player.getInventory().getItemInMainHand()));
        player.setItemOnCursor(tool.clone()); server.getScheduler().performOneTick(); assertEmpty(player.getItemOnCursor());
        assertTrue(lab.tryStart(player, null, site, find)); assertEquals(6, board().dirtyLeft());
    }

    @Test public void materialSpecificWaterCleaningAndReloadedDirtBudgetTakeEffectOnNewWindows() {
        when(catalogs.materialOf("ceramic")).thenReturn(new FindMaterial("ceramic", "Ceramic", 1, Material.WHITE_STAINED_GLASS_PANE, List.of("limescale")));
        LabSettings defaults = LabSettings.defaults();
        lab.setSettings(new SketchSettings(4, ItemRef.vanilla(Material.CARTOGRAPHY_TABLE), new LabSettings(1, defaults.tools(), defaults.stains())));
        Block cabinet = player.getWorld().getBlockAt(3, 65, 4); cabinet.setType(Material.CARTOGRAPHY_TABLE);
        assertTrue(lab.tryStart(player, cabinet, site, find)); CabinetLabBoard board = board();
        assertEquals(cabinet.getLocation().add(.5, 1.05, .5), board.cabinet()); assertEquals(1, board.dirtyLeft());
        lab.handleClick(player, board, dirty(board), board.copyTool(21));
        assertTrue(find.isLabCleaned()); verify(sites).save(site);
    }

    @Test public void staleArchiveAtCompletionDoesNotResurrectDeletedSite() {
        start(); CabinetLabBoard board = board(); when(sites.findById(site.getId())).thenReturn(Optional.empty());
        ItemStack brush = board.copyTool(22);
        while (board.dirtyLeft() > 0) lab.handleClick(player, board, dirty(board), brush);
        assertTrue(board.finished()); assertFalse(find.isLabCleaned()); verify(sites, never()).save(any());
        server.getScheduler().performOneTick(); assertNotSame(board.getInventory(), player.getOpenInventory().getTopInventory());
    }

    @Test public void stoppingClosesActiveWipesAndCleansTemporaryCursorItems() {
        start(); CabinetLabBoard board = board(); player.setItemOnCursor(board.copyTool(22));
        lab.stop(); assertNotSame(board.getInventory(), player.getOpenInventory().getTopInventory());
        assertEmpty(player.getItemOnCursor()); assertFalse(find.isLabCleaned());
        assertTrue(lab.tryStart(player, null, site, find)); assertNotSame(board, board());
    }

    @Test public void clicksOnEmptyRackCellsAndCleanPanesChangeNothingAndStaySilent() {
        start(); CabinetLabBoard board = board(); int dirty = board.dirtyLeft(); messages();
        int clean = -1; for (int slot = 0; slot < LabSettings.FIELD_SLOTS && clean < 0; slot++) if (!board.isDirty(slot)) clean = slot;
        ItemStack air = new ItemStack(Material.AIR); ItemStack brush = board.copyTool(22);
        assertSame(air, lab.handleClick(player, board, 18, air)); assertSame(brush, lab.handleClick(player, board, 26, brush));
        assertSame(air, lab.handleClick(player, board, clean, air)); assertSame(brush, lab.handleClick(player, board, clean, brush));
        assertEquals(dirty, board.dirtyLeft()); assertTrue(messages().isEmpty()); verify(sites, never()).save(any());
    }

    @Test public void pieceWhoseArtifactWasRemovedFromConfigCanStillBeCleaned() {
        when(catalogs.artifact("pot")).thenReturn(null);
        when(catalogs.materialOf(null)).thenReturn(new FindMaterial("unknown", "Unknown", 1, Material.WHITE_STAINED_GLASS_PANE, List.of()));
        start(); CabinetLabBoard board = board(); assertEquals("unknown", board.material().id());
        ItemStack water = board.copyTool(21);
        while (board.dirtyLeft() > 0) lab.handleClick(player, board, dirty(board), water);
        assertTrue(find.isLabCleaned()); verify(sites).save(site);
    }

    @Test public void finishingTheWipeThenDisconnectingKeepsTheCleanOnFile() {
        start(); CabinetLabBoard board = board(); ItemStack brush = board.copyTool(22);
        while (board.dirtyLeft() > 0) lab.handleClick(player, board, dirty(board), brush);
        // Paper closes the open window as part of disconnecting, before the quit completes.
        player.closeInventory(); player.disconnect(); server.getScheduler().performOneTick();
        assertTrue(find.isLabCleaned()); verify(sites).save(site); assertFalse(player.isOnline());
    }

    @Test public void labWindowBlockedByAnotherPluginCanBeOpenedOnceTheBlockLifts() {
        // Region protection, combat tags and the like cancel inventory opens they do not allow; Paper's
        // openInventory then returns null. MockBukkit swaps in a fresh view first and returns that instead.
        PlayerMock blocked = spy(player); doReturn(null).when(blocked).openInventory(any(org.bukkit.inventory.Inventory.class));
        messages(); assertTrue(lab.tryStart(blocked, null, site, find));
        assertEquals(List.of(), messages());
        start();
        assertEquals(find.getId(), board().findId()); assertTrue(messages().stream().anyMatch(line -> line.contains("Pick a tool")));
    }

    private void start() { assertTrue(lab.tryStart(player, null, site, find)); }
    private CabinetLabBoard board() { return (CabinetLabBoard) player.getOpenInventory().getTopInventory().getHolder(); }
    private static int dirty(CabinetLabBoard board) { for (int slot = 0; slot < LabSettings.FIELD_SLOTS; slot++) if (board.isDirty(slot)) return slot; throw new AssertionError("No dirty cells"); }
    private static void assertEmpty(ItemStack stack) { assertTrue(stack == null || stack.getType().isAir()); }
    private List<String> messages() { List<String> result = new ArrayList<>(); String message; while ((message = player.nextMessage()) != null) result.add(ChatColor.stripColor(message)); return result; }
}
