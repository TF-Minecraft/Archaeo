package com.nowko.archeology.prospect;

import com.nowko.archeology.item.ProspectItem;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Routes right-clicks with the prospecting kit into {@link ProspectService}.
 */
public class ProspectListener implements Listener {
    private final ProspectItem item;
    private final ProspectService service;

    /**
     * @param item kit recognition
     * @param service sample logic
     */
    public ProspectListener(ProspectItem item, ProspectService service) {
        this.item = item;
        this.service = service;
    }

    /**
     * Consumes the vanilla hoe action on sampleable ground. Stone and other non-soil
     * blocks get a short refusal; chests and stations stay vanilla.
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
        if (!item.isProspect(event.getItem())) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        if (!service.isSampleGround(block)) {
            if (block.getType().isInteractable()) {
                return;
            }
            event.setCancelled(true);
            service.refuseWrongGround(event.getPlayer());
            return;
        }
        event.setCancelled(true);
        service.begin(event.getPlayer(), block);
    }

    /**
     * @param event quit event
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        service.cancel(event.getPlayer());
    }
}
