package net.tfminecraft.archaeo.excavation;

import net.tfminecraft.archaeo.config.ArtifactTemplate;
import net.tfminecraft.archaeo.config.CatalogRegistry;
import net.tfminecraft.archaeo.model.BlockCell;
import net.tfminecraft.archaeo.model.BuriedFind;
import net.tfminecraft.archaeo.model.FindState;
import net.tfminecraft.archaeo.model.Site;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class NeighborTracesTest {
    private CatalogRegistry catalogs;
    private Site site;
    private Block origin;

    @Before
    public void setup() {
        World world = MockBukkit.mock().addSimpleWorld("traces");
        catalogs = mock(CatalogRegistry.class);
        site = new Site();
        origin = world.getBlockAt(8, 40, 8);
    }

    @After
    public void teardown() {
        MockBukkit.unmock();
    }

    @Test
    public void emptyNeighbourhoodProducesNoChatAndClearHud() {
        Map<String, Integer> counts = NeighborTraces.count(site, catalogs, origin);
        assertTrue(counts.isEmpty());
        assertNull(NeighborTraces.chatLine(catalogs, counts));
        assertEquals("clear", NeighborTraces.hudFragment(catalogs, counts));
        verifyNoInteractions(catalogs);
    }

    @Test
    public void countsEachFaceAdjacentCubeIncludingSeveralFromTheSameArtifact() {
        artifact("pot", "ceramic");
        BuriedFind pot = add("pot", origin.getRelative(BlockFace.WEST), origin.getRelative(-1, 0, -1),
                origin.getRelative(BlockFace.NORTH), origin.getRelative(1, 0, -1),
                origin.getRelative(BlockFace.EAST));
        artifact("bone", "bone");
        add("bone", origin.getRelative(BlockFace.UP));
        add("bone", origin.getRelative(BlockFace.DOWN));
        add("bone", origin.getRelative(BlockFace.SOUTH));
        Map<String, Integer> counts = NeighborTraces.count(site, catalogs, origin);
        assertEquals(Map.of("ceramic", 3, "bone", 3), counts);
        assertEquals(List.of("bone", "ceramic"), List.copyOf(counts.keySet()));
        assertEquals(FindState.HIDDEN, pot.getState());
        assertEquals(100, pot.getConservation());
    }

    @Test
    public void ignoresOriginDiagonalsAndMoreDistantFindCells() {
        add("pot", origin);
        add("pot", origin.getRelative(1, 1, 0));
        add("pot", origin.getRelative(1, 0, 1));
        add("pot", origin.getRelative(0, 0, 2));
        assertTrue(NeighborTraces.count(site, catalogs, origin).isEmpty());
        verifyNoInteractions(catalogs);
    }

    @Test
    public void lostRecoveredAndMissingTerrainDoNotLeaveTraces() {
        add("lost", origin.getRelative(BlockFace.UP)).setState(FindState.LOST);
        add("recovered", origin.getRelative(BlockFace.DOWN)).setState(FindState.RECOVERED);
        BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST};
        Material[] missing = {Material.AIR, Material.WATER, Material.TORCH};
        for (int i = 0; i < faces.length; i++) {
            Block block = origin.getRelative(faces[i]);
            add("gone", block);
            block.setType(missing[i]);
        }
        assertTrue(NeighborTraces.count(site, catalogs, origin).isEmpty());
        verifyNoInteractions(catalogs);
    }

    @Test
    public void missingTemplatesAndNullOrBlankMaterialGroupAsUnknown() {
        add("missing", origin.getRelative(BlockFace.UP));
        artifact("null", null);
        add("null", origin.getRelative(BlockFace.DOWN));
        artifact("blank", "  ");
        add("blank", origin.getRelative(BlockFace.NORTH));
        assertEquals(Map.of("unknown", 3), NeighborTraces.count(site, catalogs, origin));
    }

    @Test
    public void chatAndHudUseCatalogLabelsAndPreserveCountOrder() {
        when(catalogs.materialDisplayName("ceramic")).thenReturn("Ceramic");
        when(catalogs.materialDisplayName("bone")).thenReturn("Bone");
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("ceramic", 2);
        assertEquals("Traces of Ceramic: 2", NeighborTraces.chatLine(catalogs, counts));
        assertEquals("Ceramic: 2", NeighborTraces.hudFragment(catalogs, counts));
        counts.put("bone", 1);
        assertEquals("Traces of Ceramic: 2 · Bone: 1", NeighborTraces.chatLine(catalogs, counts));
        assertEquals("Ceramic: 2 · Bone: 1", NeighborTraces.hudFragment(catalogs, counts));
    }

    private void artifact(String id, String material) {
        when(catalogs.artifact(id)).thenReturn(new ArtifactTemplate(id, id, 1, 6, material,
                null, false, 1, Set.of(), Set.of(), null, List.of(), null));
    }

    private BuriedFind add(String id, Block... cells) {
        BuriedFind find = new BuriedFind();
        find.setArtifactId(id);
        for (Block cell : cells) {
            cell.setType(Material.STONE);
            find.getCells().add(new BlockCell(cell.getX(), cell.getY(), cell.getZ()));
        }
        site.getFinds().add(find);
        return find;
    }
}
