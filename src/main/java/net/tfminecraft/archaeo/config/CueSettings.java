package net.tfminecraft.archaeo.config;

import org.bukkit.Material;
import org.bukkit.Tag;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Archaeo-owned cue clock: ticks between cling / Release, plus hard/soft fill affinity.
 * When a tool profile sets a numeric {@code tempo}, vanilla {@code getBreakSpeed} is not used.
 * Each cling / Release always costs {@link #STAGE_COST} break-units (two vanilla breaks, or
 * twice the affinity-adjusted tempo interval).
 *
 * @param pickFaster hard / stone-like fill; empty means {@link Tag#MINEABLE_PICKAXE}
 * @param shovelFaster soft / soil-like fill; empty means {@link Tag#MINEABLE_SHOVEL}
 * @param matchedFactor scale applied to numeric tempo when dig-class matches the block list ({@code < 1} = faster)
 * @param mismatchedFactor scale when dig-class is the other list ({@code > 1} = slower)
 */
public record CueSettings(
        Set<Material> pickFaster,
        Set<Material> shovelFaster,
        double matchedFactor,
        double mismatchedFactor
) {
    /**
     * Break-units each cling / Release needs. Not a config dial: the cue clock is meant to feel
     * slower than a single vanilla destroy cycle.
     */
    public static final int STAGE_COST = 2;

    /**
     * @return packaged affinity using vanilla mineable tags and mild match / mismatch scales
     */
    public static CueSettings defaults() {
        return new CueSettings(Set.of(), Set.of(), 0.7, 1.35);
    }

    /**
     * Ticks to wait between cue beats for this tool and aimed fill, including {@link #STAGE_COST}.
     *
     * @param cueTicks profile base interval ({@code excavation.tools.*.cue-ticks})
     * @param digClass pick / shovel / none from the held stack
     * @param block aimed fill material
     * @return at least {@code 1}
     */
    public int intervalTicks(int cueTicks, DigClass digClass, Material block) {
        int base = Math.max(1, cueTicks);
        int affinity = base;
        if (digClass != null && digClass != DigClass.NONE && block != null) {
            boolean pickBlock = isPickFaster(block);
            boolean shovelBlock = isShovelFaster(block);
            if (digClass == DigClass.PICK && pickBlock) {
                affinity = scale(base, matchedFactor);
            } else if (digClass == DigClass.SHOVEL && shovelBlock) {
                affinity = scale(base, matchedFactor);
            } else if (digClass == DigClass.PICK && shovelBlock) {
                affinity = scale(base, mismatchedFactor);
            } else if (digClass == DigClass.SHOVEL && pickBlock) {
                affinity = scale(base, mismatchedFactor);
            }
        }
        return Math.max(1, affinity * STAGE_COST);
    }

    /**
     * Progress threshold for one cue when using the legacy vanilla break sample.
     *
     * @return {@link #STAGE_COST}
     */
    public float progressPerCue() {
        return STAGE_COST;
    }

    /**
     * @param block fill cell
     * @return whether picks clear this faster
     */
    public boolean isPickFaster(Material block) {
        if (block == null) {
            return false;
        }
        if (!pickFaster.isEmpty()) {
            return pickFaster.contains(block);
        }
        return Tag.MINEABLE_PICKAXE.isTagged(block);
    }

    /**
     * @param block fill cell
     * @return whether shovels clear this faster
     */
    public boolean isShovelFaster(Material block) {
        if (block == null) {
            return false;
        }
        if (!shovelFaster.isEmpty()) {
            return shovelFaster.contains(block);
        }
        return Tag.MINEABLE_SHOVEL.isTagged(block);
    }

    /**
     * @param base cue-ticks
     * @param factor scale
     * @return at least {@code 1}
     */
    private static int scale(int base, double factor) {
        if (factor <= 0) {
            return 1;
        }
        return Math.max(1, (int) Math.round(base * factor));
    }

    /**
     * @param materials configured set
     * @return unmodifiable copy
     */
    public static Set<Material> copyOf(Set<Material> materials) {
        if (materials == null || materials.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(materials));
    }
}
