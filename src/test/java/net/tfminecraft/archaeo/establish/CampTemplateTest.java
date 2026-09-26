package net.tfminecraft.archaeo.establish;

import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.data.type.Slab;
import org.bukkit.util.Vector;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class CampTemplateTest {
    @Before public void setUp() { MockBukkit.mock(); }
    @After public void tearDown() { MockBukkit.unmock(); }

    @Test
    public void templateIsAnImmutableUniqueCampAndRecolorsOnlyWoolRoles() {
        var defaults = CampTemplate.basic();
        assertEquals(defaults, CampTemplate.basic(null, null));
        var custom = CampTemplate.basic(Material.BLUE_WOOL, Material.YELLOW_WOOL);
        assertEquals(defaults.size(), custom.size());
        Set<Vector> cells = new HashSet<>();
        for (int i = 0; i < defaults.size(); i++) {
            var before = defaults.get(i); var after = custom.get(i);
            assertTrue(cells.add(new Vector(before.dx(), before.dy(), before.dz())));
            assertEquals(before.dx(), after.dx()); assertEquals(before.dy(), after.dy()); assertEquals(before.dz(), after.dz());
            Material expected = before.wool() == CampWoolRole.PRIMARY ? Material.BLUE_WOOL
                    : before.wool() == CampWoolRole.SECONDARY ? Material.YELLOW_WOOL : before.material();
            assertEquals(expected, after.material());
        }
        assertEquals(1, defaults.stream().filter(p -> p.material() == Material.OAK_SIGN).count());
        assertEquals(1, defaults.stream().filter(p -> p.material() == Material.CAMPFIRE).count());
        assertThrows(UnsupportedOperationException.class, defaults::clear);
    }

    @Test
    public void facingWrapsYawAndSnapsAtCardinalBoundaries() {
        float[] angles = {-540, -180, 180, 540, 135, -135.01f, 179};
        for (float angle : angles) assertEquals(BlockFace.NORTH, CampTemplate.facingFromYaw(angle));
        for (float angle : new float[]{-45, 0, 44.99f, 360, 720}) assertEquals(BlockFace.SOUTH, CampTemplate.facingFromYaw(angle));
        for (float angle : new float[]{45, 90, 134.99f, 450}) assertEquals(BlockFace.WEST, CampTemplate.facingFromYaw(angle));
        for (float angle : new float[]{-135, -90, -45.01f, 270}) assertEquals(BlockFace.EAST, CampTemplate.facingFromYaw(angle));
    }

    @Test
    public void rotationAndClampingKeepEveryPieceInsideItsChunk() {
        assertEquals(new Vector(2, 0, 1), CampTemplate.rotate(2, 1, BlockFace.SOUTH));
        assertEquals(new Vector(-1, 0, 2), CampTemplate.rotate(2, 1, BlockFace.WEST));
        assertEquals(new Vector(-2, 0, -1), CampTemplate.rotate(2, 1, BlockFace.NORTH));
        assertEquals(new Vector(1, 0, -2), CampTemplate.rotate(2, 1, BlockFace.EAST));
        for (BlockFace facing : new BlockFace[]{BlockFace.SOUTH, BlockFace.WEST, BlockFace.NORTH, BlockFace.EAST}) {
            int[] bounds = CampTemplate.offsetBounds(CampTemplate.basic(), facing);
            for (int desired : new int[]{-100, -16, -8, -1, 100}) {
                int x = CampTemplate.clampOrigin(desired, bounds[0], bounds[1], -16, -1);
                int z = CampTemplate.clampOrigin(desired, bounds[2], bounds[3], -16, -1);
                for (var piece : CampTemplate.basic()) {
                    Vector offset = CampTemplate.rotate(piece.dx(), piece.dz(), facing);
                    assertTrue(x + offset.getBlockX() >= -16 && x + offset.getBlockX() <= -1);
                    assertTrue(z + offset.getBlockZ() >= -16 && z + offset.getBlockZ() <= -1);
                }
            }
        }
        assertEquals(7, CampTemplate.clampOrigin(100, -20, 20, 0, 15));
    }

    @Test
    public void blockStatesFaceTheCampAndProvideLitFireAndBottomSlab() {
        for (BlockFace facing : new BlockFace[]{BlockFace.SOUTH, BlockFace.WEST, BlockFace.NORTH, BlockFace.EAST}) {
            assertEquals(facing.getOppositeFace(), ((Rotatable) CampTemplate.dataFor(Material.OAK_SIGN, facing)).getRotation());
            var fire = CampTemplate.dataFor(Material.CAMPFIRE, facing);
            assertEquals(facing, ((Directional) fire).getFacing());
            assertTrue(((Lightable) fire).isLit());
            assertEquals(Slab.Type.BOTTOM, ((Slab) CampTemplate.dataFor(Material.OAK_SLAB, facing)).getType());
            assertEquals(Material.WHITE_WOOL, CampTemplate.dataFor(Material.WHITE_WOOL, facing).getMaterial());
        }
    }

    @Test
    public void woolSelectionWrapsAndInvalidSavedColorsUseTheFallback() {
        DyeColor[] palette = CampWools.palette();
        DyeColor first = palette[0]; palette[0] = DyeColor.BLACK;
        assertEquals(first, CampWools.palette()[0]);
        for (DyeColor color : DyeColor.values()) {
            assertEquals(color, CampWools.parse(color.name().toLowerCase().replace('_', ' ')));
            assertEquals(Material.valueOf(color.name() + "_WOOL"), CampWools.woolOf(color.name()));
            assertEquals(DyeColor.values()[(color.ordinal() + 1) % DyeColor.values().length], CampWools.next(color.name()));
        }
        for (String invalid : new String[]{null, " ", "unknown"}) {
            assertEquals(DyeColor.RED, CampWools.parse(invalid));
            assertEquals(Material.BLUE_WOOL, CampWools.woolOf(invalid, DyeColor.BLUE));
        }
        assertEquals("light blue wool", CampWools.label(DyeColor.LIGHT_BLUE));
    }
}
