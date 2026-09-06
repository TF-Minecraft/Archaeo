package com.nowko.archeology.config;

import org.bukkit.Particle;

import java.util.List;

/**
 * Tracker scan radii and pip timing from {@code config.yml}. Item id is {@code tracker.item}.
 *
 * @param enabled whether holding the item scans for hidden sites
 * @param defaultMaxRange YAML {@code max-range}: global cap; the tighter of this and the site radius wins
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
    /**
     * @return packaged defaults matching {@code config.yml}
     */
    public static TrackerSettings defaults() {
        return new TrackerSettings(
                true,
                256,
                64,
                32,
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
}
