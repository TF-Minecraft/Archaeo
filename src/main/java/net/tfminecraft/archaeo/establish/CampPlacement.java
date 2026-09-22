package net.tfminecraft.archaeo.establish;

import org.bukkit.block.data.BlockData;

import java.util.List;

/**
 * Client-only camp ghost plus a short reason the player can act on.
 *
 * @param allowed whether confirming would succeed
 * @param reason action-bar / chat line
 * @param ghosts blocks to send to this player only
 * @param originX template origin X (look-at, after clamp)
 * @param originY template origin Y
 * @param originZ template origin Z
 */
public record CampPlacement(
        boolean allowed,
        String reason,
        List<Ghost> ghosts,
        int originX,
        int originY,
        int originZ
) {
    /**
     * One preview cell. {@code fitting} false means tint with the invalid material.
     *
     * @param x block X
     * @param y block Y
     * @param z block Z
     * @param data client-only block
     * @param fitting whether this cell is clear and supported
     */
    public record Ghost(int x, int y, int z, BlockData data, boolean fitting) {
    }

    /**
     * Chunk or terrain rule that failed, in priority order for the action bar.
     */
    public enum Issue {
        /** Crosshair did not hit a block. */
        LOOK_MISS("Aim so the ghost sits where you want it."),
        /** Aimed chunk is the archaeological dig. */
        ON_DIG("The camp cannot sit on the dig."),
        /** Ruin or camp already registered here. */
        OCCUPIED("That chunk is already used."),
        /** Not a side-adjacent neighbor of the confirmed ruin. */
        NOT_NEIGHBOR("Look at a chunk that shares a side with the dig."),
        /** A template cell is not free. */
        BLOCKED("Something already occupies that space."),
        /** Ground under a floor piece is water or lava. */
        FLUID("Need solid ground, not water or lava."),
        /** Floor piece would float. */
        UNSUPPORTED("Uneven ground; the camp would float.");

        private final String message;

        /**
         * @param message English line for HUD and chat
         */
        Issue(String message) {
            this.message = message;
        }

        /**
         * @return English line for HUD and chat
         */
        public String message() {
            return message;
        }
    }
}
