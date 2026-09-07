package com.nowko.archeology.excavation;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.List;

/**
 * Distinguishes archaeological fill from air, fluids, and placeable decorations.
 * Any other world block in the prism is fill: cobble, bricks, planks, glass, machines, and so on.
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
     * World block the Hand Pick should work. Air, fluids, and placeable decorations are not fill.
     *
     * @param material block type
     * @return whether this is excavation substrate
     */
    public static boolean isTerrainFill(Material material) {
        return material != null
                && material.isBlock()
                && !material.isAir()
                && !isFluid(material)
                && !exempt(material);
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
     * Water, lava, and bubble columns stay vanilla; they are not excavation substrate.
     *
     * @param material block type
     * @return whether this is a fluid
     */
    static boolean isFluid(Material material) {
        return material == Material.WATER
                || material == Material.LAVA
                || material == Material.BUBBLE_COLUMN;
    }

    /**
     * Torches, signs, scaffolding, plants, and similar the player may still place and remove.
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
                 GLOW_LICHEN, FIRE, SOUL_FIRE, NETHER_PORTAL, END_PORTAL,
                 END_GATEWAY, LIGHT -> true;
            default -> false;
        };
    }
}
