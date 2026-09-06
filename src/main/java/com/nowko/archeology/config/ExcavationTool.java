package com.nowko.archeology.config;

import com.nowko.archeology.item.ItemRef;
import org.bukkit.Material;

import java.util.List;

/**
 * One named excavation profile from {@code excavation.tools}.
 * Cue tempo is {@code getBreakSpeed} of the live stack (vanilla tool stats and any
 * MMOItems / ItemsAdder values already on that item or player). {@code mining-speed} /
 * {@code mining-speed-multiplier} replace that sample only. {@code chime-ticks} is a
 * last-resort beat if that speed is still {@code 0}.
 *
 * @param id YAML key such as {@code hand}, {@code light}, or {@code heavy}
 * @param materials stacks that use this profile (vanilla, ItemsAdder, or MMOItems)
 * @param chimeTicks leftover YAML {@code chime-ticks}; {@code 0} never uses the metronome
 * @param miningSpeed YAML {@code mining-speed}: tool default mining speed for the clock sample; {@code null} keeps the stack
 * @param miningSpeedMultiplier YAML {@code mining-speed-multiplier}: extra scale on {@code getBreakSpeed}; {@code null} means {@code 1}
 * @param cellsOnTime YAML {@code lift-on-ready}: cubes lifted on the ready chime
 * @param cellsOnLate YAML {@code lift-if-late}: cubes lifted if the player holds past ready
 * @param breakShape YAML {@code break-shape}: same pattern for on-time and late lifts
 * @param jornadaCost work-day actions spent when the cut resolves
 * @param readyWindowTicks YAML {@code release-window-ticks}: ticks after Release in which letting go is still on time
 */
public record ExcavationTool(
        String id,
        List<ItemRef> materials,
        int chimeTicks,
        Float miningSpeed,
        Float miningSpeedMultiplier,
        int cellsOnTime,
        int cellsOnLate,
        BreakShape breakShape,
        int jornadaCost,
        int readyWindowTicks
) {
    /**
     * Packaged lifts and whitelist for a YAML profile id.
     *
     * @param id {@code hand}, {@code light}, {@code heavy}, or unknown
     * @return {@link #hand()} when {@code id} is not a packaged name
     */
    public static ExcavationTool packaged(String id) {
        if (id == null) {
            return hand();
        }
        return switch (id) {
            case "light" -> light();
            case "heavy" -> heavy();
            default -> hand();
        };
    }

    /**
     * Empty hand: one cube on time or late.
     *
     * @return packaged {@code hand} profile
     */
    public static ExcavationTool hand() {
        return new ExcavationTool(
                "hand",
                List.of(ItemRef.air()),
                0,
                null,
                null,
                1,
                1,
                BreakShape.DOWN,
                1,
                20
        );
    }

    /**
     * Wooden pick and shovel: one cube on time, one extra below when late.
     *
     * @return packaged {@code light} profile
     */
    public static ExcavationTool light() {
        return new ExcavationTool(
                "light",
                List.of(
                        ItemRef.vanilla(Material.WOODEN_PICKAXE),
                        ItemRef.vanilla(Material.WOODEN_SHOVEL)
                ),
                0,
                null,
                null,
                1,
                2,
                BreakShape.DOWN,
                1,
                20
        );
    }

    /**
     * Stone-and-up picks and shovels: 3×3×2 around the aim; more cubes if late.
     *
     * @return packaged {@code heavy} profile
     */
    public static ExcavationTool heavy() {
        return new ExcavationTool(
                "heavy",
                List.of(
                        ItemRef.vanilla(Material.STONE_PICKAXE),
                        ItemRef.vanilla(Material.COPPER_PICKAXE),
                        ItemRef.vanilla(Material.IRON_PICKAXE),
                        ItemRef.vanilla(Material.GOLDEN_PICKAXE),
                        ItemRef.vanilla(Material.DIAMOND_PICKAXE),
                        ItemRef.vanilla(Material.NETHERITE_PICKAXE),
                        ItemRef.vanilla(Material.STONE_SHOVEL),
                        ItemRef.vanilla(Material.COPPER_SHOVEL),
                        ItemRef.vanilla(Material.IRON_SHOVEL),
                        ItemRef.vanilla(Material.GOLDEN_SHOVEL),
                        ItemRef.vanilla(Material.DIAMOND_SHOVEL),
                        ItemRef.vanilla(Material.NETHERITE_SHOVEL)
                ),
                0,
                null,
                null,
                2,
                4,
                BreakShape.AROUND,
                1,
                20
        );
    }

    /**
     * @return whether a YAML beat should run when vanilla mining speed is zero
     */
    public boolean hasChimeFallback() {
        return chimeTicks > 0;
    }
}
