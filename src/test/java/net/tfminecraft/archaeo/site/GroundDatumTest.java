package net.tfminecraft.archaeo.site;

import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.world.WorldMock;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class GroundDatumTest {
    @Before public void setUp() { MockBukkit.mock(); }
    @After public void tearDown() { MockBukkit.unmock(); }

    @Test
    public void medianIsRobustToOutliersAndLeavesSamplesUnchanged() {
        int[] odd = {99, 4, 3, 5, -20};
        assertEquals(4, GroundDatum.median(odd));
        assertArrayEquals(new int[]{99, 4, 3, 5, -20}, odd);
        assertEquals(4, GroundDatum.median(new int[]{99, 4, 5, -20}));
        assertEquals(-4, GroundDatum.median(new int[]{-5, -4}));
        assertEquals(7, GroundDatum.median(new int[]{7}));
    }

    @Test
    public void excludesCanopyFluidsAndPlantsFromTheTerrainDatum() {
        WorldMock world = MockBukkit.getMock().addSimpleWorld("world");
        var block = world.getBlockAt(0, 20, 0);
        for (Material excluded : new Material[]{Material.AIR, Material.WATER, Material.OAK_LEAVES,
                Material.SHORT_GRASS, Material.OAK_LOG, Material.SNOW, Material.POWDER_SNOW,
                Material.BAMBOO, Material.BAMBOO_SAPLING, Material.MUSHROOM_STEM,
                Material.BROWN_MUSHROOM_BLOCK, Material.RED_MUSHROOM_BLOCK,
                Material.MANGROVE_ROOTS, Material.MUDDY_MANGROVE_ROOTS, Material.TORCH}) {
            block.setType(excluded);
            assertFalse(excluded.name(), GroundDatum.isGround(block));
        }
        for (Material included : new Material[]{Material.DIRT, Material.STONE, Material.SAND, Material.GRAVEL}) {
            block.setType(included);
            assertTrue(included.name(), GroundDatum.isGround(block));
        }
    }

    @Test
    public void chunkMedianUsesNegativeChunkCoordinatesAndWalksDownThroughCanopy() {
        WorldMock actual = MockBukkit.getMock().addSimpleWorld("world");
        WorldMock world = spy(actual);
        doReturn(20).when(world).getHighestBlockYAt(anyInt(), anyInt(), eq(HeightMap.MOTION_BLOCKING_NO_LEAVES));
        for (int x = -16; x < 0; x++) {
            for (int z = 0; z < 16; z++) {
                actual.getBlockAt(x, 10, z).setType(Material.STONE);
                actual.getBlockAt(x, 20, z).setType(Material.OAK_LOG);
            }
        }
        assertEquals(10, GroundDatum.columnGroundY(world, -16, 0));
        var chunk = mock(org.bukkit.Chunk.class);
        when(chunk.getWorld()).thenReturn(world); when(chunk.getX()).thenReturn(-1);
        assertEquals(10, GroundDatum.medianY(chunk));
        verify(world).getBlockAt(-1, 10, 15);
    }

    @Test
    public void emptyColumnFallsBackToWorldMinimum() {
        var world = mock(org.bukkit.World.class);
        var air = mock(org.bukkit.block.Block.class);
        when(air.getType()).thenReturn(Material.AIR);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getHighestBlockYAt(0, 0, HeightMap.MOTION_BLOCKING_NO_LEAVES)).thenReturn(-62);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(air);
        assertEquals(-64, GroundDatum.columnGroundY(world, 0, 0));
        verify(world, never()).getBlockAt(0, -65, 0);
    }
}
