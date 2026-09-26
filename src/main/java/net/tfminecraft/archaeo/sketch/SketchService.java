package net.tfminecraft.archaeo.sketch;

import net.tfminecraft.archaeo.config.ArtifactTemplate;
import net.tfminecraft.archaeo.config.CatalogRegistry;
import net.tfminecraft.archaeo.config.SketchSettings;
import net.tfminecraft.archaeo.establish.CampIdentifyBoard;
import net.tfminecraft.archaeo.item.ItemMatcher;
import net.tfminecraft.archaeo.item.ItemRef;
import net.tfminecraft.archaeo.item.RecoveredFindItem;
import net.tfminecraft.archaeo.item.SketchSupplies;
import net.tfminecraft.archaeo.model.BuriedFind;
import net.tfminecraft.archaeo.model.Site;
import net.tfminecraft.archaeo.site.SiteRepository;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Input;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Field sketch: a configured sheet plus pencil become a {@code FILLED_MAP} marked by this plugin.
 * Hold an unsigned sheet to edit; switch it away to save; sign, then register the drawing at the cabinet
 * with the recovered piece in hand.
 */
public class SketchService {
    private static final long TICK_PERIOD = 1L;
    /** How often changed open sheets are checkpointed to disk (~2 s). */
    private static final int AUTOSAVE_PERIOD_TICKS = 40;

    private final JavaPlugin plugin;
    private final SketchSupplies supplies;
    private final RecoveredFindItem recovered;
    private final SiteRepository sites;
    private final CatalogRegistry catalogs;
    private SketchSettings settings;
    private ItemMatcher matcher = ItemMatcher.vanillaOnly();
    private final CabinetLab lab;
    private final SketchAutosaveStore autosaves;
    private final NamespacedKey markerKey;
    private final NamespacedKey cellsKey;
    private final NamespacedKey signedKey;
    private final NamespacedKey authorKey;
    private final NamespacedKey mapIdKey;
    private final NamespacedKey revisionKey;
    private final NamespacedKey findIdKey;
    private final NamespacedKey siteIdKey;
    private final NamespacedKey titleKey;
    private final NamespacedKey labelKey;
    private final NamespacedKey freezeKey;
    private final Map<UUID, SketchSession> sessions = new HashMap<>();
    private final Map<Integer, SketchSheet> sheets = new HashMap<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private BukkitTask task;
    private int autosaveClock;

    /**
     * @param plugin scheduler and PDC owner
     * @param supplies configured sheet and pencil
     * @param recovered recovered-find tags, for filing a signed sketch
     * @param sites excavation archive
     * @param catalogs artifact names and conservation grades
     * @param settings pencil wear, cabinet block, and lab wipe
     */
    public SketchService(
            JavaPlugin plugin,
            SketchSupplies supplies,
            RecoveredFindItem recovered,
            SiteRepository sites,
            CatalogRegistry catalogs,
            SketchSettings settings
    ) {
        this.plugin = plugin;
        this.supplies = supplies;
        this.recovered = recovered;
        this.sites = sites;
        this.catalogs = catalogs;
        this.settings = settings == null ? SketchSettings.defaults() : settings;
        this.lab = new CabinetLab(plugin, sites, catalogs, recovered, this.settings);
        this.autosaves = new SketchAutosaveStore(plugin.getDataFolder().toPath());
        this.markerKey = new NamespacedKey(plugin, "sketch_proto");
        this.cellsKey = new NamespacedKey(plugin, "sketch_cells");
        this.signedKey = new NamespacedKey(plugin, "sketch_signed");
        this.authorKey = new NamespacedKey(plugin, "sketch_author");
        this.mapIdKey = new NamespacedKey(plugin, "sketch_map_id");
        this.revisionKey = new NamespacedKey(plugin, "sketch_revision");
        this.findIdKey = new NamespacedKey(plugin, "sketch_find_id");
        this.siteIdKey = new NamespacedKey(plugin, "sketch_site_id");
        this.titleKey = new NamespacedKey(plugin, "sketch_title");
        this.labelKey = new NamespacedKey(plugin, "sketch_find_label");
        this.freezeKey = new NamespacedKey(plugin, "sketch_freeze");
    }

    /**
     * @param settings pencil wear, cabinet block, and lab wipe after reload; the catalogue never returns {@code null}
     */
    public void setSettings(SketchSettings settings) {
        this.settings = settings;
        this.lab.setSettings(settings);
    }

    /**
     * @param matcher ItemsAdder furniture lookup; the plugin already substitutes vanilla-only for {@code null}
     */
    public void setMatcher(ItemMatcher matcher) {
        this.matcher = matcher;
    }

    /**
     * Starts the input loop, rebinds maps already in the world, and re-enters anyone holding an unsigned sheet.
     */
    public void start() {
        stop();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, TICK_PERIOD, TICK_PERIOD);
        for (World world : Bukkit.getWorlds()) {
            for (ItemFrame frame : world.getEntitiesByClass(ItemFrame.class)) {
                hydrate(frame.getItem());
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (ItemStack stack : player.getInventory().getContents()) {
                hydrate(stack);
            }
            hydrate(player.getInventory().getItemInOffHand());
            unstackCarried(player);
            supplies.stampAll(player.getInventory().getContents());
            supplies.stampInstructions(player.getInventory().getItemInOffHand());
            syncHand(player);
        }
    }

    /**
     * Ends every session (restores walk), closes lab wipes, and stops the input loop.
     */
    public void stop() {
        lab.stop();
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (UUID playerId : Map.copyOf(sessions).keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                leave(player, false, findSaveTarget(player, sessions.get(playerId)));
            } else {
                sessions.remove(playerId);
            }
        }
        for (BossBar bar : bars.values()) {
            bar.removeAll();
        }
        bars.clear();
    }

    /**
     * Staff command: new blank sheet, or a reminder if already holding one.
     *
     * @param player staff tester
     */
    public void begin(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (editing(player)) {
            player.sendMessage(ChatColor.GRAY + "Switch the map away to save. Left-click, then type sign to lock it.");
            return;
        }
        if (isSketchMap(hand)) {
            if (isSigned(hand)) {
                player.sendMessage(ChatColor.GRAY + "This sketch is signed. It cannot be edited.");
                hydrate(hand);
                return;
            }
            enter(player, hand);
            return;
        }
        enter(player, createBlank(player));
    }

    /**
     * Turns one sheet into an unsigned sketch when the other hand holds the pencil.
     *
     * @param player holder
     * @return whether a map was created
     */
    public boolean tryStartFromHands(Player player) {
        if (editing(player)) {
            return false;
        }
        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();
        if (supplies.isPaper(main) && supplies.isPencil(off)) {
            return replacePaperWithMap(player, true);
        }
        if (supplies.isPaper(off) && supplies.isPencil(main)) {
            return replacePaperWithMap(player, false);
        }
        return false;
    }

    /**
     * Turns the sheet into a sketch wherever it sits; the pencil stays and wears one use.
     *
     * @param player clicker
     * @param cursor live cursor stack
     * @param slot live clicked stack
     * @return whether this click crafted a sketch
     */
    public boolean tryCraftOnClick(Player player, ItemStack cursor, ItemStack slot) {
        if (player == null || editing(player)) {
            return false;
        }
        if (supplies.isPaper(cursor) && supplies.isPencil(slot)) {
            if (supplies.isSpent(slot)) {
                player.sendMessage(ChatColor.GOLD + "This pencil is spent.");
                return true;
            }
            supplies.wear(player, slot);
            becomeSketch(player, cursor);
            player.sendMessage(ChatColor.GOLD + "Field sketch ready. Hold it to draw.");
            return true;
        }
        if (supplies.isPencil(cursor) && supplies.isPaper(slot)) {
            if (supplies.isSpent(cursor)) {
                player.sendMessage(ChatColor.GOLD + "This pencil is spent.");
                return true;
            }
            supplies.wear(player, cursor);
            becomeSketch(player, slot);
            player.sendMessage(ChatColor.GOLD + "Field sketch ready. Hold it to draw.");
            return true;
        }
        return false;
    }

    /**
     * Overwrites a field sheet with a new unsigned map. A stack of several sheets loses one
     * and the map is given separately.
     *
     * @param player holder
     * @param paper live sheet stack
     */
    private void becomeSketch(Player player, ItemStack paper) {
        ItemStack map = createUnsigned(player.getWorld());
        if (paper.getAmount() > 1) {
            paper.setAmount(paper.getAmount() - 1);
            giveOrDrop(player, map);
            return;
        }
        paper.setType(map.getType());
        paper.setAmount(1);
        paper.setItemMeta(map.getItemMeta());
    }

    /**
     * @param block clicked block, or {@code null}
     * @return whether this is the configured cabinet
     */
    public boolean isCabinet(Block block) {
        return matcher.matchesPlaced(block, settings.cabinet());
    }

    /**
     * Opens the matching cabinet window for the recovered piece in hand: clean, register
     * a drawing, or take a reading. Sneaking leaves vanilla alone so the table can still
     * be placed against. An empty hand does not open a cabinet window.
     * <p>
     * Bukkit block clicks only. ItemsAdder furniture is opened solely from
     * {@link #tryOpenCabinet(Player, String, Entity, Block, boolean)} via
     * {@code FurnitureInteractEvent}, so the same click does not send cues twice.
     *
     * @param player clicker
     * @param block clicked block
     * @param sneaking whether the player is sneaking
     * @return whether the cabinet handled the click
     */
    public boolean tryOpenCabinet(Player player, Block block, boolean sneaking) {
        if (settings.cabinet().kind() == ItemRef.Kind.ITEMSADDER) {
            return false;
        }
        return tryOpenCabinet(player, null, null, block, sneaking);
    }

    /**
     * ItemsAdder furniture path ({@code FurnitureInteractEvent}). Do not call this from
     * Bukkit entity or block interact handlers: those would double-fire with the furniture event.
     *
     * @param player clicker
     * @param namespacedId ItemsAdder furniture id, or {@code null}
     * @param entity furniture entity, or {@code null}
     * @param block block under the furniture ({@code PackPluginHook} derives it from the entity), or {@code null}
     * @param sneaking whether the player is sneaking
     * @return whether the cabinet handled the click
     */
    public boolean tryOpenCabinet(
            Player player,
            String namespacedId,
            Entity entity,
            Block block,
            boolean sneaking
    ) {
        if (editing(player) || sneaking) {
            return false;
        }
        boolean cabinet = matcher.matchesNamespacedId(namespacedId, settings.cabinet())
                || matcher.matchesEntity(entity, settings.cabinet())
                || matcher.matchesPlaced(block, settings.cabinet());
        if (!cabinet) {
            return false;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!recovered.isRecovered(hand)) {
            player.sendMessage(ChatColor.GOLD + "Bring an artifact.");
            return true;
        }
        UUID findId = recovered.findIdOf(hand);
        UUID siteId = recovered.siteIdOf(hand);
        Site site = siteId == null ? null : sites.findById(siteId).orElse(null);
        BuriedFind find = site == null ? null : site.findById(findId).orElse(null);
        if (find == null) {
            player.sendMessage(ChatColor.GOLD + "This piece is not in the excavation archive.");
            return true;
        }
        if (recordComplete(find)) {
            player.sendMessage(ChatColor.GOLD + "The record on this piece is complete.");
            return true;
        }
        if (lab.tryStart(player, block, site, find)) {
            return true;
        }
        if (!find.hasFieldSketch()) {
            new SketchCabinet().open(
                    player,
                    site,
                    find,
                    catalogs.artifact(find.getArtifactId()),
                    recovered,
                    catalogs);
            return true;
        }
        beginCabinetReading(player, null, site, find);
        return true;
    }

    /**
     * @param find archive row
     * @return whether clean, drawing, and every station question are already on file
     */
    private boolean recordComplete(BuriedFind find) {
        return find.isLabCleaned()
                && find.hasFieldSketch()
                && catalogs.nextOpenType(find) == null;
    }

    /**
     * Parks an unfinished cabinet clean if the player leaves or the plugin stops.
     *
     * @param player holder
     */
    public void cancelLab(Player player) {
        lab.cancel(player);
    }

    /**
     * @param stack candidate
     * @return whether this stack was spawned by the lab wipe window
     */
    boolean isLabItem(ItemStack stack) {
        return lab.isLabItem(stack);
    }

    /**
     * Click in the lab wipe window.
     *
     * @param player worker
     * @param board open window
     * @param slot clicked top slot
     * @param cursor item on the cursor
     * @return cursor after the click
     */
    ItemStack handleLabClick(Player player, CabinetLabBoard board, int slot, ItemStack cursor) {
        return lab.handleClick(player, board, slot, cursor);
    }

    /**
     * Drops fake lab stacks when the wipe window closes.
     *
     * @param player worker
     * @param board window that closed
     */
    void handleLabClose(Player player, CabinetLabBoard board) {
        lab.handleClose(player, board);
    }

    /**
     * @param top furnace cabinet
     * @param slot clicked furnace index
     * @param cursor item on the cursor (mutated)
     * @return cursor after the click
     */
    public ItemStack handleCabinetClick(Player player, Inventory top, int slot, ItemStack cursor) {
        if (slot == SketchCabinet.SLOT_REGISTER) {
            tryRegisterCabinet(player, top);
            return cursor;
        }
        if (slot != SketchCabinet.SLOT_SKETCH) {
            return cursor;
        }
        ItemStack there = top.getItem(slot);
        if (isEmpty(cursor)) {
            if (!isEmpty(there)) {
                top.setItem(slot, null);
                return there;
            }
            return cursor;
        }
        if (!acceptFinishedDrawing(player, cursor)) {
            return cursor;
        }
        top.setItem(slot, cursor);
        return isEmpty(there) ? new ItemStack(Material.AIR) : there;
    }

    /**
     * Shift-click a finished drawing from the bag into the empty top slot.
     *
     * @param player cataloguer
     * @param top furnace cabinet
     * @param stack clicked bag stack (mutated)
     * @return whether the stack moved
     */
    public boolean tryDepositCabinet(Player player, Inventory top, ItemStack stack) {
        if (isEmpty(stack) || !isEmpty(top.getItem(SketchCabinet.SLOT_SKETCH))) {
            return false;
        }
        if (!acceptFinishedDrawing(player, stack)) {
            return false;
        }
        top.setItem(SketchCabinet.SLOT_SKETCH, stack.clone());
        consumeAll(stack);
        return true;
    }

    /**
     * Files a signed drawing for the piece in hand, then closes like a finished lab wipe.
     *
     * @param player cataloguer
     * @param top furnace cabinet
     */
    public void tryRegisterCabinet(Player player, Inventory top) {
        SketchCabinet cabinet = (SketchCabinet) top.getHolder();
        Site site = sites.findById(cabinet.siteId()).orElse(null);
        BuriedFind find = site == null ? null : site.findById(cabinet.findId()).orElse(null);
        if (find == null) {
            refuseRegister(player, "This piece is not in the excavation archive.");
            return;
        }
        if (!recovered.isInMainHand(player, find.getId())) {
            refuseRegister(player, "Keep the artifact in your hand.");
            return;
        }
        if (!find.isLabCleaned()) {
            refuseRegister(player, "Clean the piece at the cabinet first.");
            player.sendMessage(ChatColor.GRAY + "Use the cabinet with this in hand. (1/3)");
            return;
        }
        if (find.hasFieldSketch()) {
            beginCabinetReading(player, top, site, find);
            return;
        }
        // The top slot only ever accepts inked, signed drawings (see acceptFinishedDrawing).
        ItemStack sketch = top.getItem(SketchCabinet.SLOT_SKETCH);
        if (!isSigned(sketch)) {
            refuseRegister(player, "Place the drawing in the top slot.");
            return;
        }
        UUID bound = boundFindId(sketch);
        if (bound != null && !bound.equals(find.getId())) {
            refuseRegister(player, "This drawing already records a different find.");
            return;
        }
        bindSketch(sketch, find, site);
        top.setItem(SketchCabinet.SLOT_SKETCH, sketch);
        find.setFieldSketch(true);
        sites.save(site);
        ArtifactTemplate template = catalogs.artifact(find.getArtifactId());
        String grade = catalogs.pick().conservation().gradeLabel(find.getConservation());
        recovered.refreshCarried(player, site, find, template, grade, catalogs);
        player.sendMessage(ChatColor.GOLD + "Drawing registered for "
                + recovered.labelOf(player.getInventory().getItemInMainHand()) + ". (2/3)");
        player.sendMessage(ChatColor.GRAY + "Use the cabinet with this in hand for a reading. (3/3)");
        handBackCabinet(player, top);
        CabinetCues.complete(plugin, player);
    }

    /**
     * Returns the drawing if the furnace still holds it, then opens the three-offer reading.
     *
     * @param player cataloguer
     * @param top furnace cabinet, or {@code null} when opening from a click with the piece in hand
     * @param site excavation
     * @param find archive row
     */
    private void beginCabinetReading(Player player, Inventory top, Site site, BuriedFind find) {
        if (catalogs.nextOpenType(find) == null) {
            player.sendMessage(ChatColor.GOLD + "The record on this piece is complete.");
            return;
        }
        if (!site.mayCatalog(player.getUniqueId())) {
            player.sendMessage(ChatColor.GOLD + "You are not authorised to write this record.");
            return;
        }
        if (top != null) {
            handBackCabinet(player, top);
        }
        UUID siteId = site.getId();
        UUID findId = find.getId();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            Site live = sites.findById(siteId).orElse(null);
            if (live == null) {
                return;
            }
            new CampIdentifyBoard(siteId, findId, catalogs, recovered, true).open(player, live);
        });
    }

    /**
     * Chat plus the villager refusal so a failed place or Register click is obvious.
     *
     * @param player cataloguer
     * @param message gold line
     */
    private void refuseRegister(Player player, String message) {
        player.sendMessage(ChatColor.GOLD + message);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.PLAYERS, 0.8f, 1f);
    }

    /**
     * Only a signed field sketch with enough ink may occupy the top slot.
     *
     * @param player cataloguer
     * @param stack candidate
     * @return whether the stack may go in
     */
    private boolean acceptFinishedDrawing(Player player, ItemStack stack) {
        if (!isSigned(stack)) {
            refuseRegister(player, "That cannot be registered.");
            return false;
        }
        if (!sheetOf(stack).hasInk()) {
            refuseRegister(player, "That drawing is still blank.");
            return false;
        }
        return true;
    }

    /**
     * Gives back the drawing only. The fuel slot is a stand-in and must not be duplicated.
     *
     * @param player receiver
     * @param top furnace cabinet
     */
    private void handBackCabinet(Player player, Inventory top) {
        ItemStack sketch = top.getItem(SketchCabinet.SLOT_SKETCH);
        top.setItem(SketchCabinet.SLOT_SKETCH, null);
        if (!isEmpty(sketch)) {
            giveOrDrop(player, sketch);
        }
    }

    /**
     * Unstacks sketch maps and restores how-to lore on Archaeo-given vanilla kits only.
     *
     * @param player holder
     */
    public void stampKitsLater(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            unstackCarried(player);
            supplies.stampAll(player.getInventory().getContents());
            supplies.stampInstructions(player.getInventory().getItemInOffHand());
            supplies.stampInstructions(player.getItemOnCursor());
        });
    }

    /**
     * Writes cursor and slot after converting a sheet, then unstacks maps and restamps given kits.
     *
     * @param player clicker
     * @param inventory clicked bag
     * @param slotIndex clicked slot
     * @param cursor converted cursor stack
     * @param slot converted slot stack
     */
    public void afterBagCraft(
            Player player,
            Inventory inventory,
            int slotIndex,
            ItemStack cursor,
            ItemStack slot
    ) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            player.getOpenInventory().setCursor(isEmpty(cursor) ? null : cursor);
            inventory.setItem(slotIndex, isEmpty(slot) ? null : slot);
            player.updateInventory();
            unstackCarried(player);
            supplies.stampAll(player.getInventory().getContents());
            supplies.stampInstructions(player.getInventory().getItemInOffHand());
            supplies.stampInstructions(player.getItemOnCursor());
            syncHand(player);
        });
    }

    /**
     * Opens the editor for an unsigned map already in (or just moved to) the main hand.
     *
     * @param player holder
     * @param stack unsigned sketch
     */
    public void enter(Player player, ItemStack stack) {
        if (!isSketchMap(stack) || isSigned(stack) || editing(player)) {
            return;
        }
        hydrate(stack);
        unstackSketch(player, stack);
        MapView view = mapView(stack);
        if (view == null) {
            return;
        }
        sessions.put(player.getUniqueId(), new SketchSession(view, sheets.get(view.getId())));
        freeze(player);
        player.sendMessage(ChatColor.GOLD + "Editing field sketch.");
        player.sendMessage(ChatColor.WHITE + "Sneak paints. Right-click erases. Space changes ink.");
        player.sendMessage(ChatColor.WHITE + "Switch the item away to save. Left-click, then type sign to lock.");
    }

    /**
     * Writes the sheet onto {@code stack} when possible, then thaws. Callers only leave an open
     * session: the listener checks {@link #editing(Player)} and the service holds the session.
     *
     * @param player editor
     * @param announce whether this leave was a player action (hotbar, inventory, drop) and should tell them
     * @param stack item that left the hand, or {@code null} to search the inventory
     */
    public void leave(Player player, boolean announce, ItemStack stack) {
        SketchSession session = sessions.remove(player.getUniqueId());
        thaw(player);
        hideHud(player);
        ItemStack target = matchesView(stack, session.view().getId())
                ? stack
                : findSaveTarget(player, session);
        if (target == null) {
            checkpoint(session);
            return;
        }
        // Save targets are always sketch maps, so the drawing is now on the item.
        writeItem(target, session.sheet(), isSigned(target), authorOf(target));
        deleteAutosave(session.view().getId());
        if (announce) {
            player.sendMessage(ChatColor.GRAY + "Sketch saved.");
        }
    }

    /**
     * @param player editor
     * @param announce whether this leave was a player action and should tell them
     */
    public void leave(Player player, boolean announce) {
        leave(player, announce, findSaveTarget(player, sessions.get(player.getUniqueId())));
    }

    /**
     * Prefers a matching stack among {@code extras} (death drops, a thrown item) before searching the inventory.
     *
     * @param player editor
     * @param announce whether this leave was a player action and should tell them
     * @param extras stacks that just left the inventory
     */
    public void leave(Player player, boolean announce, Iterable<ItemStack> extras) {
        int viewId = sessions.get(player.getUniqueId()).view().getId();
        ItemStack target = null;
        for (ItemStack stack : extras) {
            if (matchesView(stack, viewId)) {
                target = stack;
                break;
            }
        }
        leave(player, announce, target);
    }

    /**
     * Enters or leaves according to the stack that is (or will be) in the main hand.
     *
     * @param player holder
     * @param hand main-hand stack after the change; {@code null} means empty
     */
    public void syncHand(Player player, ItemStack hand) {
        SketchSession session = sessions.get(player.getUniqueId());
        if (session != null) {
            if (!matchesView(hand, session.view().getId())) {
                leave(player, true);
            } else {
                return;
            }
        }
        if (isSketchMap(hand) && !isSigned(hand)) {
            enter(player, hand);
        } else if (isSketchMap(hand)) {
            hydrate(hand);
        }
    }

    /**
     * Enters or leaves according to whatever is in the main hand now.
     *
     * @param player holder
     */
    public void syncHand(Player player) {
        syncHand(player, player.getInventory().getItemInMainHand());
    }

    /**
     * After a tick, so inventory clicks have finished moving the stack.
     *
     * @param player holder
     * @param extras stacks involved in the click (cursor, clicked slot, hotbar swap)
     */
    public void syncHandLater(Player player, ItemStack... extras) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            SketchSession session = sessions.get(player.getUniqueId());
            if (session != null && !holdingThisSketch(player, session)) {
                leave(player, true, java.util.Arrays.asList(extras));
            }
            syncHand(player);
        });
    }

    /**
     * Chat confirm for signing runs on the main thread.
     *
     * @param player editor
     * @param raw chat line
     */
    public void handleSignChatLater(Player player, String raw) {
        plugin.getServer().getScheduler().runTask(plugin, () -> handleSignChat(player, raw));
    }

    /**
     * Asks for a chat confirm before locking the sheet. A blank page is refused here
     * so the player keeps drawing instead of typing {@code sign} for nothing.
     *
     * @param player editor
     */
    public void askToSign(Player player) {
        SketchSession session = sessions.get(player.getUniqueId());
        if (!session.sheet().hasInk()) {
            refuseBlankSign(player);
            return;
        }
        session.setAwaitingSign(true);
        player.sendMessage(ChatColor.GOLD + "Lock this sketch forever?");
        player.sendMessage(ChatColor.WHITE + "Type sign to finish, or cancel to keep editing.");
    }

    /**
     * @param player editor who just typed
     * @param raw chat line
     * @return whether this line was the sign prompt
     */
    public boolean handleSignChat(Player player, String raw) {
        SketchSession session = sessions.get(player.getUniqueId());
        if (session == null || !session.awaitingSign()) {
            return false;
        }
        if (raw.equalsIgnoreCase("cancel")) {
            session.setAwaitingSign(false);
            player.sendMessage(ChatColor.GRAY + "Still editing.");
            return true;
        }
        if (!raw.equalsIgnoreCase("sign")) {
            player.sendMessage(ChatColor.WHITE + "Type sign to finish, or cancel to keep editing.");
            return true;
        }
        if (!session.sheet().hasInk()) {
            session.setAwaitingSign(false);
            refuseBlankSign(player);
            return true;
        }
        // The chat task can run before the hand sync, so lock the session's own stack, not the hand.
        ItemStack sketch = findSaveTarget(player, session);
        if (sketch == null) {
            leave(player, false, (ItemStack) null);
            player.sendMessage(ChatColor.GOLD + "The sketch is no longer with you. Nothing was signed.");
            return true;
        }
        writeItem(sketch, session.sheet(), true, player.getName());
        leave(player, false, sketch);
        player.sendMessage(ChatColor.GOLD + "Sketch signed. It can no longer be edited.");
        return true;
    }

    /**
     * Tells the editor the page still has no marks.
     *
     * @param player editor
     */
    private void refuseBlankSign(Player player) {
        player.sendMessage(ChatColor.GOLD + "The sheet is still blank. Draw something first.");
        player.sendMessage(ChatColor.GRAY + "Sneak paints. Right-click erases.");
    }

    /**
     * @param player possible editor
     * @return whether they are frozen in the prototype
     */
    public boolean editing(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    /**
     * @param stack sketch map
     * @return excavation id stamped when the drawing was registered, or {@code null}
     */
    public UUID siteIdOf(ItemStack stack) {
        if (!isSketchMap(stack)) {
            return null;
        }
        String raw = stack.getItemMeta().getPersistentDataContainer()
                .get(siteIdKey, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /**
     * @param player editor
     * @return open session, or {@code null}
     */
    SketchSession session(Player player) {
        return sessions.get(player.getUniqueId());
    }

    /**
     * @param view map the renderer is painting
     * @return sheet for that view, or {@code null} if this plugin did not create it
     */
    SketchSheet sheetOf(MapView view) {
        return sheets.get(view.getId());
    }

    /**
     * Prefers the live editor buffer so a map still in hand is judged as drawn, then the item PDC.
     *
     * @param stack sketch map
     * @return cells for that item; never {@code null}
     */
    private SketchSheet sheetOf(ItemStack stack) {
        hydrate(stack);
        return storedSheet(stack);
    }

    /**
     * Same lookup without hydrating: a sketch in a chest or ender chest has no live buffer
     * after a restart, so its own stored pixels must be kept rather than a blank page.
     *
     * @param stack sketch map
     * @return live cells for its view, else the cells stored on the item; never {@code null}
     */
    private SketchSheet storedSheet(ItemStack stack) {
        MapView view = mapView(stack);
        SketchSheet live = view == null ? null : sheets.get(view.getId());
        return live != null ? live : SketchSheet.fromBytes(cellsOf(stack));
    }

    /**
     * Clears the cursor cell. Used by right-click / interact; the listener only calls this after
     * {@link #editing(Player)}, so the session exists.
     *
     * @param player editor
     */
    public void erase(Player player) {
        sessions.get(player.getUniqueId()).beginEraseStroke();
    }

    /**
     * @param stack item in a hand
     * @return whether this is a prototype sketch map
     */
    public boolean isSketchMap(ItemStack stack) {
        if (stack == null || stack.getType() != Material.FILLED_MAP) {
            return false;
        }
        return stack.getItemMeta().getPersistentDataContainer().has(markerKey, PersistentDataType.BYTE);
    }

    /**
     * Reads WASD as cursor steps and sneak as a continuous stroke. Also opens the editor
     * for anyone who is holding an unsigned sheet, so a missed hotbar event cannot skip enter.
     * Open sheets are written back onto the map item on a short interval so a crash does not
     * wipe strokes since the last leave.
     */
    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!sessions.containsKey(player.getUniqueId())) {
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (isSketchMap(hand) && !isSigned(hand)) {
                    enter(player, hand);
                } else if (isSketchMap(hand)) {
                    hydrate(hand);
                }
            }
        }
        for (Map.Entry<UUID, SketchSession> entry : Map.copyOf(sessions).entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                sessions.remove(entry.getKey());
                continue;
            }
            SketchSession session = entry.getValue();
            if (!holdingThisSketch(player, session)) {
                leave(player, true);
                continue;
            }
            applyInput(player, session);
            sendHud(player, session);
        }
        autosaveClock += (int) TICK_PERIOD;
        if (autosaveClock >= AUTOSAVE_PERIOD_TICKS) {
            autosaveClock = 0;
            autosaveOpenSessions();
        }
    }

    /**
     * Saves changed sheets to disk without replacing the map item in the player's hand.
     * Only {@link #tick()} calls this, after it has already dropped sessions of offline players.
     */
    private void autosaveOpenSessions() {
        for (SketchSession session : sessions.values()) {
            checkpoint(session);
        }
    }

    /**
     * Persists a changed session without updating its held map item.
     *
     * @param session open sketch
     */
    private void checkpoint(SketchSession session) {
        if (!session.needsAutosave()) {
            return;
        }
        try {
            autosaves.save(session.view().getId(), session.sheet());
            session.markAutosaved();
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not autosave field sketch "
                    + session.view().getId() + ": " + exception.getMessage());
        }
    }

    /**
     * @param player editor
     * @param session their map
     */
    private void applyInput(Player player, SketchSession session) {
        Input input = player.getCurrentInput();
        session.moveFromInput(
                (input.isLeft() ? -1 : 0) + (input.isRight() ? 1 : 0),
                (input.isForward() ? -1 : 0) + (input.isBackward() ? 1 : 0));
        session.updateEraseStroke();
        if (input.isJump() && !session.jumpHeld()) {
            session.cycleInk();
        }
        session.setJumpHeld(input.isJump());
        if (input.isSneak()) {
            session.paint();
        }
    }

    /**
     * Same strip as camp placement: a per-player boss bar, so the action bar stays free.
     *
     * @param player editor
     * @param session cursor and ink
     */
    private void sendHud(Player player, SketchSession session) {
        BossBar bar = bars.computeIfAbsent(
                player.getUniqueId(),
                id -> plugin.getServer().createBossBar("", BarColor.WHITE, BarStyle.SOLID));
        if (!bar.getPlayers().contains(player)) {
            bar.addPlayer(player);
        }
        bar.setVisible(true);
        bar.setProgress(1.0);
        bar.setColor(player.getCurrentInput().isSneak() ? BarColor.GREEN : BarColor.WHITE);
        bar.setTitle(ChatColor.WHITE
                + (session.awaitingSign()
                ? "Type sign to lock, or cancel"
                : session.ink().label()
                + "  " + session.cursorX() + "," + session.cursorY()
                + "  ·  sneak paints  ·  right-click erases  ·  space ink  ·  left-click signs"));
    }

    /**
     * @param player editor
     */
    private void hideHud(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar == null) {
            return;
        }
        bar.removePlayer(player);
        bar.removeAll();
    }

    /**
     * @param player tester
     * @param session map they should be holding
     * @return whether the main hand is that sketch
     */
    private boolean holdingThisSketch(Player player, SketchSession session) {
        return matchesView(player.getInventory().getItemInMainHand(), session.view().getId());
    }

    /**
     * Loads pixels from the item and binds our renderer to its map view.
     *
     * @param stack sketch map
     */
    public void hydrate(ItemStack stack) {
        if (!isSketchMap(stack)) {
            return;
        }
        MapView view = mapView(stack);
        if (view == null) {
            return;
        }
        bindRenderer(view);
        attachView(stack, view);
        markUnstackable(stack);
        int id = view.getId();
        if (!sheets.containsKey(id)) {
            long itemRevision = metaRevision(stack);
            SketchAutosaveStore.Snapshot saved = null;
            try {
                saved = autosaves.load(id);
            } catch (IOException exception) {
                plugin.getLogger().warning("Could not load field sketch autosave "
                        + id + ": " + exception.getMessage());
            }
            if (saved != null && saved.revision() > itemRevision) {
                sheets.put(id, SketchSheet.fromBytes(saved.cells(), saved.revision()));
            } else {
                sheets.put(id, SketchSheet.fromBytes(cellsOf(stack), itemRevision));
            }
        }
        retitleFromLive(stack);
    }

    /**
     * Rewrites {@code #name-n} on a registered sketch after the excavation is renamed.
     *
     * @param stack signed sketch, or {@code null}
     * @param site excavation, or {@code null} once staff purged the dossier the sketch names
     * @return whether the label was rewritten
     */
    public boolean retitle(ItemStack stack, Site site) {
        if (site == null || !site.getId().equals(siteIdOf(stack))) {
            return false;
        }
        UUID findId = boundFindId(stack);
        BuriedFind find = findId == null ? null : site.findById(findId).orElse(null);
        if (find == null) {
            return false;
        }
        org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
        // Recovered finds are always numbered (Site#assignMissingFindNumbers), so the label exists.
        String label = find.publicNumber(site);
        String current = meta.getPersistentDataContainer().get(labelKey, PersistentDataType.STRING);
        if (label.equals(current)) {
            return false;
        }
        meta.getPersistentDataContainer().set(labelKey, PersistentDataType.STRING, label);
        stack.setItemMeta(meta);
        writeItem(stack, storedSheet(stack), isSigned(stack), authorOf(stack));
        return true;
    }

    /**
     * Same as {@link #retitle} using the site stamped on the map.
     *
     * @param stack sketch, or {@code null}
     */
    private void retitleFromLive(ItemStack stack) {
        UUID siteId = siteIdOf(stack);
        if (siteId == null) {
            return;
        }
        retitle(stack, sites.findById(siteId).orElse(null));
    }

    /**
     * @param stack sketch or {@code null}
     * @return whether this sheet is locked
     */
    public boolean isSigned(ItemStack stack) {
        if (!isSketchMap(stack)) {
            return false;
        }
        Byte flag = stack.getItemMeta().getPersistentDataContainer().get(signedKey, PersistentDataType.BYTE);
        return flag != null && flag == 1;
    }

    /**
     * @param player tester
     * @return new unsigned map now in the main hand
     */
    private ItemStack createBlank(Player player) {
        ItemStack map = createUnsigned(player.getWorld());
        ItemStack previous = player.getInventory().getItemInMainHand();
        player.getInventory().setItemInMainHand(map);
        if (!previous.getType().isAir()) {
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(previous);
            leftover.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        }
        return player.getInventory().getItemInMainHand();
    }

    /**
     * @param world map owner world
     * @return new unsigned sketch map, not yet in an inventory
     */
    public ItemStack createUnsigned(World world) {
        MapView view = Bukkit.createMap(world);
        bindRenderer(view);
        SketchSheet sheet = new SketchSheet();
        sheets.put(view.getId(), sheet);
        ItemStack map = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) map.getItemMeta();
        meta.setMapView(view);
        map.setItemMeta(meta);
        writeItem(map, sheet, false, null);
        return map;
    }

    /**
     * Splits stacked sketches in the bag so each sheet is its own map.
     *
     * @param player holder
     */
    public void unstackCarried(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (!isSketchMap(stack) || stack.getAmount() <= 1) {
                markUnstackable(stack);
                continue;
            }
            unstackSketch(player, stack);
            player.getInventory().setItem(i, stack);
        }
        unstackSketch(player, player.getInventory().getItemInOffHand());
        unstackSketch(player, player.getItemOnCursor());
    }

    /**
     * Keeps one sheet in {@code stack} and gives the rest as new maps with the same pixels.
     *
     * @param player receiver
     * @param stack stacked sketches, or {@code null}
     */
    private void unstackSketch(Player player, ItemStack stack) {
        if (!isSketchMap(stack)) {
            return;
        }
        markUnstackable(stack);
        if (stack.getAmount() <= 1) {
            return;
        }
        int extra = stack.getAmount() - 1;
        stack.setAmount(1);
        World world = player.getWorld();
        for (int i = 0; i < extra; i++) {
            giveOrDrop(player, duplicateSketch(world, stack));
        }
    }

    /**
     * @param world map owner world
     * @param original sheet whose pixels are copied
     * @return a new map item with its own view
     */
    private ItemStack duplicateSketch(World world, ItemStack original) {
        ItemStack map = createUnsigned(world);
        SketchSheet sheet = SketchSheet.fromBytes(cellsOf(original));
        sheets.put(mapView(map).getId(), sheet);
        writeItem(map, sheet, isSigned(original), authorOf(original));
        return map;
    }

    /**
     * Filled maps with the same view would otherwise stack; each sketch is one sheet.
     *
     * @param stack sketch, or empty
     */
    private static void markUnstackable(ItemStack stack) {
        if (stack == null || !(stack.getItemMeta() instanceof MapMeta meta)) {
            return;
        }
        meta.setMaxStackSize(1);
        stack.setItemMeta(meta);
    }

    /**
     * @param view map to own
     */
    private void bindRenderer(MapView view) {
        view.setLocked(true);
        view.setTrackingPosition(false);
        view.setUnlimitedTracking(false);
        boolean ours = false;
        for (org.bukkit.map.MapRenderer renderer : view.getRenderers()) {
            if (renderer instanceof SketchRenderer sketchRenderer && sketchRenderer.belongsTo(this)) {
                ours = true;
                break;
            }
        }
        if (ours) {
            return;
        }
        for (org.bukkit.map.MapRenderer renderer : view.getRenderers()) {
            view.removeRenderer(renderer);
        }
        view.addRenderer(new SketchRenderer(this));
    }

    /**
     * @param stack sketch map (always a {@code FILLED_MAP})
     * @param sheet pixels
     * @param signed locked
     * @param author signer, or {@code null} on unsigned or legacy drawings
     */
    private void writeItem(ItemStack stack, SketchSheet sheet, boolean signed, String author) {
        MapMeta meta = (MapMeta) stack.getItemMeta();
        MapView view = meta.getMapView();
        if (view == null) {
            view = mapView(stack);
        }
        org.bukkit.persistence.PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String boundFind = pdc.get(findIdKey, PersistentDataType.STRING);
        String boundTitle = pdc.get(titleKey, PersistentDataType.STRING);
        String boundLabel = pdc.get(labelKey, PersistentDataType.STRING);
        pdc.set(markerKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(cellsKey, PersistentDataType.BYTE_ARRAY, sheet.toBytes());
        pdc.set(revisionKey, PersistentDataType.LONG, sheet.revision());
        pdc.set(signedKey, PersistentDataType.BYTE, (byte) (signed ? 1 : 0));
        if (view != null) {
            pdc.set(mapIdKey, PersistentDataType.INTEGER, view.getId());
            meta.setMapView(view);
        }
        if (signed && author != null) {
            pdc.set(authorKey, PersistentDataType.STRING, author);
        }
        if (!signed) {
            pdc.remove(authorKey);
        }
        applySketchAppearance(meta, signed, boundFind, boundTitle, boundLabel);
        meta.setMaxStackSize(1);
        stack.setItemMeta(meta);
    }

    /**
     * Removes a checkpoint after its drawing has been written onto the map item.
     *
     * @param mapId Bukkit map ID
     */
    private void deleteAutosave(int mapId) {
        try {
            autosaves.delete(mapId);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not remove field sketch autosave "
                    + mapId + ": " + exception.getMessage());
        }
    }

    /**
     * @param stack sketch map (always a {@code FILLED_MAP}; {@link #hydrate} has already read its map meta)
     * @return persisted sheet revision, or zero for maps saved before revisions were stored
     */
    private long metaRevision(ItemStack stack) {
        Long revision = stack.getItemMeta().getPersistentDataContainer().get(revisionKey, PersistentDataType.LONG);
        return revision == null ? 0 : revision;
    }

    /**
     * @param meta live map meta
     * @param signed locked
     * @param boundFind attached find id, or {@code null}
     * @param boundTitle artifact name, or {@code null}
     * @param boundLabel public find number, or {@code null}
     */
    private void applySketchAppearance(
            MapMeta meta,
            boolean signed,
            String boundFind,
            String boundTitle,
            String boundLabel
    ) {
        // bindSketch writes the find id, title, and label together, and only on signed drawings.
        if (boundFind != null) {
            meta.setDisplayName(ChatColor.WHITE + "Sketch of " + boundTitle);
            meta.setLore(java.util.List.of(ChatColor.GOLD + boundLabel, ChatColor.GRAY + "Hold to look at the drawing."));
            return;
        }
        if (signed) {
            meta.setDisplayName(ChatColor.WHITE + "Field sketch (signed)");
            meta.setLore(java.util.List.of(
                    ChatColor.GRAY + "Right-click the cabinet,",
                    ChatColor.GRAY + "put the piece and this drawing in,",
                    ChatColor.GRAY + "then click Register."));
            return;
        }
        meta.setDisplayName(ChatColor.WHITE + "Field sketch");
        meta.setLore(java.util.List.of(
                ChatColor.GRAY + "Hold to draw. Sneak paints. Right-click erases.",
                ChatColor.GRAY + "Switch the map away to save.",
                ChatColor.GRAY + "Left-click, then type sign to lock."));
    }

    /**
     * @param player editor
     * @param mainHand whether the sheet is in the main hand (else off-hand)
     * @return whether the map replaced the sheet
     */
    private boolean replacePaperWithMap(Player player, boolean mainHand) {
        ItemStack paper = mainHand
                ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();
        ItemStack pencil = mainHand
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        if (supplies.isSpent(pencil)) {
            player.sendMessage(ChatColor.GOLD + "This pencil is spent.");
            return false;
        }
        consumeOne(paper);
        ItemStack map = createUnsigned(player.getWorld());
        supplies.wear(player, pencil);
        if (isEmpty(pencil)) {
            if (mainHand) {
                player.getInventory().setItemInOffHand(null);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
        }
        if (isEmpty(paper)) {
            if (mainHand) {
                player.getInventory().setItemInMainHand(map);
            } else {
                player.getInventory().setItemInOffHand(map);
            }
        } else {
            if (mainHand) {
                player.getInventory().setItemInMainHand(paper);
            } else {
                player.getInventory().setItemInOffHand(paper);
            }
            giveOrDrop(player, map);
        }
        player.sendMessage(ChatColor.GOLD + "Field sketch ready. Hold it to draw.");
        enter(player, player.getInventory().getItemInMainHand());
        return true;
    }

    /**
     * Writes find id, site, and title onto a signed sketch so the map names the piece.
     *
     * @param sketch signed map
     * @param find archive row
     * @param site excavation
     */
    private void bindSketch(ItemStack sketch, BuriedFind find, Site site) {
        MapMeta meta = (MapMeta) sketch.getItemMeta();
        org.bukkit.persistence.PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(findIdKey, PersistentDataType.STRING, find.getId().toString());
        pdc.set(siteIdKey, PersistentDataType.STRING, site.getId().toString());
        ArtifactTemplate template = catalogs.artifact(find.getArtifactId());
        String title = find.shownName(template == null ? null : template.displayName());
        pdc.set(titleKey, PersistentDataType.STRING, title);
        pdc.set(labelKey, PersistentDataType.STRING, find.publicNumber(site));
        sketch.setItemMeta(meta);
        writeItem(sketch, sheetOf(sketch), true, authorOf(sketch));
    }

    /**
     * @param stack sketch map
     * @return bound find id, or {@code null}
     */
    private UUID boundFindId(ItemStack stack) {
        String raw = stack.getItemMeta().getPersistentDataContainer().get(findIdKey, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /**
     * @param stack kit stack
     */
    private static void consumeOne(ItemStack stack) {
        int left = stack.getAmount() - 1;
        if (left <= 0) {
            stack.setAmount(0);
            stack.setType(Material.AIR);
            return;
        }
        stack.setAmount(left);
    }

    /**
     * @param stack deposited drawing to empty
     */
    private static void consumeAll(ItemStack stack) {
        stack.setAmount(0);
        stack.setType(Material.AIR);
    }

    /**
     * @param stack item, or {@code null}
     * @return whether it cannot be placed back
     */
    private static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.isEmpty();
    }

    /**
     * @param player receiver
     * @param stack extra map
     */
    private static void giveOrDrop(Player player, ItemStack stack) {
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        leftover.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
    }

    /**
     * @param stack sketch map
     * @return stored cells, or {@code null}
     */
    private byte[] cellsOf(ItemStack stack) {
        return stack.getItemMeta().getPersistentDataContainer().get(cellsKey, PersistentDataType.BYTE_ARRAY);
    }

    /**
     * @param stack sketch map
     * @return signer, or {@code null}
     */
    private String authorOf(ItemStack stack) {
        return stack.getItemMeta().getPersistentDataContainer().get(authorKey, PersistentDataType.STRING);
    }

    /**
     * @param stack sketch map
     * @return view, or {@code null}
     */
    @SuppressWarnings("deprecation")
    private MapView mapView(ItemStack stack) {
        MapMeta meta = (MapMeta) stack.getItemMeta();
        MapView view = meta.getMapView();
        if (view != null) {
            return view;
        }
        // Paper's getMapView already resolves the vanilla map id; the stored copy covers a stripped link.
        Integer stored = meta.getPersistentDataContainer().get(mapIdKey, PersistentDataType.INTEGER);
        return stored == null ? null : Bukkit.getMap(stored);
    }

    /**
     * Puts the live {@link MapView} back on the stack when vanilla meta lost the link.
     *
     * @param stack sketch map
     * @param view resolved view
     */
    private void attachView(ItemStack stack, MapView view) {
        MapMeta meta = (MapMeta) stack.getItemMeta();
        if (meta.getMapView() != null) {
            return;
        }
        meta.setMapView(view);
        meta.getPersistentDataContainer().set(mapIdKey, PersistentDataType.INTEGER, view.getId());
        stack.setItemMeta(meta);
    }

    /**
     * @param stack possible sketch
     * @param viewId session map
     * @return whether this stack is that map
     */
    private boolean matchesView(ItemStack stack, int viewId) {
        if (!isSketchMap(stack)) {
            return false;
        }
        MapView view = mapView(stack);
        return view != null && view.getId() == viewId;
    }

    /**
     * @param player editor
     * @param session open sheet
     * @return the item that should receive the pixels, or {@code null} if it is gone
     */
    private ItemStack findSaveTarget(Player player, SketchSession session) {
        int viewId = session.view().getId();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (matchesView(hand, viewId)) {
            return hand;
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        if (matchesView(off, viewId)) {
            return off;
        }
        // The bottom of every open view is the player's own inventory, and its cursor is theirs.
        InventoryView open = player.getOpenInventory();
        if (matchesView(open.getCursor(), viewId)) {
            return open.getCursor();
        }
        ItemStack inTop = firstMatch(open.getTopInventory(), viewId);
        return inTop != null ? inTop : firstMatch(open.getBottomInventory(), viewId);
    }

    /**
     * @param inventory bag, crafting grid, or {@code null}
     * @param viewId session map
     * @return first matching stack, or {@code null}
     */
    private ItemStack firstMatch(Inventory inventory, int viewId) {
        if (inventory == null) {
            return null;
        }
        for (ItemStack stack : inventory.getContents()) {
            if (matchesView(stack, viewId)) {
                return stack;
            }
        }
        return null;
    }

    /**
     * Zeroes walking so WASD is free for the cursor. Jump and block-break speed are also
     * zeroed so space and clicks do not rubber-band; the client never starts those actions.
     *
     * @param player editor
     */
    private void freeze(Player player) {
        setFrozen(player, true);
    }

    /**
     * @param player editor
     */
    private void thaw(Player player) {
        setFrozen(player, false);
        player.setWalkSpeed(0.2f);
    }

    /**
     * @param player editor
     * @param frozen whether movement, jump, and mining should be multiplied to zero
     */
    private void setFrozen(Player player, boolean frozen) {
        zeroAttribute(player, Attribute.MOVEMENT_SPEED, frozen);
        zeroAttribute(player, Attribute.JUMP_STRENGTH, frozen);
        zeroAttribute(player, Attribute.BLOCK_BREAK_SPEED, frozen);
    }

    /**
     * @param player editor
     * @param attribute walk, jump, or mining speed
     * @param frozen whether to attach the zeroing modifier
     */
    private void zeroAttribute(Player player, Attribute attribute, boolean frozen) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        zeroAttribute(instance, frozen);
    }

    /**
     * @param instance live attribute
     * @param frozen whether to attach the zeroing modifier
     */
    private void zeroAttribute(AttributeInstance instance, boolean frozen) {
        AttributeModifier found = null;
        for (AttributeModifier modifier : instance.getModifiers()) {
            if (freezeKey.equals(modifier.getKey())) {
                found = modifier;
                break;
            }
        }
        if (frozen && found == null) {
            instance.addModifier(new AttributeModifier(
                    freezeKey,
                    -1.0,
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1,
                    EquipmentSlotGroup.ANY));
            return;
        }
        if (!frozen && found != null) {
            instance.removeModifier(found);
        }
    }
}
