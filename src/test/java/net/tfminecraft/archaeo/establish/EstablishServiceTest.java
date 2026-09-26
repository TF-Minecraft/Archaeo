package net.tfminecraft.archaeo.establish;

import net.tfminecraft.archaeo.config.EstablishSettings;
import net.tfminecraft.archaeo.excavation.FindDustService;
import net.tfminecraft.archaeo.item.EstablishItem;
import net.tfminecraft.archaeo.item.ItemRef;
import net.tfminecraft.archaeo.item.SiteLabelRefresh;
import net.tfminecraft.archaeo.model.*;
import net.tfminecraft.archaeo.site.SiteRepository;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.data.BlockData;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BlockIterator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.mockito.ArgumentCaptor;

import java.util.*;
import java.util.function.Consumer;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class EstablishServiceTest {
    private JavaPlugin plugin;
    private Server server;
    private WorldMock world;
    private Player player;
    private PlayerInventory inventory;
    private SiteRepository sites;
    private EstablishService service;
    private Site site;
    private BossBar bar;
    private BukkitTask repeating;
    private ItemStack kits;
    private final List<Site> records = new ArrayList<>();
    private final List<Runnable> scheduled = new ArrayList<>();
    private ArmorStand proxy;

    @Before public void setup() {
        var mockServer = MockBukkit.mock();
        world = spy(new WorldMock(Material.GRASS_BLOCK, -64, 320, 3));
        world.setName("world"); mockServer.addWorld(world);
        plugin = mock(JavaPlugin.class); server = mock(Server.class); player = mock(Player.class);
        inventory = mock(PlayerInventory.class); sites = mock(SiteRepository.class); bar = mock(BossBar.class);
        UUID playerId = UUID.randomUUID();
        when(plugin.getServer()).thenReturn(server); when(server.getWorld("world")).thenReturn(world);
        when(server.getPlayer(playerId)).thenReturn(player); doReturn(List.of(player)).when(server).getOnlinePlayers();
        when(player.getUniqueId()).thenReturn(playerId); when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenAnswer(call -> new Location(world, 20, 4, 4));
        when(player.getEyeLocation()).thenAnswer(call -> new Location(world, 20, 5.6, 4));
        when(player.getInventory()).thenReturn(inventory); when(player.isOnline()).thenReturn(true);
        when(player.getPose()).thenReturn(Pose.STANDING);
        kits = new ItemStack(Material.STICK, 2);
        when(inventory.getItemInMainHand()).thenReturn(kits);
        when(inventory.getItemInOffHand()).thenReturn(new ItemStack(Material.AIR));
        doReturn(world.getBlockAt(20, 3, 4)).when(player).getTargetBlockExact(64);
        when(server.createBossBar(anyString(), any(), any())).thenReturn(bar);
        BukkitScheduler scheduler = mock(BukkitScheduler.class); when(server.getScheduler()).thenReturn(scheduler);
        repeating = mock(BukkitTask.class);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(20L), eq(5L))).thenReturn(repeating);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            scheduled.add(call.getArgument(1)); return mock(BukkitTask.class);
        });
        site = hidden("world", 0, 0); site.confirmProspect(playerId); records.add(site);
        when(sites.all()).thenAnswer(call -> List.copyOf(records));
        when(sites.findById(any())).thenAnswer(call -> records.stream().filter(s -> s.getId().equals(call.getArgument(0))).findFirst());
        when(sites.findByChunk(anyString(), anyInt(), anyInt())).thenAnswer(call -> records.stream()
                .filter(s -> s.getWorldName().equals(call.getArgument(0)) && s.getChunkX() == (int) call.getArgument(1)
                        && s.getChunkZ() == (int) call.getArgument(2)).findFirst());
        when(sites.findByEstablishmentChunk(anyString(), anyInt(), anyInt())).thenAnswer(call -> records.stream()
                .filter(s -> s.hasEstablishment() && s.getWorldName().equals(call.getArgument(0))
                        && s.getEstablishmentChunkX() == (int) call.getArgument(1)
                        && s.getEstablishmentChunkZ() == (int) call.getArgument(2)).findFirst());
        service = new EstablishService(plugin, sites, new EstablishItem(ItemRef.vanilla(Material.STICK)), EstablishSettings.defaults());
        proxy = mock(ArmorStand.class); UUID proxyId = UUID.randomUUID();
        when(proxy.getUniqueId()).thenReturn(proxyId); when(proxy.getWorld()).thenReturn(world); when(proxy.isValid()).thenReturn(true);
        when(server.getEntity(proxyId)).thenReturn(proxy);
        doAnswer(call -> { Consumer<ArmorStand> configure = call.getArgument(2); configure.accept(proxy); return proxy; })
                .when(world).spawn(any(Location.class), eq(ArmorStand.class), any(Consumer.class));
    }

    @After public void teardown() { MockBukkit.unmock(); }

    @Test public void previewIsClientOnlyAndConfirmPlantsTheShownCampConsumesOneKitAndPersists() {
        FindDustService dust = mock(FindDustService.class); service.setFindDust(dust);
        Block ground = world.getBlockAt(20, 3, 4);
        service.pulse();
        verify(player, atLeastOnce()).sendBlockChange(any(Location.class), any(BlockData.class));
        verify(bar).setColor(org.bukkit.boss.BarColor.GREEN);
        assertEquals(SiteStatus.HIDDEN, site.getStatus()); assertTrue(site.getCampBlocks().isEmpty());
        assertEquals(Material.GRASS_BLOCK, ground.getType());
        assertEquals(Material.AIR, world.getBlockAt(18, 4, 6).getType());
        when(player.getTargetBlockExact(64)).thenReturn(null);
        service.tryUseKit(player, null);
        assertEquals(SiteStatus.ESTABLISHED, site.getStatus()); assertEquals(player.getUniqueId(), site.getDirector());
        assertEquals(Integer.valueOf(1), site.getEstablishmentChunkX()); assertEquals(Integer.valueOf(0), site.getEstablishmentChunkZ());
        assertEquals(Integer.valueOf(20), site.getCampX()); assertFalse(site.getCampBlocks().isEmpty());
        assertNotNull(site.getCampSignX()); assertEquals(1, kits.getAmount());
        for (BlockCell cell : site.getCampBlocks()) assertFalse(world.getBlockAt(cell.x(), cell.y(), cell.z()).getType().isAir());
        verify(sites).save(site); verify(dust).syncTimer();
        assertFalse(scheduled.isEmpty()); scheduled.forEach(Runnable::run);
        verify(player).sendMessage("You established an archaeological excavation.");
    }

    @Test public void invalidTerrainAndNonNeighborTargetsLeaveWorldAndInventoryUntouched() {
        assertRejected(null, CampPlacement.Issue.LOOK_MISS);
        assertRejected(world.getBlockAt(4, 3, 4), CampPlacement.Issue.ON_DIG);
        assertRejected(world.getBlockAt(20, 3, 20), CampPlacement.Issue.NOT_NEIGHBOR);
        records.add(hidden("world", 1, 0));
        assertRejected(world.getBlockAt(20, 3, 4), CampPlacement.Issue.OCCUPIED); records.removeLast();
        var obstruction = spy(world.getBlockAt(18, 4, 6));
        doReturn(false).when(obstruction).isPassable();
        doReturn(obstruction).when(world).getBlockAt(18, 4, 6);
        obstruction.setType(Material.STONE);
        assertRejected(world.getBlockAt(20, 3, 4), CampPlacement.Issue.BLOCKED);
        world.getBlockAt(18, 4, 6).setType(Material.AIR); world.getBlockAt(18, 3, 6).setType(Material.AIR);
        assertRejected(world.getBlockAt(20, 3, 4), CampPlacement.Issue.UNSUPPORTED);
        world.getBlockAt(18, 3, 6).setType(Material.WATER);
        assertRejected(world.getBlockAt(20, 3, 4), CampPlacement.Issue.FLUID);
        assertEquals(2, kits.getAmount()); assertEquals(SiteStatus.HIDDEN, site.getStatus()); verify(sites, never()).save(any());
    }

    @Test public void eligibilityRequiresConfirmationNearbyWorldAndDirectorCapacity() {
        site.getProspectConfirmed().clear(); service.tryUseKit(player, null);
        verify(player).sendMessage("Look at a chunk next to a ruin you have confirmed.");
        site.confirmProspect(player.getUniqueId()); site.setWorldName("other"); service.pulse();
        verify(bar).setTitle(contains("No confirmed ruin nearby"));
        site.setWorldName("world"); site.setChunkX(10); service.tryUseKit(player, null);
        site.setChunkX(0); when(sites.countDirectedCamps(player.getUniqueId())).thenReturn(1);
        service.tryUseKit(player, null); verify(player).sendMessage(contains("already direct an excavation"));
        service.setSettings(settings(true, 2)); when(sites.countDirectedCamps(player.getUniqueId())).thenReturn(2);
        service.pulse(); service.tryUseKit(player, null); verify(player).sendMessage(contains("already direct 2 excavations"));
        assertEquals(SiteStatus.HIDDEN, site.getStatus());
        service.setSettings(settings(true, 0)); service.tryUseKit(player, null);
        assertEquals(SiteStatus.ESTABLISHED, site.getStatus());
    }

    @Test public void nearestConfirmedRuinWinsAndAnOffhandKitIsConsumed() {
        Site farther = hidden("world", 0, 1); farther.confirmProspect(player.getUniqueId()); records.addFirst(farther);
        when(inventory.getItemInMainHand()).thenReturn(new ItemStack(Material.AIR));
        when(inventory.getItemInOffHand()).thenReturn(kits);
        service.pulse(); service.tryUseKit(player, null);
        assertEquals(SiteStatus.ESTABLISHED, site.getStatus()); assertEquals(SiteStatus.HIDDEN, farther.getStatus());
        assertEquals(1, kits.getAmount());
    }

    @Test public void previewDiffsAvoidDuplicatePacketsAndClearRestoresRealBlocks() {
        service.pulse(); clearInvocations(player); service.pulse();
        verify(player, never()).sendBlockChange(any(Location.class), any(BlockData.class));
        service.clearPreview(player);
        ArgumentCaptor<Location> locations = ArgumentCaptor.forClass(Location.class);
        ArgumentCaptor<BlockData> data = ArgumentCaptor.forClass(BlockData.class);
        verify(player, atLeastOnce()).sendBlockChange(locations.capture(), data.capture());
        for (int i = 0; i < locations.getAllValues().size(); i++) {
            Location at = locations.getAllValues().get(i);
            assertEquals(world.getBlockAt(at).getBlockData(), data.getAllValues().get(i));
        }
        clearInvocations(player); service.clearPreview(player);
        verify(player, never()).sendBlockChange(any(Location.class), any(BlockData.class));
    }

    @Test public void renameValidatesOwnershipAndRetitlesStoredFindsAfterSaving() {
        establishDirectly(); SiteLabelRefresh labels = mock(SiteLabelRefresh.class); service.setLabelRefresh(labels);
        service.beginRename(player, site); assertTrue(service.isRenaming(player));
        assertTrue(service.handleRenameChat(player, "§ ")); assertTrue(service.isRenaming(player));
        assertTrue(service.handleRenameChat(player, "x".repeat(41))); assertTrue(service.isRenaming(player));
        assertTrue(service.handleRenameChat(player, "  River § camp  "));
        assertEquals("River   camp", site.getName()); assertFalse(service.isRenaming(player));
        verify(sites).save(site); verify(labels).retitle(site);
        assertFalse(service.handleRenameChat(player, "ordinary chat"));
        service.beginRename(player, site); assertTrue(service.handleRenameChat(player, "CaNcEl"));
        assertEquals("River   camp", site.getName());
        service.beginRename(player, site); service.abortRename(player); assertFalse(service.isRenaming(player));
        service.beginRename(player, site); site.setDirector(UUID.randomUUID());
        assertTrue(service.handleRenameChat(player, "Unauthorized")); assertEquals("River   camp", site.getName());
        service.beginRename(player, site); site.setStatus(SiteStatus.CLOSED);
        assertTrue(service.handleRenameChat(player, "Closed")); verify(player).sendMessage("That excavation is no longer active.");
    }

    @Test public void woolCyclingIsOncePerTickAndRequiresSneakingWithTheKit() {
        assertFalse(service.tryCycleWool(player)); when(player.getPose()).thenReturn(Pose.SNEAKING);
        assertTrue(service.tryCycleWool(player)); assertTrue(service.tryCycleWool(player));
        service.tryUseKit(player, null);
        assertEquals(CampWools.next("RED").name(), site.getCampWoolSecondary());
        service.beginRelocate(player, site); assertFalse(service.tryCycleWool(player));
        service.cancelRelocate(player);
        when(inventory.getItemInMainHand()).thenReturn(new ItemStack(Material.AIR));
        assertFalse(service.tryCycleWool(player));
    }

    @Test public void recoloringChangesOnlyTheSelectedRoleAndRequiresTheDirector() {
        service.tryUseKit(player, null);
        List<BlockCell> originals = List.copyOf(site.getCampBlocks());
        service.applyCampWool(player, site, DyeColor.BLUE, CampWoolRole.PRIMARY);
        assertEquals("BLUE", site.getCampWoolPrimary());
        assertTrue(originals.stream().anyMatch(c -> world.getBlockAt(c.x(), c.y(), c.z()).getType() == Material.BLUE_WOOL));
        assertTrue(originals.stream().anyMatch(c -> world.getBlockAt(c.x(), c.y(), c.z()).getType() == Material.RED_WOOL));
        service.applyCampWool(player, site, DyeColor.YELLOW, CampWoolRole.SECONDARY);
        assertEquals("YELLOW", site.getCampWoolSecondary());
        site.setDirector(UUID.randomUUID()); clearInvocations(sites);
        service.applyCampWool(player, site, DyeColor.GREEN, CampWoolRole.PRIMARY);
        assertEquals("BLUE", site.getCampWoolPrimary()); verify(sites, never()).save(any());
    }

    @Test public void legacyCampRecoloringUsesRecordedCellsWithoutTouchingOtherMaterials() {
        establishDirectly(); site.setCampFacing("legacy-facing");
        Block wool = world.getBlockAt(20, 4, 4), stone = world.getBlockAt(21, 4, 4);
        wool.setType(Material.WHITE_WOOL); stone.setType(Material.STONE);
        site.getCampBlocks().addAll(List.of(new BlockCell(20, 4, 4), new BlockCell(21, 4, 4)));
        service.applyCampWool(player, site, DyeColor.BLACK, CampWoolRole.PRIMARY);
        assertEquals(Material.BLACK_WOOL, wool.getType()); assertEquals(Material.STONE, stone.getType());
        assertEquals("BLACK", site.getCampWoolPrimary()); verify(sites).save(site);
    }

    @Test public void relocationPreviewHidesOldCampAndConfirmationMovesItWithoutSpendingAKit() {
        service.tryUseKit(player, null); List<BlockCell> old = List.copyOf(site.getCampBlocks());
        service.beginRelocate(player, site);
        Block destination = spy(world.getBlockAt(-8, 3, 4));
        doReturn(false).when(destination).isPassable();
        try (var rays = mockConstruction(BlockIterator.class, (iterator, context) -> {
            when(iterator.hasNext()).thenReturn(true, false); when(iterator.next()).thenReturn(destination);
        })) {
            service.pulse(); service.pulse();
            verify(proxy).teleport(any(Location.class));
            verify(proxy).setInvisible(true); verify(proxy).setPersistent(false);
            assertTrue(service.isRelocating(player));
            assertFalse(service.tryFinishMoveOnAimProxy(player, mock(Entity.class)));
            assertTrue(service.tryFinishMoveOnAimProxy(player, proxy));
        }
        assertFalse(service.isRelocating(player)); assertEquals(Integer.valueOf(-1), site.getEstablishmentChunkX());
        assertEquals(1, kits.getAmount());
        for (BlockCell cell : old) assertEquals(Material.AIR, world.getBlockAt(cell.x(), cell.y(), cell.z()).getType());
        verify(proxy).remove(); verify(player).sendMessage("Camp moved.");
    }

    @Test public void cancellingOrDisablingRelocationPreservesExistingCampAndDropsSessions() {
        service.tryUseKit(player, null); List<BlockCell> old = List.copyOf(site.getCampBlocks());
        service.beginRelocate(player, site); service.beginRename(player, site);
        service.abortSessionsFor(site.getId()); assertFalse(service.isRelocating(player)); assertFalse(service.isRenaming(player));
        service.beginRelocate(player, site); assertTrue(service.tryCancelMove(player)); assertFalse(service.tryCancelMove(player));
        service.beginRelocate(player, site); service.setSettings(settings(false, 1)); service.pulse();
        assertFalse(service.isRelocating(player)); assertEquals(old, site.getCampBlocks());
        for (BlockCell cell : old) assertFalse(world.getBlockAt(cell.x(), cell.y(), cell.z()).getType().isAir());
        service.beginRename(player, site); service.clearSession(player); assertFalse(service.isRenaming(player));
        assertFalse(service.tryFinishMove(player)); assertFalse(service.handleMoveAimProxyAttack(player, null));
    }

    @Test public void shutdownCancelsPreviewTaskAndRemovesHudAndGhosts() {
        service.start(); service.pulse(); service.stop(); service.stop();
        verify(repeating).cancel(); verify(bar).removeAll();
        assertEquals(SiteStatus.HIDDEN, site.getStatus()); assertEquals(2, kits.getAmount());
    }


    @Test public void claimingPreviouslyDestroyedTerrainRecordsLossWithoutBlamingTheDirector() {
        BuriedFind find = new BuriedFind(); find.setId(UUID.randomUUID()); find.setArtifactId("coin");
        BlockCell missing = new BlockCell(1, 1, 1); find.getCells().add(missing); site.getFinds().add(find);
        world.getBlockAt(1, 1, 1).setType(Material.AIR);
        service.tryUseKit(player, null);
        assertEquals(SiteStatus.ESTABLISHED, site.getStatus()); assertEquals(FindState.LOST, find.getState());
        assertTrue(find.isDisturbedBeforeDig()); assertFalse(find.isFieldDamaged());
        assertNull(find.getRecoveredBy()); assertTrue(find.getFindNumber() > 0);
        verify(player).sendMessage("One find was already disturbed before this dig opened, 1 beyond recovery.");
        verify(sites).save(site);
    }

    @Test public void attackingMoveProxyCancelsTheMoveAndRestoresTheExistingCamp() {
        service.tryUseKit(player, null); List<BlockCell> original = List.copyOf(site.getCampBlocks());
        service.beginRelocate(player, site);
        Block destination = spy(world.getBlockAt(-8, 3, 4)); doReturn(false).when(destination).isPassable();
        try (var rays = mockConstruction(BlockIterator.class, (iterator, context) -> {
            when(iterator.hasNext()).thenReturn(true, false); when(iterator.next()).thenReturn(destination);
        })) {
            service.pulse(); assertFalse(service.handleMoveAimProxyAttack(player, mock(Entity.class)));
            assertTrue(service.handleMoveAimProxyAttack(player, proxy));
        }
        assertFalse(service.isRelocating(player)); assertEquals(original, site.getCampBlocks());
        for (BlockCell cell : original) assertFalse(world.getBlockAt(cell.x(), cell.y(), cell.z()).getType().isAir());
        verify(proxy).remove(); verify(player).sendMessage("Camp move cancelled. It stays where it is.");
    }

    @Test public void campClosedDuringMoveCannotBeRelocatedFromTheLastPreview() {
        service.tryUseKit(player, null); List<BlockCell> original = List.copyOf(site.getCampBlocks());
        service.beginRelocate(player, site);
        Block destination = spy(world.getBlockAt(-8, 3, 4)); doReturn(false).when(destination).isPassable();
        try (var rays = mockConstruction(BlockIterator.class, (iterator, context) -> {
            when(iterator.hasNext()).thenReturn(true, false); when(iterator.next()).thenReturn(destination);
        })) {
            service.pulse(); assertTrue(site.closeCamp()); clearInvocations(sites);
            assertFalse("A closed camp must reject confirmation of an earlier move preview", service.tryFinishMove(player));
        }
        assertFalse(service.isRelocating(player)); assertEquals(SiteStatus.CLOSED, site.getStatus());
        assertEquals(Integer.valueOf(20), site.getCampX()); assertEquals(original, site.getCampBlocks());
        assertEquals(1, kits.getAmount()); verify(sites, never()).save(any()); verify(proxy).remove();
        for (BlockCell cell : original) assertFalse(world.getBlockAt(cell.x(), cell.y(), cell.z()).getType().isAir());
    }

    @Test public void previewTickCancelsMoveWhenAnotherPlayerClosesTheCamp() {
        service.tryUseKit(player, null); service.beginRelocate(player, site);
        Block destination = spy(world.getBlockAt(-8, 3, 4)); doReturn(false).when(destination).isPassable();
        try (var rays = mockConstruction(BlockIterator.class, (iterator, context) -> {
            when(iterator.hasNext()).thenReturn(true, false); when(iterator.next()).thenReturn(destination);
        })) {
            service.pulse(); assertTrue(site.closeCamp()); clearInvocations(sites);
            service.pulse();
        }
        assertFalse("The next preview tick must abandon the closed camp", service.isRelocating(player));
        verify(proxy).remove(); verify(sites, never()).save(any()); assertEquals(Integer.valueOf(20), site.getCampX());
    }

    @Test public void renameUpdatesTheLoadedSignAndRefreshesLabelsAfterTheRecordIsSaved() {
        establishDirectly();
        Block signBlock = spy(world.getBlockAt(18, 4, 2)); signBlock.setType(Material.OAK_SIGN);
        Sign sign = spy((Sign) signBlock.getState());
        // MockBukkit stores real sign lines but leaves wax support unimplemented.
        doNothing().when(sign).setWaxed(true); doReturn(sign).when(signBlock).getState();
        doReturn(signBlock).when(world).getBlockAt(18, 4, 2);
        site.setCampSignX(18); site.setCampSignY(4); site.setCampSignZ(2);
        SiteLabelRefresh labels = mock(SiteLabelRefresh.class); service.setLabelRefresh(labels);
        service.beginRename(player, site); service.handleRenameChat(player, "  Ancient river settlement  ");
        assertEquals("Ancient river settlement", site.getName());
        assertArrayEquals(new String[]{"Ancient river", "settlement", "", ""}, sign.getSide(Side.FRONT).getLines());
        var order = inOrder(sites, labels, sign);
        order.verify(sites).save(site); order.verify(labels).retitle(site); order.verify(sign).setWaxed(true); order.verify(sign).update();
        assertFalse(service.isRenaming(player));
    }

    @Test public void legacyRotatedCampsInferWoolCellsFromTheirSignPosition() {
        establishDirectly();
        for (var facing : List.of(org.bukkit.block.BlockFace.SOUTH, org.bukkit.block.BlockFace.WEST,
                org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.EAST)) {
            site.setCampFacing(null);
            var sign = CampTemplate.rotate(-2, -2, facing);
            site.setCampSignX(20 + sign.getBlockX()); site.setCampSignY(4); site.setCampSignZ(4 + sign.getBlockZ());
            var pieces = CampTemplate.basic(Material.WHITE_WOOL, Material.RED_WOOL);
            for (var piece : pieces) {
                if (piece.wool() == null) continue;
                var offset = CampTemplate.rotate(piece.dx(), piece.dz(), facing);
                world.getBlockAt(20 + offset.getBlockX(), 4 + piece.dy(), 4 + offset.getBlockZ()).setType(piece.material());
            }
            service.applyCampWool(player, site, DyeColor.BLUE, CampWoolRole.PRIMARY);
            for (var piece : pieces) {
                if (piece.wool() == null) continue;
                var offset = CampTemplate.rotate(piece.dx(), piece.dz(), facing);
                assertEquals(piece.wool() == CampWoolRole.PRIMARY ? Material.BLUE_WOOL : Material.RED_WOOL,
                        world.getBlockAt(20 + offset.getBlockX(), 4 + piece.dy(), 4 + offset.getBlockZ()).getType());
            }
        }
        verify(sites, times(4)).save(site);
    }

    @Test public void worldChangeRestoresPreviewUsingTheWorldItWasDrawnIn() {
        service.pulse(); clearInvocations(player);
        WorldMock destination = new WorldMock(); destination.setName("destination");
        when(player.getWorld()).thenReturn(destination);
        service.clearPreviewFromWorld(player, world);
        ArgumentCaptor<Location> locations = ArgumentCaptor.forClass(Location.class);
        ArgumentCaptor<BlockData> data = ArgumentCaptor.forClass(BlockData.class);
        verify(player, atLeastOnce()).sendBlockChange(locations.capture(), data.capture());
        for (int i = 0; i < locations.getAllValues().size(); i++) {
            Location at = locations.getAllValues().get(i);
            assertSame(world, at.getWorld()); assertEquals(world.getBlockAt(at).getBlockData(), data.getAllValues().get(i));
        }
        clearInvocations(player); service.clearPreviewFromWorld(player, world);
        verify(player, never()).sendBlockChange(any(Location.class), any(BlockData.class));
    }

    @Test public void portallingAwayMidMoveNeverReadsTheDestinationWorldAtTheGhostsCoordinates() {
        service.tryUseKit(player, null); service.beginRelocate(player, site);
        Block aimed = spy(world.getBlockAt(-8, 3, 4)); doReturn(false).when(aimed).isPassable();
        try (var rays = mockConstruction(BlockIterator.class, (iterator, context) -> {
            when(iterator.hasNext()).thenReturn(true, false); when(iterator.next()).thenReturn(aimed);
        })) { service.pulse(); }
        clearInvocations(player);
        // PlayerChangedWorldEvent fires after the move, so the listener's clearSession sees the new world.
        WorldMock destination = spy(new WorldMock()); destination.setName("destination");
        when(player.getWorld()).thenReturn(destination);
        service.clearSession(player); service.clearPreviewFromWorld(player, world);
        assertFalse(service.isRelocating(player));
        verify(destination, never()).getBlockAt(anyInt(), anyInt(), anyInt());
        ArgumentCaptor<Location> sent = ArgumentCaptor.forClass(Location.class);
        verify(player, atLeast(0)).sendBlockChange(sent.capture(), any(BlockData.class));
        for (Location at : sent.getAllValues()) assertNotSame(destination, at.getWorld());
    }

    @Test public void shutdownRemovesOfflineViewersAimProxyAndBossBar() {
        service.tryUseKit(player, null); service.beginRelocate(player, site);
        Block destination = spy(world.getBlockAt(-8, 3, 4)); doReturn(false).when(destination).isPassable();
        try (var rays = mockConstruction(BlockIterator.class, (iterator, context) -> {
            when(iterator.hasNext()).thenReturn(true, false); when(iterator.next()).thenReturn(destination);
        })) { service.pulse(); }
        when(server.getPlayer(player.getUniqueId())).thenReturn(null);
        when(player.isOnline()).thenReturn(false);
        clearInvocations(proxy, bar, player); service.stop();
        verify(proxy).remove(); verify(bar).removeAll();
        verify(player, never()).sendBlockChange(any(Location.class), any(BlockData.class));
        assertEquals(SiteStatus.ESTABLISHED, site.getStatus());
    }

    @Test public void changingAimRestoresThePreviousGhostAndPuttingAwayTheKitRemovesTheHud() {
        service.pulse(); clearInvocations(player, bar);
        Block abandoned = world.getBlockAt(18, 4, 6);
        doReturn(world.getBlockAt(-8, 3, 4)).when(player).getTargetBlockExact(64); service.pulse();
        verify(player).sendBlockChange(abandoned.getLocation(), abandoned.getBlockData());
        assertEquals(Material.AIR, abandoned.getType()); assertEquals(SiteStatus.HIDDEN, site.getStatus());
        when(inventory.getItemInMainHand()).thenReturn(new ItemStack(Material.AIR));
        clearInvocations(player); service.pulse();
        verify(bar).removePlayer(player);
        ArgumentCaptor<Location> restored = ArgumentCaptor.forClass(Location.class);
        ArgumentCaptor<BlockData> data = ArgumentCaptor.forClass(BlockData.class);
        verify(player, atLeastOnce()).sendBlockChange(restored.capture(), data.capture());
        for (int n = 0; n < restored.getAllValues().size(); n++)
            assertEquals(world.getBlockAt(restored.getAllValues().get(n)).getBlockData(), data.getAllValues().get(n));
        verify(sites, never()).save(any()); assertEquals(2, kits.getAmount());
    }

    @Test public void relocationCanRecoverFromLookingIntoAirAndIgnoresHiddenCampAndPassableBlocks() {
        service.tryUseKit(player, null); service.beginRelocate(player, site);
        List<BlockCell> original = List.copyOf(site.getCampBlocks());
        BlockCell oldCell = original.getFirst(); Block oldCamp = world.getBlockAt(oldCell.x(), oldCell.y(), oldCell.z());
        Block air = world.getBlockAt(0, 5, 0), water = world.getBlockAt(0, 5, 1); water.setType(Material.WATER);
        Block grass = spy(world.getBlockAt(0, 5, 2)); grass.setType(Material.SHORT_GRASS); doReturn(true).when(grass).isPassable();
        Block target = spy(world.getBlockAt(-8, 3, 4)); doReturn(false).when(target).isPassable();
        java.util.concurrent.atomic.AtomicBoolean aimedAtGround = new java.util.concurrent.atomic.AtomicBoolean(false);
        try (var rays = mockConstruction(BlockIterator.class, (ray, context) -> {
            Iterator<Block> blocks = (aimedAtGround.get() ? List.of(oldCamp, air, water, grass, target) : List.of(oldCamp, air)).iterator();
            when(ray.hasNext()).thenAnswer(call -> blocks.hasNext()); when(ray.next()).thenAnswer(call -> blocks.next());
        })) {
            service.pulse(); verify(bar).setColor(org.bukkit.boss.BarColor.RED);
            assertTrue(service.tryFinishMove(player));
            assertEquals(original, site.getCampBlocks()); verify(player).sendMessage(CampPlacement.Issue.LOOK_MISS.message());
            aimedAtGround.set(true); service.tryUseKit(player, null);
        }
        assertEquals(Integer.valueOf(-8), site.getCampX()); assertFalse(service.isRelocating(player));
        assertEquals(1, kits.getAmount()); verify(player).sendMessage("Camp moved.");
    }

    @Test public void cancellingMoveRestoresSignTextAfterTheBlockPacketWithoutChangingTheSign() {
        service.tryUseKit(player, null); scheduled.clear();
        Block signBlock = world.getBlockAt(site.getCampSignX(), site.getCampSignY(), site.getCampSignZ());
        // MockBukkit's setBlockData placement does not materialise sign state; actual Paper does.
        signBlock.setType(Material.OAK_SIGN);
        Sign sign = (Sign) signBlock.getState(); sign.getSide(Side.FRONT).setLine(0, "River camp"); sign.update();
        service.beginRelocate(player, site);
        Block target = spy(world.getBlockAt(-8, 3, 4)); doReturn(false).when(target).isPassable();
        try (var rays = mockConstruction(BlockIterator.class, (ray, context) -> {
            when(ray.hasNext()).thenReturn(true, false); when(ray.next()).thenReturn(target);
        })) { service.pulse(); }
        when(inventory.getItemInMainHand()).thenReturn(new ItemStack(Material.AIR)); clearInvocations(player, bar);
        service.tryCancelMove(player);
        verify(player).sendBlockChange(signBlock.getLocation(), signBlock.getBlockData());
        verify(player, never()).sendSignChange(any(Location.class), any(String[].class)); verify(bar).removePlayer(player);
        assertFalse(scheduled.isEmpty()); List.copyOf(scheduled).forEach(Runnable::run);
        verify(player).sendSignChange(eq(signBlock.getLocation()), org.mockito.AdditionalMatchers.aryEq(new String[]{"River camp", "", "", ""}));
        assertEquals("River camp", ((Sign) signBlock.getState()).getSide(Side.FRONT).getLine(0));
        assertEquals(SiteStatus.ESTABLISHED, site.getStatus()); assertEquals(Integer.valueOf(20), site.getCampX());
    }

    @Test public void kitClicksOnAnExistingCampAndDisabledPlacementDoNotSpendItemsOrReplaceIt() {
        service.tryUseKit(player, null); clearInvocations(sites);
        List<BlockCell> original = List.copyOf(site.getCampBlocks());
        BlockCell cell = original.getFirst(); Block camp = world.getBlockAt(cell.x(), cell.y(), cell.z());
        when(sites.findLockedCampBlock("world", cell.x(), cell.y(), cell.z())).thenReturn(Optional.of(site));
        service.tryUseKit(player, camp);
        service.setSettings(settings(false, 1)); service.tryUseKit(player, null);
        assertEquals(original, site.getCampBlocks()); assertEquals(1, kits.getAmount()); verify(sites, never()).save(any());
    }

    @Test public void newCampRetainsTheDirectorsLastChosenWoolPalette() {
        service.tryUseKit(player, null);
        service.applyCampWool(player, site, DyeColor.BLUE, CampWoolRole.PRIMARY);
        service.applyCampWool(player, site, DyeColor.YELLOW, CampWoolRole.SECONDARY);
        assertTrue(site.closeCamp());
        Site next = hidden("world", 0, 1); next.confirmProspect(player.getUniqueId()); records.add(next);
        doReturn(world.getBlockAt(20, 3, 20)).when(player).getTargetBlockExact(64);
        service.tryUseKit(player, null);
        assertEquals(SiteStatus.ESTABLISHED, next.getStatus());
        assertEquals("BLUE", next.getCampWoolPrimary()); assertEquals("YELLOW", next.getCampWoolSecondary());
        assertTrue(next.getCampBlocks().stream().anyMatch(cell -> world.getBlockAt(cell.x(), cell.y(), cell.z()).getType() == Material.BLUE_WOOL));
        assertTrue(next.getCampBlocks().stream().anyMatch(cell -> world.getBlockAt(cell.x(), cell.y(), cell.z()).getType() == Material.YELLOW_WOOL));
        verify(sites).save(next);
    }

    @Test public void renameOfAnErasedRecordStopsAndAnUnloadedCampWorldStillKeepsTheNewName() {
        establishDirectly(); service.beginRename(player, site); records.remove(site);
        assertTrue(service.handleRenameChat(player, "Gone")); verify(player).sendMessage("That excavation is no longer active.");
        records.add(site); site.setCampSignX(18); site.setCampSignY(4); site.setCampSignZ(2);
        when(server.getWorld("world")).thenReturn(null); // The camp world is unloaded; the dossier still takes the name.
        service.beginRename(player, site); assertTrue(service.handleRenameChat(player, "Upper terrace"));
        assertEquals("Upper terrace", site.getName()); verify(sites).save(site); assertFalse(service.isRenaming(player));
        assertNotEquals(Material.OAK_SIGN, world.getBlockAt(18, 4, 2).getType());
        verify(player).sendMessage("Excavation renamed to Upper terrace.");
    }

    @Test public void eachSneakClickOnANewTickAdvancesTheTentColourEvenAwayFromAnyRuin() {
        when(player.isSneaking()).thenReturn(true);
        // doDaylightCycle is off, so the day clock stands still while server ticks pass.
        doReturn(world.getFullTime()).when(world).getFullTime();
        assertTrue(service.tryCycleWool(player)); MockBukkit.getMock().getScheduler().performOneTick(); assertTrue(service.tryCycleWool(player));
        site.setWorldName("other"); MockBukkit.getMock().getScheduler().performOneTick(); clearInvocations(player);
        assertTrue(service.tryCycleWool(player)); // No ruin in reach: nothing to redraw, but the choice is kept.
        verify(player, never()).sendBlockChange(any(Location.class), any(BlockData.class));
        site.setWorldName("world"); service.pulse(); service.tryUseKit(player, null);
        String third = CampWools.next(CampWools.next(CampWools.next("RED").name()).name()).name();
        assertEquals(third, site.getCampWoolSecondary());
        assertTrue(site.getCampBlocks().stream().anyMatch(cell -> world.getBlockAt(cell.x(), cell.y(), cell.z()).getType() == Material.valueOf(third + "_WOOL")));
    }

    @Test public void cyclingWoolDuringAMoveNeitherRecolorsTheCampNorDrawsAFirstPlantGhost() {
        service.tryUseKit(player, null); String secondary = site.getCampWoolSecondary(); service.beginRelocate(player, site);
        clearInvocations(player); service.cycleWool(player);
        verify(player, never()).sendBlockChange(any(Location.class), any(BlockData.class));
        assertEquals(secondary, site.getCampWoolSecondary()); assertTrue(service.isRelocating(player));
    }

    @Test public void recoloringWhileTheCampWorldIsUnloadedStillFilesTheChoice() {
        service.tryUseKit(player, null);
        List<Material> before = site.getCampBlocks().stream().map(cell -> world.getBlockAt(cell.x(), cell.y(), cell.z()).getType()).toList();
        when(server.getWorld("world")).thenReturn(null);
        service.applyCampWool(player, site, DyeColor.GREEN, CampWoolRole.SECONDARY);
        assertEquals("GREEN", site.getCampWoolSecondary()); verify(sites, times(2)).save(site);
        assertEquals(before, site.getCampBlocks().stream().map(cell -> world.getBlockAt(cell.x(), cell.y(), cell.z()).getType()).toList());
    }

    @Test public void dossiersWithoutAUsableOriginOrOrientationRecolorOnlyTheirRecordedCells() {
        establishDirectly();
        Block wool = world.getBlockAt(20, 5, 4), stone = world.getBlockAt(21, 5, 4);
        site.getCampBlocks().addAll(List.of(new BlockCell(20, 5, 4), new BlockCell(21, 5, 4)));
        // States a hand-edited dossier or an older plugin version can leave behind.
        List<Runnable> damage = List.of(
                () -> { site.setCampFacing("SOUTH"); site.setCampX(null); },
                () -> { site.setCampX(20); site.setCampFacing(null); site.setCampSignX(18); site.setCampSignY(5); site.setCampSignZ(2); },
                () -> { site.setCampSignX(20); site.setCampSignY(4); site.setCampSignZ(4); });
        List<DyeColor> colours = List.of(DyeColor.BLUE, DyeColor.GREEN, DyeColor.BLACK);
        for (int i = 0; i < damage.size(); i++) {
            wool.setType(CampWools.woolOf(site.getCampWoolSecondary(), DyeColor.RED)); stone.setType(Material.STONE); damage.get(i).run();
            service.applyCampWool(player, site, colours.get(i), CampWoolRole.SECONDARY);
            assertEquals(CampWools.woolOf(colours.get(i).name()), wool.getType()); assertEquals(Material.STONE, stone.getType());
        }
        assertEquals("BLACK", site.getCampWoolSecondary());
    }

    @Test public void unreadableStoredFacingFallsBackToTheSignPosition() {
        establishDirectly(); var sign = CampTemplate.rotate(-2, -2, org.bukkit.block.BlockFace.WEST);
        site.setCampSignX(20 + sign.getBlockX()); site.setCampSignY(4); site.setCampSignZ(4 + sign.getBlockZ());
        for (DyeColor colour : List.of(DyeColor.CYAN, DyeColor.LIME)) {
            site.setCampFacing(colour == DyeColor.CYAN ? "UP" : "NORTH_EAST");
            service.applyCampWool(player, site, colour, CampWoolRole.PRIMARY);
            for (var piece : CampTemplate.basic()) {
                if (piece.wool() != CampWoolRole.PRIMARY) continue;
                var offset = CampTemplate.rotate(piece.dx(), piece.dz(), org.bukkit.block.BlockFace.WEST);
                assertEquals(CampWools.woolOf(colour.name()), world.getBlockAt(20 + offset.getBlockX(), 4 + piece.dy(), 4 + offset.getBlockZ()).getType());
            }
        }
    }

    @Test public void plantingOverPartlyOpenedGroundCountsEachDisturbedFindWithoutCallingItLost() {
        for (int n = 0; n < 2; n++) {
            BuriedFind find = new BuriedFind(); find.setId(UUID.randomUUID()); find.setArtifactId("coin");
            find.getCells().addAll(List.of(new BlockCell(n * 2, 1, 1), new BlockCell(n * 2, 1, 2))); site.getFinds().add(find);
            world.getBlockAt(n * 2, 1, 1).setType(Material.AIR);
        }
        service.tryUseKit(player, null);
        verify(player).sendMessage("2 finds were already disturbed before this dig opened.");
        assertTrue(site.getFinds().stream().allMatch(find -> find.isDisturbedBeforeDig() && find.getState() != FindState.LOST));
    }

    @Test public void aMoveEndsOnTheNextTickWhenTheRecordIsErasedOrTheCampChangesHands() {
        service.tryUseKit(player, null); List<BlockCell> original = List.copyOf(site.getCampBlocks());
        service.beginRelocate(player, site); records.remove(site); service.pulse(); assertFalse(service.isRelocating(player));
        records.add(site); service.beginRelocate(player, site); site.setDirector(UUID.randomUUID()); service.pulse();
        assertFalse(service.isRelocating(player)); assertEquals(original, site.getCampBlocks()); verify(sites, times(1)).save(site);
    }

    @Test public void aFartherRuinListedLaterDoesNotDisplaceTheNearestOne() {
        Site farther = hidden("world", 0, 1); farther.confirmProspect(player.getUniqueId()); records.add(farther);
        service.tryUseKit(player, null);
        assertEquals(SiteStatus.ESTABLISHED, site.getStatus()); assertEquals(SiteStatus.HIDDEN, farther.getStatus());
    }

    @Test public void everyUnsupportedFloorPieceIsDrawnInvalidAndOneReasonIsShown() {
        for (int x = 16; x <= 31; x++) for (int z = 0; z <= 15; z++) world.getBlockAt(x, 3, z).setType(Material.AIR);
        doReturn(world.getBlockAt(20, 2, 4)).when(player).getTargetBlockExact(64); world.getBlockAt(20, 2, 4).setType(Material.STONE);
        world.getBlockAt(20, 2, 4).setType(Material.STONE); service.pulse(); clearInvocations(player);
        world.getBlockAt(20, 2, 4).setType(Material.AIR); doReturn(world.getBlockAt(20, 3, 4)).when(player).getTargetBlockExact(64);
        service.pulse();
        ArgumentCaptor<BlockData> shown = ArgumentCaptor.forClass(BlockData.class);
        verify(player, atLeastOnce()).sendBlockChange(any(Location.class), shown.capture());
        long floor = CampTemplate.basic().stream().filter(piece -> piece.dy() == 0).count();
        assertTrue(shown.getAllValues().stream().filter(data -> data.getMaterial() == Material.RED_STAINED_GLASS).count() >= floor);
        verify(bar).setTitle(org.bukkit.ChatColor.RED + CampPlacement.Issue.UNSUPPORTED.message());
        service.tryUseKit(player, null); assertEquals(SiteStatus.HIDDEN, site.getStatus()); assertEquals(2, kits.getAmount());
    }

    @Test public void aGhostDrawnForOneRuinIsNeverPlantedForAnotherThatBecameNearer() {
        service.pulse(); verify(bar).setColor(org.bukkit.boss.BarColor.GREEN);
        Site other = hidden("world", 0, 3); other.confirmProspect(player.getUniqueId()); records.add(other);
        when(player.getLocation()).thenAnswer(call -> new Location(world, 8, 4, 50)); // Walked away before the next preview tick.
        service.tryUseKit(player, null);
        assertEquals(SiteStatus.HIDDEN, site.getStatus()); assertEquals(SiteStatus.HIDDEN, other.getStatus());
        verify(player).sendMessage(CampPlacement.Issue.NOT_NEIGHBOR.message()); assertEquals(2, kits.getAmount());
    }

    @Test public void aDroppedMoveProxyIsRespawnedAndCancellingCopesWithAVanishedStand() {
        service.tryUseKit(player, null); assertFalse(service.tryFinishMoveOnAimProxy(player, proxy));
        service.beginRelocate(player, site);
        Block destination = spy(world.getBlockAt(-8, 3, 4)); doReturn(false).when(destination).isPassable();
        try (var rays = mockConstruction(BlockIterator.class, (iterator, context) -> {
            when(iterator.hasNext()).thenReturn(true, false); when(iterator.next()).thenReturn(destination);
        })) {
            service.pulse(); when(proxy.isValid()).thenReturn(false); // A chunk unload removed the non-persistent stand.
            service.pulse(); verify(world, times(2)).spawn(any(Location.class), eq(ArmorStand.class), any(Consumer.class));
            verify(proxy, never()).teleport(any(Location.class));
        }
        when(server.getEntity(proxy.getUniqueId())).thenReturn(null);
        assertTrue(service.tryCancelMove(player)); verify(proxy, never()).remove();
        assertFalse(service.handleMoveAimProxyAttack(player, proxy)); assertEquals(Integer.valueOf(20), site.getCampX());
    }

    @Test public void campsMayStandInAnySideChunkButNotTwoChunksAway() {
        assertRejected(world.getBlockAt(40, 3, 4), CampPlacement.Issue.NOT_NEIGHBOR);
        Block south = world.getBlockAt(4, 3, 20); when(player.getTargetBlockExact(64)).thenReturn(south); service.tryUseKit(player, null);
        assertEquals(SiteStatus.ESTABLISHED, site.getStatus());
        assertEquals(Integer.valueOf(0), site.getEstablishmentChunkX()); assertEquals(Integer.valueOf(1), site.getEstablishmentChunkZ());
    }

    @Test public void aSideChunkHoldingAnotherExcavationsCampIsOccupied() {
        Site neighbour = hidden("world", 2, 0); neighbour.establish(UUID.randomUUID(), 1, 0, 24, 4, 4); records.add(neighbour);
        assertRejected(world.getBlockAt(20, 3, 4), CampPlacement.Issue.OCCUPIED);
        assertEquals(SiteStatus.HIDDEN, site.getStatus()); assertEquals(2, kits.getAmount());
    }

    @Test public void aCampCanBeShiftedAcrossItsOwnFootprint() {
        service.tryUseKit(player, null); service.beginRelocate(player, site);
        Block shifted = spy(world.getBlockAt(21, 3, 4)); doReturn(false).when(shifted).isPassable();
        try (var rays = mockConstruction(BlockIterator.class, (iterator, context) -> {
            when(iterator.hasNext()).thenReturn(true, false); when(iterator.next()).thenReturn(shifted);
        })) { service.pulse(); assertTrue(service.tryFinishMove(player)); }
        assertEquals(Integer.valueOf(21), site.getCampX()); assertEquals(Integer.valueOf(1), site.getEstablishmentChunkX());
        for (BlockCell cell : site.getCampBlocks()) assertFalse(world.getBlockAt(cell.x(), cell.y(), cell.z()).getType().isAir());
        service.beginRelocate(player, site); // A four-block shift lands the new side wall on the old one.
        Block further = spy(world.getBlockAt(25, 3, 4)); doReturn(false).when(further).isPassable();
        try (var rays = mockConstruction(BlockIterator.class, (iterator, context) -> {
            when(iterator.hasNext()).thenReturn(true, false); when(iterator.next()).thenReturn(further);
        })) { service.pulse(); assertTrue(service.tryFinishMove(player)); }
        assertEquals(Integer.valueOf(25), site.getCampX());
        for (BlockCell cell : site.getCampBlocks()) assertFalse(world.getBlockAt(cell.x(), cell.y(), cell.z()).getType().isAir());
        verify(player, times(2)).sendMessage("Camp moved."); assertEquals(1, kits.getAmount());
    }

    @Test public void aPieceCannotBePlantedIntoStandingWater() {
        Block pool = spy(world.getBlockAt(18, 4, 6)); pool.setType(Material.WATER);
        // Paper reports water as passable (it has no collision box); MockBukkit does not model passability.
        doReturn(true).when(pool).isPassable(); doReturn(pool).when(world).getBlockAt(18, 4, 6);
        assertRejected(world.getBlockAt(20, 3, 4), CampPlacement.Issue.BLOCKED);
        assertEquals(2, kits.getAmount()); assertEquals(SiteStatus.HIDDEN, site.getStatus());
    }

    @Test public void moveAimSkipsGrassButRestsOnAnOpenGateLikeAnySolidBlock() {
        service.tryUseKit(player, null); service.beginRelocate(player, site);
        Block grass = spy(world.getBlockAt(-8, 8, 4)); grass.setType(Material.SHORT_GRASS); doReturn(true).when(grass).isPassable();
        Block gate = spy(world.getBlockAt(-8, 3, 4)); gate.setType(Material.OAK_FENCE_GATE);
        doReturn(true).when(gate).isPassable(); // An open gate has no collision box.
        try (var rays = mockConstruction(BlockIterator.class, (ray, context) -> {
            Iterator<Block> blocks = List.of(grass, gate, world.getBlockAt(-8, 2, 4)).iterator();
            when(ray.hasNext()).thenAnswer(call -> blocks.hasNext()); when(ray.next()).thenAnswer(call -> blocks.next());
        })) { service.pulse(); assertTrue(service.tryFinishMove(player)); }
        assertEquals(Integer.valueOf(4), site.getCampY()); assertEquals(Integer.valueOf(-1), site.getEstablishmentChunkX());
        verify(player).sendMessage("Camp moved.");
    }

    @Test public void signTextIsNotResentToAViewerWhoLeftBeforeTheFollowingTick() {
        service.tryUseKit(player, null); scheduled.clear();
        Block signBlock = world.getBlockAt(site.getCampSignX(), site.getCampSignY(), site.getCampSignZ());
        signBlock.setType(Material.OAK_SIGN); // MockBukkit's setBlockData placement does not materialise sign state.
        service.beginRelocate(player, site);
        Block target = spy(world.getBlockAt(-8, 3, 4)); doReturn(false).when(target).isPassable();
        try (var rays = mockConstruction(BlockIterator.class, (ray, context) -> {
            when(ray.hasNext()).thenReturn(true, false); when(ray.next()).thenReturn(target);
        })) { service.pulse(); }
        service.tryCancelMove(player); assertFalse(scheduled.isEmpty());
        when(player.isOnline()).thenReturn(false); List.copyOf(scheduled).forEach(Runnable::run);
        verify(player, never()).sendSignChange(any(Location.class), any(String[].class));
    }

    @Test public void erasingAnotherRuinLeavesThisDirectorsMoveAndRenameRunning() {
        service.tryUseKit(player, null); service.beginRelocate(player, site);
        service.abortSessionsFor(UUID.randomUUID()); assertTrue(service.isRelocating(player));
        service.cancelRelocate(player); service.beginRename(player, site);
        service.abortSessionsFor(UUID.randomUUID()); assertTrue(service.isRenaming(player));
    }

    @Test public void aMoveNeverCarriesTheCampIntoAWorldTheDossierDoesNotNameAfterAReload() {
        service.tryUseKit(player, null); service.beginRelocate(player, site); clearInvocations(sites);
        site.setWorldName("world_nether"); // Staff edit the dossier's world and run /archaeo reload mid-move.
        Block destination = spy(world.getBlockAt(-8, 3, 4)); doReturn(false).when(destination).isPassable();
        try (var rays = mockConstruction(BlockIterator.class, (iterator, context) -> {
            when(iterator.hasNext()).thenReturn(true, false); when(iterator.next()).thenReturn(destination);
        })) { service.pulse(); assertTrue(service.tryFinishMove(player)); }
        verify(bar).setTitle(org.bukkit.ChatColor.RED + CampPlacement.Issue.NOT_NEIGHBOR.message());
        ArgumentCaptor<BlockData> shown = ArgumentCaptor.forClass(BlockData.class);
        verify(player, atLeastOnce()).sendBlockChange(any(Location.class), shown.capture()); // No ruin frame in the wrong world.
        assertTrue(shown.getAllValues().stream().noneMatch(data -> data.getMaterial() == Material.LIGHT_BLUE_STAINED_GLASS));
        verify(player).sendMessage(CampPlacement.Issue.NOT_NEIGHBOR.message());
        assertEquals(Integer.valueOf(20), site.getCampX()); assertTrue(service.isRelocating(player)); verify(sites, never()).save(any());
    }

    @Test public void campsArePlantedOverTallGrassAndReplaceIt() {
        Block tuft = spy(world.getBlockAt(18, 4, 6)); tuft.setType(Material.SHORT_GRASS);
        // Paper reports grass as passable; MockBukkit does not model passability.
        doReturn(true).when(tuft).isPassable(); doReturn(tuft).when(world).getBlockAt(18, 4, 6);
        service.tryUseKit(player, null);
        assertEquals(SiteStatus.ESTABLISHED, site.getStatus()); assertTrue(site.isCampBlock(18, 4, 6));
        assertNotEquals(Material.SHORT_GRASS, tuft.getType());
    }

    private void assertRejected(Block target, CampPlacement.Issue issue) {
        when(player.getTargetBlockExact(64)).thenReturn(target); service.tryUseKit(player, null);
        verify(player).sendMessage(issue.message());
    }
    private void establishDirectly() { site.establish(player.getUniqueId(), 1, 0, 20, 4, 4); }
    private static EstablishSettings settings(boolean enabled, int max) {
        return new EstablishSettings(enabled, true, Material.RED_STAINED_GLASS, Material.LIGHT_BLUE_STAINED_GLASS, 18, max);
    }
    private static Site hidden(String world, int x, int z) {
        Site site = new Site(); site.setId(UUID.randomUUID()); site.setWorldName(world); site.setChunkX(x); site.setChunkZ(z);
        site.setInterest(InterestLevel.LOW); site.setName("Old camp");
        return site;
    }
}
