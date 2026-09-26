package net.tfminecraft.archaeo.excavation;

import net.tfminecraft.archaeo.config.BreakShape;
import net.tfminecraft.archaeo.config.ExcavationTool;
import net.tfminecraft.archaeo.model.Site;
import net.tfminecraft.archaeo.model.StratumBand;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.*;

public class LiftPlanTest {
    private World world;
    private Site site;
    private Block origin;

    @Before
    public void setup() {
        world = MockBukkit.mock().addSimpleWorld("lift");
        site = new Site();
        StratumBand band = new StratumBand();
        band.setPresent(true);
        band.setMinY(38);
        band.setMaxY(42);
        site.getStrata().put("I", band);
        origin = stone(8, 40, 8);
    }

    @After
    public void teardown() {
        MockBukkit.unmock();
    }

    @Test
    public void invalidOriginCannotStartLiftForAnyShape() {
        for (BreakShape shape : BreakShape.values()) {
            for (Material invalid : List.of(Material.AIR, Material.WATER, Material.TORCH)) {
                origin.setType(invalid);
                assertTrue(LiftPlan.cells(site, origin, tool(shape, 3, 5), false).isEmpty());
            }
            assertTrue(LiftPlan.cells(site, stone(16, 40, 8), tool(shape, 3, 5), false).isEmpty());
            assertTrue(LiftPlan.cells(site, stone(8, 43, 8), tool(shape, 3, 5), false).isEmpty());
        }
    }

    @Test
    public void minimumCountAlwaysIncludesOnlyTheAimedCell() {
        fillFootprint(origin);
        for (BreakShape shape : BreakShape.values()) {
            for (int count : new int[] {-2, 0, 1}) {
                assertEquals(List.of(origin), LiftPlan.cells(site, origin, tool(shape, count, -4), false));
                assertEquals(List.of(origin), LiftPlan.cells(site, origin, tool(shape, count, -4), true));
            }
        }
    }

    @Test
    public void lateCountCannotShrinkTheReadyLift() {
        fillFootprint(origin);
        assertEquals(4, LiftPlan.cells(site, origin, tool(BreakShape.AROUND, 4, 2), true).size());
        assertEquals(2, LiftPlan.cells(site, origin, tool(BreakShape.AROUND, 2, 4), false).size());
        assertEquals(4, LiftPlan.cells(site, origin, tool(BreakShape.AROUND, 2, 4), true).size());
    }

    @Test
    public void downLiftsInDescendingOrderAndStopsAtThePrismFloor() {
        for (int y = 36; y < 40; y++) stone(8, y, 8);
        assertEquals(List.of(origin, world.getBlockAt(8, 39, 8), world.getBlockAt(8, 38, 8)),
                LiftPlan.cells(site, origin, tool(BreakShape.DOWN, 8, 8), false));
        assertEquals(List.of(origin, world.getBlockAt(8, 39, 8)),
                LiftPlan.cells(site, origin, tool(BreakShape.DOWN, 2, 2), false));
    }

    @Test
    public void downNeverJumpsAcrossAirFluidOrDecoration() {
        stone(8, 38, 8);
        for (Material barrier : List.of(Material.AIR, Material.LAVA, Material.TORCH)) {
            world.getBlockAt(8, 39, 8).setType(barrier);
            assertEquals(List.of(origin), LiftPlan.cells(site, origin, tool(BreakShape.DOWN, 3, 3), false));
        }
    }

    @Test
    public void aroundPrefersCardinalsThenDiagonalsThenLowerLayer() {
        fillFootprint(origin);
        List<Block> lift = LiftPlan.cells(site, origin, tool(BreakShape.AROUND, 30, 30), false);
        int[][] offsets = {{0, 0}, {0, 1}, {1, 0}, {0, -1}, {-1, 0},
                {1, 1}, {1, -1}, {-1, -1}, {-1, 1}};
        assertEquals(18, lift.size());
        for (int down = 0; down <= 1; down++) {
            for (int i = 0; i < offsets.length; i++) {
                assertEquals(origin.getRelative(offsets[i][0], -down, offsets[i][1]), lift.get(down * 9 + i));
            }
        }
        assertConnected(lift);
    }

    @Test
    public void disconnectedDiagonalCannotJoinThroughAnEdgeOrCorner() {
        Block diagonal = stone(9, 40, 9);
        for (BreakShape shape : List.of(BreakShape.AROUND, BreakShape.RANDOM)) {
            assertEquals(List.of(origin), LiftPlan.cells(site, origin, tool(shape, 18, 18), false));
        }
        Block bridge = stone(8, 40, 9);
        assertEquals(List.of(origin, bridge, diagonal),
                LiftPlan.cells(site, origin, tool(BreakShape.AROUND, 18, 18), false));
    }

    @Test
    public void lowerLayerCanConnectOtherwiseIsolatedUpperCell() {
        Block bottom = stone(8, 39, 8);
        Block bottomEast = stone(9, 39, 8);
        Block bottomCorner = stone(9, 39, 9);
        Block topCorner = stone(9, 40, 9);
        assertEquals(List.of(origin, bottom, bottomEast, bottomCorner, topCorner),
                LiftPlan.cells(site, origin, tool(BreakShape.AROUND, 18, 18), false));
    }

    @Test
    public void footprintClipsAtChunkAndStratumBoundariesIncludingNegativeChunks() {
        for (int chunk : new int[] {0, -1}) {
            site.setChunkX(chunk);
            site.setChunkZ(chunk);
            Block edge = stone(chunk * 16, 38, chunk * 16);
            fillFootprint(edge);
            for (BreakShape shape : List.of(BreakShape.AROUND, BreakShape.RANDOM)) {
                List<Block> lift = LiftPlan.cells(site, edge, tool(shape, 30, 30), false);
                assertEquals(4, lift.size());
                assertConnected(lift);
                for (Block block : lift) {
                    assertEquals(38, block.getY());
                    assertTrue(site.isInPrism(block.getX(), block.getY(), block.getZ()));
                }
            }
        }
    }

    @Test
    public void randomLiftsPreserveCountBoundsUniquenessAndFaceConnectivity() {
        fillFootprint(origin);
        origin.getRelative(1, 0, 0).setType(Material.AIR);
        origin.getRelative(0, -1, -1).setType(Material.WATER);
        for (int count = 2; count <= 20; count++) {
            for (int sample = 0; sample < 5; sample++) {
                List<Block> lift = LiftPlan.cells(site, origin, tool(BreakShape.RANDOM, count, count), false);
                assertEquals(Math.min(count, 16), lift.size());
                assertEquals(origin, lift.getFirst());
                assertConnected(lift);
                for (Block block : lift) {
                    assertTrue(Math.abs(block.getX() - origin.getX()) <= 1);
                    assertTrue(Math.abs(block.getZ() - origin.getZ()) <= 1);
                    assertTrue(block.getY() == origin.getY() || block.getY() == origin.getY() - 1);
                    assertEquals(Material.STONE, block.getType());
                }
            }
        }
    }

    private static void assertConnected(List<Block> lift) {
        assertEquals(lift.size(), new HashSet<>(lift).size());
        for (int i = 1; i < lift.size(); i++) {
            Block cell = lift.get(i);
            boolean attached = false;
            for (int j = 0; j < i; j++) {
                Block prior = lift.get(j);
                int distance = Math.abs(cell.getX() - prior.getX())
                        + Math.abs(cell.getY() - prior.getY()) + Math.abs(cell.getZ() - prior.getZ());
                attached |= distance == 1;
            }
            assertTrue("Each new cell shares a face with an earlier cell", attached);
        }
    }

    private void fillFootprint(Block center) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy <= 0; dy++) center.getRelative(dx, dy, dz).setType(Material.STONE);
            }
        }
    }

    private Block stone(int x, int y, int z) {
        Block block = world.getBlockAt(x, y, z);
        block.setType(Material.STONE);
        return block;
    }

    private static ExcavationTool tool(BreakShape shape, int onTime, int late) {
        return new ExcavationTool("test", List.of(), 0, null, 0, null, null,
                onTime, late, shape, 1, 20);
    }
}
