package com.nowko.archeology.sketch;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Short audio and particle cues for cabinet steps, paired with the villager refusal on a bad Register.
 */
public final class CabinetCues {
    private CabinetCues() {
    }

    /**
     * Villager yes and happy motes, then close the window next tick like a finished lab wipe.
     *
     * @param plugin scheduler owner
     * @param player cataloguer
     */
    public static void complete(JavaPlugin plugin, Player player) {
        if (player == null) {
            return;
        }
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, SoundCategory.PLAYERS, 0.9f, 1.1f);
        player.getWorld().spawnParticle(
                Particle.HAPPY_VILLAGER,
                player.getLocation().add(0, 1.1, 0),
                10,
                0.35,
                0.25,
                0.35,
                0.02);
        if (plugin == null) {
            player.closeInventory();
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.closeInventory();
            }
        });
    }

    /**
     * Page-turn when a station offer is signed and more questions remain.
     *
     * @param player cataloguer
     */
    public static void signedReading(Player player) {
        if (player == null) {
            return;
        }
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 1f, 1.1f);
    }
}
