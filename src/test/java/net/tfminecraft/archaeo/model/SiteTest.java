package net.tfminecraft.archaeo.model;

import static org.junit.Assert.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

public class SiteTest {
    private final UUID director = UUID.randomUUID();
    private final UUID excavator = UUID.randomUUID();
    private final UUID outsider = UUID.randomUUID();

    @Test public void relocatingCampPreservesSiteIdentityStaffAndFieldRecords() {
        Site site = new Site();
        assertEquals(SiteType.MANAGED_RUIN, site.getType());
        assertEquals(SiteStatus.HIDDEN, site.getStatus());
        assertEquals("private", site.getVisibility());
        assertEquals(-1L, site.getJornadaWorldDay());
        assertFalse(site.hasEstablishment());
        UUID id = UUID.randomUUID();
        Instant created = Instant.parse("2020-01-02T03:04:05Z");
        site.setId(id);
        site.setSerial(42);
        site.setInterest(InterestLevel.HIGH);
        site.setName("Old quarry");
        site.setWorldName("overworld");
        site.setChunkX(-2);
        site.setChunkZ(3);
        site.setSurfaceY(64);
        site.setCreatedBy(director);
        site.setCreatedAt(created);
        site.setDirector(director);
        site.setVisibility("public");
        site.setRecoveredCount(5);
        site.setCampSignX(-25);
        site.setCampSignY(65);
        site.setCampSignZ(57);
        site.setCampFacing("WEST");
        site.setJornadaWorldDay(12L);
        site.setJornadaPickLeft(20);
        site.getFactions().add("historians");
        site.relocateCamp(9, 10, 144, 70, 160);
        assertEquals(id, site.getId());
        assertEquals(42, site.getSerial());
        assertEquals(InterestLevel.HIGH, site.getInterest());
        assertEquals("Old quarry", site.getName());
        assertEquals("overworld", site.getWorldName());
        assertEquals(-2, site.getChunkX());
        assertEquals(3, site.getChunkZ());
        assertEquals(64, site.getSurfaceY());
        assertEquals(director, site.getCreatedBy());
        assertEquals(created, site.getCreatedAt());
        assertEquals(director, site.getDirector());
        assertEquals("public", site.getVisibility());
        assertEquals(5, site.getRecoveredCount());
        assertEquals(Integer.valueOf(-25), site.getCampSignX());
        assertEquals(Integer.valueOf(65), site.getCampSignY());
        assertEquals(Integer.valueOf(57), site.getCampSignZ());
        assertEquals("WEST", site.getCampFacing());
        assertEquals(12L, site.getJornadaWorldDay());
        assertEquals(20, site.getJornadaPickLeft());
        assertEquals(List.of("historians"), site.getFactions());
        assertEquals(-24, site.centerBlockX());
        assertEquals(56, site.centerBlockZ());
        assertEquals("Old quarry", site.publicName());
        assertEquals("#42 — Old quarry", site.displayLabel());
        site.setName(" \t");
        assertEquals("Site", site.publicName());
        site.setName(null);
        assertEquals("Site", site.publicName());
    }

    @Test public void campWoolDefaultsHandleMissingAndBlankLegacyValues() {
        Site site = new Site();
        assertEquals("WHITE", site.getCampWoolPrimary());
        assertEquals("RED", site.getCampWoolSecondary());
        site.setCampWoolPrimary(null);
        site.setCampWoolSecondary(null);
        assertEquals("WHITE", site.getCampWoolPrimary());
        assertEquals("RED", site.getCampWoolSecondary());
        site.setCampWoolPrimary(" ");
        site.setCampWoolSecondary("\t");
        assertEquals("WHITE", site.getCampWoolPrimary());
        assertEquals("RED", site.getCampWoolSecondary());
        site.setCampWoolPrimary("BLUE");
        site.setCampWoolSecondary("GREEN");
        assertEquals("BLUE", site.getCampWoolPrimary());
        assertEquals("GREEN", site.getCampWoolSecondary());
    }

    @Test public void prismUsesInclusivePresentBandsAndNegativeChunkBoundaries() {
        Site site = new Site();
        site.setChunkX(-1);
        site.setChunkZ(2);
        assertTrue(site.isInRuinChunk(-16, 32));
        assertTrue(site.isInRuinChunk(-1, 47));
        assertFalse(site.isInRuinChunk(-17, 32));
        assertFalse(site.isInRuinChunk(0, 32));
        assertFalse(site.isInRuinChunk(-1, 31));
        assertFalse(site.isInRuinChunk(-1, 48));
        assertFalse(site.isInPrism(-1, 62, 32));
        assertNull(site.stratumAt(62));
        site.getStrata().put("absent", band(false, 0, 100));
        StratumBand present = band(true, 60, 64);
        site.getStrata().put("present", present);
        for (int y : new int[] {60, 62, 64}) {
            assertTrue(site.isInPrism(-1, y, 32));
            assertSame(present, site.stratumAt(y));
        }
        for (int y : new int[] {59, 65}) {
            assertFalse(site.isInPrism(-1, y, 32));
            assertNull(site.stratumAt(y));
        }
        assertFalse(site.isInPrism(0, 62, 32));
    }

    @Test public void rosterRolesAndReadOnlyLookupsPreserveAppointmentsAndTallies() {
        Site site = new Site();
        assertFalse(site.isDirector(director));
        assertFalse(site.onStaff(null));
        assertFalse(site.onStaff(outsider));
        assertNull(site.staffLog(null));
        assertNull(site.staffLog(outsider));
        assertNull(site.workerRecord(outsider));
        assertEquals(SiteRole.ARCHAEOLOGIST, site.roleOf(outsider));
        assertTrue(site.getWorkers().isEmpty());
        site.setDirector(director);
        assertTrue(site.isDirector(director));
        assertFalse(site.isDirector(outsider));
        assertTrue(site.onStaff(director));
        assertEquals(SiteRole.DIRECTOR, site.roleOf(director));
        assertEquals(SiteRole.DIRECTOR, site.staffLog(director).getRole());
        assertFalse(site.grantExcavator(null));
        assertTrue(site.grantExcavator(excavator));
        assertFalse(site.grantExcavator(excavator));
        assertTrue(site.onStaff(excavator));
        WorkerRecord record = site.workerRecord(excavator);
        assertNotNull(record.getJoinedAt());
        assertSame(record, site.worker(excavator));
        assertSame(record, site.staffLog(excavator));
        assertFalse(site.assignRole(null, SiteRole.EXCAVATOR));
        assertFalse(site.assignRole(excavator, null));
        assertFalse(site.assignRole(excavator, SiteRole.DIRECTOR));
        assertFalse(site.assignRole(director, SiteRole.EXCAVATOR));
        assertFalse(site.assignRole(outsider, SiteRole.EXCAVATOR));
        assertFalse(site.assignRole(excavator, SiteRole.ARCHAEOLOGIST));
        assertTrue(site.assignRole(excavator, SiteRole.EXCAVATOR));
        assertEquals(SiteRole.EXCAVATOR, site.roleOf(excavator));
        record.addBlocksRemoved(3);
        assertFalse(site.revokeExcavator(null));
        assertFalse(site.revokeExcavator(director));
        assertFalse(site.revokeExcavator(outsider));
        assertTrue(site.revokeExcavator(excavator));
        assertFalse(site.onStaff(excavator));
        assertEquals(3, record.getBlocksRemoved());
        assertEquals(SiteRole.ARCHAEOLOGIST, record.getRole());
        assertTrue(site.grantExcavator(excavator));
        assertSame(record, site.workerRecord(excavator));
        site.getExcavators().add(outsider); // Legacy roster entries need not have a worker page.
        assertTrue(site.revokeExcavator(outsider));
    }

    @Test public void lifecycleControlsFieldWorkButKeepsArchiveAvailable() {
        Site site = new Site();
        site.setDirector(director);
        site.grantExcavator(excavator);
        for (SiteStatus status : SiteStatus.values()) {
            site.setStatus(status);
            boolean working = status == SiteStatus.ESTABLISHED;
            boolean consult = status != SiteStatus.HIDDEN;
            assertEquals(working, site.mayWork(director));
            assertEquals(working, site.mayWork(excavator));
            assertEquals(working, site.mayRecover(excavator));
            assertFalse(site.mayWork(null));
            assertFalse(site.mayWork(outsider));
            assertFalse(site.mayCatalog(null));
            assertFalse(site.mayCatalog(outsider));
            assertEquals(consult, site.mayConsult());
            assertEquals(consult, site.mayCatalog(director));
            assertEquals(consult, site.mayCatalog(excavator));
            assertEquals(status == SiteStatus.ESTABLISHED || status == SiteStatus.EXHAUSTED, site.isCampLocked());
        }
        site.setStatus(SiteStatus.ESTABLISHED);
        site.assignRole(excavator, SiteRole.EXCAVATOR);
        assertTrue(site.mayWork(excavator));
        assertFalse(site.mayRecover(excavator));
        assertFalse(site.mayCatalog(excavator));
    }

    @Test public void establishmentAndRelocationPreserveTheArchaeologicalChunk() {
        Site site = new Site();
        site.setChunkX(-1);
        site.setChunkZ(2);
        site.setEstablishmentChunkX(1);
        assertFalse(site.hasEstablishment());
        site.establish(director, 3, 4, 48, 65, 64);
        assertTrue(site.hasEstablishment());
        assertEquals(SiteType.EXCAVATION, site.getType());
        assertEquals(SiteStatus.ESTABLISHED, site.getStatus());
        assertEquals(director, site.getDirector());
        assertEquals(SiteRole.DIRECTOR, site.worker(director).getRole());
        site.establish(director, 3, 4, 48, 65, 64);
        assertEquals(List.of(director), site.getExcavators());
        site.relocateCamp(-3, -4, -48, 70, -64);
        assertEquals(Integer.valueOf(-3), site.getEstablishmentChunkX());
        assertEquals(Integer.valueOf(-4), site.getEstablishmentChunkZ());
        assertEquals(Integer.valueOf(-48), site.getCampX());
        assertEquals(Integer.valueOf(70), site.getCampY());
        assertEquals(Integer.valueOf(-64), site.getCampZ());
        assertEquals(-1, site.getChunkX());
        assertEquals(2, site.getChunkZ());
        site.getCampBlocks().add(new BlockCell(-48, 70, -64));
        assertTrue(site.isCampBlock(-48, 70, -64));
        assertFalse(site.isCampBlock(-48, 71, -64));
    }

    @Test public void prospectSamplesAreUniquePerPlayerAndConfirmationIsIdempotent() {
        Site site = new Site();
        BlockCell cell = new BlockCell(1, 2, 3);
        assertTrue(site.prospectSamples(director).isEmpty());
        assertTrue(site.allProspectSamples().isEmpty());
        assertFalse(site.hasProspectSample(director, cell));
        assertTrue(site.addProspectSample(director, cell));
        assertFalse(site.addProspectSample(director, new BlockCell(1, 2, 3)));
        assertTrue(site.hasProspectSample(director, cell));
        assertTrue(site.addProspectSample(excavator, cell));
        assertEquals(List.of(cell), site.prospectSamples(director));
        assertSame(site.prospectSamples(director), site.allProspectSamples().get(director));
        assertFalse(site.isProspectConfirmed(director));
        site.confirmProspect(director);
        site.confirmProspect(director);
        assertTrue(site.isProspectConfirmed(director));
        assertFalse(site.isProspectConfirmed(excavator));
        assertEquals(1, site.getProspectConfirmed().size());
    }

    @Test public void findLookupsAndCatalogSortingDoNotExposeUnregisteredHiddenFinds() {
        Site site = new Site();
        BuriedFind hidden = find(FindState.HIDDEN, 0);
        BuriedFind numbered = find(FindState.HIDDEN, 7);
        BuriedFind recovered = find(FindState.RECOVERED, 3);
        BuriedFind lost = find(FindState.LOST, 0);
        BlockCell cell = new BlockCell(1, 2, 3);
        recovered.getCells().add(cell);
        site.getFinds().addAll(List.of(hidden, numbered, recovered, lost));
        assertSame(recovered, site.findAt(cell).orElseThrow());
        assertTrue(site.findAt(new BlockCell(9, 9, 9)).isEmpty());
        assertTrue(site.findById(null).isEmpty());
        assertTrue(site.findById(UUID.randomUUID()).isEmpty());
        assertSame(recovered, site.findById(recovered.getId()).orElseThrow());
        assertEquals(List.of(lost, recovered, numbered), site.cataloguedFinds());
        assertEquals(List.of(hidden, numbered, recovered, lost), site.getFinds());
    }

    @Test public void catalogingSettledFindsAssignsSequentialNumbersAndPreservesProvenance() {
        Site site = new Site();
        BuriedFind hidden = find(FindState.HIDDEN, 0);
        BuriedFind old = find(FindState.RECOVERED, 8);
        old.setRecoveredAt(Instant.EPOCH);
        old.setRecoveredBy(outsider);
        BuriedFind recovered = find(FindState.RECOVERED, 0);
        BuriedFind lost = find(FindState.LOST, 0);
        site.getFinds().addAll(List.of(hidden, old, recovered, lost));
        Instant before = Instant.now();
        assertTrue(site.catalogSettledFinds(null));
        assertEquals(9, recovered.getFindNumber());
        assertEquals(10, lost.getFindNumber());
        assertEquals(0, hidden.getFindNumber());
        assertNull(hidden.getRecoveredAt());
        assertFalse(recovered.getRecoveredAt().isBefore(before));
        assertEquals(recovered.getRecoveredAt(), lost.getRecoveredAt());
        assertNull(recovered.getRecoveredBy());
        assertEquals(Instant.EPOCH, old.getRecoveredAt());
        assertEquals(outsider, old.getRecoveredBy());
        assertFalse(site.catalogSettledFinds(null));
        assertTrue(site.catalogSettledFinds(director));
        assertEquals(director, recovered.getRecoveredBy());
        assertEquals(director, lost.getRecoveredBy());
        assertEquals(outsider, old.getRecoveredBy());
        assertFalse(site.catalogSettledFinds(excavator));
    }

    @Test public void legacyNumberMigrationDoesNotInventRecoveryTimeOrActor() {
        Site site = new Site();
        BuriedFind hidden = find(FindState.HIDDEN, 0);
        BuriedFind old = find(FindState.RECOVERED, 6);
        BuriedFind lost = find(FindState.LOST, 0);
        site.getFinds().addAll(List.of(hidden, old, lost));
        assertTrue(site.assignMissingFindNumbers());
        assertEquals(7, lost.getFindNumber());
        assertEquals(6, old.getFindNumber());
        assertEquals(0, hidden.getFindNumber());
        assertNull(lost.getRecoveredBy());
        assertNull(lost.getRecoveredAt());
        assertFalse(site.assignMissingFindNumbers());
    }

    @Test public void completionCountsLostPiecesAndExhaustsOnlyNonemptySettledActiveCuts() {
        Site site = new Site();
        assertFalse(site.hasPendingFinds());
        assertEquals(0, site.settledFindCount());
        assertEquals(0, site.completionPercent());
        assertFalse(site.isUnfinishedCut());
        assertFalse(site.exhaustIfSettled());
        site.setStatus(SiteStatus.ESTABLISHED);
        assertFalse(site.exhaustIfSettled());
        BuriedFind pending = find(FindState.PARTIAL, 0);
        site.getFinds().addAll(List.of(find(FindState.RECOVERED, 1), find(FindState.LOST, 2), pending));
        assertTrue(site.hasPendingFinds());
        assertEquals(2, site.settledFindCount());
        assertEquals(67, site.completionPercent());
        assertTrue(site.isUnfinishedCut());
        assertFalse(site.exhaustIfSettled());
        pending.setState(FindState.RECOVERED);
        assertFalse(site.hasPendingFinds());
        assertEquals(100, site.completionPercent());
        assertFalse(site.isUnfinishedCut());
        assertTrue(site.exhaustIfSettled());
        assertEquals(SiteStatus.EXHAUSTED, site.getStatus());
        assertFalse(site.exhaustIfSettled());
        assertTrue(site.closeCamp());
        assertEquals(SiteStatus.CLOSED, site.getStatus());
        assertFalse(site.closeCamp());
        site.setStatus(SiteStatus.HIDDEN);
        assertFalse(site.closeCamp());
        site.setStatus(SiteStatus.ESTABLISHED);
        assertTrue(site.closeCamp());
    }

    @Test public void recordedWoundsDetectFieldDamagePriorDisturbanceAndRevealedState() {
        Site site = new Site();
        assertFalse(site.hasRecordedFindWounds());
        BuriedFind find = find(FindState.HIDDEN, 0);
        site.getFinds().add(find);
        assertFalse(site.hasRecordedFindWounds());
        find.setState(FindState.DISCOVERED);
        assertTrue(site.hasRecordedFindWounds());
        find.setState(FindState.HIDDEN);
        BlockCell cell = new BlockCell(0, 60, 0);
        find.getPriorCells().add(cell);
        assertTrue(site.hasRecordedFindWounds());
        find.getPriorCells().clear();
        find.getGrazedCells().add(cell);
        assertTrue(site.hasRecordedFindWounds());
    }

    @Test public void rebuildingGeneratedLayoutClearsWealthButKeepsIdentityAndConfirmedProspects() {
        Site site = new Site();
        UUID id = UUID.randomUUID();
        site.setId(id);
        site.setName("Ruin");
        site.setChunkX(3);
        site.setCreatedBy(director);
        site.setRecoveredCount(2);
        site.getStrata().put("I", band(true, 0, 5));
        site.getFinds().add(find(FindState.HIDDEN, 0));
        site.getHintIds().add("pottery");
        BlockCell cell = new BlockCell(1, 2, 3);
        assertEquals(2, site.addFillDamage(cell, 2));
        assertEquals(5, site.addFillDamage(new BlockCell(1, 2, 3), 3));
        assertEquals(Integer.valueOf(5), site.getFillDamage().get(cell));
        site.clearFillDamage(cell);
        assertTrue(site.getFillDamage().isEmpty());
        site.addFillDamage(cell, 1);
        site.addProspectSample(director, cell);
        site.confirmProspect(director);
        site.clearGeneratedLayout();
        assertTrue(site.getStrata().isEmpty());
        assertTrue(site.getFinds().isEmpty());
        assertTrue(site.getHintIds().isEmpty());
        assertTrue(site.getFillDamage().isEmpty());
        assertTrue(site.allProspectSamples().isEmpty());
        assertEquals(0, site.getRecoveredCount());
        assertEquals(id, site.getId());
        assertEquals("Ruin", site.getName());
        assertEquals(3, site.getChunkX());
        assertEquals(director, site.getCreatedBy());
        assertTrue(site.isProspectConfirmed(director));
    }

    private static StratumBand band(boolean present, int min, int max) {
        StratumBand band = new StratumBand();
        band.setPresent(present);
        band.setMinY(min);
        band.setMaxY(max);
        return band;
    }

    private static BuriedFind find(FindState state, int number) {
        BuriedFind find = new BuriedFind();
        find.setId(UUID.randomUUID());
        find.setState(state);
        find.setFindNumber(number);
        return find;
    }
}
