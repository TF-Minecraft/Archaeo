package net.tfminecraft.archaeo.excavation;

import net.tfminecraft.archaeo.model.BuriedFind;
import net.tfminecraft.archaeo.model.FindState;
import net.tfminecraft.archaeo.model.WorkerRecord;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

public class FindWoundLedgerTest {
    @Test
    public void missingWorkerOrFindDoesNotConsumeTheCharge() {
        FindWoundLedger ledger = new FindWoundLedger();
        WorkerRecord worker = new WorkerRecord();
        BuriedFind find = find();
        ledger.charge(null, find);
        ledger.charge(worker, null);
        ledger.charge(null, null);
        assertFalse(worker.hasWorked());
        assertNull(worker.getLastActiveAt());
        ledger.charge(worker, find);
        assertEquals(1, worker.getFindsDamaged());
        assertEquals(0, worker.getFindsLost());
        assertNotNull(worker.getLastActiveAt());
    }

    @Test
    public void repeatedCellsAndReloadedInstancesCountOncePerFindId() {
        FindWoundLedger ledger = new FindWoundLedger();
        WorkerRecord worker = new WorkerRecord();
        worker.setFindsDamaged(3);
        BuriedFind find = find();
        ledger.charge(worker, find);
        ledger.charge(worker, find);
        BuriedFind reloaded = find();
        reloaded.setId(find.getId());
        ledger.charge(worker, reloaded);
        assertEquals(4, worker.getFindsDamaged());
        assertEquals(0, worker.getFindsLost());
        ledger.charge(worker, find());
        assertEquals(5, worker.getFindsDamaged());
    }

    @Test
    public void laterFatalCellAddsLossWithoutChargingDamageAgain() {
        FindWoundLedger ledger = new FindWoundLedger();
        WorkerRecord worker = new WorkerRecord();
        worker.setFindsLost(2);
        BuriedFind find = find();
        ledger.charge(worker, find);
        find.setState(FindState.LOST);
        ledger.charge(worker, find);
        ledger.charge(worker, find);
        assertEquals(1, worker.getFindsDamaged());
        assertEquals(3, worker.getFindsLost());
    }

    @Test
    public void fatalFirstHitCountsBothDamageAndLossAndDistinctPiecesAccumulate() {
        FindWoundLedger ledger = new FindWoundLedger();
        WorkerRecord worker = new WorkerRecord();
        for (int i = 0; i < 2; i++) {
            BuriedFind find = find();
            find.setState(FindState.LOST);
            ledger.charge(worker, find);
            ledger.charge(worker, find);
        }
        assertEquals(2, worker.getFindsDamaged());
        assertEquals(2, worker.getFindsLost());
        assertEquals(0, worker.getFindsRecovered());
    }

    @Test
    public void separateEventsEachChargeTheirOwnDamage() {
        WorkerRecord worker = new WorkerRecord();
        BuriedFind find = find();
        new FindWoundLedger().charge(worker, find);
        new FindWoundLedger().charge(worker, find);
        assertEquals(2, worker.getFindsDamaged());
        assertEquals(0, worker.getFindsLost());
    }

    private static BuriedFind find() {
        BuriedFind find = new BuriedFind();
        find.setId(UUID.randomUUID());
        return find;
    }
}
