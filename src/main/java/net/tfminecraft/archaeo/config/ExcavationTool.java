package net.tfminecraft.archaeo.config;

import net.tfminecraft.archaeo.item.ItemRef;
import org.bukkit.Material;

import java.util.List;

/**
 * One named excavation profile from {@code excavation.tools}.
 * Tempo is {@code tempo:} — {@code vanilla} (or omit) uses {@code getBreakSpeed};
 * a whole number is ticks between cling / Release before the fixed stage cost.
 * Hard/soft fill lists under {@code excavation.cues} only scale the numeric tempo.
 *
 * @param id YAML key such as {@code hand}, {@code light}, or {@code heavy}
 * @param materials stacks that use this profile (vanilla, ItemsAdder, or MMOItems)
 * @param cueTicks ticks between cling / Release; {@code 0} means vanilla mining tempo
 * @param digClass forced dig-class for every item in this profile, or {@code null} to infer from the held material
 * @param chimeTicks legacy fallback beat when tempo is vanilla and the sample is {@code 0}
 * @param miningSpeed legacy YAML {@code mining-speed} for the vanilla sample only
 * @param miningSpeedMultiplier legacy YAML {@code mining-speed-multiplier} on the vanilla sample
 * @param cellsOnTime YAML {@code lift-on-ready}: cubes lifted on the ready chime
 * @param cellsOnLate YAML {@code lift-if-late}: cubes lifted if the player holds past ready
 * @param breakShape YAML {@code break-shape}: same pattern for on-time and late lifts
 * @param jornadaCost work-day actions spent when the cut resolves
 * @param readyWindowTicks YAML {@code release-window-ticks}: ticks after Release in which letting go is still on time
 */
public record ExcavationTool(
        String id,
        List<ItemRef> materials,
        int cueTicks,
        DigClass digClass,
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
            case "super-heavy" -> superHeavy();
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
                DigClass.NONE,
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
     * Iron pick and shovel: wider late lifts.
     *
     * @return packaged {@code super-heavy} profile
     */
    public static ExcavationTool superHeavy() {
        return new ExcavationTool(
                "super-heavy",
                List.of(
                        ItemRef.vanilla(Material.IRON_PICKAXE),
                        ItemRef.vanilla(Material.IRON_SHOVEL)
                ),
                0,
                null,
                0,
                null,
                null,
                4,
                8,
                BreakShape.RANDOM,
                1,
                20
        );
    }

    /**
     * @return whether the Archaeo metronome owns the cue clock
     */
    public boolean usesCueTicks() {
        return cueTicks > 0;
    }

    /**
     * @return whether a YAML beat should run when vanilla mining speed is zero
     */
    public boolean hasChimeFallback() {
        return chimeTicks > 0;
    }

    /**
     * Dig-class for affinity: profile override, else inferred from the live stack.
     *
     * @param held main-hand stack
     * @return class used against {@link CueSettings} block lists
     */
    public DigClass resolveDigClass(org.bukkit.inventory.ItemStack held) {
        if (digClass != null) {
            return digClass;
        }
        return DigClass.of(held);
    }
}
