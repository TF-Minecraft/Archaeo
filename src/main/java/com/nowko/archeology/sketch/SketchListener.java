package com.nowko.archeology.sketch;

import org.bukkit.Location;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.AsyncPlayerChatEvent;
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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

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
     * Right-click with sheet and pencil starts a sketch. Right-click on the cabinet
     * with a recovered piece in hand opens clean, register, or a reading. While editing,
     * right-click erases and left-click starts the sign confirm.
     *
     * @param event interact
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();
        if (!sketches.editing(player)
                && action == Action.RIGHT_CLICK_BLOCK
                && event.getHand() == EquipmentSlot.HAND
                && sketches.tryOpenCabinet(player, event.getClickedBlock(), player.isSneaking())) {
            event.setCancelled(true);
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
            return;
        }
        if (!sketches.editing(player)
                && (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)
                && event.getHand() == EquipmentSlot.HAND
                && sketches.tryStartFromHands(player)) {
            event.setCancelled(true);
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
            return;
        }
        if (!sketches.editing(player)) {
            return;
        }
        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            sketches.askToSign(player);
            return;
        }
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            sketches.erase(player);
        }
    }

    /**
     * Combines sheet+pencil in the player's bag. Filing a signed sketch happens at the cabinet.
     *
     * @param event click
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCombine(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null) {
            return;
        }
        InventoryType type = event.getClickedInventory().getType();
        if (type != InventoryType.PLAYER && type != InventoryType.CRAFTING && type != InventoryType.CREATIVE) {
            return;
        }
        ItemStack cursor = event.getCursor() == null
                ? new ItemStack(org.bukkit.Material.AIR)
                : event.getCursor();
        ItemStack slot = event.getCurrentItem() == null
                ? new ItemStack(org.bukkit.Material.AIR)
                : event.getCurrentItem();
        if (sketches.tryCraftOnClick(player, cursor, slot)) {
            event.setCancelled(true);
            event.setCurrentItem(emptyToNull(slot));
            event.getView().setCursor(emptyToNull(cursor));
            sketches.afterBagCraft(
                    player,
                    event.getClickedInventory(),
                    event.getSlot(),
                    cursor,
                    slot);
        }
    }

    /**
     * @param stack result stack
     * @return {@code null} when Bukkit should treat the slot as empty
     */
    private static ItemStack emptyToNull(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            return null;
        }
        return stack;
    }

    /**
     * Places a drawing in the furnace top slot, or files it on Register.
     *
     * @param event click
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCabinetClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SketchCabinet)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (event.getClickedInventory() == top) {
            event.setCancelled(true);
            ItemStack cursor = event.getCursor() == null
                    ? new ItemStack(org.bukkit.Material.AIR)
                    : event.getCursor().clone();
            ItemStack next = sketches.handleCabinetClick(player, top, event.getSlot(), cursor);
            event.getView().setCursor(next);
            return;
        }
        if (event.isShiftClick()) {
            event.setCancelled(true);
            ItemStack current = event.getCurrentItem();
            if (current != null && sketches.tryDepositCabinet(player, top, current)) {
                event.setCurrentItem(current);
            }
        }
    }

    /**
     * Picks a rack tool or wipes dirt in the cabinet lab window. The bag cannot take lab stacks.
     *
     * @param event click
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLabClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof CabinetLabBoard board)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        ItemStack cursor = event.getCursor() == null
                ? new ItemStack(org.bukkit.Material.AIR)
                : event.getCursor().clone();
        ItemStack next = sketches.handleLabClick(player, board, event.getSlot(), cursor);
        event.getView().setCursor(next);
    }

    /**
     * @param event drag across the register furnace
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCabinetDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SketchCabinet)) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        for (int slot : event.getRawSlots()) {
            if (slot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /**
     * Lab tools cannot be dragged into the field or the bag.
     *
     * @param event drag across the lab window
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLabDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof CabinetLabBoard) {
            event.setCancelled(true);
        }
    }

    /**
     * Returns the drawing when the register closes so it is not destroyed with the furnace.
     *
     * @param event close
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onCabinetClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof SketchCabinet cabinet)) {
            return;
        }
        if (event.getPlayer() instanceof Player player) {
            cabinet.returnContents(player);
        }
    }

    /**
     * Drops fake lab tools and panes so they never stay in the bag.
     *
     * @param event close
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onLabClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof CabinetLabBoard board)) {
            return;
        }
        if (event.getPlayer() instanceof Player player) {
            sketches.handleLabClose(player, board);
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
     * Own bag (E) stays available so moving the map away can save. Chests, villagers,
     * workbenches, the camp board, and other GUIs stay closed while editing.
     *
     * @param event inventory open
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInventory(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player) || !sketches.editing(player)) {
            return;
        }
        InventoryType type = event.getInventory().getType();
        if (type == InventoryType.CRAFTING || type == InventoryType.CREATIVE || type == InventoryType.PLAYER) {
            return;
        }
        event.setCancelled(true);
    }

    /**
     * Pack plugins may rewrite lore on open; stamp Archaeo how-to again.
     *
     * @param event inventory open
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpened(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) {
            sketches.stampKitsLater(player);
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
     * Dropping the map saves onto the dropped stack and leaves the editor.
     * Lab rack copies cannot be dropped into the world.
     *
     * @param event drop
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (sketches.isLabItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            return;
        }
        if (!sketches.editing(player)) {
            return;
        }
        ItemStack dropped = event.getItemDrop().getItemStack();
        if (sketches.isSketchMap(dropped)) {
            sketches.leave(player, true, dropped);
        }
    }

    /**
     * Hotbar numbers and the scroll wheel: save the sheet that left, then enter if the new slot is unsigned.
     *
     * @param event hotbar change
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (sketches.editing(player)) {
            sketches.leave(player, true, player.getInventory().getItem(event.getPreviousSlot()));
        }
        sketches.syncHand(player, player.getInventory().getItem(event.getNewSlot()));
        sketches.stampKitsLater(player);
    }

    /**
     * @param event off-hand swap
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        sketches.syncHandLater(event.getPlayer());
        sketches.stampKitsLater(event.getPlayer());
    }

    /**
     * @param event death
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        sketches.cancelLab(player);
        if (!sketches.editing(player)) {
            return;
        }
        sketches.leave(player, false, event.getDrops());
    }

    /**
     * @param event dimension change
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorld(PlayerChangedWorldEvent event) {
        sketches.cancelLab(event.getPlayer());
        sketches.syncHandLater(event.getPlayer());
    }

    /**
     * @param event disconnect
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        sketches.cancelLab(event.getPlayer());
        if (sketches.editing(event.getPlayer())) {
            sketches.leave(event.getPlayer(), false);
        }
    }

    /**
     * @param event chat
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        SketchSession session = sketches.session(player);
        if (session == null || !session.awaitingSign()) {
            return;
        }
        event.setCancelled(true);
        String raw = event.getMessage() == null ? "" : event.getMessage().trim();
        sketches.handleSignChatLater(player, raw);
    }

    /**
     * Moving the map out of the hotbar (or onto the cursor) saves onto that stack.
     *
     * @param event click
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack hotbar = event.getHotbarButton() >= 0
                ? player.getInventory().getItem(event.getHotbarButton())
                : null;
        sketches.syncHandLater(player, event.getCursor(), event.getCurrentItem(), hotbar);
        sketches.stampKitsLater(player);
    }

    /**
     * @param event drag
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        java.util.ArrayList<ItemStack> extras = new java.util.ArrayList<>();
        extras.add(event.getCursor());
        extras.add(event.getOldCursor());
        extras.addAll(event.getNewItems().values());
        sketches.syncHandLater(player, extras.toArray(ItemStack[]::new));
    }

    /**
     * Closing with the map on the cursor dumps it back into the bag; save if it left the hand.
     *
     * @param event close
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            sketches.syncHandLater(player, event.getView().getCursor());
            sketches.stampKitsLater(player);
        }
    }

    /**
     * @param event pickup
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            sketches.hydrate(event.getItem().getItemStack());
            sketches.syncHandLater(player);
            sketches.stampKitsLater(player);
        }
    }

    /**
     * @param event join
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        for (ItemStack stack : player.getInventory().getContents()) {
            sketches.hydrate(stack);
        }
        sketches.hydrate(player.getInventory().getItemInOffHand());
        sketches.syncHandLater(player);
        sketches.stampKitsLater(player);
    }

    /**
     * Maps hanging in frames must show the saved drawing after a restart.
     *
     * @param event chunk
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunk(ChunkLoadEvent event) {
        for (org.bukkit.entity.Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof ItemFrame frame) {
                sketches.hydrate(frame.getItem());
            }
        }
    }
}
