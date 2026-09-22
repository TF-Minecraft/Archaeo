package net.tfminecraft.archaeo.excavation;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Feedback when a buried find cube is smashed instead of recovered with the brush.
 * Used on the established cut and on unclaimed ruins so a random miner still notices.
 */
public final class FindBreakCue {
    private static final String MESSAGE = "Buried archaeological remains were destroyed.";

    private FindBreakCue() {
    }

    /**
     * Shatter at the cell and tell the breaker, if any.
     *
     * @param player miner who removed the cube, or {@code null} for world-only sound
     * @param block cell that held the find
     */
    public static void play(Player player, Block block) {
        if (block == null) {
            return;
        }
        Location at = block.getLocation().add(0.5, 0.5, 0.5);
        block.getWorld().playSound(at, Sound.BLOCK_DECORATED_POT_SHATTER, SoundCategory.BLOCKS, 1.15f, 0.8f);
        block.getWorld().playSound(at, Sound.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 0.55f, 0.55f);
        if (player != null) {
            player.sendMessage(MESSAGE);
        }
    }
}
