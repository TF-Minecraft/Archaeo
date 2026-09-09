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
 * @param signHintRadius blocks from the camp sign at which roster motes appear; {@code 0} disables them
 * @param signHintIntervalTicks ticks between motes
 */
public record EstablishSettings(
        boolean enabled,
        boolean protectDigSite,
        Material invalidBlock,
        Material ruinOutlineBlock,
        int maxStaff,
        int signHintRadius,
        int signHintIntervalTicks
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
                40,
                15
        );
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
     * @param requested value from {@code establish.sign-hint-radius}
     * @return usable radius in blocks, or {@code 0} to disable
     */
    public static int clampSignHintRadius(int requested) {
        if (requested <= 0) {
            return 0;
        }
        return Math.min(requested, 128);
    }

    /**
     * @param requested value from {@code establish.sign-hint-interval-ticks}
     * @return ticks between motes
     */
    public static int clampSignHintInterval(int requested) {
        return Math.max(1, requested);
    }
}
