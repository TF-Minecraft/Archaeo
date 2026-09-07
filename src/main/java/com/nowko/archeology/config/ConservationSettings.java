package com.nowko.archeology.config;

import java.util.List;

/**
 * How well a find survived before anyone dug it, and how the resulting number is described.
 *
 * <p>Conservation has two sources. The ground decides most of it: a piece rolls a buried
 * condition when the site is generated, biased low so an untouched find is rare. Field work
 * can only subtract from that roll. A careful dig therefore preserves what survived; it does
 * not create a perfect piece.
 *
 * @param buriedMin lowest buried condition a find may roll
 * @param buriedMax highest buried condition a find may roll
 * @param bias exponent on the centred roll; {@code 1} keeps the middle common and both ends rare,
 *             higher values push results toward {@code buriedMin}
 * @param depthPenalty subtracted per stratum step below the top layer
 * @param disturbedPenalty subtracted when the find sits in a disturbed band
 * @param grades condition bands, highest {@code minPercent} first
 */
public record ConservationSettings(
        int buriedMin,
        int buriedMax,
        double bias,
        int depthPenalty,
        int disturbedPenalty,
        List<ConservationGrade> grades
) {
    /**
     * @return packaged defaults matching {@code config.yml}
     */
    public static ConservationSettings defaults() {
        return new ConservationSettings(
                40,
                100,
                1.0,
                4,
                10,
                List.of(
                        new ConservationGrade("intact", 92, "Intact"),
                        new ConservationGrade("sound", 72, "Sound"),
                        new ConservationGrade("worn", 48, "Worn"),
                        new ConservationGrade("fragmentary", 24, "Fragmentary"),
                        new ConservationGrade("crumbling", 1, "Crumbling")
                )
        );
    }

    /**
     * @param percent conservation 0–100
     * @return band that describes this value, or {@code null} when nothing matches
     */
    public ConservationGrade grade(int percent) {
        for (ConservationGrade grade : grades) {
            if (percent >= grade.minPercent()) {
                return grade;
            }
        }
        return null;
    }

    /**
     * @param percent conservation 0–100
     * @return band label, or an empty string when no band matches
     */
    public String gradeLabel(int percent) {
        ConservationGrade grade = grade(percent);
        return grade == null ? "" : grade.label();
    }
}
