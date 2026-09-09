package com.nowko.archeology.sketch;

import com.nowko.archeology.item.RecoveredFindItem;
import com.nowko.archeology.item.SketchSupplies;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Input;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Field sketch: a configured sheet plus pencil become a {@code FILLED_MAP} marked by this plugin.
 * Hold an unsigned sheet to edit; switch it away to save; sign, then click it onto a recovered find.
 */
public class SketchService {
    private static final long TICK_PERIOD = 2L;

    private final JavaPlugin plugin;
    private final SketchSupplies supplies;
    private final RecoveredFindItem recovered;
    private final NamespacedKey markerKey;
    private final NamespacedKey cellsKey;
    private final NamespacedKey signedKey;
    private final NamespacedKey authorKey;
    private final NamespacedKey mapIdKey;
    private final NamespacedKey findIdKey;
    private final NamespacedKey siteIdKey;
    private final NamespacedKey titleKey;
    private final NamespacedKey freezeKey;
    private final Map<UUID, SketchSession> sessions = new HashMap<>();
    private final Map<Integer, SketchSheet> sheets = new HashMap<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private BukkitTask task;

    /**
     * @param plugin scheduler and PDC owner
     * @param supplies configured sheet and pencil
     * @param recovered recovered-find tags, for attaching a signed sketch
     */
    public SketchService(JavaPlugin plugin, SketchSupplies supplies, RecoveredFindItem recovered) {
        this.plugin = plugin;
        this.supplies = supplies;
        this.recovered = recovered;
        this.markerKey = new NamespacedKey(plugin, "sketch_proto");
        this.cellsKey = new NamespacedKey(plugin, "sketch_cells");
        this.signedKey = new NamespacedKey(plugin, "sketch_signed");
        this.authorKey = new NamespacedKey(plugin, "sketch_author");
        this.mapIdKey = new NamespacedKey(plugin, "sketch_map_id");
        this.findIdKey = new NamespacedKey(plugin, "sketch_find_id");
        this.siteIdKey = new NamespacedKey(plugin, "sketch_site_id");
        this.titleKey = new NamespacedKey(plugin, "sketch_title");
        this.freezeKey = new NamespacedKey(plugin, "sketch_freeze");
    }

    /**
     * @return configured sheet and pencil
     */
    public SketchSupplies supplies() {
        return supplies;
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
            supplies.stampAll(player.getInventory().getContents());
            supplies.stampInstructions(player.getInventory().getItemInOffHand());
            syncHand(player);
        }
    }

    /**
     * Ends every session (restores walk) and cancels the input loop.
     */
    public void stop() {
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
     * Sheet onto pencil, or pencil onto sheet, in the player's own bag.
     *
     * @param player clicker
     * @param cursor item on the cursor
     * @param slot item in the clicked slot
     * @param setCursor writes the post-click cursor
     * @param setSlot writes the post-click slot
     * @return whether this click crafted a sketch
     */
    public boolean tryCraftOnClick(
            Player player,
            ItemStack cursor,
            ItemStack slot,
            java.util.function.Consumer<ItemStack> setCursor,
            java.util.function.Consumer<ItemStack> setSlot
    ) {
        if (player == null || editing(player)) {
            return false;
        }
        if (supplies.isPaper(cursor) && supplies.isPencil(slot)) {
            consumeOne(cursor);
            ItemStack map = createUnsigned(player.getWorld());
            player.sendMessage(ChatColor.GOLD + "Field sketch ready. Hold it to draw.");
            if (isEmpty(cursor)) {
                setCursor.accept(map);
            } else {
                setCursor.accept(cursor);
                giveOrDrop(player, map);
            }
            return true;
        }
        if (supplies.isPencil(cursor) && supplies.isPaper(slot)) {
            consumeOne(slot);
            ItemStack map = createUnsigned(player.getWorld());
            player.sendMessage(ChatColor.GOLD + "Field sketch ready. Hold it to draw.");
            setCursor.accept(cursor);
            if (isEmpty(slot)) {
                setSlot.accept(map);
            } else {
                setSlot.accept(slot);
                giveOrDrop(player, map);
            }
            return true;
        }
        return false;
    }

    /**
     * Signed sketch onto a recovered find (or the reverse). Unsigned sketches explain why they refuse.
     *
     * @param player clicker
     * @param cursor item on the cursor
     * @param slot item in the clicked slot
     * @return whether the click was a sketch-and-find pair (cancel it even on refusal)
     */
    public boolean tryAttachOnClick(Player player, ItemStack cursor, ItemStack slot) {
        if (player == null || editing(player)) {
            return false;
        }
        if (isSketchMap(cursor) && recovered.isRecovered(slot)) {
            attachOrExplain(player, cursor, slot);
            return true;
        }
        if (isSketchMap(slot) && recovered.isRecovered(cursor)) {
            attachOrExplain(player, slot, cursor);
            return true;
        }
        return false;
    }

    /**
     * Re-applies sheet/pencil how-to lore after pack plugins have had a tick to write theirs.
     *
     * @param player holder
     */
    public void stampKitsLater(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player == null || !player.isOnline()) {
                return;
            }
            supplies.stampAll(player.getInventory().getContents());
            supplies.stampInstructions(player.getInventory().getItemInOffHand());
            supplies.stampInstructions(player.getItemOnCursor());
        });
    }

    /**
     * Writes cursor and slot after a cancelled combine, then restamps kit lore.
     *
     * @param player clicker
     * @param inventory clicked bag
     * @param slotIndex clicked slot
     * @param cursor item on the cursor
     * @param slot item in that slot
     */
    public void applyBagClick(
            Player player,
            Inventory inventory,
            int slotIndex,
            ItemStack cursor,
            ItemStack slot
    ) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player == null || !player.isOnline()) {
                return;
            }
            player.setItemOnCursor(cursor);
            if (inventory != null && slotIndex >= 0) {
                inventory.setItem(slotIndex, slot);
            }
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
        if (stack == null || !isSketchMap(stack) || isSigned(stack) || editing(player)) {
            return;
        }
        hydrate(stack);
        MapView view = mapView(stack);
        if (view == null) {
            return;
        }
        SketchSheet sheet = sheets.get(view.getId());
        if (sheet == null) {
            return;
        }
        sessions.put(player.getUniqueId(), new SketchSession(player.getUniqueId(), view, sheet));
        freeze(player);
        player.sendMessage(ChatColor.GOLD + "Editing field sketch.");
        player.sendMessage(ChatColor.WHITE + "Sneak paints. Right-click erases. Space changes ink.");
        player.sendMessage(ChatColor.WHITE + "Switch the item away to save. Left-click, then type sign to lock.");
    }

    /**
     * Writes the sheet onto {@code stack} when possible, then thaws.
     *
     * @param player editor
     * @param announce whether this leave was a player action (hotbar, inventory, drop) and should tell them
     * @param stack item that left the hand, or {@code null} to search the inventory
     */
    public void leave(Player player, boolean announce, ItemStack stack) {
        SketchSession session = sessions.remove(player.getUniqueId());
        thaw(player);
        hideHud(player);
        if (session == null) {
            return;
        }
        ItemStack target = stack != null && matchesView(stack, session.view().getId())
                ? stack
                : findSaveTarget(player, session);
        if (target == null) {
            return;
        }
        writeItem(target, session.sheet(), isSigned(target), authorOf(target));
        if (announce) {
            player.sendMessage(ChatColor.GRAY + "Sketch saved on the map.");
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
        SketchSession session = sessions.get(player.getUniqueId());
        ItemStack target = null;
        if (session != null && extras != null) {
            int viewId = session.view().getId();
            for (ItemStack stack : extras) {
                if (matchesView(stack, viewId)) {
                    target = stack;
                    break;
                }
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
                leave(player, true, extras == null ? java.util.List.of() : java.util.Arrays.asList(extras));
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
     * Asks for a chat confirm before locking the sheet.
     *
     * @param player editor
     */
    public void askToSign(Player player) {
        SketchSession session = sessions.get(player.getUniqueId());
        if (session == null) {
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
        ItemStack hand = player.getInventory().getItemInMainHand();
        writeItem(hand, session.sheet(), true, player.getName());
        leave(player, false, hand);
        player.sendMessage(ChatColor.GOLD + "Sketch signed. It can no longer be edited.");
        return true;
    }

    /**
     * @param player possible editor
     * @return whether they are frozen in the prototype
     */
    public boolean editing(Player player) {
        return sessions.containsKey(player.getUniqueId());
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
     * Clears the cursor cell. Used by right-click / interact.
     *
     * @param player editor
     */
    public void erase(Player player) {
        SketchSession session = sessions.get(player.getUniqueId());
        if (session != null) {
            session.erase();
        }
    }

    /**
     * @param stack item in a hand
     * @return whether this is a prototype sketch map
     */
    public boolean isSketchMap(ItemStack stack) {
        if (stack == null || stack.getType() != Material.FILLED_MAP || !(stack.getItemMeta() instanceof MapMeta meta)) {
            return false;
        }
        return meta.getPersistentDataContainer().has(markerKey, PersistentDataType.BYTE);
    }

    /**
     * Reads WASD as cursor steps and sneak as a continuous stroke. Also opens the editor
     * for anyone who is holding an unsigned sheet, so a missed hotbar event cannot skip enter.
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
        for (UUID playerId : Map.copyOf(sessions).keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                sessions.remove(playerId);
                continue;
            }
            SketchSession session = sessions.get(playerId);
            if (session == null) {
                continue;
            }
            if (!holdingThisSketch(player, session)) {
                leave(player, true);
                continue;
            }
            applyInput(player, session);
            sendHud(player, session);
        }
    }

    /**
     * @param player editor
     * @param session their map
     */
    private void applyInput(Player player, SketchSession session) {
        Input input = player.getCurrentInput();
        session.move(
                (input.isLeft() ? -1 : 0) + (input.isRight() ? 1 : 0),
                (input.isForward() ? -1 : 0) + (input.isBackward() ? 1 : 0));
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
        int id = view.getId();
        if (!sheets.containsKey(id)) {
            sheets.put(id, SketchSheet.fromBytes(cellsOf(stack)));
        }
    }

    /**
     * @param stack sketch or {@code null}
     * @return whether this sheet is locked
     */
    public boolean isSigned(ItemStack stack) {
        if (!isSketchMap(stack) || !(stack.getItemMeta() instanceof MapMeta meta)) {
            return false;
        }
        Byte flag = meta.getPersistentDataContainer().get(signedKey, PersistentDataType.BYTE);
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
        if (previous != null && previous.getType() != Material.AIR) {
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
        if (map.getItemMeta() instanceof MapMeta meta) {
            meta.setMapView(view);
            map.setItemMeta(meta);
        }
        writeItem(map, sheet, false, null);
        return map;
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
            if (renderer instanceof SketchRenderer) {
                ours = true;
                break;
            }
        }
        if (ours) {
            return;
        }
        view.getRenderers().clear();
        view.addRenderer(new SketchRenderer(this));
    }

    /**
     * @param stack map item
     * @param sheet pixels
     * @param signed locked
     * @param author signer, or {@code null}
     * @return whether the PDC write reached {@code setItemMeta}
     */
    private boolean writeItem(ItemStack stack, SketchSheet sheet, boolean signed, String author) {
        if (stack == null || !(stack.getItemMeta() instanceof MapMeta meta)) {
            return false;
        }
        MapView view = meta.getMapView();
        if (view == null) {
            view = mapView(stack);
        }
        org.bukkit.persistence.PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String boundFind = pdc.get(findIdKey, PersistentDataType.STRING);
        String boundTitle = pdc.get(titleKey, PersistentDataType.STRING);
        pdc.set(markerKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(cellsKey, PersistentDataType.BYTE_ARRAY, sheet.toBytes());
        pdc.set(signedKey, PersistentDataType.BYTE, (byte) (signed ? 1 : 0));
        if (view != null) {
            pdc.set(mapIdKey, PersistentDataType.INTEGER, view.getId());
            meta.setMapView(view);
        }
        if (signed && author != null && !author.isEmpty()) {
            pdc.set(authorKey, PersistentDataType.STRING, author);
        }
        if (!signed) {
            pdc.remove(authorKey);
        }
        String storedAuthor = pdc.get(authorKey, PersistentDataType.STRING);
        applySketchAppearance(meta, signed, storedAuthor, boundFind, boundTitle);
        stack.setItemMeta(meta);
        return true;
    }

    /**
     * @param meta live map meta
     * @param signed locked
     * @param author signer, or {@code null}
     * @param boundFind attached find id, or {@code null}
     * @param boundTitle artifact name, or {@code null}
     */
    private void applySketchAppearance(
            MapMeta meta,
            boolean signed,
            String author,
            String boundFind,
            String boundTitle
    ) {
        boolean bound = boundFind != null && !boundFind.isBlank();
        String title = boundTitle == null || boundTitle.isBlank() ? "find" : boundTitle;
        if (signed && bound) {
            meta.setDisplayName(ChatColor.WHITE + title);
            meta.setLore(java.util.List.of(
                    ChatColor.GRAY + "Field sketch of " + title + ".",
                    ChatColor.DARK_GRAY + (author == null ? "Signed." : "Signed by " + author + "."),
                    ChatColor.DARK_GRAY + "This drawing cannot be edited."));
            return;
        }
        if (signed) {
            meta.setDisplayName(ChatColor.WHITE + "Field sketch (signed)");
            meta.setLore(java.util.List.of(
                    ChatColor.GRAY + (author == null ? "Signed." : "Signed by " + author + "."),
                    ChatColor.GRAY + "Click this onto a recovered find to attach it.",
                    ChatColor.DARK_GRAY + "This drawing cannot be edited."));
            return;
        }
        meta.setDisplayName(ChatColor.WHITE + "Field sketch");
        meta.setLore(java.util.List.of(
                ChatColor.GRAY + "Hold to edit. Switch away to save.",
                ChatColor.GRAY + "Left-click, then type sign to lock.",
                ChatColor.DARK_GRAY + "After signing, click this onto a recovered find."));
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
        consumeOne(paper);
        ItemStack map = createUnsigned(player.getWorld());
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
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (isSketchMap(hand) && !isSigned(hand)) {
            enter(player, hand);
        }
        return true;
    }

    /**
     * @param player clicker
     * @param sketch map to tag
     * @param find recovered piece
     */
    private void attachOrExplain(Player player, ItemStack sketch, ItemStack find) {
        if (!isSigned(sketch)) {
            player.sendMessage(ChatColor.GOLD + "Sign the sketch first.");
            player.sendMessage(ChatColor.WHITE + "Hold it, left-click, then type sign in chat.");
            player.sendMessage(ChatColor.GRAY + "An unfinished drawing cannot be attached to a find.");
            return;
        }
        if (boundFindId(sketch) != null) {
            player.sendMessage(ChatColor.GRAY + "This sketch already records a find.");
            return;
        }
        UUID findId = recovered.findIdOf(find);
        UUID siteId = recovered.siteIdOf(find);
        String title = recovered.labelOf(find);
        if (findId == null) {
            return;
        }
        if (!(sketch.getItemMeta() instanceof MapMeta meta)) {
            return;
        }
        org.bukkit.persistence.PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(findIdKey, PersistentDataType.STRING, findId.toString());
        if (siteId != null) {
            pdc.set(siteIdKey, PersistentDataType.STRING, siteId.toString());
        }
        pdc.set(titleKey, PersistentDataType.STRING, title);
        sketch.setItemMeta(meta);
        hydrate(sketch);
        MapView view = mapView(sketch);
        SketchSheet sheet = view == null ? new SketchSheet() : sheets.getOrDefault(view.getId(), new SketchSheet());
        writeItem(sketch, sheet, true, authorOf(sketch));
        player.sendMessage(ChatColor.GOLD + "Sketch attached to " + title + ".");
    }

    /**
     * @param stack sketch
     * @return bound find id, or {@code null}
     */
    private UUID boundFindId(ItemStack stack) {
        if (!(stack.getItemMeta() instanceof MapMeta meta)) {
            return null;
        }
        String raw = meta.getPersistentDataContainer().get(findIdKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
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
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        int left = stack.getAmount() - 1;
        if (left <= 0) {
            stack.setAmount(0);
            stack.setType(Material.AIR);
            return;
        }
        stack.setAmount(left);
    }

    /**
     * @param stack item, or {@code null}
     * @return whether it cannot be placed back
     */
    private static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getType().isAir() || stack.getAmount() <= 0;
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
     * @param stack map
     * @return stored cells, or {@code null}
     */
    private byte[] cellsOf(ItemStack stack) {
        if (!(stack.getItemMeta() instanceof MapMeta meta)) {
            return null;
        }
        return meta.getPersistentDataContainer().get(cellsKey, PersistentDataType.BYTE_ARRAY);
    }

    /**
     * @param stack map
     * @return signer, or {@code null}
     */
    private String authorOf(ItemStack stack) {
        if (!(stack.getItemMeta() instanceof MapMeta meta)) {
            return null;
        }
        return meta.getPersistentDataContainer().get(authorKey, PersistentDataType.STRING);
    }

    /**
     * @param stack map
     * @return view, or {@code null}
     */
    @SuppressWarnings("deprecation")
    private MapView mapView(ItemStack stack) {
        if (!(stack.getItemMeta() instanceof MapMeta meta)) {
            return null;
        }
        MapView view = meta.getMapView();
        if (view != null) {
            return view;
        }
        Integer stored = meta.getPersistentDataContainer().get(mapIdKey, PersistentDataType.INTEGER);
        if (stored != null) {
            view = Bukkit.getMap(stored);
            if (view != null) {
                return view;
            }
        }
        if (meta.hasMapId()) {
            return Bukkit.getMap(meta.getMapId());
        }
        return null;
    }

    /**
     * Puts the live {@link MapView} back on the stack when vanilla meta lost the link.
     *
     * @param stack sketch map
     * @param view resolved view
     */
    private void attachView(ItemStack stack, MapView view) {
        if (!(stack.getItemMeta() instanceof MapMeta meta) || view == null) {
            return;
        }
        if (meta.getMapView() != null && meta.getMapView().getId() == view.getId()) {
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
        if (player == null || session == null) {
            return null;
        }
        int viewId = session.view().getId();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (matchesView(hand, viewId)) {
            return hand;
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        if (matchesView(off, viewId)) {
            return off;
        }
        InventoryView open = player.getOpenInventory();
        if (open != null) {
            if (matchesView(open.getCursor(), viewId)) {
                return open.getCursor();
            }
            ItemStack inTop = firstMatch(open.getTopInventory(), viewId);
            if (inTop != null) {
                return inTop;
            }
            ItemStack inBottom = firstMatch(open.getBottomInventory(), viewId);
            if (inBottom != null) {
                return inBottom;
            }
        }
        if (matchesView(player.getItemOnCursor(), viewId)) {
            return player.getItemOnCursor();
        }
        return firstMatch(player.getInventory(), viewId);
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
        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null) {
            player.setWalkSpeed(frozen ? 0f : 0.2f);
        } else {
            zeroAttribute(speed, frozen);
        }
        zeroAttribute(player, Attribute.JUMP_STRENGTH, frozen);
        zeroAttribute(player, Attribute.BLOCK_BREAK_SPEED, frozen);
    }

    /**
     * @param player editor
     * @param attribute jump or mining speed
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
