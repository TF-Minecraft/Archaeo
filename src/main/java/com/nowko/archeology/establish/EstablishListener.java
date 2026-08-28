package com.nowko.archeology.establish;

import com.nowko.archeology.item.EstablishItem;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Routes right-clicks with the establishment kit into {@link EstablishService}.
 */
public class EstablishListener implements Listener {
    private final EstablishItem item;
    private final EstablishService service;

    /**
     * @param item kit recognition
     * @param service claim logic
     */
    public EstablishListener(EstablishItem item, EstablishService service) {
        this.item = item;
        this.service = service;
    }

    /**
     * Consumes the vanilla stick/block action so only Archaeo establishment runs.
     *
     * @param event interact event
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!item.isEstablish(event.getItem())) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        event.setCancelled(true);
        service.tryEstablish(event.getPlayer(), block);
    }

    /**
     * Drops the client-only tint so it cannot linger after disconnect.
     *
     * @param event quit event
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        service.clearPreview(event.getPlayer());
        service.hideHud(event.getPlayer());
    }

    /**
     * @param event world change
     */
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        service.clearPreviewFromWorld(event.getPlayer(), event.getFrom());
    }
}
