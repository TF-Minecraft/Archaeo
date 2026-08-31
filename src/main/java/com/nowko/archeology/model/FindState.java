package com.nowko.archeology.model;

/**
 * How far a buried find has been revealed in the excavation minigame.
 */
public enum FindState {
    /** Shape is fully unknown. */
    HIDDEN,
    /** Some cells have been uncovered. */
    PARTIAL,
    /** Shape is fully mapped; recovery of one item is allowed. */
    DISCOVERED,
    /** Every fill cell of the shape was smashed; no item will be recovered. */
    LOST
}
