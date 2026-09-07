package com.nowko.archeology.config;

/**
 * One entry from {@code materials.yml}: the label players see on field traces and how well
 * this material tends to survive burial.
 *
 * @param id catalog key such as {@code ceramic}
 * @param displayName English name shown in chat and HUD
 * @param survival multiplier on the buried-condition roll; {@code 1.0} keeps the roll, lower rots
 */
public record FindMaterial(String id, String displayName, double survival) {
}
