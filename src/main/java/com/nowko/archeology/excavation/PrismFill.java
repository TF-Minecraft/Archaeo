package com.nowko.archeology.excavation;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.List;

/**
 * Distinguishes archaeological fill (must not break vanilla) from lights, scaffolding, and plants.
 */
public final class PrismFill {
    /**
     * Six block faces used for open-cut leaks and neighbour-trace counts.
     */
    public static final BlockFace[] FACES = {
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    private PrismFill() {
    }

    /**
     * Natural ground the Hand Pick should work. Planks, cobble walls, and machines are not fill.
     *
     * @param material block type
     * @return whether this is excavation substrate
     */
    public static boolean isTerrainFill(Material material) {
        if (exempt(material) || !material.isSolid()) {
            return false;
        }
        if (Tag.DIRT.isTagged(material)
                || Tag.BASE_STONE_OVERWORLD.isTagged(material)
                || Tag.BASE_STONE_NETHER.isTagged(material)
                || Tag.SAND.isTagged(material)
                || Tag.TERRACOTTA.isTagged(material)) {
            return true;
        }
        String name = material.name();
        if (name.contains("ORE") || name.contains("SANDSTONE") || name.contains("SCULK")) {
            return true;
        }
        return switch (material) {
            case GRAVEL, CLAY, MOSS_BLOCK, MUD, PACKED_MUD, SNOW_BLOCK,
                 ICE, PACKED_ICE, BLUE_ICE, CALCITE, TUFF, DRIPSTONE_BLOCK,
                 SMOOTH_BASALT, SOUL_SAND, SOUL_SOIL, MAGMA_BLOCK,
                 OBSIDIAN, CRYING_OBSIDIAN, AMETHYST_BLOCK -> true;
            default -> false;
        };
    }

    /**
     * Open cut: at least one face meets air, fluid, or plants, so the block is part of the working face.
     *
     * @param block cell in the world
     * @return whether a player could reach this face from an opening
     */
    public static boolean hasOpenFace(Block block) {
        for (BlockFace face : FACES) {
            if (isOpening(block.getRelative(face))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Faces of this cell that currently open onto air, fluid, or plants.
     *
     * @param block cell in the world
     * @return open directions; empty if the cell is sealed
     */
    public static List<BlockFace> openingFaces(Block block) {
        List<BlockFace> faces = new ArrayList<>(6);
        for (BlockFace face : FACES) {
            if (isOpening(block.getRelative(face))) {
                faces.add(face);
            }
        }
        return faces;
    }

    /**
     * @param neighbor adjacent cell
     * @return whether that cell does not seal the face
     */
    static boolean isOpening(Block neighbor) {
        Material type = neighbor.getType();
        if (type.isAir() || neighbor.isLiquid()) {
            return true;
        }
        if (Tag.REPLACEABLE.isTagged(type) || type == Material.SNOW || type == Material.POWDER_SNOW) {
            return true;
        }
        return false;
    }

    /**
     * Torches, signs, scaffolding, and other non-terrain the player may still place and remove.
     *
     * @param material block type
     * @return whether this is not excavation fill
     */
    static boolean exempt(Material material) {
        if (material.isAir()) {
            return true;
        }
        if (Tag.REPLACEABLE.isTagged(material)
                || Tag.ALL_SIGNS.isTagged(material)
                || Tag.ALL_HANGING_SIGNS.isTagged(material)
                || Tag.CANDLES.isTagged(material)
                || Tag.CANDLE_CAKES.isTagged(material)
                || Tag.BANNERS.isTagged(material)
                || Tag.BUTTONS.isTagged(material)
                || Tag.PRESSURE_PLATES.isTagged(material)
                || Tag.CLIMBABLE.isTagged(material)
                || Tag.WOOL_CARPETS.isTagged(material)
                || Tag.FLOWER_POTS.isTagged(material)
                || Tag.RAILS.isTagged(material)) {
            return true;
        }
        return switch (material) {
            case TORCH, WALL_TORCH, SOUL_TORCH, SOUL_WALL_TORCH,
                 REDSTONE_TORCH, REDSTONE_WALL_TORCH,
                 LANTERN, SOUL_LANTERN, CAMPFIRE, SOUL_CAMPFIRE,
                 SCAFFOLDING, REDSTONE_WIRE, REPEATER, COMPARATOR,
                 LEVER, TRIPWIRE, TRIPWIRE_HOOK, LIGHTNING_ROD, END_ROD,
                 GLOW_LICHEN -> true;
            default -> false;
        };
    }
}
