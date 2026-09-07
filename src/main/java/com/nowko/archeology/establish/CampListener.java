package com.nowko.archeology.establish;

import com.nowko.archeology.item.EstablishItem;
import com.nowko.archeology.config.CatalogRegistry;
import com.nowko.archeology.excavation.HandPickService;
import com.nowko.archeology.excavation.PrismOutlineService;
import com.nowko.archeology.model.Site;
import com.nowko.archeology.site.SiteRepository;
import org.bukkit.OfflinePlayer;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Locks camp blocks, opens the excavation board from the sign, and finishes board actions.
 */
public class CampListener implements Listener {
    private final JavaPlugin plugin;
    private final SiteRepository sites;
    private final CatalogRegistry catalogs;
    private final EstablishItem establishItem;
    private final EstablishService establish;
    private final HandPickService handPick;
    private final PrismOutlineService outline;
    private final Map<UUID, UUID> inviteForSite = new ConcurrentHashMap<>();

    /**
     * @param plugin chat prompts must run on the main thread
     * @param sites camp lookup
     * @param catalogs dossier texts and work-day size
     * @param establishItem kit recognition
     * @param establish plant / move / rename
     * @param handPick refreshes the work-day figure on the board
     * @param outline draws the prism when the board asks for limits
     */
    public CampListener(
            JavaPlugin plugin,
            SiteRepository sites,
            CatalogRegistry catalogs,
            EstablishItem establishItem,
            EstablishService establish,
            HandPickService handPick,
            PrismOutlineService outline
    ) {
        this.plugin = plugin;
        this.sites = sites;
        this.catalogs = catalogs;
        this.establishItem = establishItem;
        this.establish = establish;
        this.handPick = handPick;
        this.outline = outline;
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
        int slot = event.getRawSlot();
        if (slot == CampBoard.SLOT_PERSONAL) {
            new CampStaffBoard(site.getId(), board.director()).open(player, site);
            return;
        }
        if (slot == CampBoard.SLOT_LIMITS) {
            player.closeInventory();
            outline.show(player, site);
            return;
        }
        if (!board.director()) {
            return;
        }
        if (slot == CampBoard.SLOT_RENAME) {
            player.closeInventory();
            inviteForSite.remove(player.getUniqueId());
            establish.beginRename(player, site);
            return;
        }
        if (slot == CampBoard.SLOT_MOVE) {
            player.closeInventory();
            inviteForSite.remove(player.getUniqueId());
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
    public void onStaffClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof CampStaffBoard staff)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        Site site = sites.findById(staff.siteId()).orElse(null);
        if (site == null || !site.isCampLocked()) {
            player.closeInventory();
            return;
        }
        boolean director = site.isDirector(player.getUniqueId());
        int slot = event.getRawSlot();
        if (slot == CampStaffBoard.SLOT_BACK) {
            openBoard(player, site);
            return;
        }
        if (!director) {
            return;
        }
        if (slot == CampStaffBoard.SLOT_ADD) {
            player.closeInventory();
            establish.abortRename(player);
            inviteForSite.put(player.getUniqueId(), site.getId());
            player.sendMessage("Type the player name in chat, or type cancel.");
            return;
        }
        UUID member = staff.playerAt(slot);
        if (member == null) {
            return;
        }
        if (!site.revokeExcavator(member)) {
            player.sendMessage("The director cannot be removed.");
            return;
        }
        sites.save(site);
        player.sendMessage("Removed " + CampNames.of(player, member) + " from the excavation staff.");
        Player online = player.getServer().getPlayer(member);
        if (online != null) {
            online.sendMessage("You may no longer work on " + site.displayLabel() + ".");
        }
        new CampStaffBoard(site.getId(), true).open(player, site);
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
        if (!site.isDirector(player.getUniqueId())) {
            player.closeInventory();
            return;
        }
        int slot = event.getRawSlot();
        if (slot == CampWoolPicker.SLOT_BACK) {
            openBoard(player, site);
            return;
        }
        DyeColor color = CampWoolPicker.colorAt(slot);
        if (color == null) {
            return;
        }
        establish.applyCampWool(player, site, color, picker.role());
        openBoard(player, site);
    }

    /**
     * @param event drag
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBoardDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof CampBoard
                || event.getInventory().getHolder() instanceof CampWoolPicker
                || event.getInventory().getHolder() instanceof CampStaffBoard) {
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
        if (inviteForSite.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            plugin.getServer().getScheduler().runTask(plugin, () -> handleInviteChat(player, raw));
            return;
        }
        if (!establish.isRenaming(player)) {
            return;
        }
        event.setCancelled(true);
        plugin.getServer().getScheduler().runTask(plugin, () -> establish.handleRenameChat(player, raw));
    }

    /**
     * Adds a known player to the excavation roster from chat.
     *
     * @param player director
     * @param raw typed name
     */
    private void handleInviteChat(Player player, String raw) {
        UUID siteId = inviteForSite.remove(player.getUniqueId());
        if (siteId == null) {
            return;
        }
        if (raw.equalsIgnoreCase("cancel")) {
            player.sendMessage("Add worker cancelled.");
            return;
        }
        Site site = sites.findById(siteId).orElse(null);
        if (site == null || !site.isCampLocked() || !site.isDirector(player.getUniqueId())) {
            player.sendMessage("That excavation is no longer yours to staff.");
            return;
        }
        OfflinePlayer target = CampNames.known(raw);
        if (target == null || target.getUniqueId() == null) {
            player.sendMessage("No player with that name has joined this server.");
            inviteForSite.put(player.getUniqueId(), siteId);
            return;
        }
        if (site.mayWork(target.getUniqueId())) {
            player.sendMessage(CampNames.of(player, target.getUniqueId()) + " can already excavate here.");
            new CampStaffBoard(site.getId(), true).open(player, site);
            return;
        }
        if (!site.grantExcavator(target.getUniqueId())) {
            player.sendMessage("Could not add that player.");
            return;
        }
        sites.save(site);
        String added = CampNames.of(player, target.getUniqueId());
        player.sendMessage("Added " + added + " to the excavation staff.");
        Player online = player.getServer().getPlayer(target.getUniqueId());
        if (online != null) {
            online.sendMessage("You may now excavate " + site.displayLabel() + ".");
        }
        new CampStaffBoard(site.getId(), true).open(player, site);
    }

    /**
     * @param player viewer
     * @param site excavation
     */
    private void openBoard(Player player, Site site) {
        if (player.getWorld() != null) {
            handPick.ensureJornada(site, player.getWorld());
        }
        boolean director = site.isDirector(player.getUniqueId());
        new CampBoard(site.getId(), director, catalogs).open(player, site);
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
