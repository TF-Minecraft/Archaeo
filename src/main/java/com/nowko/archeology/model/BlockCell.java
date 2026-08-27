package com.nowko.archeology.model;

/**
 * One world-block coordinate belonging to a buried find shape.
 *
 * @param x block X
 * @param y block Y
 * @param z block Z
 */
public record BlockCell(int x, int y, int z) {
}
