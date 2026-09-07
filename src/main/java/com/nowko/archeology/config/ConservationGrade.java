package com.nowko.archeology.config;

/**
 * One condition band from {@code excavation.conservation.grades}. Replaces a single
 * damaged / not damaged threshold: a piece is described, not stamped.
 *
 * @param id catalog key such as {@code worn}
 * @param minPercent lowest conservation that still reads as this grade
 * @param label English text shown on the piece and in recovery chat
 */
public record ConservationGrade(String id, int minPercent, String label) {
}
