package com.nowko.archeology.excavation;

import com.nowko.archeology.item.HandPickItem;
import com.nowko.archeology.site.SiteRepository;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Left-click hold drives a plugin strike cycle. Vanilla mining is cancelled so the block never “breaks”.
 */
public class HandPickListener implements Listener {
    private final HandPickItem item;
    private final HandPickService pick;
    private final SiteRepository sites;

    /**
     * @param item Hand Pick recognition
     * @param pick strike cycles
     * @param sites working-face lookup
     */
    public HandPickListener(HandPickItem item, HandPickService pick, SiteRepository sites) {
        this.item = item;
        this.pick = pick;
        this.sites = sites;
    }

    /**
     * Starts the cycle on the open cut and stops vanilla hardness from advancing.
     *
     * @param event start of vanilla block damage
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onDamage(BlockDamageEvent event) {
        Player player = event.getPlayer();
        if (!item.isPick(player.getInventory().getItemInMainHand())) {
            return;
        }
        event.setInstaBreak(false);
        Block block = event.getBlock();
        if (DigCut.isWorkingFace(sites, block)) {
            event.setCancelled(true);
            pick.noteMining(player, block);
            return;
        }
        event.setCancelled(true);
        pick.warnOffCut(player);
    }

    /**
     * Client predicted a vanilla break; restore the block and clear crack overlay.
     *
     * @param event would-be break
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!item.isPick(player.getInventory().getItemInMainHand())) {
            return;
        }
        event.setCancelled(true);
        pick.suppressVanillaBreak(player, event.getBlock());
    }

    /**
     * Arm swings while holding left-click keep the cycle from going stale.
     *
     * @param event swing
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        Player player = event.getPlayer();
        if (!item.isPick(player.getInventory().getItemInMainHand())) {
            return;
        }
        Block block = player.getTargetBlockExact(6);
        if (block != null && DigCut.isWorkingFace(sites, block)) {
            pick.noteMining(player, block);
        }
    }

    /**
     * Right-click must not use the pick as a vanilla item.
     *
     * @param event interact
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!item.isPick(player.getInventory().getItemInMainHand())) {
            return;
        }
        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_AIR) {
            pick.warnOffCut(player);
            return;
        }
        if (action == Action.LEFT_CLICK_BLOCK) {
            Block clicked = event.getClickedBlock();
            if (clicked != null && DigCut.isWorkingFace(sites, clicked)) {
                pick.noteMining(player, clicked);
            }
            return;
        }
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) {
            return;
        }
        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        Block block = event.getClickedBlock();
        if (block == null) {
            block = player.getTargetBlockExact(6);
        }
        if (block == null || !DigCut.isWorkingFace(sites, block)) {
            pick.warnOffCut(player);
        }
    }

    /**
     * @param event hotbar change
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        pick.finish(event.getPlayer());
    }

    /**
     * @param event off-hand swap
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        pick.finish(event.getPlayer());
    }

    /**
     * @param event drop
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (item.isPick(event.getItemDrop().getItemStack())) {
            pick.finish(event.getPlayer());
        }
    }

    /**
     * @param event quit
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        pick.finish(event.getPlayer());
    }
}
