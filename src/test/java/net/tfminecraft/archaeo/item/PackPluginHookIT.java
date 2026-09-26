package net.tfminecraft.archaeo.item;

import net.tfminecraft.archaeo.ArcheologyPlugin;
import net.tfminecraft.archaeo.museum.MuseumListener;
import net.tfminecraft.archaeo.sketch.SketchService;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Real ItemsAdder event contracts; production entry points are used without accessing private handlers. */
public class PackPluginHookIT {
    private static final String FURNITURE = "dev.lone.itemsadder.api.Events.FurnitureInteractEvent";
    private static final String LOAD = "dev.lone.itemsadder.api.Events.ItemsAdderLoadDataEvent";
    private ArcheologyPlugin plugin;
    private PluginManager plugins;
    private PlayerMock player;
    private SketchService sketch;
    private MuseumListener museum;
    private Logger logger;
    private Class<?> furnitureEvent;
    private final Map<String, EventExecutor> executors = new HashMap<>();
    private final Map<String, EventPriority> priorities = new HashMap<>();

    @Before public void setup() throws Exception {
        ServerMock mockServer = MockBukkit.mock(); player = mockServer.addPlayer();
        plugin = mock(ArcheologyPlugin.class); Server server = mock(Server.class); plugins = mock(PluginManager.class);
        sketch = mock(SketchService.class); museum = mock(MuseumListener.class); logger = mock(Logger.class);
        when(plugin.getServer()).thenReturn(server); when(server.getPluginManager()).thenReturn(plugins);
        when(plugin.getLogger()).thenReturn(logger); when(plugin.sketch()).thenReturn(sketch); when(plugin.museum()).thenReturn(museum);
        doAnswer(call -> {
            Class<?> type = call.getArgument(0);
            executors.put(type.getName(), call.getArgument(3)); priorities.put(type.getName(), call.getArgument(2));
            return null;
        }).when(plugins).registerEvent(any(), any(), any(), any(), eq(plugin), eq(false));
        furnitureEvent = Class.forName(FURNITURE);
        PackPluginHook.register(plugin);
    }
    @After public void teardown() { MockBukkit.unmock(); }

    @Test public void furnitureEventGivesMuseumFirstRefusalAndCancelsHandledClicksExactlyOnce() throws Exception {
        assertEquals(EventPriority.LOWEST, priorities.get(FURNITURE)); assertEquals(EventPriority.MONITOR, priorities.get(LOAD));
        Entity entity = mock(Entity.class); when(entity.getLocation()).thenReturn(player.getLocation());
        Block support = player.getLocation().getBlock(); player.setSneaking(true);
        Event event = event("museum:plaque", entity);
        when(museum.tryOpenFromSupport(player, "museum:plaque", entity, support, true)).thenReturn(true);
        dispatch(event);
        verify(museum).tryOpenFromSupport(player, "museum:plaque", entity, support, true);
        verifyNoInteractions(sketch); cancelled(event, true);
    }

    @Test public void unhandledMuseumSupportFallsThroughToCabinetWithTheSameContext() throws Exception {
        Entity entity = mock(Entity.class); when(entity.getLocation()).thenReturn(player.getLocation());
        Block support = player.getLocation().getBlock(); Event event = event("museum:cabinet", entity);
        when(sketch.tryOpenCabinet(player, "museum:cabinet", entity, support, false)).thenReturn(true);
        dispatch(event);
        var order = inOrder(museum, sketch);
        order.verify(museum).tryOpenFromSupport(player, "museum:cabinet", entity, support, false);
        order.verify(sketch).tryOpenCabinet(player, "museum:cabinet", entity, support, false);
        cancelled(event, true);
    }

    @Test public void unrelatedFurnitureDoesNotCancelTheOriginalInteraction() throws Exception {
        Event event = event("other:chair", null); dispatch(event);
        verify(museum).tryOpenFromSupport(player, "other:chair", null, null, false);
        verify(sketch).tryOpenCabinet(player, "other:chair", null, null, false);
        cancelled(event, false);
    }

    @Test public void realLoadEventRebindsTheMatcherAfterTheApiBecomesAvailable() throws Exception {
        Plugin itemsAdder = mock(Plugin.class); when(itemsAdder.isEnabled()).thenReturn(true);
        when(plugins.getPlugin("ItemsAdder")).thenReturn(itemsAdder);
        Class<?> stackApi = Class.forName("dev.lone.itemsadder.api.CustomStack"); Object custom = mock(stackApi);
        when(call(custom, "getNamespacedID")).thenReturn("museum:brush"); ItemStack stack = new ItemStack(Material.BRUSH);
        Event load = (Event) Class.forName(LOAD).getConstructor(boolean.class).newInstance(false);
        try (var api = mockStatic(stackApi)) {
            api.when(() -> stackApi.getMethod("byItemStack", ItemStack.class).invoke(null, stack)).thenReturn(custom);
            executors.get(LOAD).execute(null, load);
            ArgumentCaptor<ItemMatcher> rebound = ArgumentCaptor.forClass(ItemMatcher.class);
            verify(plugin).bindItemMatcher(rebound.capture());
            assertTrue(rebound.getValue().matches(stack, ItemRef.parse(plugin, "ia:museum:brush").orElseThrow()));
            assertFalse(rebound.getValue().matches(stack, ItemRef.vanilla(Material.BRUSH)));
        }
    }

    @Test public void missingEventIdFallsBackToTheFurnitureWrapper() throws Exception {
        Object furniture = mock(Class.forName("dev.lone.itemsadder.api.CustomFurniture"));
        when(call(furniture, "getNamespacedID")).thenReturn("museum:cabinet");
        for (String blank : new String[]{null, " "}) {
            Event event = event(blank, null); when(call(event, "getFurniture")).thenReturn(furniture);
            dispatch(event);
        }
        verify(sketch, times(2)).tryOpenCabinet(player, "museum:cabinet", null, null, false);
        when(call(furniture, "getNamespacedID")).thenReturn(null);
        Event unnamed = event(null, null); when(call(unnamed, "getFurniture")).thenReturn(furniture); dispatch(unnamed);
        dispatch(event(null, null));
        verify(sketch, times(2)).tryOpenCabinet(player, null, null, null, false);
    }

    @Test public void itemsAdderFailingMidLookupIsLoggedAndOpensNothing() throws Exception {
        Event event = event(null, null);
        when(call(event, "getFurniture")).thenThrow(new IllegalStateException("furniture unloading"));
        dispatch(event);
        verify(logger).log(eq(java.util.logging.Level.FINE), eq("Furniture interact could not be read."), any(java.lang.reflect.InvocationTargetException.class));
        verifyNoInteractions(museum, sketch); cancelled(event, false);
    }

    @Test public void incompatibleItemsAdderWhoseFurnitureEventIsNoBukkitEventStillListensForLoads() throws Exception {
        // A build that reuses the event's name for a plain class: asSubclass(Event) throws ClassCastException.
        byte[] notAnEvent = new net.bytebuddy.ByteBuddy().subclass(Object.class).name(FURNITURE).make().getBytes();
        ClassLoader incompatible = new HiddenPackApiLoader().replacing(FURNITURE, notAnEvent);
        executors.clear(); priorities.clear();
        incompatible.loadClass(PackPluginHook.class.getName()).getMethod("register", ArcheologyPlugin.class).invoke(null, plugin);
        verify(logger).log(eq(java.util.logging.Level.WARNING), eq("Could not listen for ItemsAdder furniture."), any(ClassCastException.class));
        verify(logger, never()).log(any(java.util.logging.Level.class), eq("Could not listen for ItemsAdder load."), any(Throwable.class));
        assertEquals(Map.of(LOAD, EventPriority.MONITOR), priorities);
    }

    private Event event(String id, Entity entity) throws Exception {
        Event event = (Event) mock(furnitureEvent);
        when(call(event, "getPlayer")).thenReturn(player); when(call(event, "getNamespacedID")).thenReturn(id);
        when(call(event, "getBukkitEntity")).thenReturn(entity); return event;
    }
    private void dispatch(Event event) throws Exception { executors.get(FURNITURE).execute(null, event); }
    private void cancelled(Event event, boolean handled) throws Exception {
        furnitureEvent.getMethod("setCancelled", boolean.class).invoke(verify(event, handled ? times(1) : never()), true);
    }
    private static Object call(Object target, String method) throws Exception { return target.getClass().getMethod(method).invoke(target); }
}
