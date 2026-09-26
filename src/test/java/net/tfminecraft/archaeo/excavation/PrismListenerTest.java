package net.tfminecraft.archaeo.excavation;

import net.tfminecraft.archaeo.model.*;
import net.tfminecraft.archaeo.config.PickSettings;
import net.tfminecraft.archaeo.site.SiteRepository;
import org.bukkit.ExplosionResult;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.block.BlockMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PrismListenerTest {
    private ServerMock server;
    private SnapshotWorld world;
    private SiteRepository sites;
    private PrismListener listener;
    private Player player;
    private PlayerInventory inventory;
    private Site site;
    private BuriedFind find;
    private Block block;

    @Before
    public void setup() {
        server = MockBukkit.mock();
        world = new SnapshotWorld();
        world.setName("prism");
        server.addWorld(world);
        sites = mock(SiteRepository.class);
        DigTools tools = mock(DigTools.class);
        when(tools.isAllowed(any())).thenAnswer(invocation -> {
            ItemStack item = invocation.getArgument(0);
            return item != null && item.getType() == Material.IRON_PICKAXE;
        });
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(new ItemStack(Material.STICK));
        site = new Site();
        site.setId(UUID.randomUUID());
        site.setStatus(SiteStatus.ESTABLISHED);
        site.setWorldName(world.getName());
        site.setDirector(player.getUniqueId());
        StratumBand band = new StratumBand();
        band.setId("I");
        band.setPresent(true);
        band.setMinY(39);
        band.setMaxY(41);
        site.getStrata().put("I", band);
        find = new BuriedFind();
        find.setId(UUID.randomUUID());
        for (int x = 8; x <= 10; x++) {
            find.getCells().add(new BlockCell(x, 40, 8));
            stone(x, 40, 8);
        }
        site.getFinds().add(find);
        block = world.getBlockAt(8, 40, 8);
        when(sites.findPrism(anyString(), anyInt(), anyInt(), anyInt())).thenAnswer(invocation ->
                world.getName().equals(invocation.getArgument(0))
                        && site.isInPrism(invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3))
                        ? Optional.of(site) : Optional.empty());
        when(sites.findEstablishedPrism(anyString(), anyInt(), anyInt(), anyInt())).thenAnswer(invocation ->
                site.getStatus() == SiteStatus.ESTABLISHED
                        && world.getName().equals(invocation.getArgument(0))
                        && site.isInPrism(invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3))
                        ? Optional.of(site) : Optional.empty());
        listener = new PrismListener(sites, tools, true);
        server.getPluginManager().registerEvents(listener, MockBukkit.createMockPlugin());
    }

    @After
    public void teardown() { MockBukkit.unmock(); }

    @Test
    public void protectedBreakAndDamageAreCancelledWithoutWoundingAndWarningIsThrottled() {
        for (int i = 0; i < 2; i++) {
            BlockBreakEvent event = new BlockBreakEvent(block, player);
            server.getPluginManager().callEvent(event);
            assertTrue(event.isCancelled());
        }
        BlockDamageEvent damage = new BlockDamageEvent(player, block, new ItemStack(Material.STICK), false);
        server.getPluginManager().callEvent(damage);
        assertTrue(damage.isCancelled());
        assertEquals(100, find.getConservation());
        assertFalse(site.stratumAt(40).isDisturbed());
        verify(player, times(1)).sendMessage("Use an excavation tool.");
        verify(sites, never()).save(any());
    }

    @Test
    public void whitelistedToolsAreDelegatedToTheHandPickListener() {
        when(inventory.getItemInMainHand()).thenReturn(new ItemStack(Material.IRON_PICKAXE));
        BlockBreakEvent event = new BlockBreakEvent(block, player);
        listener.onBreak(event);
        assertFalse(event.isCancelled());
        BlockDamageEvent damage = new BlockDamageEvent(player, block, new ItemStack(Material.IRON_PICKAXE), false);
        listener.onDamage(damage);
        assertFalse(damage.isCancelled());
        verify(player, never()).sendMessage(anyString());
    }

    @Test
    public void offPrismAndDecorativeBlocksAreNotLocked() {
        for (Block candidate : List.of(stone(16, 40, 8), stone(8, 42, 8))) {
            BlockBreakEvent event = new BlockBreakEvent(candidate, player);
            server.getPluginManager().callEvent(event);
            assertFalse(event.isCancelled());
        }
        block.setType(Material.TORCH);
        BlockDamageEvent decoration = new BlockDamageEvent(player, block, new ItemStack(Material.STICK), false);
        server.getPluginManager().callEvent(decoration);
        assertFalse(decoration.isCancelled());
        assertEquals(100, find.getConservation());
        verify(sites, never()).save(any());
    }

    @Test
    public void unprotectedBreakGrazesFindDisturbsLayerAndOnlySavesFreshChanges() {
        listener.setProtectDigSite(false);
        BlockBreakEvent first = new BlockBreakEvent(block, player);
        server.getPluginManager().callEvent(first);
        assertFalse(first.isCancelled());
        assertEquals(67, find.getConservation());
        assertTrue(site.stratumAt(40).isDisturbed());
        assertEquals(1, site.staffLog(player.getUniqueId()).getFindsDamaged());
        verify(sites).save(site);
        server.getPluginManager().callEvent(new BlockBreakEvent(block, player));
        assertEquals(67, find.getConservation());
        verify(sites, times(1)).save(site);
        verify(player, times(1)).sendMessage("Buried archaeological remains were destroyed.");
    }

    @Test
    public void hiddenRuinIsNotProtectedButVanillaDamageStillRecordsDamage() {
        site.setStatus(SiteStatus.HIDDEN);
        BlockBreakEvent event = new BlockBreakEvent(block, player);
        server.getPluginManager().callEvent(event);
        assertFalse(event.isCancelled());
        assertEquals(67, find.getConservation());
        assertTrue(site.stratumAt(40).isDisturbed());
        verify(sites).save(site);
    }

    @Test
    public void cancelledExternalBreakCannotChargeWorkerOrDamageFind() {
        listener.setProtectDigSite(false);
        BlockBreakEvent event = new BlockBreakEvent(block, player);
        event.setCancelled(true);
        server.getPluginManager().callEvent(event);
        assertEquals(100, find.getConservation());
        assertTrue(site.getWorkers().isEmpty());
        verify(sites, never()).save(any());
    }

    @Test
    public void fireAndEntityChangesRespectProtectionThenRecordEnvironmentalWounds() {
        BlockBurnEvent fire = new BlockBurnEvent(block);
        EntityChangeBlockEvent change = new EntityChangeBlockEvent(mock(Entity.class), block,
                Material.AIR.createBlockData());
        server.getPluginManager().callEvent(fire);
        server.getPluginManager().callEvent(change);
        assertTrue(fire.isCancelled());
        assertTrue(change.isCancelled());
        assertEquals(100, find.getConservation());
        listener.setProtectDigSite(false);
        server.getPluginManager().callEvent(new BlockBurnEvent(block));
        server.getPluginManager().callEvent(new EntityChangeBlockEvent(mock(Entity.class),
                world.getBlockAt(9, 40, 8), Material.AIR.createBlockData()));
        assertEquals(33, find.getConservation());
        assertTrue(site.getWorkers().isEmpty());
        verify(sites, times(2)).save(site);
    }

    @Test
    public void playerEntityChangeAttributesDamageToTheWorker() {
        listener.setProtectDigSite(false);
        server.getPluginManager().callEvent(new EntityChangeBlockEvent(player, block,
                Material.AIR.createBlockData()));
        assertEquals(67, find.getConservation());
        assertEquals(1, site.staffLog(player.getUniqueId()).getFindsDamaged());
    }

    @Test
    public void explosionsRemoveOnlyProtectedFillFromTheirMutableBlockLists() {
        Block outside = stone(16, 40, 8);
        Block decoration = world.getBlockAt(12, 40, 8);
        decoration.setType(Material.TORCH);
        List<Block> blockList = new ArrayList<>(List.of(block, outside, decoration));
        BlockExplodeEvent explosion = blockExplosion(blockList);
        server.getPluginManager().callEvent(explosion);
        assertFalse(explosion.isCancelled());
        assertEquals(List.of(outside, decoration), explosion.blockList());
        assertEquals(100, find.getConservation());
        List<Block> entityList = new ArrayList<>(List.of(block, outside));
        EntityExplodeEvent entityExplosion = entityExplosion(mock(Entity.class), entityList);
        server.getPluginManager().callEvent(entityExplosion);
        assertEquals(List.of(outside), entityExplosion.blockList());
        assertEquals(100, find.getConservation());
    }

    @Test
    public void playerExplosionChargesEachPieceOnceAndArchivesFatalLoss() {
        listener.setProtectDigSite(false);
        List<Block> cells = new ArrayList<>(List.of(block, world.getBlockAt(9, 40, 8),
                world.getBlockAt(10, 40, 8), stone(16, 40, 8)));
        server.getPluginManager().callEvent(entityExplosion(player, cells));
        assertEquals(FindState.LOST, find.getState());
        assertEquals(0, find.getConservation());
        assertEquals(1, site.staffLog(player.getUniqueId()).getFindsDamaged());
        assertEquals(1, site.staffLog(player.getUniqueId()).getFindsLost());
        assertEquals(SiteStatus.EXHAUSTED, site.getStatus());
        assertEquals(player.getUniqueId(), find.getRecoveredBy());
        assertTrue(find.getFindNumber() > 0);
        verify(player, times(1)).sendMessage("Buried archaeological remains were destroyed.");
        verify(sites, times(2)).save(site);
    }

    @Test
    public void environmentalExplosionSavesOneDossierForSeveralWoundedCells() {
        listener.setProtectDigSite(false);
        server.getPluginManager().callEvent(blockExplosion(new ArrayList<>(List.of(block,
                world.getBlockAt(9, 40, 8)))));
        assertEquals(33, find.getConservation());
        assertEquals(2, find.getGrazedCells().size());
        assertTrue(site.getWorkers().isEmpty());
        verify(sites).save(site);
    }

    @Test
    public void pistonsCannotMoveProtectedFillOrPushAHeadIntoIt() {
        Block piston = piston(7, 40, 8);
        BlockPistonExtendEvent headOnly = new BlockPistonExtendEvent(piston, List.of(), BlockFace.EAST);
        server.getPluginManager().callEvent(headOnly);
        assertTrue(headOnly.isCancelled());
        BlockPistonExtendEvent push = new BlockPistonExtendEvent(piston, List.of(block), BlockFace.EAST);
        server.getPluginManager().callEvent(push);
        assertTrue(push.isCancelled());
        BlockPistonRetractEvent pull = new BlockPistonRetractEvent(piston, List.of(block), BlockFace.WEST);
        server.getPluginManager().callEvent(pull);
        assertTrue(pull.isCancelled());
        assertEquals(100, find.getConservation());
        verify(sites, never()).save(any());
    }

    @Test
    public void permittedPistonMovesDamageFindsButEmptyOffPrismMovesDoNothing() {
        listener.setProtectDigSite(false);
        Block piston = piston(7, 40, 8);
        BlockPistonExtendEvent push = new BlockPistonExtendEvent(piston, List.of(block), BlockFace.EAST);
        server.getPluginManager().callEvent(push);
        assertFalse(push.isCancelled());
        BlockPistonRetractEvent pull = new BlockPistonRetractEvent(piston,
                List.of(world.getBlockAt(9, 40, 8)), BlockFace.WEST);
        server.getPluginManager().callEvent(pull);
        assertFalse(pull.isCancelled());
        assertEquals(33, find.getConservation());
        BlockPistonRetractEvent empty = new BlockPistonRetractEvent(piston(16, 40, 8), List.of(), BlockFace.EAST);
        server.getPluginManager().callEvent(empty);
        assertFalse(empty.isCancelled());
        assertTrue(site.getWorkers().isEmpty());
        verify(sites, times(2)).save(site);
    }

    @Test
    public void placedDecorationFamiliesRemainMineableWhileConstructionBlocksAreProtected() {
        Block decoration = world.getBlockAt(12, 40, 8);
        stone(12, 39, 8);
        stone(12, 41, 8);
        stone(12, 40, 9);
        for (Material material : List.of(Material.SHORT_GRASS, Material.OAK_SIGN, Material.OAK_HANGING_SIGN,
                Material.CANDLE, Material.CANDLE_CAKE, Material.WHITE_BANNER, Material.STONE_BUTTON,
                Material.STONE_PRESSURE_PLATE, Material.LADDER, Material.WHITE_CARPET, Material.FLOWER_POT,
                Material.RAIL, Material.TORCH)) {
            decoration.setType(material);
            BlockDamageEvent mine = new BlockDamageEvent(player, decoration, new ItemStack(Material.STICK), false);
            server.getPluginManager().callEvent(mine);
            assertFalse(material + " should remain mineable", mine.isCancelled());
        }
        for (Material material : List.of(Material.OAK_PLANKS, Material.GLASS, Material.FURNACE,
                Material.PISTON, Material.CHEST)) {
            decoration.setType(material);
            BlockDamageEvent mine = new BlockDamageEvent(player, decoration, new ItemStack(Material.STICK), false);
            server.getPluginManager().callEvent(mine);
            assertTrue(material + " is construction fill and stays protected", mine.isCancelled());
        }
        assertEquals(100, find.getConservation());
        assertFalse(site.stratumAt(40).isDisturbed());
        verify(sites, never()).save(any());
    }

    @Test
    public void snowVegetationAndFluidsExposeFindDustButSolidCoverSealsIt() {
        find.getCells().clear();
        find.getCells().add(new BlockCell(8, 40, 8));
        for (BlockFace face : PrismFill.FACES) block.getRelative(face).setType(Material.STONE);
        world.loadChunk(0, 0);
        when(sites.all()).thenReturn(List.of(site));
        PickSettings defaults = PickSettings.defaults();
        PickSettings dustSettings = new PickSettings(true, 8, false, true, 1, 1,
                defaults.conservation(), defaults.limits(), false, defaults.cues(), defaults.profiles());
        FindDustService dust = new FindDustService(MockBukkit.createMockPlugin(), sites, dustSettings);
        try {
            dust.start();
            for (Material cover : List.of(Material.SHORT_GRASS, Material.SNOW, Material.POWDER_SNOW,
                    Material.WATER, Material.LAVA, Material.BUBBLE_COLUMN)) {
                block.getRelative(BlockFace.UP).setType(cover);
                world.clearSpawnedParticles();
                server.getScheduler().performTicks(1);
                assertEquals(cover + " leaves an exposed face", FindState.DISCOVERED, find.getState());
                assertEquals(2, world.getSpawnedParticles().size());
                assertEquals(Material.STONE, block.getType());
                assertEquals(cover, block.getRelative(BlockFace.UP).getType());
            }
            block.getRelative(BlockFace.UP).setType(Material.STONE);
            world.clearSpawnedParticles();
            server.getScheduler().performTicks(1);
            assertEquals(FindState.HIDDEN, find.getState());
            assertTrue(world.getSpawnedParticles().isEmpty());
        } finally {
            dust.stop();
        }
    }

    @Test
    public void lockWarningRepeatsOnlyAfterTheCooldown() {
        java.util.concurrent.atomic.AtomicLong now = new java.util.concurrent.atomic.AtomicLong(1_000_000); listener.clock = now::get;
        server.getPluginManager().callEvent(new BlockBreakEvent(block, player));
        now.addAndGet(2999); server.getPluginManager().callEvent(new BlockBreakEvent(block, player));
        verify(player, times(1)).sendMessage("Use an excavation tool.");
        now.addAndGet(1);
        BlockBreakEvent later = new BlockBreakEvent(block, player);
        server.getPluginManager().callEvent(later);
        assertTrue(later.isCancelled());
        verify(player, times(2)).sendMessage("Use an excavation tool.");
        assertEquals(Material.STONE, block.getType());
    }

    private BlockExplodeEvent blockExplosion(List<Block> cells) {
        Block source = world.getBlockAt(6, 40, 8);
        source.setType(Material.RESPAWN_ANCHOR);
        var beforeExplosion = source.getState();
        source.setType(Material.AIR);
        return new BlockExplodeEvent(source, beforeExplosion, cells, 1f, ExplosionResult.DESTROY);
    }

    private EntityExplodeEvent entityExplosion(Entity source, List<Block> cells) {
        return new EntityExplodeEvent(source, block.getLocation(), cells, 1f, ExplosionResult.DESTROY);
    }

    private Block piston(int x, int y, int z) {
        Block result = world.getBlockAt(x, y, z);
        result.setType(Material.STICKY_PISTON);
        return result;
    }

    private Block stone(int x, int y, int z) {
        Block result = world.getBlockAt(x, y, z);
        result.setType(Material.STONE);
        return result;
    }

    private static class SnapshotWorld extends WorldMock {
        private final Map<BlockCell, BlockMock> blocks = new HashMap<>();
        @Override public BlockMock getBlockAt(int x, int y, int z) {
            return blocks.computeIfAbsent(new BlockCell(x, y, z), key -> new BlockMock(new Location(this, x, y, z)) {
                @Override public Location getLocation() { return super.getLocation().clone(); }
            });
        }
    }
}
