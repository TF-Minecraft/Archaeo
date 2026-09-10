package com.nowko.archeology.config;

import org.bukkit.Material;

/**
 * Establishment kit (camp) rules from {@code config.yml}. Item id is {@code establish.item}.
 *
 * @param enabled whether the kit can claim a confirmed ruin
 * @param protectDigSite whether terrain fill in every present stratum band is locked against vanilla damage
 * @param invalidBlock client-only block for template cells that cannot be planted
 * @param ruinOutlineBlock client-only glass for the dig chunk perimeter
 * @param maxStaff people on one excavation roster, including the director; never above {@link #STAFF_BOARD_SLOTS}
 * @param maxExcavations open camps one player may direct at once; {@code 0} means no cap
 */
public record EstablishSettings(
        boolean enabled,
        boolean protectDigSite,
        Material invalidBlock,
        Material ruinOutlineBlock,
        int maxStaff,
        int maxExcavations
) {
    /**
     * First two rows of the staff chest. {@code establish.max-staff} cannot exceed this: there is no
     * second page, so a larger YAML value is ignored.
     */
    public static final int STAFF_BOARD_SLOTS = 18;

    /**
     * @return packaged defaults matching {@code config.yml}
     */
    public static EstablishSettings defaults() {
        return new EstablishSettings(
                true,
                true,
                Material.RED_STAINED_GLASS,
                Material.LIGHT_BLUE_STAINED_GLASS,
                STAFF_BOARD_SLOTS,
                1
        );
    }

    /**
     * @return whether a player may direct as many camps as they can plant
     */
    public boolean unlimitedExcavations() {
        return maxExcavations <= 0;
    }

    /**
     * Clamps a YAML staff cap: below 1 uses the default (the board's two rows); above the board
     * size is ignored because those heads would have nowhere to sit.
     *
     * @param requested value from {@code establish.max-staff}
     * @return usable cap
     */
    public static int clampMaxStaff(int requested) {
        if (requested < 1) {
            return STAFF_BOARD_SLOTS;
        }
        return Math.min(requested, STAFF_BOARD_SLOTS);
    }

    /**
     * Normalises {@code establish.max-excavations}. Negative YAML is treated as unlimited ({@code 0}).
     *
     * @param requested value from config
     * @return {@code 0} for no cap, otherwise a positive count
     */
    public static int clampMaxExcavations(int requested) {
        return Math.max(0, requested);
    }
}
