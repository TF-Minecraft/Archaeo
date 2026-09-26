package net.tfminecraft.archaeo.model;

import static org.junit.Assert.*;

import java.time.Instant;
import java.util.function.Consumer;
import org.junit.Test;

public class WorkerRecordTest {
    @Test public void invalidLegacyTalliesDoNotInventWorkAndMissingRoleDefaultsToArchaeologist() {
        WorkerRecord record = new WorkerRecord();
        record.setRole(null);
        record.setBlocksRemoved(-12);
        record.setCellsBrushed(-11);
        record.setFindsRecovered(-10);
        record.setFindsDamaged(-9);
        record.setFindsLost(-8);
        assertEquals(SiteRole.ARCHAEOLOGIST, record.getRole());
        assertEquals(0, record.getBlocksRemoved());
        assertEquals(0, record.getCellsBrushed());
        assertEquals(0, record.getFindsRecovered());
        assertEquals(0, record.getFindsDamaged());
        assertEquals(0, record.getFindsLost());
        assertFalse(record.hasWorked());
        record.noteFindRecovered();
        assertTrue(record.hasWorked());
        assertEquals(1, record.getFindsRecovered());
    }

    @Test public void handEditedDossierRolesParseLooselyAndUnknownRolesCannotGrantDirection() {
        assertEquals(SiteRole.DIRECTOR, SiteRole.fromYaml(" Director "));
        assertEquals(SiteRole.EXCAVATOR, SiteRole.fromYaml("EXCAVATOR"));
        for (String raw : new String[] {null, "", " ", "foreman", "owner"}) assertEquals(raw, SiteRole.ARCHAEOLOGIST, SiteRole.fromYaml(raw));
    }

    @Test public void nonPositiveSpoilActionsDoNotCountOrRefreshActivity() {
        WorkerRecord record = new WorkerRecord();
        Instant active = Instant.EPOCH;
        record.setLastActiveAt(active);
        record.addBlocksRemoved(0);
        record.addBlocksRemoved(-1);
        assertEquals(0, record.getBlocksRemoved());
        assertEquals(active, record.getLastActiveAt());
        assertFalse(record.hasWorked());
    }

    @Test public void eachFieldActionIndependentlyCountsAsWorkAndStampsActivity() {
        assertAction(record -> record.addBlocksRemoved(3));
        assertAction(WorkerRecord::noteCellBrushed);
        assertAction(WorkerRecord::noteFindRecovered);
        assertAction(WorkerRecord::noteFindDamaged);
        assertAction(WorkerRecord::noteFindLost);
        WorkerRecord record = new WorkerRecord();
        record.addBlocksRemoved(2);
        record.addBlocksRemoved(3);
        record.noteCellBrushed();
        record.noteCellBrushed();
        record.noteFindRecovered();
        record.noteFindDamaged();
        record.noteFindLost();
        assertEquals(5, record.getBlocksRemoved());
        assertEquals(2, record.getCellsBrushed());
        assertEquals(1, record.getFindsRecovered());
        assertEquals(1, record.getFindsDamaged());
        assertEquals(1, record.getFindsLost());
    }

    private static void assertAction(Consumer<WorkerRecord> action) {
        WorkerRecord record = new WorkerRecord();
        Instant before = Instant.now();
        action.accept(record);
        assertTrue(record.hasWorked());
        assertNotNull(record.getLastActiveAt());
        assertFalse(record.getLastActiveAt().isBefore(before));
        assertFalse(record.getLastActiveAt().isAfter(Instant.now()));
        assertNull(record.getJoinedAt());
    }
}
