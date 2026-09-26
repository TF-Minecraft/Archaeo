package net.tfminecraft.archaeo.site;

import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ChunkRuinFitnessTest {
    private World world;
    private Chunk chunk;

    @Before public void setup() {
        MockBukkit.mock();
        world = mock(World.class); chunk = mock(Chunk.class);
        when(chunk.getWorld()).thenReturn(world);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getSeaLevel()).thenReturn(63);
    }

    @After public void teardown() { MockBukkit.unmock(); }

    @Test public void rejectsWaterAtAnyCoastalSampleAndSupportsDisablingTheBiomeGate() {
        when(chunk.getX()).thenReturn(-2); when(chunk.getZ()).thenReturn(3);
        when(world.getBiome(anyInt(), anyInt(), anyInt())).thenReturn(Biome.PLAINS);
        assertFalse(ChunkRuinFitness.isExcludedWaterBiome(chunk, null));
        assertFalse(ChunkRuinFitness.isExcludedWaterBiome(chunk, Set.of()));
        verify(world, never()).getBiome(anyInt(), anyInt(), anyInt());
        int[][] positions = {{-24, 56}, {-32, 48}, {-17, 48}, {-32, 63}, {-17, 63}};
        assertFalse(ChunkRuinFitness.isExcludedWaterBiome(chunk, Set.of("ocean", "river")));
        for (int[] position : positions) {
            when(world.getBiome(position[0], 63, position[1])).thenReturn(Biome.OCEAN);
            assertTrue(ChunkRuinFitness.isExcludedWaterBiome(chunk, Set.of("ocean")));
            when(world.getBiome(position[0], 63, position[1])).thenReturn(Biome.PLAINS);
        }
    }

    @Test public void waterGateReadsBiomeKeysThroughSpigotRegistryAwareAndSkipsUnregisteredBiomesWithoutThrowing() {
        when(chunk.getX()).thenReturn(0); when(chunk.getZ()).thenReturn(0);
        // Spigot's RegistryAware adds getKeyOrNull, and there getKey() throws for an unregistered biome.
        Biome ocean = mock(Biome.class, withSettings().extraInterfaces(SiteGeneratorTest.RegistryAwareBiome.class));
        when(((SiteGeneratorTest.RegistryAwareBiome) ocean).getKeyOrNull()).thenReturn(NamespacedKey.minecraft("deep_ocean"));
        when(ocean.getKey()).thenThrow(new IllegalStateException("deprecated key read"));
        Biome unregistered = mock(Biome.class, withSettings().extraInterfaces(SiteGeneratorTest.RegistryAwareBiome.class));
        when(unregistered.getKey()).thenThrow(new IllegalStateException("unregistered biome"));
        when(world.getBiome(anyInt(), anyInt(), anyInt())).thenReturn(unregistered);
        assertFalse(ChunkRuinFitness.isExcludedWaterBiome(chunk, Set.of("deep_ocean")));
        when(world.getBiome(15, 63, 15)).thenReturn(ocean);
        assertTrue(ChunkRuinFitness.isExcludedWaterBiome(chunk, Set.of("deep_ocean")));
        assertFalse(ChunkRuinFitness.isExcludedWaterBiome(chunk, Set.of("river")));
    }

    @Test public void sparseSamplesMeasureSoilAndTrimSingleHeightOutliers() {
        when(chunk.getX()).thenReturn(-1); when(chunk.getZ()).thenReturn(2);
        int[] heights = {0, 60, 60, 60, 60, 60, 60, 60, 64, 64, 64, 64, 64, 64, 64, 100};
        Block dirt = block(Material.DIRT), stone = block(Material.STONE);
        when(world.getHighestBlockYAt(anyInt(), anyInt(), eq(HeightMap.MOTION_BLOCKING_NO_LEAVES)))
                .thenAnswer(call -> heights[index(call.getArgument(0), call.getArgument(1))]);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt()))
                .thenAnswer(call -> index(call.getArgument(0), call.getArgument(2)) < 12 ? dirt : stone);
        ChunkRuinFitness.Sample sample = ChunkRuinFitness.sample(chunk);
        assertEquals(62, sample.medianY()); assertEquals(4, sample.reliefBlocks());
        assertEquals(0.75, sample.soilFraction(), 0);
        verify(world, times(16)).getHighestBlockYAt(anyInt(), anyInt(), eq(HeightMap.MOTION_BLOCKING_NO_LEAVES));
    }

    @Test public void shallowGroundSearchSkipsCanopyButNeverDescendsIntoDeepCaves() {
        when(world.getHighestBlockYAt(0, 0, HeightMap.MOTION_BLOCKING_NO_LEAVES)).thenReturn(70);
        Block log = block(Material.OAK_LOG), ground = block(Material.DIRT);
        when(world.getBlockAt(eq(0), anyInt(), eq(0))).thenReturn(log);
        when(world.getBlockAt(0, 64, 0)).thenReturn(ground);
        assertEquals(64, ChunkRuinFitness.surfaceGroundY(world, 0, 0));
        when(world.getBlockAt(0, 64, 0)).thenReturn(log);
        when(world.getBlockAt(0, 63, 0)).thenReturn(ground);
        assertEquals(-65, ChunkRuinFitness.surfaceGroundY(world, 0, 0));
        verify(world, never()).getBlockAt(0, 63, 0);
    }

    @Test public void emptyColumnsDoNotDiluteTheUsableSoilFraction() {
        Block dirt = block(Material.DIRT), air = block(Material.AIR);
        when(world.getHighestBlockYAt(anyInt(), anyInt(), eq(HeightMap.MOTION_BLOCKING_NO_LEAVES))).thenReturn(-64);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(air);
        when(world.getBlockAt(0, -64, 0)).thenReturn(dirt);
        ChunkRuinFitness.Sample sample = ChunkRuinFitness.sample(chunk);
        assertEquals(1, sample.soilFraction(), 0);
        assertEquals(-65, sample.medianY());
        when(world.getBlockAt(0, -64, 0)).thenReturn(air);
        assertEquals(0, ChunkRuinFitness.sample(chunk).soilFraction(), 0);
        verify(world, never()).getBlockAt(anyInt(), eq(-65), anyInt());
    }

    private static int index(int x, int z) { return ((x + 16) / 4) * 4 + ((z - 32) / 4); }
    private static Block block(Material material) {
        Block block = mock(Block.class); when(block.getType()).thenReturn(material); return block;
    }
}
