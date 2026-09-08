package com.nowko.archeology.sketch;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Keeps a sketch editor still and maps sneak / interact / space onto paint, erase, and ink.
 * Vanilla attack, jump, flight, and block use are cancelled so those keys only mean sketch.
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
     * Allows looking around but not walking or jumping away from the easel.
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
     * Right-click erases. Left-click is swallowed so it never stamps, attacks, or mines.
     *
     * @param event interact
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (!sketches.editing(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            sketches.erase(event.getPlayer());
        }
    }

    /**
     * Left-click on a mob or player must not deal damage, and does not paint.
     *
     * @param event melee hit
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onHit(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && sketches.editing(player)) {
            event.setCancelled(true);
        }
    }

    /**
     * Right-click on an entity must not open, mount, or trade; it still erases.
     *
     * @param event entity use
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onEntity(PlayerInteractEntityEvent event) {
        if (!sketches.editing(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND) {
            sketches.erase(event.getPlayer());
        }
    }

    /**
     * @param event armour-stand click
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onArmorStand(PlayerInteractAtEntityEvent event) {
        if (sketches.editing(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /**
     * @param event armour-stand slot change
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onArmorStandEdit(PlayerArmorStandManipulateEvent event) {
        if (sketches.editing(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /**
     * @param event item-frame or painting break
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onHanging(HangingBreakByEntityEvent event) {
        if (event.getRemover() instanceof Player player && sketches.editing(player)) {
            event.setCancelled(true);
        }
    }

    /**
     * Hides the punch animation so a stamp does not look like an attack.
     *
     * @param event arm swing
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        if (sketches.editing(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /**
     * @param event start of vanilla mining
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onBlockDamage(BlockDamageEvent event) {
        if (sketches.editing(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /**
     * @param event would-be break
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onBreak(BlockBreakEvent event) {
        if (sketches.editing(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /**
     * Space in creative must not start or stop flight; jump strength is already zeroed.
     *
     * @param event flight toggle
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlight(PlayerToggleFlightEvent event) {
        if (sketches.editing(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /**
     * @param event sprint
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSprint(PlayerToggleSprintEvent event) {
        if (sketches.editing(event.getPlayer()) && event.isSprinting()) {
            event.setCancelled(true);
        }
    }

    /**
     * @param event bow, trident, snowball, and similar
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player player && sketches.editing(player)) {
            event.setCancelled(true);
        }
    }

    /**
     * @param event fishing rod
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onFish(PlayerFishEvent event) {
        if (sketches.editing(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /**
     * Chests, villagers, the camp board, and any other GUI must not open over the map.
     *
     * @param event inventory open
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInventory(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && sketches.editing(player)) {
            event.setCancelled(true);
        }
    }

    /**
     * @param event placing a liquid
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (sketches.editing(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /**
     * @param event scooping a liquid
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (sketches.editing(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /**
     * @param event bed
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onBed(PlayerBedEnterEvent event) {
        if (sketches.editing(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /**
     * @param event eating or drinking
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (sketches.editing(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /**
     * @param event lectern
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onLectern(PlayerTakeLecternBookEvent event) {
        if (sketches.editing(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /**
     * @param event sweet berries and similar
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onHarvest(PlayerHarvestBlockEvent event) {
        if (sketches.editing(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /**
     * @param event shears
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onShear(PlayerShearEntityEvent event) {
        if (sketches.editing(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /**
     * Dropping the map leaves the editor; any other drop is swallowed so Q is only that exit.
     *
     * @param event drop
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!sketches.editing(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
        if (sketches.isSketchMap(event.getItemDrop().getItemStack())) {
            sketches.leave(event.getPlayer(), true);
        }
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
