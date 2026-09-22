package net.tfminecraft.archaeo.model;

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
    /** The piece was lifted; the cut no longer holds it. */
    RECOVERED,
    /** Every fill cell of the shape was smashed; no item will be recovered. */
    LOST
}
