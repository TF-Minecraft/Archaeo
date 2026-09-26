package net.tfminecraft.archaeo.model;

import org.junit.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.*;

public class BuriedFindTest {
    @Test
    public void normalizesOptionalItemsAndUsesTheFirstAvailableName() {
        BuriedFind find = new BuriedFind();
        assertEquals("recovered find", find.shownName(null));
        find.setArtifactId(" ");
        assertEquals("recovered find", find.shownName(" "));
        find.setArtifactId("coin");
        assertEquals("coin", find.shownName(null));
        assertEquals("Roman coin", find.shownName("Roman coin"));
        find.setGivenName("Lucky coin");
        assertEquals("Lucky coin", find.getGivenName());
        assertEquals("Lucky coin", find.shownName("Roman coin"));
        find.setGivenName(" \t");
        assertNull(find.getGivenName());
        assertEquals("Roman coin", find.shownName("Roman coin"));
        find.setGivenName(null);
        assertNull(find.getGivenName());
        find.setItem("itemsadder:archaeo:coin");
        assertEquals("itemsadder:archaeo:coin", find.getItem());
        find.setItem(" \t");
        assertNull(find.getItem());
        find.setItem(null);
        assertNull(find.getItem());
    }

    @Test
    public void sanitizesNamesAndCapsTheStoredLength() {
        assertNull(BuriedFind.sanitizeGivenName(null));
        assertNull(BuriedFind.sanitizeGivenName("  § \t"));
        assertEquals("Old  Coin", BuriedFind.sanitizeGivenName("  Old§ Coin  "));
        assertEquals("a".repeat(40), BuriedFind.sanitizeGivenName("a".repeat(40)));
        assertEquals("a".repeat(40), BuriedFind.sanitizeGivenName("a".repeat(41)));
        assertEquals("a".repeat(39), BuriedFind.sanitizeGivenName("a".repeat(39) + " b"));
    }

    @Test
    public void numbersAreNonNegativeAndScopedToThePublicSiteName() {
        BuriedFind find = new BuriedFind();
        assertEquals(0, find.getFindNumber());
        assertNull(find.publicNumber(null));
        find.setFindNumber(-5);
        assertEquals(0, find.getFindNumber());
        find.setFindNumber(7);
        assertEquals(7, find.getFindNumber());
        assertEquals("#Site-7", find.publicNumber(null));
        Site site = new Site();
        site.setName("River camp");
        assertEquals("#River camp-7", find.publicNumber(site));
    }

    @Test
    public void validatesReadingsAndRestrictsQuestionsAndTotalCapacity() {
        BuriedFind find = new BuriedFind();
        assertFalse(find.addInterpretation(null));
        assertFalse(find.addInterpretation(reading("purpose", null)));
        assertFalse(find.addInterpretation(reading("purpose", " ")));
        assertFalse(find.hasType(null));
        assertFalse(find.hasType(" "));
        assertFalse(find.hasType("purpose"));
        assertFalse(find.hasInterpretation(null));
        assertFalse(find.hasInterpretation("coin"));
        assertFalse(find.removeInterpretation(null));
        FindInterpretation first = reading("purpose", "coin");
        FindInterpretation second = reading(null, "bronze");
        FindInterpretation third = reading(" ", "roman");
        assertTrue(find.addInterpretation(first));
        assertFalse(find.addInterpretation(reading("purpose", "token")));
        assertFalse(find.addInterpretation(reading("age", "coin")));
        assertTrue(find.addInterpretation(second));
        assertTrue(find.addInterpretation(third));
        assertEquals(List.of(first, second, third), find.getInterpretations());
        assertFalse(find.addInterpretation(reading("age", "medieval")));
        assertTrue(find.hasType("purpose"));
        assertFalse(find.hasType("age"));
        assertTrue(find.hasInterpretation("bronze"));
        assertFalse(find.hasInterpretation("missing"));
        assertFalse(find.removeInterpretation("missing"));
        assertTrue(find.removeInterpretation("bronze"));
        assertEquals(List.of(first, third), find.getInterpretations());
        assertTrue(find.addInterpretation(reading("age", "medieval")));
    }

    @Test
    public void statusLabelsPreferLossThenCatalogThenStudyProgress() {
        BuriedFind find = new BuriedFind();
        assertFalse(find.hasLeftTheCut());
        assertFalse(find.isCatalogued());
        assertEquals("Field catalog", find.catalogStatusLabel());
        find.setLabCleaned(true);
        assertTrue(find.isLabCleaned());
        assertEquals("Cleaned", find.catalogStatusLabel());
        find.setFieldSketch(true);
        assertTrue(find.hasFieldSketch());
        assertEquals("Sketched", find.catalogStatusLabel());
        find.setStudied(true);
        assertTrue(find.isStudied());
        assertEquals("Studied", find.catalogStatusLabel());
        assertTrue(find.addInterpretation(reading("purpose", "coin")));
        assertTrue(find.isCatalogued());
        assertEquals("Catalogued", find.catalogStatusLabel());
        find.setState(FindState.RECOVERED);
        assertTrue(find.hasLeftTheCut());
        find.setState(FindState.LOST);
        assertTrue(find.hasLeftTheCut());
        assertEquals("Lost in the cut", find.catalogStatusLabel());
    }

    @Test
    public void brushingRetainsProgressUntilCompletionOrCancellation() {
        BuriedFind find = shape(1);
        BlockCell cell = find.getCells().getFirst();
        assertNull(find.brushRemaining(cell));
        assertFalse(find.isCleaned(cell));
        find.setBrushRemaining(cell, 20);
        assertEquals(Integer.valueOf(20), find.brushRemaining(cell));
        assertEquals(Integer.valueOf(20), find.getBrushRemaining().get(cell));
        find.setBrushRemaining(cell, 0);
        assertNull(find.brushRemaining(cell));
        find.setBrushRemaining(cell, 2);
        find.setBrushRemaining(cell, -1);
        assertNull(find.brushRemaining(cell));
        find.setBrushRemaining(null, 2);
        assertFalse(find.getBrushRemaining().containsKey(null));
        find.setBrushRemaining(cell, 10);
        find.markCleaned(cell);
        find.markCleaned(cell);
        assertTrue(find.isCleaned(cell));
        assertEquals(Set.of(cell), find.getCleanedCells());
        assertNull(find.brushRemaining(cell));
    }

    @Test
    public void preexistingDamageAndGrazesShareOnePenaltyWhileDirectHitsAddTwo() {
        BuriedFind find = shape(4);
        BlockCell first = find.getCells().get(0);
        BlockCell second = find.getCells().get(1);
        assertFalse(find.isDisturbedBeforeDig());
        assertFalse(find.isFieldDamaged());
        assertFalse(find.woundBeforeDig(null));
        assertFalse(find.woundFromAbove(new BlockCell(100, 100, 100)));
        assertTrue(find.woundBeforeDig(first));
        assertFalse(find.woundBeforeDig(first));
        assertTrue(find.isDisturbedBeforeDig());
        assertFalse(find.isFieldDamaged());
        assertEquals(Set.of(first), find.getPriorCells());
        assertEquals(75, find.getConservation());
        assertTrue(find.woundFromAbove(first));
        assertFalse(find.woundFromAbove(first));
        assertTrue(find.isFieldDamaged());
        assertEquals(Set.of(first), find.getGrazedCells());
        assertEquals(75, find.getConservation());
        assertTrue(find.woundDirect(second));
        assertFalse(find.woundDirect(second));
        assertEquals(Set.of(second), find.getDirectHitCells());
        assertEquals(25, find.getConservation());
        assertTrue(find.woundDirect(first));
        assertEquals(0, find.getConservation());
        assertEquals(FindState.LOST, find.getState());
        find.refreshConservation();
        assertEquals(FindState.LOST, find.getState());
    }

    @Test
    public void roundsFractionalDamageAndPreservesRecoveredState() {
        BuriedFind find = shape(3);
        find.setBuriedConservation(90);
        assertEquals(90, find.getBuriedConservation());
        assertEquals(90, find.getConservation());
        assertTrue(find.woundDirect(find.getCells().get(0)));
        assertTrue(find.isFieldDamaged());
        assertEquals(23, find.getConservation());
        find.setState(FindState.RECOVERED);
        assertTrue(find.woundFromAbove(find.getCells().get(1)));
        assertEquals(0, find.getConservation());
        assertEquals(FindState.RECOVERED, find.getState());
    }

    @Test
    public void clampsConservationAndHandlesEmptyShapes() {
        BuriedFind find = new BuriedFind(); find.setState(FindState.DISCOVERED);
        find.setConservation(1);
        assertEquals(FindState.DISCOVERED, find.getState());
        find.setConservation(-1);
        assertEquals(0, find.getConservation());
        assertEquals(FindState.LOST, find.getState()); // Nothing left to recover.
        BuriedFind recovered = new BuriedFind(); recovered.setState(FindState.RECOVERED); recovered.setConservation(0);
        assertEquals(FindState.RECOVERED, recovered.getState()); // A settled find keeps its record.
        find.setConservation(42);
        assertEquals(42, find.getConservation());
        find.setConservation(101);
        assertEquals(100, find.getConservation());
        find.setBuriedConservation(101);
        assertEquals(100, find.getBuriedConservation());
        assertEquals(100, find.getConservation());
        find.setBuriedConservation(-1);
        assertEquals(0, find.getBuriedConservation());
        assertEquals(0, find.getConservation());
        assertEquals(FindState.LOST, find.getState());
    }

    private static BuriedFind shape(int size) {
        BuriedFind find = new BuriedFind();
        for (int x = 0; x < size; x++) {
            find.getCells().add(new BlockCell(x, 10, 0));
        }
        return find;
    }

    private static FindInterpretation reading(String type, String id) {
        return new FindInterpretation(type, id, UUID.randomUUID(), Instant.EPOCH);
    }
}
