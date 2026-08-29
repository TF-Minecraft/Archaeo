package com.nowko.archeology.establish;

import com.nowko.archeology.item.EstablishItem;
import com.nowko.archeology.model.Site;
import com.nowko.archeology.site.SiteRepository;
import org.bukkit.Tag;
import org.bukkit.DyeColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Iterator;
import java.util.List;

/**
 * Locks camp blocks, opens the excavation board from the sign, and finishes board actions.
 */
public class CampListener implements Listener {
    private final JavaPlugin plugin;
    private final SiteRepository sites;
    private final EstablishItem establishItem;
    private final EstablishService establish;

    /**
     * @param plugin chat rename must run on the main thread
     * @param sites camp lookup
     * @param establishItem kit recognition
     * @param establish plant / move / rename
     */
    public CampListener(
            JavaPlugin plugin,
            SiteRepository sites,
            EstablishItem establishItem,
            EstablishService establish
    ) {
        this.plugin = plugin;
        this.sites = sites;
        this.establishItem = establishItem;
        this.establish = establish;
    }

    /**
     * @param event break event
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (locked(event.getBlock())) {
            event.setCancelled(true);
            return;
        }
        if (establish.sneakHeld(event.getPlayer()) && establish.isRelocating(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /**
     * @param event burn event
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (locked(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /**
     * @param event fade event
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        if (locked(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /**
     * @param event entity-block change
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent event) {
        if (locked(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /**
     * @param event explosion
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(BlockExplodeEvent event) {
        stripLocked(event.blockList());
    }

    /**
     * @param event explosion
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        stripLocked(event.blockList());
    }

    /**
     * @param event piston push
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (anyLocked(event.getBlocks()) || locked(event.getBlock().getRelative(event.getDirection()))) {
            event.setCancelled(true);
        }
    }

    /**
     * @param event piston pull
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (anyLocked(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    /**
     * @param event vanilla sign edit
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        if (locked(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /**
     * Opens the board, confirms a camp move, or aborts a move.
     *
     * @param event interact event
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            if (establish.tryCancelMove(event.getPlayer())) {
                event.setCancelled(true);
            }
            return;
        }
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block != null && Tag.ALL_SIGNS.isTagged(block.getType()) && locked(block)) {
            event.setCancelled(true);
            Site site = sites.findLockedCampBlock(
                    block.getWorld().getName(),
                    block.getX(),
                    block.getY(),
                    block.getZ()).orElse(null);
            if (site == null) {
                return;
            }
            if (establish.isRelocating(event.getPlayer())) {
                establish.tryCancelMove(event.getPlayer());
            }
            openBoard(event.getPlayer(), site);
            return;
        }
        if (establishItem.isEstablish(event.getItem())) {
            return;
        }
        if (establish.isRelocating(event.getPlayer())) {
            event.setCancelled(true);
            establish.tryFinishMove(event.getPlayer());
        }
    }

    /**
     * Empty-hand right-click air never reaches the server; the move aim proxy is what they actually hit.
     *
     * @param event entity interact at a point
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onAimProxyAt(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (establish.tryFinishMoveOnAimProxy(event.getPlayer(), event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    /**
     * @param event entity interact
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onAimProxy(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (establish.tryFinishMoveOnAimProxy(event.getPlayer(), event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    /**
     * Left-click on the move aim proxy is an attack, not {@code LEFT_CLICK_AIR}.
     *
     * @param event damage
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onAimProxyAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (establish.handleMoveAimProxyAttack(player, event.getEntity())) {
            event.setCancelled(true);
        }
    }

    /**
     * Changing hotbar slots aborts a camp move.
     *
     * @param event hotbar change
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        if (!establish.isRelocating(event.getPlayer())) {
            return;
        }
        establish.tryCancelMove(event.getPlayer());
    }

    /**
     * @param event inventory click
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBoardClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof CampBoard board)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        Site site = sites.findById(board.siteId()).orElse(null);
        if (site == null || !site.isCampLocked()) {
            player.closeInventory();
            return;
        }
        if (!board.director()) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot == CampBoard.SLOT_RENAME) {
            player.closeInventory();
            establish.beginRename(player, site);
            return;
        }
        if (slot == CampBoard.SLOT_MOVE) {
            player.closeInventory();
            establish.beginRelocate(player, site);
            return;
        }
        if (slot == CampBoard.SLOT_WOOL_PRIMARY) {
            new CampWoolPicker(site.getId(), CampWoolRole.PRIMARY).open(player, site);
            return;
        }
        if (slot == CampBoard.SLOT_WOOL_SECONDARY) {
            new CampWoolPicker(site.getId(), CampWoolRole.SECONDARY).open(player, site);
        }
    }

    /**
     * @param event inventory click
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWoolPickerClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof CampWoolPicker picker)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        Site site = sites.findById(picker.siteId()).orElse(null);
        if (site == null || !site.isCampLocked()) {
            player.closeInventory();
            return;
        }
        if (site.getDirector() == null || !site.getDirector().equals(player.getUniqueId())) {
            player.closeInventory();
            return;
        }
        int slot = event.getRawSlot();
        if (slot == CampWoolPicker.SLOT_BACK) {
            new CampBoard(site.getId(), true).open(player, site);
            return;
        }
        DyeColor color = CampWoolPicker.colorAt(slot);
        if (color == null) {
            return;
        }
        establish.applyCampWool(player, site, color, picker.role());
        new CampBoard(site.getId(), true).open(player, site);
    }

    /**
     * @param event drag
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBoardDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof CampBoard
                || event.getInventory().getHolder() instanceof CampWoolPicker) {
            event.setCancelled(true);
        }
    }

    /**
     * @param event chat
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String raw = event.getMessage() == null ? "" : event.getMessage().trim();
        if (establish.isRelocating(player) && raw.equalsIgnoreCase("cancel")) {
            event.setCancelled(true);
            plugin.getServer().getScheduler().runTask(plugin, () -> establish.tryCancelMove(player));
            return;
        }
        if (!establish.isRenaming(player)) {
            return;
        }
        event.setCancelled(true);
        plugin.getServer().getScheduler().runTask(plugin, () -> establish.handleRenameChat(player, raw));
    }

    /**
     * @param player viewer
     * @param site excavation
     */
    private void openBoard(Player player, Site site) {
        boolean director = site.getDirector() != null && site.getDirector().equals(player.getUniqueId());
        new CampBoard(site.getId(), director).open(player, site);
    }

    /**
     * @param block world block
     * @return whether it belongs to a locked camp
     */
    private boolean locked(Block block) {
        return sites.findLockedCampBlock(
                block.getWorld().getName(),
                block.getX(),
                block.getY(),
                block.getZ()).isPresent();
    }

    /**
     * @param blocks piston or explode list
     * @return whether any cell is locked
     */
    private boolean anyLocked(List<Block> blocks) {
        for (Block block : blocks) {
            if (locked(block)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param blocks explosion list
     */
    private void stripLocked(List<Block> blocks) {
        Iterator<Block> iterator = blocks.iterator();
        while (iterator.hasNext()) {
            if (locked(iterator.next())) {
                iterator.remove();
            }
        }
    }
}
