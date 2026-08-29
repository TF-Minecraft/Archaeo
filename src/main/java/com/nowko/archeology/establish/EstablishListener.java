package com.nowko.archeology.establish;

import com.nowko.archeology.item.EstablishItem;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Routes establishment-kit clicks into {@link EstablishService}.
 */
public class EstablishListener implements Listener {
    private final EstablishItem item;
    private final EstablishService service;

    /**
     * @param item kit recognition
     * @param service claim and move logic
     */
    public EstablishListener(EstablishItem item, EstablishService service) {
        this.item = item;
        this.service = service;
    }

    /**
     * Right-click plants; sneak+left-click cycles wool on first plant; left-click aborts a camp move.
     *
     * @param event interact event
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!item.isEstablish(event.getItem())) {
            return;
        }
        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            if (service.tryCancelMove(event.getPlayer())) {
                event.setCancelled(true);
                return;
            }
            if (service.sneakHeld(event.getPlayer())) {
                event.setCancelled(true);
                service.tryCycleWool(event.getPlayer());
            }
            return;
        }
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) {
            return;
        }
        event.setCancelled(true);
        service.tryUseKit(event.getPlayer(), event.getClickedBlock());
    }

    /**
     * Sneak + hotbar scroll cycles wool while holding the kit for a first plant.
     * Any hotbar change during a camp move aborts it.
     *
     * @param event hotbar change
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        if (service.isRelocating(event.getPlayer())) {
            service.tryCancelMove(event.getPlayer());
            return;
        }
        if (!service.sneakHeld(event.getPlayer())) {
            return;
        }
        org.bukkit.inventory.ItemStack stack = event.getPlayer().getInventory().getItem(event.getPreviousSlot());
        if (!item.isEstablish(stack)) {
            return;
        }
        event.setCancelled(true);
        service.cycleWool(event.getPlayer());
    }

    /**
     * Drops the client-only tint so it cannot linger after disconnect.
     *
     * @param event quit event
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        service.clearSession(event.getPlayer());
        service.clearPreview(event.getPlayer());
        service.hideHud(event.getPlayer());
    }

    /**
     * @param event world change
     */
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        service.clearSession(event.getPlayer());
        service.clearPreviewFromWorld(event.getPlayer(), event.getFrom());
    }
}
