package net.tfminecraft.archaeo.site;

import net.tfminecraft.archaeo.config.*;
import net.tfminecraft.archaeo.establish.*;
import net.tfminecraft.archaeo.excavation.*;
import net.tfminecraft.archaeo.item.*;
import net.tfminecraft.archaeo.model.*;
import net.tfminecraft.archaeo.sketch.SketchService;
import org.bukkit.*;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SiteStoredItemsTest {
    private ServerMock server;
    private JavaPlugin plugin;
    private WorldMock world;
    private PlayerMock player;
    private Site site;
    private RecoveredFindItem recovered;
    private CatalogRegistry catalogs;
    private SketchService sketch;
    private SiteRepository sites;
    private EstablishService establish;
    private RecoverService recover;
    private RuinAutoSpawner autoRuins;
    private FindDustService dust;
    private CampClosure closure;
    private ItemStack artifact;
    private ItemStack unrelated;
    private Inventory chest;
    private Item dropped;
    private ItemFrame frame;
    private ItemDisplay display;
    private ArmorStand stand;
    private EntityEquipment equipment;

    @Before public void setUp() {
        server = MockBukkit.mock(); plugin = MockBukkit.createMockPlugin();
        world = spy(new WorldMock()); server.addWorld(world); player = server.addPlayer();
        player.teleport(new Location(world, 2, 5, 2)); player.openInventory(Bukkit.createInventory(null, 9));
        recovered = new RecoveredFindItem(plugin); catalogs = mock(CatalogRegistry.class);
        when(catalogs.pick()).thenReturn(PickSettings.defaults());
        when(catalogs.materialOf(any())).thenReturn(new FindMaterial("ceramic", "Ceramic", 1, null, List.of()));
        ArtifactTemplate template = new ArtifactTemplate("pot", "Pot", 1, 1, "ceramic", "", false, 1,
                Set.of(), Set.of(), FindProfile.OBJECT, List.of(), "");
        when(catalogs.artifact("pot")).thenReturn(template);
        site = new Site(); site.setId(UUID.randomUUID()); site.setName("Old camp"); site.setWorldName(world.getName());
        site.establish(player.getUniqueId(), 0, 0, 2, 4, 2);
        BuriedFind find = new BuriedFind(); find.setId(UUID.randomUUID()); find.setArtifactId("pot");
        find.setState(FindState.RECOVERED); find.setStratumId("I"); find.setFindNumber(1); site.getFinds().add(find);
        artifact = recovered.create(template, site, find, player.getUniqueId(), "Good", false, catalogs);
        Site another = new Site(); another.setId(UUID.randomUUID()); another.setName("Other camp"); another.getFinds().add(find);
        unrelated = recovered.create(template, another, find, player.getUniqueId(), "Good", false, catalogs);
        sketch = mock(SketchService.class); sites = mock(SiteRepository.class); establish = mock(EstablishService.class);
        recover = mock(RecoverService.class); autoRuins = mock(RuinAutoSpawner.class); dust = mock(FindDustService.class);
        closure = mock(CampClosure.class); when(closure.archiveBook()).thenReturn(new CampArchiveBook(plugin));
        chest = Bukkit.createInventory(null, 27);
        Chest tile = mock(Chest.class); when(tile.getInventory()).thenReturn(chest);
        Chunk chunk = mock(Chunk.class); when(chunk.getTileEntities()).thenReturn(new BlockState[]{tile});
        doReturn(new Chunk[]{chunk}).when(world).getLoadedChunks();
        dropped = mock(Item.class); when(dropped.getItemStack()).thenReturn(artifact.clone());
        frame = mock(ItemFrame.class); when(frame.getItem()).thenReturn(artifact.clone());
        display = mock(ItemDisplay.class); when(display.getItemStack()).thenReturn(artifact.clone());
        stand = mock(ArmorStand.class); equipment = mock(EntityEquipment.class); when(stand.getEquipment()).thenReturn(equipment);
        when(equipment.getItem(EquipmentSlot.HEAD)).thenReturn(artifact.clone());
        doReturn(List.of(dropped)).when(world).getEntitiesByClass(Item.class);
        doReturn(List.of(frame)).when(world).getEntitiesByClass(ItemFrame.class);
        doReturn(List.of(display)).when(world).getEntitiesByClass(ItemDisplay.class);
        doReturn(List.of(stand)).when(world).getEntitiesByClass(ArmorStand.class);
    }
    @After public void tearDown() { MockBukkit.unmock(); }

    @Test public void renameRefreshesLoadedStorageAndDisplaysButPreservesOtherExcavations() {
        player.getInventory().setItem(0, artifact.clone()); player.getInventory().setItem(1, unrelated.clone());
        player.getEnderChest().setItem(0, artifact.clone()); player.setItemOnCursor(artifact.clone());
        chest.setItem(0, artifact.clone()); chest.setItem(1, unrelated.clone());
        ItemStack box = packed(artifact.clone(), unrelated.clone()); player.getInventory().setItem(2, box);
        site.setName("River camp"); new SiteLabelRefresh(plugin, recovered, catalogs, sketch).retitle(site);
        for (ItemStack stack : List.of(player.getInventory().getItem(0), player.getEnderChest().getItem(0), player.getItemOnCursor(), chest.getItem(0), unpack(player.getInventory().getItem(2), 0))) {
            assertEquals("River camp", recovered.siteNameOf(stack));
        }
        assertEquals(unrelated, player.getInventory().getItem(1)); assertEquals(unrelated, chest.getItem(1));
        assertEquals(unrelated, unpack(player.getInventory().getItem(2), 1));
        verify(dropped).setItemStack(argThat(stack -> "River camp".equals(recovered.siteNameOf(stack))));
        verify(frame).setItem(argThat(stack -> "River camp".equals(recovered.siteNameOf(stack))));
        verify(display).setItemStack(argThat(stack -> "River camp".equals(recovered.siteNameOf(stack))));
        verify(equipment).setItem(eq(EquipmentSlot.HEAD), argThat(stack -> "River camp".equals(recovered.siteNameOf(stack))));
    }

    @Test public void purgeRemovesOnlySiteItemsAcrossStorageAndDisplaysAndCancelsItsServices() {
        player.getInventory().setItem(0, artifact.clone()); player.getInventory().setItem(1, unrelated.clone());
        player.getInventory().setItem(2, packed(artifact.clone(), unrelated.clone()));
        player.getEnderChest().setItem(0, closure.archiveBook().create(player, site));
        player.setItemOnCursor(new FindReportBook(plugin).create(player, site, catalogs));
        chest.setItem(0, artifact.clone()); chest.setItem(1, unrelated.clone());
        BlockCell camp = new BlockCell(2, 4, 2); site.getCampBlocks().add(camp); world.getBlockAt(2, 4, 2).setType(Material.WHITE_WOOL);
        world.getBlockAt(3, 4, 2).setType(Material.STONE);
        new SitePurge(plugin, sites, autoRuins, establish, recover, dust, recovered, closure, sketch).erase(site);
        assertNull(player.getInventory().getItem(0)); assertNull(player.getEnderChest().getItem(0));
        assertTrue(player.getItemOnCursor() == null || player.getItemOnCursor().getType().isAir());
        assertNull(chest.getItem(0)); assertEquals(unrelated, chest.getItem(1));
        assertNull(unpack(player.getInventory().getItem(2), 0)); assertEquals(unrelated, unpack(player.getInventory().getItem(2), 1));
        assertEquals(unrelated, player.getInventory().getItem(1));
        assertEquals(Material.AIR, world.getBlockAt(2, 4, 2).getType()); assertEquals(Material.STONE, world.getBlockAt(3, 4, 2).getType());
        verify(dropped).remove(); verify(frame).setItem(null); verify(display).setItemStack(null); verify(equipment).setItem(EquipmentSlot.HEAD, null);
        var order = inOrder(establish, recover, autoRuins, sites, dust);
        order.verify(establish).abortSessionsFor(site.getId()); order.verify(recover).abortForSite(site.getId());
        order.verify(autoRuins).forgetChunk(world.getName(), 0, 0); order.verify(sites).erase(site); order.verify(dust).syncTimer();
    }

    @Test public void purgeClosesOnlyTheDeletedSitesOpenBoards() {
        CampWoolPicker own = new CampWoolPicker(site.getId(), CampWoolRole.PRIMARY); own.open(player, site);
        PlayerMock other = server.addPlayer(); CampWoolPicker keep = new CampWoolPicker(UUID.randomUUID(), CampWoolRole.PRIMARY); keep.open(other, site);
        new SitePurge(plugin, sites, autoRuins, establish, recover, dust, recovered, closure, sketch).erase(site);
        assertNotSame(own.getInventory(), player.getOpenInventory().getTopInventory());
        assertSame(keep, other.getOpenInventory().getTopInventory().getHolder());
    }

    @Test public void purgeInvalidatesEveryArchiveAndCabinetWindowForThatSite() {
        UUID findId = site.getFinds().getFirst().getId();
        var cabinet = mock(net.tfminecraft.archaeo.sketch.SketchCabinet.class);
        when(cabinet.siteId()).thenReturn(site.getId());
        var lab = mock(net.tfminecraft.archaeo.sketch.CabinetLabBoard.class);
        when(lab.siteId()).thenReturn(site.getId());
        List<InventoryHolder> windows = List.of(
                new CampBoard(site.getId(), false, false, catalogs),
                new CampStaffBoard(site.getId(), false, 20),
                new CampWorkerBoard(site.getId(), player.getUniqueId(), false),
                new CampFindsBoard(site.getId(), false, catalogs, recovered),
                new CampFindBoard(site.getId(), findId, catalogs),
                new CampIdentifyBoard(site.getId(), findId, catalogs, recovered), cabinet, lab);
        for (InventoryHolder holder : windows) {
            // Purge acts on the holder's archive identity; presentation is covered by each board's tests.
            Inventory window = Bukkit.createInventory(holder, 9); player.openInventory(window);
            new SitePurge(plugin, sites, autoRuins, establish, recover, dust, recovered, closure, sketch).erase(site);
            assertNotSame(holder.getClass().getSimpleName(), window, player.getOpenInventory().getTopInventory());
        }
    }

    @Test public void renameLeavesUnrelatedEmptyAndPlainBlockHoldersAloneButRelabelsRegisteredSketches() {
        ItemStack drawing = new ItemStack(Material.FILLED_MAP), hive = new ItemStack(Material.BEEHIVE), box = packed(unrelated.clone(), null);
        assertTrue(hive.getItemMeta() instanceof BlockStateMeta);
        when(sketch.retitle(argThat(stack -> stack != null && stack.getType() == Material.FILLED_MAP), eq(site))).thenReturn(true);
        when(dropped.getItemStack()).thenReturn(unrelated.clone()); when(frame.getItem()).thenReturn(new ItemStack(Material.AIR));
        when(display.getItemStack()).thenReturn(unrelated.clone()); when(equipment.getItem(EquipmentSlot.HEAD)).thenReturn(drawing);
        tiles(mock(org.bukkit.block.Sign.class));
        player.getInventory().setItem(0, hive.clone()); player.getInventory().setItem(1, box.clone()); player.setItemOnCursor(null);
        site.setName("River camp"); new SiteLabelRefresh(plugin, recovered, catalogs, sketch).retitle(site);
        verify(equipment).setItem(EquipmentSlot.HEAD, drawing);
        verify(dropped, never()).setItemStack(any()); verify(frame, never()).setItem(any()); verify(display, never()).setItemStack(any());
        assertEquals(hive, player.getInventory().getItem(0)); assertEquals(box, player.getInventory().getItem(1));
        assertEquals(unrelated, unpack(player.getInventory().getItem(1), 0));
        assertTrue(player.getItemOnCursor() == null || player.getItemOnCursor().getType().isAir());
    }

    @Test public void purgeStripsRegisteredSketchesButLeavesUnrelatedAndPlainBlockItems() {
        ItemStack drawing = new ItemStack(Material.FILLED_MAP), hive = new ItemStack(Material.BEEHIVE), box = packed(unrelated.clone(), null);
        when(sketch.siteIdOf(argThat(stack -> stack != null && stack.getType() == Material.FILLED_MAP))).thenReturn(site.getId());
        when(dropped.getItemStack()).thenReturn(unrelated.clone()); when(frame.getItem()).thenReturn(new ItemStack(Material.AIR));
        when(display.getItemStack()).thenReturn(unrelated.clone()); when(equipment.getItem(EquipmentSlot.HEAD)).thenReturn(drawing.clone());
        tiles(mock(org.bukkit.block.Sign.class));
        player.getInventory().setItem(0, hive.clone()); player.getInventory().setItem(1, box.clone()); player.getInventory().setItem(2, drawing.clone());
        new SitePurge(plugin, sites, autoRuins, establish, recover, dust, recovered, closure, sketch).erase(site);
        assertNull(player.getInventory().getItem(2)); verify(equipment).setItem(EquipmentSlot.HEAD, null);
        assertEquals(hive, player.getInventory().getItem(0)); assertEquals(box, player.getInventory().getItem(1));
        verify(dropped, never()).remove(); verify(frame, never()).setItem(any()); verify(display, never()).setItemStack(any());
        verify(sites).erase(site);
    }

    @Test public void purgeOfACampWhoseWorldIsUnavailableWarnsAndStillErasesTheDossier() {
        java.util.logging.Logger logger = mock(java.util.logging.Logger.class);
        JavaPlugin quiet = spy(plugin); doReturn(logger).when(quiet).getLogger();
        site.getCampBlocks().add(new BlockCell(2, 4, 2)); world.getBlockAt(2, 4, 2).setType(Material.WHITE_WOOL);
        for (String worldName : Arrays.asList("unloaded_world", null)) {
            site.setWorldName(worldName);
            new SitePurge(quiet, sites, autoRuins, establish, recover, dust, recovered, closure, sketch).erase(site);
            verify(logger).warning("Could not remove camp blocks: world " + worldName + " is not loaded.");
        }
        assertEquals(Material.WHITE_WOOL, world.getBlockAt(2, 4, 2).getType());
        verify(sites, times(2)).erase(site); verify(dust, times(2)).syncTimer();
    }

    /** Replaces the loaded tile entities with the setUp chest plus {@code extra}. */
    private void tiles(BlockState extra) {
        Chest tile = mock(Chest.class); when(tile.getInventory()).thenReturn(chest);
        Chunk chunk = mock(Chunk.class); when(chunk.getTileEntities()).thenReturn(new BlockState[]{extra, tile});
        doReturn(new Chunk[]{chunk}).when(world).getLoadedChunks();
    }

    private static ItemStack packed(ItemStack first, ItemStack second) {
        ItemStack box = new ItemStack(Material.SHULKER_BOX); BlockStateMeta meta = (BlockStateMeta) box.getItemMeta();
        ShulkerBox state = (ShulkerBox) meta.getBlockState(); state.getInventory().setItem(0, first); state.getInventory().setItem(1, second);
        meta.setBlockState(state); box.setItemMeta(meta); return box;
    }
    private static ItemStack unpack(ItemStack box, int slot) {
        return ((ShulkerBox) ((BlockStateMeta) box.getItemMeta()).getBlockState()).getInventory().getItem(slot);
    }
}
