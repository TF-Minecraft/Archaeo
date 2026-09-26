package net.tfminecraft.archaeo.excavation;

import net.tfminecraft.archaeo.model.BlockCell;
import net.tfminecraft.archaeo.model.BuriedFind;
import net.tfminecraft.archaeo.model.FindState;
import net.tfminecraft.archaeo.model.Site;
import net.tfminecraft.archaeo.model.StratumBand;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PrismWoundTest {
    @Before
    public void startServer() {
        MockBukkit.mock();
    }

    @After
    public void stopServer() {
        MockBukkit.unmock();
    }

    @Test
    public void claimCountsPiecesAndLossesAndDoesNotChargeMissingCellsTwice() {
        Site site = site();
        BuriedFind partial = find(site, 1, 2, 3, 4);
        BuriedFind lost = find(site, 5);
        BuriedFind intact = find(site, 6);
        World world = mock(World.class);
        block(world, 1, Material.AIR, false);
        block(world, 2, Material.WATER, true);
        block(world, 3, Material.TORCH, false);
        block(world, 4, Material.STONE, false);
        block(world, 5, Material.CAVE_AIR, false);
        block(world, 6, Material.STONE, false);
        PrismWound.Prior first = PrismWound.markMissingTerrain(world, site);
        assertEquals(2, first.disturbed());
        assertEquals(1, first.lost());
        assertEquals(List.of(cell(1), cell(2), cell(3)), List.copyOf(partial.getPriorCells()));
        assertEquals(25, partial.getConservation());
        assertFalse(partial.isFieldDamaged());
        assertEquals(FindState.LOST, lost.getState());
        assertEquals(100, intact.getConservation());
        assertEquals(new PrismWound.Prior(0, 0), PrismWound.markMissingTerrain(world, site));
    }

    @Test
    public void emptyClaimHasNoDisturbanceOrWorldReads() {
        World world = mock(World.class);
        assertEquals(new PrismWound.Prior(0, 0), PrismWound.markMissingTerrain(world, site()));
        verifyNoInteractions(world);
    }

    @Test
    public void missingTerrainQueryScansPastIntactCellsWithoutChangingFinds() {
        Site site = site();
        BuriedFind find = find(site, 1, 2);
        World world = mock(World.class);
        block(world, 1, Material.STONE, false);
        Block second = block(world, 2, Material.STONE, false);
        assertFalse(PrismWound.hasMissingFindTerrain(world, site));
        for (Material missing : List.of(Material.AIR, Material.WATER, Material.TORCH)) {
            when(second.getType()).thenReturn(missing);
            when(second.isLiquid()).thenReturn(missing == Material.WATER);
            assertTrue(missing.toString(), PrismWound.hasMissingFindTerrain(world, site));
        }
        assertEquals(100, find.getConservation());
        assertTrue(find.getPriorCells().isEmpty());
        assertEquals(FindState.HIDDEN, find.getState());
    }

    @Test
    public void missingTerrainQueryStopsAtFirstMissingCell() {
        Site site = site();
        find(site, 1, 2);
        World world = mock(World.class);
        block(world, 1, Material.AIR, false);
        assertTrue(PrismWound.hasMissingFindTerrain(world, site));
        verify(world, never()).getBlockAt(2, 10, 1);
    }

    @Test
    public void removalOutsideChunkOrStratumDoesNotTouchDossier() {
        Site site = site();
        BuriedFind find = find(site, 1, 2);
        for (int[] pos : new int[][] {{-1, 10, 1}, {16, 10, 1}, {1, 10, -1}, {1, 10, 16}, {1, 9, 1}, {1, 13, 1}}) {
            assertEquals(PrismWound.Removal.none(), PrismWound.onCellRemoved(site, pos[0], pos[1], pos[2]));
        }
        assertFalse(site.stratumAt(10).isDisturbed());
        assertEquals(100, find.getConservation());
    }

    @Test
    public void emptyPrismCellDisturbsLayerOnlyOnceIncludingInclusiveEdges() {
        Site site = site();
        PrismWound.Removal first = PrismWound.onCellRemoved(site, 0, 10, 0);
        assertTrue(first.dossierChanged());
        assertFalse(first.findSmashed());
        assertTrue(site.stratumAt(10).isDisturbed());
        assertEquals(PrismWound.Removal.none(), PrismWound.onCellRemoved(site, 15, 12, 15));
        Site upper = site();
        assertEquals(new PrismWound.Removal(true, false), PrismWound.onCellRemoved(upper, 15, 12, 15));
    }

    @Test
    public void removalGrazesFindEvenWhenLayerWasAlreadyDisturbed() {
        Site site = site();
        BuriedFind find = find(site, 1, 2, 3, 4);
        assertEquals(new PrismWound.Removal(true, true), PrismWound.onCellRemoved(site, 1, 10, 1));
        assertEquals(75, find.getConservation());
        assertEquals(new PrismWound.Removal(true, true), PrismWound.onCellRemoved(site, 2, 10, 1));
        assertEquals(50, find.getConservation());
        assertEquals(PrismWound.Removal.none(), PrismWound.onCellRemoved(site, 2, 10, 1));
        assertEquals(List.of(cell(1), cell(2)), List.copyOf(find.getGrazedCells()));
    }

    @Test
    public void absentStratumDoesNotAcceptRemoval() {
        Site site = site();
        site.getStrata().get("I").setPresent(false);
        assertEquals(PrismWound.Removal.none(), PrismWound.onCellRemoved(site, 1, 10, 1));
        assertFalse(site.getStrata().get("I").isDisturbed());
    }

    @Test
    public void aimedAndFallingHitsApplyDifferentDamageAndAreIdempotent() {
        Site directSite = site();
        BuriedFind direct = find(directSite, 1, 2, 3, 4);
        direct.setBuriedConservation(90);
        assertTrue(PrismWound.smashFindAt(directSite, 1, 10, 1, true));
        assertEquals(40, direct.getConservation());
        assertEquals(List.of(cell(1)), List.copyOf(direct.getDirectHitCells()));
        assertFalse(PrismWound.smashFindAt(directSite, 1, 10, 1, true));
        Site fallingSite = site();
        BuriedFind falling = find(fallingSite, 1, 2, 3, 4);
        falling.setBuriedConservation(90);
        assertTrue(PrismWound.smashFindAt(fallingSite, 1, 10, 1));
        assertEquals(65, falling.getConservation());
        assertFalse(PrismWound.smashFindAt(fallingSite, 1, 10, 1));
    }

    @Test
    public void hitsIgnoreEmptyRecoveredAndLostCells() {
        Site site = site();
        assertFalse(PrismWound.smashFindAt(site, 1, 10, 1, true));
        BuriedFind find = find(site, 1, 2);
        for (FindState terminal : List.of(FindState.RECOVERED, FindState.LOST)) {
            find.setState(terminal);
            assertFalse(PrismWound.smashFindAt(site, 1, 10, 1, true));
            assertFalse(PrismWound.smashFindAt(site, 1, 10, 1, false));
        }
        assertTrue(find.getGrazedCells().isEmpty());
        assertTrue(find.getDirectHitCells().isEmpty());
    }

    private static Site site() {
        Site site = new Site();
        StratumBand band = new StratumBand();
        band.setId("I");
        band.setPresent(true);
        band.setMinY(10);
        band.setMaxY(12);
        site.getStrata().put("I", band);
        return site;
    }

    private static BuriedFind find(Site site, int... xs) {
        BuriedFind find = new BuriedFind();
        find.setId(UUID.randomUUID());
        for (int x : xs) find.getCells().add(cell(x));
        site.getFinds().add(find);
        return find;
    }

    private static BlockCell cell(int x) {
        return new BlockCell(x, 10, 1);
    }

    private static Block block(World world, int x, Material type, boolean liquid) {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(type);
        when(block.isLiquid()).thenReturn(liquid);
        when(world.getBlockAt(x, 10, 1)).thenReturn(block);
        return block;
    }
}
