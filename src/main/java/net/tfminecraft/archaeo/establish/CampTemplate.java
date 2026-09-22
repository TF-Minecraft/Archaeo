package net.tfminecraft.archaeo.establish;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.data.type.Slab;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Relative camp pieces for establishment preview (5×5×3 tent, not a schematic file).
 */
public final class CampTemplate {
    /**
     * One block of the camp, in local space: +X is right, +Z is forward (south before rotation).
     *
     * @param dx local X
     * @param dy local Y (0 sits on the origin surface)
     * @param dz local Z
     * @param material client-only block
     * @param wool {@link CampWoolRole#PRIMARY} for {@code W}, {@link CampWoolRole#SECONDARY} for {@code R},
     *             or {@code null} for other pieces
     */
    public record Piece(int dx, int dy, int dz, Material material, CampWoolRole wool) {
    }

    private CampTemplate() {
    }

    /**
     * Open-front tent. Floors 0–2 follow the 5×5 grids (last row = front, toward the player).
     * Aim origin is the centre of the 5×5 (may be air).
     *
     * @return immutable piece list with default white / red wool
     */
    public static List<Piece> basic() {
        return basic(Material.WHITE_WOOL, Material.RED_WOOL);
    }

    /**
     * Open-front tent with chosen wool for {@code W} (primary) and {@code R} (secondary) cells.
     *
     * @param primaryWool replaces white wool in the grid
     * @param secondaryWool replaces red wool in the grid
     * @return immutable piece list
     */
    public static List<Piece> basic(Material primaryWool, Material secondaryWool) {
        Material primary = primaryWool == null ? Material.WHITE_WOOL : primaryWool;
        Material secondary = secondaryWool == null ? Material.RED_WOOL : secondaryWool;
        List<Piece> pieces = new ArrayList<>();
        addFloor(pieces, 0, primary, secondary, new String[] {
                "WXFXW",
                "WXXXW",
                "WXXXW",
                "XXXXS",
                "NXXCX"
        });
        addFloor(pieces, 1, primary, secondary, new String[] {
                "XRFRX",
                "XRXRX",
                "XRXRX",
                "XXXXX",
                "XXXXX"
        });
        addFloor(pieces, 2, primary, secondary, new String[] {
                "XXWXX",
                "XXWXX",
                "XXWXX",
                "XXXXX",
                "XXXXX"
        });
        return List.copyOf(pieces);
    }

    /**
     * One storey as a 5×5 grid. The last row is the camp front (toward the player, local −Z).
     * {@code N} is a standing oak sign.
     *
     * @param pieces list to fill
     * @param y local Y
     * @param primaryWool material for {@code W}
     * @param secondaryWool material for {@code R}
     * @param rows five strings of length 5
     */
    private static void addFloor(
            List<Piece> pieces,
            int y,
            Material primaryWool,
            Material secondaryWool,
            String[] rows
    ) {
        for (int row = 0; row < rows.length; row++) {
            String line = rows[row];
            int z = 2 - row;
            for (int col = 0; col < 5; col++) {
                GridCell cell = gridMaterial(line.charAt(col), primaryWool, secondaryWool);
                if (cell == null) {
                    continue;
                }
                pieces.add(new Piece(col - 2, y, z, cell.material(), cell.wool()));
            }
        }
    }

    /**
     * @param cell one character from a floor grid
     * @param primaryWool material for {@code W}
     * @param secondaryWool material for {@code R}
     * @return block and wool role, or {@code null} for empty
     */
    private static GridCell gridMaterial(char cell, Material primaryWool, Material secondaryWool) {
        return switch (cell) {
            case 'W' -> new GridCell(primaryWool, CampWoolRole.PRIMARY);
            case 'R' -> new GridCell(secondaryWool, CampWoolRole.SECONDARY);
            case 'F' -> new GridCell(Material.OAK_FENCE, null);
            case 'S' -> new GridCell(Material.OAK_SLAB, null);
            case 'C' -> new GridCell(Material.CAMPFIRE, null);
            case 'N' -> new GridCell(Material.OAK_SIGN, null);
            default -> null;
        };
    }

    /**
     * @param material block
     * @param wool wool role, or {@code null}
     */
    private record GridCell(Material material, CampWoolRole wool) {
    }

    /**
     * Snaps look yaw to a cardinal the template can face.
     *
     * @param yaw player yaw in degrees
     * @return south / west / north / east
     */
    public static BlockFace facingFromYaw(float yaw) {
        float wrapped = wrapYaw(yaw);
        if (wrapped >= -45 && wrapped < 45) {
            return BlockFace.SOUTH;
        }
        if (wrapped >= 45 && wrapped < 135) {
            return BlockFace.WEST;
        }
        if (wrapped >= -135 && wrapped < -45) {
            return BlockFace.EAST;
        }
        return BlockFace.NORTH;
    }

    /**
     * Rotates a local offset so +Z follows {@code facing}.
     *
     * @param dx local X
     * @param dz local Z
     * @param facing camp front
     * @return world XZ offset
     */
    public static Vector rotate(int dx, int dz, BlockFace facing) {
        return switch (facing) {
            case WEST -> new Vector(-dz, 0, dx);
            case NORTH -> new Vector(-dx, 0, -dz);
            case EAST -> new Vector(dz, 0, -dx);
            default -> new Vector(dx, 0, dz);
        };
    }

    /**
     * World XZ offset bounds of {@code pieces} after rotation (inclusive).
     *
     * @param pieces template
     * @param facing camp front
     * @return {@code {minX, maxX, minZ, maxZ}} offsets from origin
     */
    public static int[] offsetBounds(List<Piece> pieces, BlockFace facing) {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Piece piece : pieces) {
            Vector offset = rotate(piece.dx(), piece.dz(), facing);
            int ox = offset.getBlockX();
            int oz = offset.getBlockZ();
            minX = Math.min(minX, ox);
            maxX = Math.max(maxX, ox);
            minZ = Math.min(minZ, oz);
            maxZ = Math.max(maxZ, oz);
        }
        return new int[] {minX, maxX, minZ, maxZ};
    }

    /**
     * Keeps {@code desired} so {@code [desired + minOff, desired + maxOff]} stays in {@code [chunkMin, chunkMax]}.
     *
     * @param desired look-at coordinate
     * @param minOff smallest piece offset on this axis
     * @param maxOff largest piece offset on this axis
     * @param chunkMin chunk inclusive min
     * @param chunkMax chunk inclusive max
     * @return clamped origin
     */
    public static int clampOrigin(int desired, int minOff, int maxOff, int chunkMin, int chunkMax) {
        int lo = chunkMin - minOff;
        int hi = chunkMax - maxOff;
        if (lo > hi) {
            return (chunkMin + chunkMax) / 2;
        }
        return Math.max(lo, Math.min(hi, desired));
    }

    /**
     * @param material piece type
     * @param facing camp front (used when the block is directional)
     * @return block data for {@link org.bukkit.entity.Player#sendBlockChange}
     */
    public static BlockData dataFor(Material material, BlockFace facing) {
        BlockData data = material.createBlockData();
        if (material == Material.OAK_SIGN && data instanceof Rotatable rotatable) {
            BlockFace towardPlayer = facing.getOppositeFace();
            if (towardPlayer.isCartesian() && towardPlayer.getModY() == 0) {
                rotatable.setRotation(towardPlayer);
            }
        } else if (data instanceof Directional directional && facing.isCartesian() && facing.getModY() == 0) {
            directional.setFacing(facing);
        }
        if (data instanceof Slab slab) {
            slab.setType(Slab.Type.BOTTOM);
        }
        if (data instanceof Lightable lightable && material == Material.CAMPFIRE) {
            lightable.setLit(true);
        }
        return data;
    }

    /**
     * @param yaw raw yaw
     * @return yaw in {@code (-180, 180]}
     */
    private static float wrapYaw(float yaw) {
        float wrapped = yaw % 360f;
        if (wrapped <= -180f) {
            wrapped += 360f;
        }
        if (wrapped > 180f) {
            wrapped -= 360f;
        }
        return wrapped;
    }
}
