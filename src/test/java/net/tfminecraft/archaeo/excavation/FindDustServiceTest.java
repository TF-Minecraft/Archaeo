package net.tfminecraft.archaeo.excavation;

import net.tfminecraft.archaeo.config.*;
import net.tfminecraft.archaeo.model.*;
import net.tfminecraft.archaeo.site.SiteRepository;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.block.BlockMock;
import org.mockbukkit.mockbukkit.util.SpawnedParticle;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class FindDustServiceTest {
    private ServerMock server;
    private SnapshotWorld world;
    private SiteRepository sites;
    private Site site;
    private BuriedFind find;
    private FindDustService service;

    @Before
    public void setup() {
        server = MockBukkit.mock();
        world = new SnapshotWorld();
        world.setName("dust");
        server.addWorld(world);
        world.loadChunk(0, 0);
        sites = mock(SiteRepository.class);
        site = new Site();
        site.setId(UUID.randomUUID());
        site.setStatus(SiteStatus.ESTABLISHED);
        site.setWorldName(world.getName());
        find = new BuriedFind();
        find.setId(UUID.randomUUID());
        find.getCells().add(new BlockCell(8, 40, 8));
        site.getFinds().add(find);
        block(8, 40, 8).setType(Material.STONE);
        when(sites.all()).thenReturn(List.of(site));
        when(sites.findByChunk("dust", 0, 0)).thenReturn(Optional.of(site));
        service = new FindDustService(MockBukkit.createMockPlugin(), sites, settings(true, 2, 4));
    }

    @After
    public void teardown() {
        if (service != null) service.stop();
        MockBukkit.unmock();
    }

    @Test
    public void exposedFindEmitsWaxAtConfiguredCadenceWithoutChangingTerrain() {
        service.start();
        ticks(1);
        assertTrue(world.getSpawnedParticles().isEmpty());
        assertEquals(FindState.HIDDEN, find.getState());
        ticks(1);
        assertEquals(FindState.DISCOVERED, find.getState());
        List<SpawnedParticle> bursts = world.getSpawnedParticles();
        assertEquals(7, bursts.size());
        SpawnedParticle center = bursts.getFirst();
        assertEquals(8.5, center.x(), 0);
        assertEquals(40.5, center.y(), 0);
        assertEquals(8.5, center.z(), 0);
        assertEquals(4, center.count());
        assertEquals(0.3, center.offsetX(), 0);
        for (SpawnedParticle burst : bursts) {
            assertEquals(Particle.WAX_ON, burst.particle());
            assertEquals(0.1, burst.extra(), 0);
        }
        for (SpawnedParticle face : bursts.subList(1, 7)) {
            assertEquals(2, face.count());
            assertEquals(0.08, face.offsetX(), 0);
            double faceDistance = Math.abs(face.x() - 8.5) + Math.abs(face.y() - 40.5) + Math.abs(face.z() - 8.5);
            assertEquals(0.55, faceDistance, 0.000001);
        }
        assertEquals(Material.STONE, block(8, 40, 8).getType());
        verify(sites).touch(site);
        ticks(2);
        assertEquals(14, world.getSpawnedParticles().size());
        verify(sites, times(1)).touch(site);
        verify(sites, never()).save(any());
    }

    @Test
    public void exposureTransitionsFromHiddenThroughPartialToDiscovered() {
        find.getCells().add(new BlockCell(9, 40, 8));
        for (int x = 7; x <= 10; x++) {
            for (int y = 39; y <= 41; y++) {
                for (int z = 7; z <= 9; z++) block(x, y, z).setType(Material.STONE);
            }
        }
        service.start();
        ticks(2);
        assertEquals(FindState.HIDDEN, find.getState());
        assertTrue(world.getSpawnedParticles().isEmpty());
        block(8, 41, 8).setType(Material.AIR);
        ticks(2);
        assertEquals(FindState.PARTIAL, find.getState());
        assertEquals(2, world.getSpawnedParticles().size());
        block(9, 41, 8).setType(Material.AIR);
        ticks(2);
        assertEquals(FindState.DISCOVERED, find.getState());
        assertEquals(6, world.getSpawnedParticles().size());
        verify(sites, times(2)).touch(site);
    }

    @Test
    public void cleanedCubesRemainDiscoveredButStopSheddingDust() {
        find.markCleaned(new BlockCell(8, 40, 8));
        service.start();
        ticks(4);
        assertEquals(FindState.DISCOVERED, find.getState());
        assertTrue(world.getSpawnedParticles().isEmpty());
        assertEquals(Material.STONE, block(8, 40, 8).getType());
        verify(sites).touch(site);
    }

    @Test
    public void missingFillMarksLostWhileAlreadySettledFindsRemainUntouched() {
        block(8, 40, 8).setType(Material.WATER);
        for (FindState state : List.of(FindState.RECOVERED, FindState.LOST)) {
            BuriedFind settled = new BuriedFind();
            settled.setId(UUID.randomUUID());
            settled.setState(state);
            settled.getCells().add(new BlockCell(10 + site.getFinds().size(), 40, 8));
            BlockCell cell = settled.getCells().getFirst();
            block(cell.x(), cell.y(), cell.z()).setType(Material.STONE);
            site.getFinds().add(settled);
        }
        service.start();
        ticks(4);
        assertEquals(FindState.LOST, find.getState());
        assertEquals(FindState.RECOVERED, site.getFinds().get(1).getState());
        assertEquals(FindState.LOST, site.getFinds().get(2).getState());
        assertTrue(world.getSpawnedParticles().isEmpty());
        verify(sites).touch(site);
    }

    @Test
    public void loadedEstablishedSiteIsRequiredAndChunkLoadStartsTheTimer() {
        world.unloadChunk(0, 0);
        service.start();
        ticks(4);
        assertTrue(world.getSpawnedParticles().isEmpty());
        world.loadChunk(0, 0);
        service.onChunkLoad(new ChunkLoadEvent(world.getChunkAt(0, 0), false));
        ticks(2);
        assertEquals(7, world.getSpawnedParticles().size());
        service.start();
        service.start();
        ticks(2);
        assertEquals(14, world.getSpawnedParticles().size());
    }

    @Test
    public void chunkUnloadDefersStopUntilAfterEventAndChunkReloadRestartsCleanly() {
        service.start();
        ticks(2);
        world.clearSpawnedParticles();
        Chunk departing = world.getChunkAt(0, 0);
        service.onChunkUnload(new ChunkUnloadEvent(departing));
        world.unloadChunk(0, 0);
        ticks(4);
        assertTrue(world.getSpawnedParticles().isEmpty());
        world.loadChunk(0, 0);
        service.onChunkLoad(new ChunkLoadEvent(world.getChunkAt(0, 0), false));
        ticks(2);
        assertEquals(7, world.getSpawnedParticles().size());
    }

    @Test
    public void disablingStoppingOrSettlingSiteStopsDustAndReenableResumesIt() {
        service.start();
        ticks(2);
        world.clearSpawnedParticles();
        service.setSettings(settings(false, 2, 4));
        ticks(4);
        assertTrue(world.getSpawnedParticles().isEmpty());
        service.setSettings(settings(true, 2, 4));
        ticks(2);
        assertEquals(7, world.getSpawnedParticles().size());
        world.clearSpawnedParticles();
        service.stop();
        ticks(4);
        assertTrue(world.getSpawnedParticles().isEmpty());
        service.start();
        site.setStatus(SiteStatus.EXHAUSTED);
        ticks(4);
        assertTrue(world.getSpawnedParticles().isEmpty());
    }

    @Test
    public void unknownWorldAndHiddenSitesNeverRevealFinds() {
        site.setStatus(SiteStatus.HIDDEN);
        service.start();
        ticks(4);
        site.setStatus(SiteStatus.ESTABLISHED);
        site.setWorldName("missing-world");
        service.syncTimer();
        ticks(4);
        assertEquals(FindState.HIDDEN, find.getState());
        assertTrue(world.getSpawnedParticles().isEmpty());
        verify(sites, never()).touch(any());
    }

    @Test
    public void unavailableRuinsDoNotStopDustAtAnotherLoadedExcavationOrLoadTheirChunks() {
        Site missingWorld = new Site();
        missingWorld.setId(UUID.randomUUID());
        missingWorld.setWorldName("missing-world");
        missingWorld.setStatus(SiteStatus.ESTABLISHED);
        BuriedFind remoteFind = new BuriedFind(); remoteFind.setId(UUID.randomUUID());
        remoteFind.getCells().add(new BlockCell(8, 40, 8)); missingWorld.getFinds().add(remoteFind);
        Site unloadedChunk = new Site();
        unloadedChunk.setId(UUID.randomUUID());
        unloadedChunk.setWorldName(world.getName()); unloadedChunk.setChunkX(12);
        unloadedChunk.setStatus(SiteStatus.ESTABLISHED);
        BuriedFind unloadedFind = new BuriedFind(); unloadedFind.setId(UUID.randomUUID());
        unloadedFind.getCells().add(new BlockCell(200, 40, 8)); unloadedChunk.getFinds().add(unloadedFind);
        when(sites.all()).thenReturn(List.of(missingWorld, unloadedChunk, site));

        assertFalse(world.isChunkLoaded(12, 0));
        service.start(); ticks(4);

        assertEquals(FindState.DISCOVERED, find.getState());
        assertEquals(14, world.getSpawnedParticles().size());
        assertEquals(FindState.HIDDEN, remoteFind.getState());
        assertEquals(FindState.HIDDEN, unloadedFind.getState());
        assertFalse(world.isChunkLoaded(12, 0));
        verify(sites).touch(site);
        verify(sites, never()).touch(missingWorld);
        verify(sites, never()).touch(unloadedChunk);
    }

    @Test
    public void zeroIntervalPausesRevealAndMinimumParticleCountRemainsVisible() {
        service.setSettings(settings(true, 0, 0));
        ticks(4);
        assertEquals(FindState.HIDDEN, find.getState());
        assertTrue(world.getSpawnedParticles().isEmpty());
        service.setSettings(settings(true, 1, 0));
        ticks(1);
        assertEquals(FindState.DISCOVERED, find.getState());
        assertEquals(7, world.getSpawnedParticles().size());
        for (SpawnedParticle burst : world.getSpawnedParticles()) assertEquals(1, burst.count());
    }

    @Test
    public void unrelatedOrUnclaimedChunksLoadingDoNotStartTheDustLoop() {
        world.loadChunk(5, 5);
        service.onChunkLoad(new ChunkLoadEvent(world.getChunkAt(5, 5), false));
        site.setStatus(SiteStatus.HIDDEN);
        service.onChunkLoad(new ChunkLoadEvent(world.getChunkAt(0, 0), false));
        site.setStatus(SiteStatus.ESTABLISHED);
        ticks(4);
        assertTrue(world.getSpawnedParticles().isEmpty());
        assertEquals(FindState.HIDDEN, find.getState());
        verify(sites, never()).touch(any());
    }

    @Test
    public void storedFindCellInAnUnloadedNeighbourChunkIsLeftAloneAndNotLoaded() {
        // A hand-edited or migrated dossier can list a cube just across the chunk border.
        find.getCells().add(new BlockCell(16, 40, 8));
        assertFalse(world.isChunkLoaded(1, 0));
        service.start();
        ticks(4);
        assertEquals(FindState.HIDDEN, find.getState());
        assertTrue(world.getSpawnedParticles().isEmpty());
        assertFalse(world.isChunkLoaded(1, 0));
        verify(sites, never()).touch(any());
    }

    private PickSettings settings(boolean dust, int interval, int count) {
        PickSettings defaults = PickSettings.defaults();
        return new PickSettings(true, 8, false, dust, interval, count,
                defaults.conservation(), defaults.limits(), false, defaults.cues(), defaults.profiles());
    }

    private Block block(int x, int y, int z) { return world.getBlockAt(x, y, z); }
    private void ticks(int count) { server.getScheduler().performTicks(count); }

    private static class SnapshotWorld extends WorldMock {
        private final Map<BlockCell, BlockMock> blocks = new HashMap<>();
        @Override public BlockMock getBlockAt(int x, int y, int z) {
            return blocks.computeIfAbsent(new BlockCell(x, y, z), key -> new BlockMock(new Location(this, x, y, z)) {
                @Override public Location getLocation() { return super.getLocation().clone(); }
            });
        }
    }
}
