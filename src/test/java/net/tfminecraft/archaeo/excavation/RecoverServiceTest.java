package net.tfminecraft.archaeo.excavation;

import net.tfminecraft.archaeo.config.*;
import net.tfminecraft.archaeo.events.FindRecoveredEvent;
import net.tfminecraft.archaeo.item.BrushItem;
import net.tfminecraft.archaeo.item.RecoveredFindItem;
import net.tfminecraft.archaeo.model.*;
import net.tfminecraft.archaeo.site.SiteRepository;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.block.BlockMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class RecoverServiceTest {
    private RecordingServer server;
    private World world;
    private SiteRepository sites;
    private CatalogRegistry catalogs;
    private RecoveredFindItem recoveredItems;
    private RecoverService service;
    private Player player;
    private UUID playerId;
    private PlayerInventory inventory;
    private ItemStack held;
    private Site site;
    private BuriedFind find;
    private Block target;
    private ArtifactTemplate template;
    private final List<FindRecoveredEvent> events = new ArrayList<>();

    @Before
    public void setup() {
        server = MockBukkit.mock(new RecordingServer());
        WorldMock testWorld = new SnapshotLocationWorld();
        testWorld.setName("recovery");
        server.addWorld(testWorld);
        world = testWorld;
        JavaPlugin plugin = MockBukkit.createMockPlugin();
        sites = mock(SiteRepository.class);
        catalogs = mock(CatalogRegistry.class);
        BrushItem brush = mock(BrushItem.class);
        recoveredItems = mock(RecoveredFindItem.class);
        player = mock(Player.class);
        inventory = mock(PlayerInventory.class);
        held = new ItemStack(Material.BRUSH);
        playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenAnswer(invocation -> held);
        when(player.getItemInUse()).thenAnswer(invocation -> held);
        when(brush.isBrush(any())).thenAnswer(invocation -> {
            ItemStack stack = invocation.getArgument(0);
            return stack != null && stack.getType() == Material.BRUSH;
        });
        site = new Site();
        site.setId(UUID.randomUUID());
        site.setName("Dig");
        site.setWorldName(world.getName());
        site.setStatus(SiteStatus.ESTABLISHED);
        site.setDirector(player.getUniqueId());
        StratumBand band = new StratumBand();
        band.setPresent(true);
        band.setMinY(39);
        band.setMaxY(41);
        site.getStrata().put("I", band);
        find = new BuriedFind();
        find.setId(UUID.randomUUID());
        find.setArtifactId("pot");
        find.setState(FindState.DISCOVERED);
        for (int x = 8; x <= 10; x++) {
            find.getCells().add(new BlockCell(x, 40, 8));
            world.getBlockAt(x, 40, 8).setType(Material.STONE);
        }
        site.getFinds().add(find);
        target = world.getBlockAt(8, 40, 8);
        when(player.getTargetBlockExact(6)).thenAnswer(invocation -> target);
        when(sites.findEstablishedPrism(anyString(), anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> {
            boolean matches = site.getStatus() == SiteStatus.ESTABLISHED
                    && site.getWorldName().equals(invocation.getArgument(0))
                    && site.isInPrism(invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3));
            return matches ? Optional.of(site) : Optional.empty();
        });
        when(sites.findById(site.getId())).thenReturn(Optional.of(site));
        when(catalogs.toolWear()).thenReturn(new ToolWearSettings(1, 1, false));
        when(catalogs.pick()).thenReturn(PickSettings.defaults());
        template = new ArtifactTemplate("pot", "Clay pot", 1, 3, "ceramic", null, false,
                1, Set.of(), Set.of(), null, List.of(), null);
        when(catalogs.artifact("pot")).thenReturn(template);
        when(recoveredItems.create(eq(template), eq(site), eq(find), eq(playerId),
                anyString(), anyBoolean(), eq(catalogs))).thenReturn(new ItemStack(Material.BRICK));
        service = new RecoverService(plugin, sites, catalogs, brush, recoveredItems,
                new RecoverySettings(true, 8, 2, false));
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onRecovered(FindRecoveredEvent event) {
                events.add(event);
            }
        }, plugin);
    }

    @After
    public void teardown() {
        if (service != null) service.stop();
        MockBukkit.unmock();
    }

    @Test
    public void disabledRecoveryWrongToolAndUnestablishedSitesCannotStart() {
        service.setSettings(new RecoverySettings(false, 8, 2, false));
        service.begin(player, target);
        service.watch(player);
        assertTrue(find.getBrushRemaining().isEmpty());
        service.setSettings(new RecoverySettings(true, 8, 2, false));
        held = new ItemStack(Material.STICK);
        service.begin(player, target);
        service.watch(player);
        assertTrue(find.getBrushRemaining().isEmpty());
        held = new ItemStack(Material.BRUSH);
        site.setStatus(SiteStatus.HIDDEN);
        service.begin(player, target);
        ticks(10);
        assertTrue(find.getBrushRemaining().isEmpty());
        assertTrue(find.getCleanedCells().isEmpty());
        verify(sites, never()).save(any());
    }

    @Test
    public void trespasserAndExcavatorReceiveDistinctPermissionRefusals() {
        site.setDirector(UUID.randomUUID());
        service.begin(player, target);
        service.begin(player, target);
        verify(player, times(1)).sendMessage("You are not authorised to work on this excavation.");
        assertTrue(find.getBrushRemaining().isEmpty());
        Player worker = mock(Player.class);
        when(worker.getUniqueId()).thenReturn(UUID.randomUUID());
        when(worker.getInventory()).thenReturn(inventory);
        site.getExcavators().add(worker.getUniqueId());
        site.assignRole(worker.getUniqueId(), SiteRole.EXCAVATOR);
        service.begin(worker, target);
        verify(worker).sendMessage("Your role does not lift pieces on this excavation.");
        assertTrue(find.getBrushRemaining().isEmpty());
    }

    @Test
    public void hiddenTerminalCleanedAndMissingFillCellsCannotBeBrushed() {
        find.setState(FindState.HIDDEN);
        service.begin(player, target);
        verify(player).sendMessage("The shape is not fully free.");
        for (FindState state : List.of(FindState.LOST, FindState.RECOVERED)) {
            find.setState(state);
            service.begin(player, target);
        }
        find.setState(FindState.DISCOVERED);
        target.setType(Material.AIR);
        service.begin(player, target);
        target.setType(Material.STONE);
        find.markCleaned(cell(8));
        service.begin(player, target);
        service.begin(player, world.getBlockAt(12, 40, 8));
        assertTrue(find.getBrushRemaining().isEmpty());
        verify(sites, never()).save(any());
    }

    @Test
    public void watchingAndLookingAtFreshCubeDoesNotStartCleaning() {
        service.watch(player);
        service.watch(player);
        ticks(12);
        assertTrue(find.getBrushRemaining().isEmpty());
        assertTrue(find.getCleanedCells().isEmpty());
        verify(sites, never()).save(any());
    }

    @Test
    public void repeatedClickDoesNotResetProgressAndCancelResumesStoredTicks() {
        service.begin(player, target);
        ticks(2);
        assertEquals(Integer.valueOf(6), find.brushRemaining(cell(8)));
        service.begin(player, target);
        assertEquals(Integer.valueOf(6), find.brushRemaining(cell(8)));
        service.cancel(player);
        verify(sites).touch(site);
        ticks(5);
        assertEquals(Integer.valueOf(6), find.brushRemaining(cell(8)));
        service.watch(player);
        ticks(2);
        assertEquals(Integer.valueOf(4), find.brushRemaining(cell(8)));
    }

    @Test
    public void lookingAwayAndChangingCellsPreserveEachCubesIndependentProgress() {
        service.begin(player, target);
        ticks(2);
        target = null;
        ticks(4);
        assertEquals(Integer.valueOf(6), find.brushRemaining(cell(8)));
        target = world.getBlockAt(9, 40, 8);
        service.begin(player, target);
        ticks(3);
        assertEquals(Integer.valueOf(5), find.brushRemaining(cell(9)));
        target = world.getBlockAt(8, 40, 8);
        ticks(1);
        assertEquals(Integer.valueOf(5), find.brushRemaining(cell(8)));
        assertEquals(Integer.valueOf(5), find.brushRemaining(cell(9)));
        assertTrue(find.getCleanedCells().isEmpty());
    }

    @Test
    public void clickGraceExpiresWithoutActiveUseAndLegacyUseTicksResumeIt() {
        when(player.getItemInUse()).thenReturn(null);
        service.begin(player, target);
        ticks(6);
        assertEquals(Integer.valueOf(5), find.brushRemaining(cell(8)));
        when(player.getItemInUseTicks()).thenReturn(1);
        ticks(2);
        assertEquals(Integer.valueOf(3), find.brushRemaining(cell(8)));
    }

    @Test
    public void switchingToolsDisconnectingAndStoppingParkProgress() {
        service.begin(player, target);
        ticks(2);
        held = new ItemStack(Material.STICK);
        ticks(1);
        assertEquals(Integer.valueOf(6), find.brushRemaining(cell(8)));
        held = new ItemStack(Material.BRUSH);
        ticks(3);
        assertEquals(Integer.valueOf(6), find.brushRemaining(cell(8)));
        service.watch(player);
        ticks(1);
        when(player.isOnline()).thenReturn(false);
        ticks(1);
        assertEquals(Integer.valueOf(5), find.brushRemaining(cell(8)));
        when(player.isOnline()).thenReturn(true);
        service.watch(player);
        ticks(1);
        service.stop();
        ticks(3);
        assertEquals(Integer.valueOf(4), find.brushRemaining(cell(8)));
    }

    @Test
    public void permissionRevocationStopsAnActiveChannel() {
        service.begin(player, target);
        ticks(2);
        site.setDirector(UUID.randomUUID());
        ticks(3);
        assertEquals(Integer.valueOf(6), find.brushRemaining(cell(8)));
        assertTrue(find.getCleanedCells().isEmpty());
        verify(player).sendMessage("You are not authorised to work on this excavation.");
    }

    @Test
    public void newlyDestroyedFindOrRemovedTerrainInterruptsWithoutGrantingCleaning() {
        service.begin(player, target);
        ticks(2);
        target.setType(Material.AIR);
        ticks(8);
        assertEquals(Integer.valueOf(6), find.brushRemaining(cell(8)));
        assertTrue(find.getCleanedCells().isEmpty());
        target.setType(Material.STONE);
        service.begin(player, target);
        find.woundDirect(cell(8));
        find.woundDirect(cell(9));
        assertEquals(FindState.LOST, find.getState());
        ticks(8);
        assertTrue(find.getCleanedCells().isEmpty());
        assertEquals(0, ((Damageable) held.getItemMeta()).getDamage());
        verify(sites, never()).save(any());
        verifyNoInteractions(recoveredItems);
    }

    @Test
    public void cleaningOneCellUpdatesWorkerDurabilityAndDossierWithoutLiftingEarly() {
        service.begin(player, target);
        ticks(8);
        assertTrue(find.isCleaned(cell(8)));
        assertNull(find.brushRemaining(cell(8)));
        assertEquals(FindState.DISCOVERED, find.getState());
        assertEquals(Material.STONE, target.getType());
        assertEquals(1, site.staffLog(player.getUniqueId()).getCellsBrushed());
        assertEquals(1, ((Damageable) held.getItemMeta()).getDamage());
        verify(sites).save(site);
        verifyNoInteractions(recoveredItems);
        assertTrue(events.isEmpty());
    }

    @Test
    public void reachingCleaningCapClearsWholeShapeDropsOneFindAndClosesSite() {
        service.begin(player, target);
        ticks(8);
        target = world.getBlockAt(9, 40, 8);
        service.begin(player, target);
        ticks(8);
        assertEquals(FindState.RECOVERED, find.getState());
        assertEquals(2, find.getCleanedCells().size());
        for (BlockCell cell : find.getCells()) assertEquals(Material.AIR, world.getBlockAt(cell.x(), cell.y(), cell.z()).getType());
        assertEquals(1, site.getRecoveredCount());
        assertEquals(SiteStatus.EXHAUSTED, site.getStatus());
        assertEquals(player.getUniqueId(), find.getRecoveredBy());
        assertNotNull(find.getRecoveredAt());
        assertTrue(find.getFindNumber() > 0);
        assertEquals(2, site.staffLog(player.getUniqueId()).getCellsBrushed());
        assertEquals(1, site.staffLog(player.getUniqueId()).getFindsRecovered());
        assertEquals(2, ((Damageable) held.getItemMeta()).getDamage());
        List<Item> drops = world.getEntitiesByClass(Item.class).stream().toList();
        assertEquals(1, drops.size());
        assertEquals(Material.BRICK, drops.getFirst().getItemStack().getType());
        assertEquals(1, events.size());
        verify(recoveredItems).create(template, site, find, playerId, "Intact", false, catalogs);
        assertSame(player, events.getFirst().getPlayer());
        assertEquals(Material.BRICK, events.getFirst().getFind().getType());
        verify(player).sendMessage(contains("Recovered:"));
        ticks(10);
        assertEquals(1, events.size());
    }

    @Test
    public void missingTerrainReducesRequiredCleaningAndPreservesDamageInRecovery() {
        find.woundBeforeDig(cell(10));
        world.getBlockAt(10, 40, 8).setType(Material.AIR);
        find.woundFromAbove(cell(9));
        world.getBlockAt(9, 40, 8).setType(Material.WATER);
        service.begin(player, target);
        ticks(8);
        assertEquals(FindState.RECOVERED, find.getState());
        assertEquals(33, find.getConservation());
        assertEquals(Material.WATER, world.getBlockAt(9, 40, 8).getType());
        verify(recoveredItems).create(eq(template), eq(site), eq(find), eq(playerId),
                anyString(), eq(true), eq(catalogs));
        verify(player).sendMessage(contains("disturbed before the dig · hurt while digging"));
    }

    @Test
    public void missingCatalogArchivesRecoveryWithoutCreatingAnInvalidItem() {
        when(catalogs.artifact("pot")).thenReturn(null);
        service.setSettings(new RecoverySettings(true, 1, 1, false));
        service.begin(player, target);
        ticks(1);
        assertEquals(FindState.RECOVERED, find.getState());
        assertEquals(1, site.getRecoveredCount());
        assertTrue(events.isEmpty());
        verifyNoInteractions(recoveredItems);
        verify(player).sendMessage("Recovered a find, but its template is missing from the catalog.");
    }

    @Test
    public void siteAbortStopsOnlyMatchingChannelAndReloadClampsStoredDuration() {
        service.begin(player, target);
        ticks(2);
        service.abortForSite(UUID.randomUUID());
        ticks(1);
        assertEquals(Integer.valueOf(5), find.brushRemaining(cell(8)));
        service.abortForSite(site.getId());
        ticks(3);
        assertEquals(Integer.valueOf(5), find.brushRemaining(cell(8)));
        service.setSettings(new RecoverySettings(true, 3, 2, true));
        service.begin(player, target);
        assertEquals(Integer.valueOf(3), find.brushRemaining(cell(8)));
        ticks(3);
        assertTrue(find.isCleaned(cell(8)));
        assertEquals(FindState.DISCOVERED, find.getState());
    }

    @Test
    public void progressBarHidesWhenLookingAwayResumesAndRemovesViewersOnStop() {
        service.setSettings(new RecoverySettings(true, 8, 2, true));
        PlayerMock viewer = archaeologist("Viewer", target);
        service.begin(viewer, target);
        BossBar bar = server.brushBars.getFirst();
        assertTrue(bar.isVisible());
        assertEquals(List.of(viewer), bar.getPlayers());
        assertEquals(0, bar.getProgress(), 0);
        ticks(2);
        assertEquals(0.25, bar.getProgress(), 0);
        doReturn(null).when(viewer).getTargetBlockExact(6);
        ticks(1);
        assertFalse(bar.isVisible());
        assertEquals(Integer.valueOf(6), find.brushRemaining(cell(8)));
        doReturn(target).when(viewer).getTargetBlockExact(6);
        ticks(1);
        assertTrue(bar.isVisible());
        assertEquals(0.375, bar.getProgress(), 0);
        service.cancel(viewer);
        assertFalse(bar.isVisible());
        assertTrue(bar.getPlayers().isEmpty());
        service.watch(viewer);
        ticks(1);
        BossBar resumed = server.brushBars.getLast();
        assertNotSame(bar, resumed);
        assertTrue(resumed.isVisible());
        assertEquals(0.5, resumed.getProgress(), 0);
        service.stop();
        assertFalse(resumed.isVisible());
        assertTrue(resumed.getPlayers().isEmpty());
        ticks(10);
        assertEquals(Integer.valueOf(4), find.brushRemaining(cell(8)));
        assertTrue(find.getCleanedCells().isEmpty());
    }

    @Test
    public void siteAbortRemovesEveryOnlineBrushBarAndStopsTheirProgress() {
        service.setSettings(new RecoverySettings(true, 8, 2, true));
        PlayerMock first = archaeologist("First", target);
        Block otherCell = world.getBlockAt(9, 40, 8);
        PlayerMock second = archaeologist("Second", otherCell);
        service.begin(first, target);
        service.begin(second, otherCell);
        ticks(2);
        assertEquals(2, server.brushBars.size());
        service.abortForSite(UUID.randomUUID());
        assertTrue(server.brushBars.stream().allMatch(BossBar::isVisible));
        service.abortForSite(site.getId());
        for (BossBar bar : server.brushBars) {
            assertFalse(bar.isVisible());
            assertTrue(bar.getPlayers().isEmpty());
        }
        ticks(10);
        assertEquals(Integer.valueOf(6), find.brushRemaining(cell(8)));
        assertEquals(Integer.valueOf(6), find.brushRemaining(cell(9)));
        assertTrue(find.getCleanedCells().isEmpty());
        verify(sites, never()).save(any());
    }

    @Test
    public void anotherArchaeologistCanFinishStoredWorkWithoutChargingTheFirstBrush() {
        service.begin(player, target);
        ticks(3);
        service.cancel(player);
        PlayerMock successor = archaeologist("Successor", target);
        service.begin(successor, target);
        assertEquals(Integer.valueOf(5), find.brushRemaining(cell(8)));
        ticks(5);
        assertTrue(find.isCleaned(cell(8)));
        assertNull(find.brushRemaining(cell(8)));
        assertNull(site.workerRecord(playerId));
        assertEquals(1, site.staffLog(successor.getUniqueId()).getCellsBrushed());
        assertEquals(0, ((Damageable) held.getItemMeta()).getDamage());
        assertEquals(1, ((Damageable) successor.getInventory().getItemInMainHand().getItemMeta()).getDamage());
        assertEquals(FindState.DISCOVERED, find.getState());
        verify(sites).save(site);
    }

    @Test
    public void twoArchaeologistsOnSameCubeCleanAndSpendDurabilityOnlyOnce() {
        PlayerMock second = archaeologist("Second", target);
        service.begin(player, target);
        ticks(2);
        service.begin(second, target);
        ticks(5);
        assertFalse(find.isCleaned(cell(8)));
        assertEquals(Integer.valueOf(1), find.brushRemaining(cell(8)));
        ticks(1);
        assertTrue(find.isCleaned(cell(8)));
        assertEquals(1, find.getCleanedCells().size());
        assertEquals(1, site.staffLog(playerId).getCellsBrushed());
        assertNull(site.workerRecord(second.getUniqueId()));
        assertEquals(1, ((Damageable) held.getItemMeta()).getDamage());
        assertEquals(0, ((Damageable) second.getInventory().getItemInMainHand().getItemMeta()).getDamage());
        ticks(10);
        verify(sites).save(site);
        assertEquals(FindState.DISCOVERED, find.getState());
        assertTrue(events.isEmpty());
    }

    @Test
    public void simultaneousCleaningOfDifferentCellsLiftsOneSharedFindOnce() {
        service.setSettings(new RecoverySettings(true, 8, 2, true));
        PlayerMock first = archaeologist("First", target);
        Block otherCell = world.getBlockAt(9, 40, 8);
        PlayerMock second = archaeologist("Second", otherCell);
        when(recoveredItems.create(eq(template), eq(site), eq(find), any(UUID.class),
                anyString(), anyBoolean(), eq(catalogs))).thenReturn(new ItemStack(Material.BRICK));
        service.begin(first, target);
        service.begin(second, otherCell);
        ticks(8);
        assertEquals(FindState.RECOVERED, find.getState());
        assertEquals(2, find.getCleanedCells().size());
        assertEquals(1, site.staffLog(first.getUniqueId()).getCellsBrushed());
        assertEquals(1, site.staffLog(second.getUniqueId()).getCellsBrushed());
        assertEquals(second.getUniqueId(), find.getRecoveredBy());
        assertEquals(1, events.size());
        assertEquals(1, world.getEntitiesByClass(Item.class).size());
        assertEquals(1, site.getRecoveredCount());
        for (BossBar bar : server.brushBars) {
            assertFalse(bar.isVisible());
            assertTrue(bar.getPlayers().isEmpty());
        }
        ticks(10);
        assertEquals(1, events.size());
        verify(recoveredItems).create(template, site, find, second.getUniqueId(), "Intact", false, catalogs);
    }

    @Test
    public void cancelingPausedBrushCannotOverwriteAnotherWorkersProgress() {
        service.begin(player, target);
        ticks(2);
        PlayerMock second = archaeologist("Second", target);
        service.begin(second, target);
        when(player.getItemInUse()).thenReturn(null);
        ticks(2);
        assertEquals(Integer.valueOf(4), find.brushRemaining(cell(8)));
        service.cancel(player);
        assertEquals("Canceling an older paused channel must preserve the shared work",
                Integer.valueOf(4), find.brushRemaining(cell(8)));
        service.cancel(second);
        when(player.getItemInUse()).thenReturn(held);
        service.watch(player);
        ticks(1);
        assertEquals(Integer.valueOf(3), find.brushRemaining(cell(8)));
        assertTrue(find.getCleanedCells().isEmpty());
        assertEquals(0, ((Damageable) held.getItemMeta()).getDamage());
    }

    @Test
    public void resumingPausedBrushContinuesTheBestSharedProgress() {
        service.begin(player, target);
        ticks(2);
        PlayerMock second = archaeologist("Second", target);
        service.begin(second, target);
        when(player.getItemInUse()).thenReturn(null);
        ticks(2);
        assertEquals(Integer.valueOf(4), find.brushRemaining(cell(8)));
        doReturn(null).when(second).getItemInUse();
        when(player.getItemInUse()).thenReturn(held);
        ticks(1);
        assertEquals("Resuming must advance the saved work, not the older local snapshot",
                Integer.valueOf(3), find.brushRemaining(cell(8)));
        assertTrue(find.getCleanedCells().isEmpty());
        assertEquals(0, ((Damageable) held.getItemMeta()).getDamage());
    }

    @Test
    public void firstClickStartsCleaningAfterEquippedBrushWatcherHasAlreadyFocusedTheCell() {
        service.watch(player);
        ticks(2);
        assertNull(find.brushRemaining(cell(8)));
        service.begin(player, target);
        assertEquals(Integer.valueOf(8), find.brushRemaining(cell(8)));
        ticks(8);
        assertTrue(find.isCleaned(cell(8)));
        assertEquals(1, site.staffLog(playerId).getCellsBrushed());
        assertEquals(1, ((Damageable) held.getItemMeta()).getDamage());
    }

    @Test
    public void sameCoordinatesInAnotherWorldAreASeparateCubeWithTheirOwnProgress() {
        Twin twin = twinSite();
        service.begin(player, target);
        ticks(2);
        assertEquals(Integer.valueOf(6), find.brushRemaining(cell(8)));
        // Teleported mid-brush: the click lands on the same coordinates in the other dig.
        target = twin.world.getBlockAt(8, 40, 8);
        service.begin(player, target);
        assertEquals(Integer.valueOf(8), twin.find.brushRemaining(cell(8)));
        ticks(2);
        assertEquals(Integer.valueOf(6), twin.find.brushRemaining(cell(8)));
        assertEquals("The first dig keeps its own work", Integer.valueOf(6), find.brushRemaining(cell(8)));
        target = world.getBlockAt(8, 40, 8);
        ticks(2);
        assertEquals(Integer.valueOf(4), find.brushRemaining(cell(8)));
        assertEquals(Integer.valueOf(6), twin.find.brushRemaining(cell(8)));
        assertTrue(find.getCleanedCells().isEmpty());
        assertTrue(twin.find.getCleanedCells().isEmpty());
    }

    @Test
    public void brushersOnMatchingCoordinatesInTwoWorldsDoNotBlockEachOther() {
        Twin twin = twinSite();
        PlayerMock second = archaeologist("Second", target);
        Block twinCube = twin.world.getBlockAt(8, 40, 8);
        doReturn(twinCube).when(second).getTargetBlockExact(6);
        twin.site.getExcavators().add(second.getUniqueId());
        service.begin(player, target);
        service.begin(second, twinCube);
        ticks(3);
        assertEquals(Integer.valueOf(5), find.brushRemaining(cell(8)));
        assertEquals(Integer.valueOf(5), twin.find.brushRemaining(cell(8)));
    }

    @Test
    public void lookingAtGroundOutsideTheDigParksProgressUntilTheCubeIsAimedAgain() {
        service.setSettings(new RecoverySettings(true, 8, 2, true));
        PlayerMock viewer = archaeologist("Viewer", target);
        service.begin(viewer, target);
        ticks(2);
        BossBar bar = server.brushBars.getFirst();
        Block outside = world.getBlockAt(20, 40, 8);
        outside.setType(Material.STONE);
        doReturn(outside).when(viewer).getTargetBlockExact(6);
        ticks(3);
        assertFalse(bar.isVisible());
        assertEquals(Integer.valueOf(6), find.brushRemaining(cell(8)));
        doReturn(target).when(viewer).getTargetBlockExact(6);
        ticks(1);
        assertTrue(bar.isVisible());
        assertEquals(Integer.valueOf(5), find.brushRemaining(cell(8)));
    }

    @Test
    public void progressStaysOnEachCubeWhenMovingToTheCubeAboveOrBehind() {
        BlockCell above = new BlockCell(8, 41, 8), behind = new BlockCell(8, 40, 9);
        for (BlockCell extra : List.of(above, behind)) {
            find.getCells().add(extra);
            world.getBlockAt(extra.x(), extra.y(), extra.z()).setType(Material.STONE);
        }
        service.begin(player, target);
        ticks(2);
        target = world.getBlockAt(behind.x(), behind.y(), behind.z());
        ticks(1);
        assertNull("Looking at a fresh cube does not start it", find.brushRemaining(behind));
        service.begin(player, target);
        ticks(3);
        assertEquals(Integer.valueOf(5), find.brushRemaining(behind));
        // A quick click on the cube above lands before the watcher has looked at it.
        target = world.getBlockAt(above.x(), above.y(), above.z());
        service.begin(player, target);
        ticks(3);
        assertEquals(Integer.valueOf(5), find.brushRemaining(above));
        assertEquals(Integer.valueOf(5), find.brushRemaining(behind));
        assertEquals(Integer.valueOf(6), find.brushRemaining(cell(8)));
        assertTrue(find.getCleanedCells().isEmpty());
    }

    @Test
    public void parkingABrushAfterAReloadDroppedItsDossierOrFindMarksNothingDirty() {
        service.begin(player, target);
        ticks(2);
        when(sites.findById(site.getId())).thenReturn(Optional.empty());
        service.cancel(player);
        service.begin(player, target);
        ticks(2);
        Site reloaded = new Site();
        reloaded.setId(site.getId());
        BuriedFind regenerated = new BuriedFind();
        regenerated.setId(UUID.randomUUID());
        reloaded.getFinds().add(regenerated);
        when(sites.findById(site.getId())).thenReturn(Optional.of(reloaded));
        service.cancel(player);
        verify(sites, never()).touch(any());
        assertEquals(Integer.valueOf(4), find.brushRemaining(cell(8)));
    }

    @Test
    public void pieceBelowEveryConfiguredGradeIsAnnouncedWithoutAGradeLabel() {
        PickSettings defaults = PickSettings.defaults();
        ConservationSettings strict = new ConservationSettings(40, 100, 1.0, 4, 10,
                List.of(new ConservationGrade("sound", 50, "Sound")));
        when(catalogs.pick()).thenReturn(new PickSettings(true, 8, true, true, 6, 2, strict,
                defaults.limits(), false, defaults.cues(), defaults.profiles()));
        find.woundBeforeDig(cell(10));
        world.getBlockAt(10, 40, 8).setType(Material.AIR);
        find.woundFromAbove(cell(9));
        world.getBlockAt(9, 40, 8).setType(Material.AIR);
        service.begin(player, target);
        ticks(8);
        assertEquals(FindState.RECOVERED, find.getState());
        verify(recoveredItems).create(eq(template), eq(site), eq(find), eq(playerId), eq(""), eq(true), eq(catalogs));
        verify(player).sendMessage("Recovered: " + find.publicNumber(site)
                + " · Clay pot · 33% · disturbed before the dig · hurt while digging");
    }

    @Test
    public void earlyClickWarningRepeatsOnlyAfterTheCooldown() {
        java.util.concurrent.atomic.AtomicLong now = new java.util.concurrent.atomic.AtomicLong(1_000_000); service.clock = now::get;
        find.setState(FindState.PARTIAL);
        service.begin(player, target);
        now.addAndGet(2999); service.begin(player, target);
        verify(player, times(1)).sendMessage("The shape is not fully free.");
        now.addAndGet(1);
        service.begin(player, target);
        verify(player, times(2)).sendMessage("The shape is not fully free.");
        assertTrue(find.getBrushRemaining().isEmpty());
    }

    @Test
    public void cancellingWithoutAnOpenBrushChannelChangesNothing() {
        // The listener cancels on every drop, swap, quit, or switch to another item.
        service.cancel(player);
        verify(sites, never()).touch(any());
        service.begin(player, target);
        ticks(2);
        assertEquals(Integer.valueOf(6), find.brushRemaining(cell(8)));
    }

    /** A second established dig in another world whose cubes sit at the same coordinates. */
    private Twin twinSite() {
        WorldMock other = new SnapshotLocationWorld();
        other.setName("recovery-twin");
        server.addWorld(other);
        Site twin = new Site();
        twin.setId(UUID.randomUUID());
        twin.setName("Twin");
        twin.setWorldName(other.getName());
        twin.setStatus(SiteStatus.ESTABLISHED);
        twin.setDirector(playerId);
        twin.getStrata().putAll(site.getStrata());
        BuriedFind twinFind = new BuriedFind();
        twinFind.setId(UUID.randomUUID());
        twinFind.setArtifactId("pot");
        twinFind.setState(FindState.DISCOVERED);
        for (int x = 8; x <= 10; x++) {
            twinFind.getCells().add(cell(x));
            other.getBlockAt(x, 40, 8).setType(Material.STONE);
        }
        twin.getFinds().add(twinFind);
        when(sites.findEstablishedPrism(anyString(), anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> {
            for (Site candidate : List.of(site, twin)) {
                if (candidate.getStatus() == SiteStatus.ESTABLISHED
                        && candidate.getWorldName().equals(invocation.getArgument(0))
                        && candidate.isInPrism(invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3))) {
                    return Optional.of(candidate);
                }
            }
            return Optional.empty();
        });
        when(sites.findById(twin.getId())).thenReturn(Optional.of(twin));
        return new Twin(other, twin, twinFind);
    }

    private record Twin(WorldMock world, Site site, BuriedFind find) {
    }

    private PlayerMock archaeologist(String name, Block aimed) {
        PlayerMock worker = spy(new PlayerMock(server, name));
        server.addPlayer(worker);
        worker.teleport(new Location(world, aimed.getX(), 42, aimed.getZ()));
        worker.getInventory().setItemInMainHand(new ItemStack(Material.BRUSH));
        doReturn(aimed).when(worker).getTargetBlockExact(6);
        doAnswer(invocation -> worker.getInventory().getItemInMainHand()).when(worker).getItemInUse();
        doReturn(0).when(worker).getItemInUseTicks();
        site.getExcavators().add(worker.getUniqueId());
        return worker;
    }

    /** Observe real server-created bars through the public Bukkit creation API. */
    private static class RecordingServer extends ServerMock {
        private final List<BossBar> brushBars = new ArrayList<>();
        @Override
        public BossBar createBossBar(String title, BarColor color, BarStyle style, BarFlag... flags) {
            BossBar bar = super.createBossBar(title, color, style, flags);
            brushBars.add(bar);
            return bar;
        }
    }

    private void ticks(int count) {
        server.getScheduler().performTicks(count);
    }

    /** Match Bukkit's location snapshot contract, which MockBukkit 4.95 does not preserve. */
    private static class SnapshotLocationWorld extends WorldMock {
        private final Map<BlockCell, BlockMock> cells = new HashMap<>();

        @Override
        public BlockMock getBlockAt(int x, int y, int z) {
            return cells.computeIfAbsent(new BlockCell(x, y, z), key ->
                    new BlockMock(new Location(this, x, y, z)) {
                        @Override
                        public Location getLocation() {
                            return super.getLocation().clone();
                        }
                    });
        }
    }

    private static BlockCell cell(int x) {
        return new BlockCell(x, 40, 8);
    }
}
