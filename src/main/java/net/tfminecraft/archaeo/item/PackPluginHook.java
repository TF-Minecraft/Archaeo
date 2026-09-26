package net.tfminecraft.archaeo.item;

import net.tfminecraft.archaeo.ArcheologyPlugin;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerEvent;
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
    private static void registerNamed(
            ArcheologyPlugin plugin,
            Listener listener,
            String className,
            EventPriority priority,
            EventExecutor executor,
            String label
    ) {
        try {
            // asSubclass fails (and is logged below) if an incompatible ItemsAdder moved the event off Event.
            Class<? extends Event> type = Class.forName(className).asSubclass(Event.class);
            plugin.getServer().getPluginManager().registerEvent(
                    type,
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
        // FurnitureInteractEvent is a cancellable PlayerEvent, and onEnable builds the museum and
        // sketch services before it registers this hook.
        Player player = ((PlayerEvent) event).getPlayer();
        try {
            String id = readNamespacedId(event);
            Entity entity = readEntity(event);
            Block block = entity == null ? null : entity.getLocation().getBlock();
            if (plugin.museum().tryOpenFromSupport(player, id, entity, block, player.isSneaking())
                    || plugin.sketch().tryOpenCabinet(player, id, entity, block, player.isSneaking())) {
                ((Cancellable) event).setCancelled(true);
            }
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().log(Level.FINE, "Furniture interact could not be read.", exception);
        }
    }

    /**
     * @param event furniture interact
     * @return {@code namespace:id}, or {@code null}
     * @throws ReflectiveOperationException when ItemsAdder fails to answer
     */
    private static String readNamespacedId(Event event) throws ReflectiveOperationException {
        Object namespaced = event.getClass().getMethod("getNamespacedID").invoke(event);
        if (namespaced != null && !String.valueOf(namespaced).isBlank()) {
            return String.valueOf(namespaced);
        }
        // Fall back to the furniture wrapper when the event carries no id of its own.
        Object furniture = event.getClass().getMethod("getFurniture").invoke(event);
        if (furniture == null) {
            return null;
        }
        Object furnitureId = furniture.getClass().getMethod("getNamespacedID").invoke(furniture);
        return furnitureId == null ? null : String.valueOf(furnitureId);
    }

    /**
     * @param event furniture interact
     * @return furniture entity, or {@code null}
     * @throws ReflectiveOperationException when ItemsAdder fails to answer
     */
    private static Entity readEntity(Event event) throws ReflectiveOperationException {
        return (Entity) event.getClass().getMethod("getBukkitEntity").invoke(event);
    }
}
