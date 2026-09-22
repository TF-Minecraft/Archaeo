package net.tfminecraft.archaeo.excavation;

import net.tfminecraft.archaeo.model.BuriedFind;
import net.tfminecraft.archaeo.model.FindState;
import net.tfminecraft.archaeo.model.WorkerRecord;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Charges find damage to the person who caused it, once per piece and not once per cube.
 *
 * <p>One blow can take several cells of the same pot, and an explosion can take a whole shape at
 * once. The staff page is read as "how many pieces has this person hurt", so the tally has to count
 * pieces; without this ledger a single unlucky swing would read as three ruined finds.
 *
 * <p>One instance covers one event — a pick release, an explosion, a piston push — and is thrown
 * away after it.
 */
final class FindWoundLedger {
    private final Set<UUID> damaged = new HashSet<>();
    private final Set<UUID> lost = new HashSet<>();

    /**
     * Records a fresh wound against a worker's page.
     *
     * @param log staff page to charge, or {@code null} when nobody on the roster is answerable
     * @param find piece that was struck, or {@code null} when the cell held no find
     */
    void charge(WorkerRecord log, BuriedFind find) {
        if (log == null || find == null) {
            return;
        }
        if (damaged.add(find.getId())) {
            log.noteFindDamaged();
        }
        if (find.getState() == FindState.LOST && lost.add(find.getId())) {
            log.noteFindLost();
        }
    }
}
