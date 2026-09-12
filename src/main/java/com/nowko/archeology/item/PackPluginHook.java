package com.nowko.archeology.item;

import com.nowko.archeology.ArcheologyPlugin;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

import java.util.logging.Level;

/**
 * ItemsAdder furniture clicks, registered by name so the IA jar stays off the compile classpath.
 */
public final class PackPluginHook {
    private PackPluginHook() {
    }

    /**
     * Sole entry for ItemsAdder furniture cabinets (and museum plaques). Bukkit block/entity
     * interact handlers must not open the same cabinet, or cues fire twice on one click.
     *
     * @param plugin Archaeo
     */
    public static void register(ArcheologyPlugin plugin) {
        Listener marker = new Listener() {
        };
        registerNamed(
                plugin,
                marker,
                "dev.lone.itemsadder.api.Events.FurnitureInteractEvent",
                EventPriority.LOWEST,
                (listener, event) -> handleFurniture(plugin, event),
                "ItemsAdder furniture");
        registerNamed(
                plugin,
                marker,
                "dev.lone.itemsadder.api.Events.ItemsAdderLoadDataEvent",
                EventPriority.MONITOR,
                (listener, event) -> plugin.bindItemMatcher(ItemMatcher.detect(plugin)),
                "ItemsAdder load");
    }

    /**
     * @param plugin Archaeo
     * @param listener dummy registrar
     * @param className Bukkit event class
     * @param priority when to run
     * @param executor handler
     * @param label log label when registration fails
     */
    @SuppressWarnings("unchecked")
    private static void registerNamed(
            ArcheologyPlugin plugin,
            Listener listener,
            String className,
            EventPriority priority,
            EventExecutor executor,
            String label
    ) {
        try {
            Class<?> raw = Class.forName(className);
            if (!Event.class.isAssignableFrom(raw)) {
                return;
            }
            plugin.getServer().getPluginManager().registerEvent(
                    (Class<? extends Event>) raw,
                    listener,
                    priority,
                    executor,
                    plugin,
                    false);
        } catch (ClassNotFoundException ignored) {
            // ItemsAdder is optional.
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not listen for " + label + ".", exception);
        }
    }

    /**
     * @param plugin Archaeo
     * @param event ItemsAdder furniture interact
     */
    private static void handleFurniture(ArcheologyPlugin plugin, Event event) {
        if (plugin.sketch() == null && plugin.museum() == null) {
            return;
        }
        try {
            Object playerObj = event.getClass().getMethod("getPlayer").invoke(event);
            if (!(playerObj instanceof Player player)) {
                return;
            }
            String id = readNamespacedId(event);
            Entity entity = readEntity(event);
            Block block = entity == null ? null : entity.getLocation().getBlock();
            boolean handled = false;
            if (plugin.museum() != null
                    && plugin.museum().tryOpenFromSupport(player, id, entity, block, player.isSneaking())) {
                handled = true;
            }
            if (!handled
                    && plugin.sketch() != null
                    && plugin.sketch().tryOpenCabinet(player, id, entity, block, player.isSneaking())) {
                handled = true;
            }
            if (handled) {
                try {
                    event.getClass().getMethod("setCancelled", boolean.class).invoke(event, true);
                } catch (ReflectiveOperationException ignored) {
                    // Not every IA event is cancellable.
                }
            }
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().log(Level.FINE, "Furniture interact could not be read.", exception);
        }
    }

    /**
     * @param event furniture interact
     * @return {@code namespace:id}, or {@code null}
     */
    private static String readNamespacedId(Event event) throws ReflectiveOperationException {
        try {
            Object namespaced = event.getClass().getMethod("getNamespacedID").invoke(event);
            if (namespaced != null) {
                String text = String.valueOf(namespaced);
                if (!text.isBlank()) {
                    return text;
                }
            }
        } catch (NoSuchMethodException ignored) {
            // Fall through to the furniture wrapper.
        }
        try {
            Object furniture = event.getClass().getMethod("getFurniture").invoke(event);
            if (furniture == null) {
                return null;
            }
            Object namespaced = furniture.getClass().getMethod("getNamespacedID").invoke(furniture);
            return namespaced == null ? null : String.valueOf(namespaced);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    /**
     * @param event furniture interact
     * @return furniture entity, or {@code null}
     */
    private static Entity readEntity(Event event) {
        try {
            Object bukkit = event.getClass().getMethod("getBukkitEntity").invoke(event);
            return bukkit instanceof Entity found ? found : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
