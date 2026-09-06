package com.nowko.archeology.excavation;

import com.nowko.archeology.item.BrushItem;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

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
     * Starts dusting on click, or restores the stored bar when the brush is equipped.
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
     * Equipping the brush starts a look watcher so unfinished cubes can restore their bar.
     * Switching away parks the remaining ticks on the find cube.
     *
     * @param event hotbar change
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        ItemStack stack = event.getPlayer().getInventory().getItem(event.getNewSlot());
        if (brush.isBrush(stack)) {
            recover.watch(event.getPlayer());
            return;
        }
        recover.cancel(event.getPlayer());
    }

    /**
     * @param event off-hand swap
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        recover.cancel(event.getPlayer());
    }

    /**
     * @param event dropped stack
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
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
