package com.nowko.archeology.sketch;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Input;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Staff-only 32×32 map sketch, in memory only, so we can feel WASD-as-cursor before designing a real station.
 */
public class SketchService {
    private static final long TICK_PERIOD = 2L;

    private final JavaPlugin plugin;
    private final NamespacedKey markerKey;
    private final NamespacedKey freezeKey;
    private final Map<UUID, SketchSession> sessions = new HashMap<>();
    private final Map<Integer, SketchSheet> sheets = new HashMap<>();
    private BukkitTask task;

    /**
     * @param plugin scheduler and PDC owner
     */
    public SketchService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.markerKey = new NamespacedKey(plugin, "sketch_proto");
        this.freezeKey = new NamespacedKey(plugin, "sketch_freeze");
    }

    /**
     * Starts the input loop that walks the cursor while a session is open.
     */
    public void start() {
        stop();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, TICK_PERIOD, TICK_PERIOD);
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
                leave(player, false);
            } else {
                sessions.remove(playerId);
            }
        }
        sheets.clear();
    }

    /**
     * Gives a locked map and freezes the player in the editor.
     *
     * @param player staff tester
     */
    public void begin(Player player) {
        if (sessions.containsKey(player.getUniqueId())) {
            leave(player, true);
            return;
        }
        MapView view = Bukkit.createMap(player.getWorld());
        view.getRenderers().clear();
        view.setLocked(true);
        view.setTrackingPosition(false);
        view.setUnlimitedTracking(false);
        SketchSheet sheet = new SketchSheet();
        sheets.put(view.getId(), sheet);
        view.addRenderer(new SketchRenderer(this));
        SketchSession session = new SketchSession(player.getUniqueId(), view, sheet);
        sessions.put(player.getUniqueId(), session);
        putMapInHand(player, view);
        freeze(player);
        player.sendMessage(ChatColor.GOLD + "Sketch prototype.");
        player.sendMessage(ChatColor.GRAY + "Hold the map. WASD moves, sneak paints, jump changes ink.");
        player.sendMessage(ChatColor.GRAY + "Left-click paints, right-click erases, drop or /archaeo sketch leaves.");
        player.sendMessage(ChatColor.DARK_GRAY + "Nothing is saved. The drawing dies on leave or reload.");
    }

    /**
     * Thaws the player and forgets the session. The map item may still show the last blit.
     *
     * @param player editor
     * @param announce whether to tell them they left
     */
    public void leave(Player player, boolean announce) {
        sessions.remove(player.getUniqueId());
        thaw(player);
        if (announce) {
            player.sendMessage(ChatColor.GRAY + "Left the sketch. The map is a snapshot until you drop this world.");
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(""));
        }
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
     * Stamps the current ink. Used by left-click.
     *
     * @param player editor
     */
    public void paint(Player player) {
        SketchSession session = sessions.get(player.getUniqueId());
        if (session != null) {
            session.paint();
        }
    }

    /**
     * Clears the cursor cell. Used by right-click.
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
     * Reads WASD as cursor steps and sneak as a continuous stamp.
     */
    private void tick() {
        Iterator<Map.Entry<UUID, SketchSession>> iterator = sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, SketchSession> entry = iterator.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                iterator.remove();
                continue;
            }
            SketchSession session = entry.getValue();
            if (!holdingThisSketch(player, session)) {
                iterator.remove();
                thaw(player);
                player.sendMessage(ChatColor.GRAY + "Left the sketch (the map left the hand).");
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(""));
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
     * @param player editor
     * @param session cursor and ink
     */
    private void sendHud(Player player, SketchSession session) {
        String line = ChatColor.GOLD + "Sketch "
                + ChatColor.WHITE + session.cursorX() + "," + session.cursorY()
                + ChatColor.GRAY + " · "
                + ChatColor.WHITE + session.ink().label()
                + ChatColor.DARK_GRAY + " · sneak paint · jump ink";
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(line));
    }

    /**
     * @param player tester
     * @param session map they should be holding
     * @return whether the main hand is that sketch
     */
    private boolean holdingThisSketch(Player player, SketchSession session) {
        ItemStack stack = player.getInventory().getItemInMainHand();
        if (!isSketchMap(stack) || !(stack.getItemMeta() instanceof MapMeta meta)) {
            return false;
        }
        MapView held = meta.getMapView();
        return held != null && held.getId() == session.view().getId();
    }

    /**
     * @param player tester
     * @param view new map
     */
    private void putMapInHand(Player player, MapView view) {
        ItemStack map = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) map.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.setMapView(view);
        meta.setDisplayName(ChatColor.WHITE + "Field sketch");
        meta.setLore(java.util.List.of(
                ChatColor.GRAY + "Prototype 32×32.",
                ChatColor.DARK_GRAY + "Not saved."));
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
        map.setItemMeta(meta);
        ItemStack previous = player.getInventory().getItemInMainHand();
        player.getInventory().setItemInMainHand(map);
        if (previous != null && previous.getType() != Material.AIR) {
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(previous);
            leftover.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        }
    }

    /**
     * Zeroes walking so WASD is free for the cursor.
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
     * @param frozen whether movement speed should be multiplied to zero
     */
    private void setFrozen(Player player, boolean frozen) {
        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null) {
            player.setWalkSpeed(frozen ? 0f : 0.2f);
            return;
        }
        AttributeModifier found = null;
        for (AttributeModifier modifier : speed.getModifiers()) {
            if (freezeKey.equals(modifier.getKey())) {
                found = modifier;
                break;
            }
        }
        if (frozen && found == null) {
            speed.addModifier(new AttributeModifier(
                    freezeKey,
                    -1.0,
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1,
                    EquipmentSlotGroup.ANY));
            return;
        }
        if (!frozen && found != null) {
            speed.removeModifier(found);
        }
    }
}
