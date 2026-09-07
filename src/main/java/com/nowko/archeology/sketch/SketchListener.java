package com.nowko.archeology.sketch;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Keeps a sketch editor still and turns clicks / drop into paint, erase, and leave.
 */
public class SketchListener implements Listener {
    private final SketchService sketches;

    /**
     * @param sketches open sessions
     */
    public SketchListener(SketchService sketches) {
        this.sketches = sketches;
    }

    /**
     * Allows looking around but not walking away from the easel.
     *
     * @param event movement
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!sketches.editing(event.getPlayer()) || event.getTo() == null) {
            return;
        }
        if (!event.getFrom().getWorld().equals(event.getTo().getWorld())
                || event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getY() != event.getTo().getY()
                || event.getFrom().getZ() != event.getTo().getZ()) {
            Location stay = event.getFrom().clone();
            stay.setYaw(event.getTo().getYaw());
            stay.setPitch(event.getTo().getPitch());
            event.setTo(stay);
        }
    }

    /**
     * Left-click stamps, right-click erases. Cancels the swing so the map stays the gesture.
     *
     * @param event interact
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !sketches.editing(event.getPlayer())) {
            return;
        }
        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            sketches.paint(event.getPlayer());
            return;
        }
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            sketches.erase(event.getPlayer());
        }
    }

    /**
     * Dropping the map leaves the editor; the item stays so the snapshot can still be looked at.
     *
     * @param event drop
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!sketches.editing(event.getPlayer())) {
            return;
        }
        if (!sketches.isSketchMap(event.getItemDrop().getItemStack())) {
            return;
        }
        event.setCancelled(true);
        sketches.leave(event.getPlayer(), true);
    }

    /**
     * @param event hotbar change
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        if (sketches.editing(event.getPlayer())) {
            sketches.leave(event.getPlayer(), true);
        }
    }

    /**
     * @param event off-hand swap
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (sketches.editing(event.getPlayer())) {
            sketches.leave(event.getPlayer(), true);
        }
    }

    /**
     * @param event death
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (sketches.editing(event.getEntity())) {
            sketches.leave(event.getEntity(), false);
        }
    }

    /**
     * @param event dimension change
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorld(PlayerChangedWorldEvent event) {
        if (sketches.editing(event.getPlayer())) {
            sketches.leave(event.getPlayer(), true);
        }
    }

    /**
     * @param event disconnect
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (sketches.editing(event.getPlayer())) {
            sketches.leave(event.getPlayer(), false);
        }
    }
}
