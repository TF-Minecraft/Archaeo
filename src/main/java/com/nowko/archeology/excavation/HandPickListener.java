package com.nowko.archeology.excavation;

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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Left-click hold with a whitelisted tool drives the vanilla-break clock on prism fill.
 */
public class HandPickListener implements Listener {
    private final HandPickService pick;
    private final SiteRepository sites;

    /**
     * @param pick break clock and tool whitelist
     * @param sites excavation lookup
     */
    public HandPickListener(HandPickService pick, SiteRepository sites) {
        this.pick = pick;
        this.sites = sites;
    }

    /**
     * Marks the hold on prism fill. Off the cut, a whitelist pickaxe or shovel stays vanilla.
     *
     * @param event start of vanilla block damage
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onDamage(BlockDamageEvent event) {
        Player player = event.getPlayer();
        if (!pick.isExcavationTool(player.getInventory().getItemInMainHand())) {
            return;
        }
        Block block = event.getBlock();
        if (!inPrismFill(block)) {
            return;
        }
        pick.syncHeldTool(player);
        event.setInstaBreak(false);
        pick.noteMining(player, block);
    }

    /**
     * Client predicted a vanilla break on the cut; pin the real {@code BlockData} back.
     *
     * @param event would-be break
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!pick.isExcavationTool(player.getInventory().getItemInMainHand())) {
            return;
        }
        if (!inPrismFill(event.getBlock()) && !pick.isCycling(player)) {
            return;
        }
        event.setCancelled(true);
        pick.suppressVanillaBreak(player, event.getBlock());
    }

    /**
     * Arm swings while holding left-click keep the clock from going stale.
     *
     * @param event swing
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        Player player = event.getPlayer();
        if (!pick.isExcavationTool(player.getInventory().getItemInMainHand())) {
            return;
        }
        Block block = player.getTargetBlockExact(6);
        if (block != null && inPrismFill(block)) {
            pick.noteMining(player, block);
        }
    }

    /**
     * Left-click on the cut keeps the clock alive; right-click must not path or till prism fill.
     *
     * @param event interact
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!pick.isExcavationTool(player.getInventory().getItemInMainHand())) {
            return;
        }
        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_BLOCK) {
            Block clicked = event.getClickedBlock();
            if (clicked != null && inPrismFill(clicked)) {
                pick.noteMining(player, clicked);
            }
            return;
        }
        if (action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || !inPrismFill(block)) {
            return;
        }
        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
    }

    /**
     * Lock mining speed before the next click when already looking at the cut.
     *
     * @param event join
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        pick.syncHeldTool(event.getPlayer());
    }

    /**
     * @param event hotbar change
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        pick.finish(player);
        pick.syncHeldTool(player, player.getInventory().getItem(event.getNewSlot()));
    }

    /**
     * After the swap, the off-hand stack is what will sit in the main hand.
     *
     * @param event off-hand swap
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        pick.finish(player);
        pick.syncHeldTool(player, event.getOffHandItem());
    }

    /**
     * @param event drop
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (pick.isExcavationTool(event.getItemDrop().getItemStack())) {
            pick.finish(event.getPlayer());
            pick.syncHeldTool(event.getPlayer());
        }
    }

    /**
     * @param event quit
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        pick.finish(event.getPlayer());
        pick.syncHeldTool(event.getPlayer(), null);
    }

    /**
     * @param block world cell
     * @return prism fill the clock may run on
     */
    private boolean inPrismFill(Block block) {
        if (!PrismFill.isTerrainFill(block.getType())) {
            return false;
        }
        return sites.findEstablishedPrism(
                block.getWorld().getName(),
                block.getX(),
                block.getY(),
                block.getZ()).isPresent();
    }
}
