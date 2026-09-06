package com.nowko.archeology.excavation;

import com.nowko.archeology.item.BrushItem;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Right-click with the configured brush may start recovery; vanilla brush use is never cancelled.
 */
public class RecoverListener implements Listener {
    private final BrushItem brush;
    private final RecoverService recover;

    /**
     * @param brush material matcher
     * @param recover channel and lift
     */
    public RecoverListener(BrushItem brush, RecoverService recover) {
        this.brush = brush;
        this.recover = recover;
    }

    /**
     * Starts the field channel only on a discovered find cell. Any other block stays vanilla.
     *
     * @param event interact
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!brush.isBrush(event.getItem())) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        recover.begin(event.getPlayer(), block);
    }

    /**
     * @param event hotbar change
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        recover.cancel(event.getPlayer());
    }

    /**
     * @param event quit
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        recover.cancel(event.getPlayer());
    }
}
