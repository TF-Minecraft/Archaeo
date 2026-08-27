package com.nowko.archeology.config;

import org.bukkit.Particle;

import java.util.List;

/**
 * Tracker item and scan radii from {@code config.yml}.
 *
 * @param enabled whether holding the item scans for hidden sites
 * @param defaultMaxRange global cap in blocks; the tighter of this and the site radius wins
 * @param nearRange medium proximity band (two pips / two rings)
 * @param detectMessageRange close band (three pips / three rings) and prospecting chat
 * @param detectMessageShareRange extra blocks around the scanner who also see that chat; {@code 0} = holder only
 * @param pulseParticles whether each pip spawns expanding rings
 * @param beepMaxTicks ticks between bursts at the edge of range
 * @param beepMinTicks ticks between bursts next to the site
 * @param detectMessageCooldownTicks minimum ticks between detect chat lines, per viewer
 * @param waveRadii three ring sizes in blocks, drawn in order
 * @param waveStepTicks delay between consecutive rings (first ring is with the pip)
 * @param waveParticle particle used on the rings
 * @param waveBiasBlocks how far the ring centre leans toward an 8-way heading
 * @param targetSwitchMargin extra blocks another site must beat before the lock switches
 * @param itemName English display name of the item
 * @param itemLore English lore lines
 */
public record TrackerSettings(
        boolean enabled,
        int defaultMaxRange,
        int nearRange,
        int detectMessageRange,
        int detectMessageShareRange,
        boolean pulseParticles,
        int beepMaxTicks,
        int beepMinTicks,
        int detectMessageCooldownTicks,
        List<Double> waveRadii,
        int waveStepTicks,
        Particle waveParticle,
        double waveBiasBlocks,
        double targetSwitchMargin,
        String itemName,
        List<String> itemLore
) {
    /**
     * @return packaged defaults matching {@code config.yml}
     */
    public static TrackerSettings defaults() {
        return new TrackerSettings(
                true,
                256,
                48,
                16,
                0,
                true,
                70,
                5,
                200,
                List.of(1.2, 2.6, 4.2),
                3,
                Particle.END_ROD,
                1.2,
                16,
                "Archaeological tracker",
                List.of(
                        "Walk. Faster pulses mean closer.",
                        "Rings lean toward a heading; they are not a compass.",
                        "More pips when you are near. No coordinates."
                )
        );
    }
}
