package com.nowko.archeology.excavation;

import com.nowko.archeology.item.BrushItem;
import org.bukkit.block.Block;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Right-click with the configured brush on prism fill drives {@link RecoverService}.
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
     * Consumes vanilla brush use on a find cell so only the field channel runs.
     *
     * @param event interact
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
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
        if (!recover.begin(event.getPlayer(), block)) {
            return;
        }
        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
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
