package com.nowko.archeology.establish;

import com.nowko.archeology.item.BrushItem;
import com.nowko.archeology.item.EstablishItem;
import com.nowko.archeology.config.CatalogRegistry;
import com.nowko.archeology.config.ArtifactTemplate;
import com.nowko.archeology.item.RecoveredFindItem;
import com.nowko.archeology.excavation.HandPickService;
import com.nowko.archeology.excavation.PrismOutlineService;
import com.nowko.archeology.model.BuriedFind;
import com.nowko.archeology.model.FindInterpretation;
import com.nowko.archeology.model.FindState;
import com.nowko.archeology.model.Site;
import com.nowko.archeology.model.SiteRole;
import com.nowko.archeology.model.SiteStatus;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Locks camp blocks, opens the excavation board from the sign, and finishes board actions
 * including the finds register, study, interpretation, and the director's report book.
 */
public class CampListener implements Listener {
    private final JavaPlugin plugin;
    private final SiteRepository sites;
    private final CatalogRegistry catalogs;
    private final EstablishItem establishItem;
    private final EstablishService establish;
    private final HandPickService handPick;
    private final PrismOutlineService outline;
    private final BrushItem brush;
    private final RecoveredFindItem recoveredItem;
    private final FindReportBook reportBook;
    private final Map<UUID, UUID> inviteForSite = new ConcurrentHashMap<>();

    /**
     * @param plugin chat prompts must run on the main thread
     * @param sites camp lookup
     * @param catalogs dossier texts and work-day size
     * @param establishItem kit recognition
     * @param establish plant / move / rename
     * @param handPick refreshes the work-day figure on the board
     * @param outline draws the prism when the board asks for limits
     * @param brush study requires the field brush in hand
     * @param recoveredItem matches the piece in inventory and refreshes its lore
     */
    public CampListener(
            JavaPlugin plugin,
            SiteRepository sites,
            CatalogRegistry catalogs,
            EstablishItem establishItem,
            EstablishService establish,
            HandPickService handPick,
            PrismOutlineService outline,
            BrushItem brush,
            RecoveredFindItem recoveredItem
    ) {
        this.plugin = plugin;
        this.sites = sites;
        this.catalogs = catalogs;
        this.establishItem = establishItem;
        this.establish = establish;
        this.handPick = handPick;
        this.outline = outline;
        this.brush = brush;
        this.recoveredItem = recoveredItem;
        this.reportBook = new FindReportBook(plugin);
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
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
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
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
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
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
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
        if (slot == CampBoard.SLOT_DOCUMENTATION) {
            openFinds(player, site);
            return;
        }
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
        UUID member = staff.playerAt(slot);
        if (member != null) {
            new CampWorkerBoard(site.getId(), member, director).open(player, site);
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
        }
    }

    /**
     * Handles one staff file: role changes and the dismissal, both director-only.
     *
     * @param event inventory click
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWorkerClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof CampWorkerBoard file)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        Site site = sites.findById(file.siteId()).orElse(null);
        if (site == null || !site.isCampLocked()) {
            player.closeInventory();
            return;
        }
        int slot = event.getRawSlot();
        if (slot == CampWorkerBoard.SLOT_BACK) {
            new CampStaffBoard(site.getId(), site.isDirector(player.getUniqueId())).open(player, site);
            return;
        }
        if (!site.isDirector(player.getUniqueId())) {
            return;
        }
        UUID member = file.member();
        if (slot == CampWorkerBoard.SLOT_REMOVE) {
            dismissWorker(player, site, member);
            return;
        }
        SiteRole role = CampWorkerBoard.roleAt(slot);
        if (role == null) {
            return;
        }
        assignRole(player, site, member, role);
    }

    /**
     * Opens the finds register, a fiche, or issues the closing report.
     *
     * @param event inventory click
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFindsClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof CampFindsBoard board)) {
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
        if (slot == CampFindsBoard.SLOT_BACK) {
            openBoard(player, site);
            return;
        }
        if (slot == CampFindsBoard.SLOT_REPORT) {
            issueReport(player, site);
            return;
        }
        UUID findId = board.findAt(slot);
        if (findId != null) {
            new CampFindBoard(site.getId(), findId, catalogs).open(player, site);
        }
    }

    /**
     * Identify action on one find's fiche: opens the classification station.
     *
     * @param event inventory click
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFindFileClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof CampFindBoard file)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        Site site = sites.findById(file.siteId()).orElse(null);
        if (site == null || !site.isCampLocked()) {
            player.closeInventory();
            return;
        }
        int slot = event.getRawSlot();
        if (slot == CampFindBoard.SLOT_BACK) {
            openFinds(player, site);
            return;
        }
        if (slot == CampFindBoard.SLOT_IDENTIFY_ACTION) {
            openIdentify(player, site, file.findId());
        }
    }

    /**
     * Signs one of the three station offers, then asks the next empty question.
     *
     * @param event inventory click
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIdentifyClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof CampIdentifyBoard board)) {
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
        if (slot == CampIdentifyBoard.SLOT_BACK) {
            new CampFindBoard(site.getId(), board.findId(), catalogs).open(player, site);
            return;
        }
        String optionId = board.offerAt(slot);
        if (optionId != null) {
            fileStationReading(player, site, board, optionId);
        }
    }

    /**
     * @param player viewer
     * @param site excavation
     */
    private void openFinds(Player player, Site site) {
        if (site.assignMissingFindNumbers()) {
            sites.save(site);
        }
        new CampFindsBoard(site.getId(), site.isDirector(player.getUniqueId()), catalogs).open(player, site);
    }

    /**
     * Opens the classification station. The piece must be in inventory.
     *
     * @param player cataloguer
     * @param site excavation
     * @param findId archive row
     */
    private void openIdentify(Player player, Site site, UUID findId) {
        if (!site.mayCatalog(player.getUniqueId())) {
            player.sendMessage("You are not authorised to write this record.");
            return;
        }
        BuriedFind find = site.findById(findId).orElse(null);
        if (find == null || find.getState() != FindState.RECOVERED) {
            player.sendMessage("That find cannot be identified.");
            return;
        }
        if (!recoveredItem.isCarrying(player, findId)) {
            player.sendMessage("Bring the piece to the station.");
            return;
        }
        new CampIdentifyBoard(site.getId(), findId, catalogs).open(player, site);
    }

    /**
     * @param player cataloguer
     * @param site excavation
     * @param board station showing one question
     * @param optionId phrase chosen among the three
     */
    private void fileStationReading(Player player, Site site, CampIdentifyBoard board, String optionId) {
        if (!site.mayCatalog(player.getUniqueId())) {
            player.sendMessage("You are not authorised to write this record.");
            return;
        }
        if (board.type() == null) {
            return;
        }
        BuriedFind find = site.findById(board.findId()).orElse(null);
        if (find == null || find.getState() != FindState.RECOVERED) {
            player.sendMessage("That find cannot be identified.");
            return;
        }
        if (!recoveredItem.isCarrying(player, find.getId())) {
            player.sendMessage("Bring the piece to the station.");
            return;
        }
        if (!find.addInterpretation(new FindInterpretation(
                board.type().id(),
                optionId,
                player.getUniqueId(),
                Instant.now()))) {
            player.sendMessage("That question already has an answer.");
            new CampFindBoard(site.getId(), find.getId(), catalogs).open(player, site);
            return;
        }
        ArtifactTemplate template = catalogs.artifact(find.getArtifactId());
        String grade = catalogs.pick().conservation().gradeLabel(find.getConservation());
        if (template != null) {
            recoveredItem.refreshCarried(player, site, find, template, grade, catalogs);
        }
        sites.save(site);
        if (catalogs.nextOpenType(find) == null) {
            player.sendMessage("Filed the last reading on "
                    + (find.publicNumber(site.getSerial()) == null ? "the find" : find.publicNumber(site.getSerial()))
                    + ".");
            new CampFindBoard(site.getId(), find.getId(), catalogs).open(player, site);
            return;
        }
        new CampIdentifyBoard(site.getId(), find.getId(), catalogs).open(player, site);
    }

    /**
     * @param player director
     * @param site exhausted excavation
     */
    private void issueReport(Player player, Site site) {
        if (!site.isDirector(player.getUniqueId())) {
            player.sendMessage("Only the director may issue the report.");
            return;
        }
        if (site.getStatus() != SiteStatus.EXHAUSTED) {
            player.sendMessage("The report is issued when the cut is closed.");
            return;
        }
        player.closeInventory();
        ItemStack book = reportBook.create(player, site, catalogs);
        var overflow = player.getInventory().addItem(book);
        for (ItemStack extra : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), extra);
        }
        player.sendMessage("Issued a signed report for " + site.displayLabel() + ".");
    }

    /**
     * Takes a worker off the roster from their own file.
     *
     * @param player director
     * @param site excavation
     * @param member worker being dismissed
     */
    private void dismissWorker(Player player, Site site, UUID member) {
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
     * Moves a worker to another role and tells them what changed, since a role decides which tool
     * they may still pick up.
     *
     * @param player director
     * @param site excavation
     * @param member worker being reassigned
     * @param role new standing
     */
    private void assignRole(Player player, Site site, UUID member, SiteRole role) {
        if (!site.assignRole(member, role)) {
            new CampWorkerBoard(site.getId(), member, true).open(player, site);
            return;
        }
        sites.save(site);
        player.sendMessage(CampNames.of(player, member) + " is now "
                + role.displayName() + " on " + site.displayLabel() + ".");
        Player online = player.getServer().getPlayer(member);
        if (online != null) {
            online.sendMessage("You are now " + role.displayName() + " on " + site.displayLabel()
                    + ". " + role.duty());
        }
        new CampWorkerBoard(site.getId(), member, true).open(player, site);
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
                || event.getInventory().getHolder() instanceof CampStaffBoard
                || event.getInventory().getHolder() instanceof CampWorkerBoard
                || event.getInventory().getHolder() instanceof CampFindsBoard
                || event.getInventory().getHolder() instanceof CampFindBoard
                || event.getInventory().getHolder() instanceof CampIdentifyBoard) {
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
        if (site.isDirector(target.getUniqueId()) || site.getExcavators().contains(target.getUniqueId())) {
            player.sendMessage(CampNames.of(player, target.getUniqueId()) + " is already on the staff.");
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
