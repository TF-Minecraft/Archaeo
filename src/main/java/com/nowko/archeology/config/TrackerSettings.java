package com.nowko.archeology.config;

import org.bukkit.Particle;

import java.util.List;

/**
 * Tracker item and scan radii from {@code config.yml}.
 *
 * @param enabled whether holding the item scans for hidden sites
 * @param defaultMaxRange global cap in blocks; the tighter of this and the site radius wins
 * @param nearRange medium proximity band (two pips / two arcs)
 * @param detectMessageRange close cone (three pips / three arcs) and prospecting chat; on-chunk is a fourth band
 * @param detectMessageShareRange extra blocks around the scanner who also see that chat; {@code 0} = holder only
 * @param pulseParticles whether each pip spawns expanding rings
 * @param beepMaxTicks ticks between bursts at the edge of range
 * @param beepMinTicks ticks between bursts in the close cone (on-chunk is slightly faster)
 * @param detectMessageCooldownTicks minimum ticks between detect chat lines, per viewer
 * @param waveRadii ring sizes in blocks; at most the first three are drawn
 * @param waveStepTicks delay between consecutive rings (first ring is with the pip)
 * @param particleFadeTicks extra ticks after the last ring so client sprites can vanish
 * @param waveParticle particle used on the rings
 * @param waveBiasBlocks how far the arc centre is pushed along the look vector
 * @param targetSwitchMargin extra blocks another site must beat, when aim is similar, before the lock switches
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
        int particleFadeTicks,
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
                64,
                32,
                0,
                true,
                70,
                5,
                200,
                List.of(1.2, 2.6, 4.2),
                3,
                10,
                Particle.ENCHANTED_HIT,
                1.2,
                16,
                "Archaeological tracker",
                List.of(
                        "Turn. Stronger pulses mean you are facing a ruin.",
                        "Arcs fire the way you look. A full ring means you are on it.",
                        "No coordinates."
                )
        );
    }
}
