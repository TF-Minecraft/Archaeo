package net.tfminecraft.archaeo.establish;

import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Writes the excavation title onto the camp's standing sign.
 */
public final class CampSigns {
    private static final int LINE_WIDTH = 15;
    private static final int LINE_COUNT = 4;

    private CampSigns() {
    }

    /**
     * Waxes the sign and fills the front with {@code title}.
     *
     * @param block sign block
     * @param title excavation display name
     */
    public static void write(Block block, String title) {
        if (!(block.getState() instanceof Sign sign)) {
            return;
        }
        List<String> lines = wrap(title == null ? "" : title, LINE_WIDTH, LINE_COUNT);
        var front = sign.getSide(Side.FRONT);
        for (int index = 0; index < LINE_COUNT; index++) {
            front.setLine(index, index < lines.size() ? lines.get(index) : "");
        }
        sign.setWaxed(true);
        sign.update();
    }

    /**
     * Restores a real camp sign on one client after a fake overlay (air during move).
     * {@link Player#sendBlockChange} does not send tile-entity text, so the lines
     * must follow in a second packet.
     *
     * @param player viewer who received the overlay
     * @param block world sign (unchanged on the server)
     */
    public static void sendTo(Player player, Block block) {
        if (!(block.getState() instanceof Sign sign)) {
            return;
        }
        player.sendBlockChange(block.getLocation(), sign.getBlockData());
        player.sendSignChange(block.getLocation(), sign.getSide(Side.FRONT).getLines());
    }

    /**
     * @param text full title
     * @param width max characters per line
     * @param maxLines max lines
     * @return wrapped lines
     */
    static List<String> wrap(String text, int width, int maxLines) {
        List<String> lines = new ArrayList<>();
        String remaining = text.trim();
        while (!remaining.isEmpty() && lines.size() < maxLines) {
            if (remaining.length() <= width) {
                lines.add(remaining);
                break;
            }
            int split = remaining.lastIndexOf(' ', width);
            if (split <= 0) {
                split = width;
            }
            lines.add(remaining.substring(0, split).trim());
            remaining = remaining.substring(split).trim();
        }
        return lines;
    }
}
