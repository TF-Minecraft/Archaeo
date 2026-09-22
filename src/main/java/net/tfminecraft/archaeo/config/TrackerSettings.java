package net.tfminecraft.archaeo.config;

import org.bukkit.Particle;

import java.util.List;

/**
 * Tracker scan radii and pip timing from {@code config.yml}. Item id is {@code tracker.item}.
 *
 * @param enabled whether holding the item scans for hidden sites
 * @param defaultMaxRange YAML {@code max-range}: hard cap in blocks for every hidden ruin
 * @param nearRange YAML {@code medium-range}: medium pulse band
 * @param detectMessageRange YAML {@code close-range}: close pulse band and prospecting chat to the holder
 * @param pulseParticles whether each pip spawns expanding rings
 * @param beepMaxTicks ticks between bursts at the edge of range
 * @param beepMinTicks ticks between bursts in the close cone
 * @param detectMessageCooldownTicks minimum ticks between detect chat lines, per viewer
 * @param waveRadii ring sizes in blocks; at most the first three are drawn
 * @param waveStepTicks delay between consecutive rings
 * @param particleFadeTicks extra ticks after the last ring
 * @param waveParticle particle used on the rings
 * @param waveBiasBlocks how far the arc centre is pushed along the look vector
 * @param targetSwitchMargin extra blocks another site must beat before the lock switches
 */
public record TrackerSettings(
        boolean enabled,
        int defaultMaxRange,
        int nearRange,
        int detectMessageRange,
        boolean pulseParticles,
        int beepMaxTicks,
        int beepMinTicks,
        int detectMessageCooldownTicks,
        List<Double> waveRadii,
        int waveStepTicks,
        int particleFadeTicks,
        Particle waveParticle,
        double waveBiasBlocks,
        double targetSwitchMargin
) {
    /** Minecraft chunk edge in blocks; packaged {@code max-range} is {@value #DEFAULT_MAX_RANGE_CHUNKS} chunks. */
    public static final int BLOCKS_PER_CHUNK = 16;

    /** Packaged {@code tracker.max-range} in chunks. */
    public static final int DEFAULT_MAX_RANGE_CHUNKS = 32;

    /**
     * @return packaged defaults matching {@code config.yml}
     */
    public static TrackerSettings defaults() {
        int maxRange = defaultMaxRangeBlocks();
        return new TrackerSettings(
                true,
                maxRange,
                scaledNearRange(maxRange),
                scaledCloseRange(maxRange),
                true,
                70,
                5,
                200,
                List.of(1.2, 2.4, 3.6),
                3,
                10,
                Particle.ENCHANTED_HIT,
                1.2,
                16
        );
    }

    /**
     * @return packaged {@code max-range} in blocks ({@value #DEFAULT_MAX_RANGE_CHUNKS} chunks)
     */
    public static int defaultMaxRangeBlocks() {
        return DEFAULT_MAX_RANGE_CHUNKS * BLOCKS_PER_CHUNK;
    }

    /**
     * Medium pulse band when YAML omits {@code medium-range}: one quarter of {@code max-range}.
     *
     * @param maxRange tracker hard cap in blocks
     * @return medium-range in blocks, at least 1
     */
    public static int scaledNearRange(int maxRange) {
        return Math.max(1, maxRange / 4);
    }

    /**
     * Close pulse band when YAML omits {@code close-range}: one eighth of {@code max-range}.
     *
     * @param maxRange tracker hard cap in blocks
     * @return close-range in blocks, at least 1
     */
    public static int scaledCloseRange(int maxRange) {
        return Math.max(1, maxRange / 8);
    }
}
