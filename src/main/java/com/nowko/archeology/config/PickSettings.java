package com.nowko.archeology.config;

import java.util.List;

/**
 * Shared excavation rules from {@code excavation:}.
 *
 * @param enabled whether the cut clock runs
 * @param jornadaActions pick cycles restored each Minecraft day
 * @param visualCues particles and subtitles that mirror clings for players without sound
 * @param findDust whether exposed find cells shed motes
 * @param findDustIntervalTicks ticks between leak bursts on an open find cell
 * @param findDustCount motes per burst
 * @param conservation buried-condition roll and the bands used to describe a piece
 * @param neighborTraces whether lifting fill reports adjacent find cubes by material
 * @param profiles named tools from {@code excavation.tools}
 */
public record PickSettings(
        boolean enabled,
        int jornadaActions,
        boolean visualCues,
        boolean findDust,
        int findDustIntervalTicks,
        int findDustCount,
        ConservationSettings conservation,
        boolean neighborTraces,
        List<ExcavationTool> profiles
) {
    /**
     * @return packaged defaults matching {@code config.yml}
     */
    public static PickSettings defaults() {
        return new PickSettings(
                true,
                8,
                true,
                true,
                6,
                2,
                ConservationSettings.defaults(),
                true,
                List.of(ExcavationTool.hand(), ExcavationTool.light(), ExcavationTool.heavy())
        );
    }
}
