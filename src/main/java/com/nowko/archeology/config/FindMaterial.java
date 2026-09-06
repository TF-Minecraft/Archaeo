package com.nowko.archeology.config;

/**
 * One entry from {@code materials.yml}: the label players see on field traces.
 *
 * @param id catalog key such as {@code ceramic}
 * @param displayName English name shown in chat and HUD
 */
public record FindMaterial(String id, String displayName) {
}
