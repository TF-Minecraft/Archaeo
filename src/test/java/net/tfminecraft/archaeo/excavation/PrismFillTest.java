package net.tfminecraft.archaeo.excavation;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.List;

import static org.junit.Assert.*;

public class PrismFillTest {
    private WorldMock world;

    @Before
    public void setup() {
        world = MockBukkit.mock().addSimpleWorld("fill");
    }

    @After
    public void teardown() { MockBukkit.unmock(); }

    @Test
    public void buildsAndGroundAreFillWhileAirFluidsAndDecorationsAreNot() {
        for (Material fill : List.of(Material.DIRT, Material.STONE, Material.COBBLESTONE, Material.BRICKS,
                Material.OAK_PLANKS, Material.GLASS, Material.FURNACE)) {
            assertTrue(fill + " is dug as fill", PrismFill.isTerrainFill(fill));
        }
        for (Material open : List.of(Material.AIR, Material.CAVE_AIR, Material.WATER, Material.LAVA,
                Material.BUBBLE_COLUMN, Material.SHORT_GRASS, Material.TORCH, Material.OAK_SIGN,
                Material.OAK_HANGING_SIGN, Material.LADDER, Material.RAIL, Material.SCAFFOLDING, Material.LANTERN)) {
            assertFalse(open + " is not fill", PrismFill.isTerrainFill(open));
        }
    }

    @Test
    public void snowAndPowderSnowLeaveAFaceOpenButASealedCubeHasNone() {
        Block cube = world.getBlockAt(8, 40, 8);
        cube.setType(Material.STONE);
        for (BlockFace face : PrismFill.FACES) cube.getRelative(face).setType(Material.DIRT);
        assertFalse(PrismFill.hasOpenFace(cube));
        assertTrue(PrismFill.openingFaces(cube).isEmpty());
        for (Material cover : List.of(Material.SNOW, Material.POWDER_SNOW)) {
            cube.getRelative(BlockFace.UP).setType(cover);
            assertTrue(cover + " does not seal the face", PrismFill.hasOpenFace(cube));
            assertEquals(List.of(BlockFace.UP), PrismFill.openingFaces(cube));
        }
    }
}
