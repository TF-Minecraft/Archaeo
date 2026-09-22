package net.tfminecraft.archaeo.establish;

/**
 * Shared 27-slot chest layout for nested excavation-board windows.
 */
final class CampGui {
    /**
     * Bottom-right of a 27-slot chest. Every nested board that has Back puts it here so the hand
     * always finds the same corner.
     */
    static final int SLOT_BACK = 26;

    /**
     * First two chest rows. Staff heads and filed finds occupy this band; the bottom row is actions.
     */
    static final int LIST_SLOTS = 18;

    /**
     * Not constructed.
     */
    private CampGui() {
    }
}
