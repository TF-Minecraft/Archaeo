package net.tfminecraft.archaeo.item;

import net.tfminecraft.archaeo.ArcheologyPlugin;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.lang.reflect.Field;
import java.util.logging.Logger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Contract tests using the actual optional plugin classes supplied by the pack-api-tests profile. */
public class ItemMatcherPackIT {
    private ArcheologyPlugin plugin;
    private PluginManager plugins;
    private Plugin enabled;
    private Logger logger;
    private Class<?> stackApi, furnitureApi, blockApi, mmoApi, typeApi;
    private Field singleton;
    private Object previousSingleton;

    @Before public void setup() throws Exception {
        MockBukkit.mock();
        plugin = mock(ArcheologyPlugin.class); Server server = mock(Server.class);
        plugins = mock(PluginManager.class); enabled = mock(Plugin.class); logger = mock(Logger.class);
        when(plugin.getServer()).thenReturn(server); when(server.getPluginManager()).thenReturn(plugins);
        when(plugin.getLogger()).thenReturn(logger); when(enabled.isEnabled()).thenReturn(true);
        stackApi = Class.forName("dev.lone.itemsadder.api.CustomStack");
        furnitureApi = Class.forName("dev.lone.itemsadder.api.CustomFurniture");
        blockApi = Class.forName("dev.lone.itemsadder.api.CustomBlock");
        mmoApi = Class.forName("net.Indyuce.mmoitems.MMOItems");
        typeApi = Class.forName("net.Indyuce.mmoitems.api.Type");
        singleton = mmoApi.getField("plugin"); previousSingleton = singleton.get(null);
    }
    @After public void teardown() throws Exception {
        if (singleton != null) singleton.set(null, previousSingleton);
        MockBukkit.unmock();
    }

    @Test public void itemsAdderDetectionDistinguishesCustomItemsFromTheirVanillaMaterial() throws Exception {
        when(plugins.getPlugin("ItemsAdder")).thenReturn(enabled);
        ItemStack packed = new ItemStack(Material.STONE), ordinary = new ItemStack(Material.DIRT);
        Object custom = mock(stackApi); when(call(custom, "getNamespacedID")).thenReturn("Museum:BRUSH");
        try (var api = mockStatic(stackApi)) {
            api.when(() -> stackApi.getMethod("byItemStack", ItemStack.class).invoke(null, packed)).thenReturn(custom);
            ItemMatcher matcher = ItemMatcher.detect(plugin);
            assertTrue(matcher.matches(packed, ref("ia:museum:brush"))); assertTrue(matcher.isCustom(packed));
            assertFalse(matcher.matches(packed, ItemRef.vanilla(Material.STONE)));
            assertFalse(matcher.matches(packed, ref("ia:museum:other")));
            assertTrue(matcher.matches(ordinary, ItemRef.vanilla(Material.DIRT)));
            assertFalse(matcher.isCustom(ordinary));
            when(enabled.isEnabled()).thenReturn(false);
            ItemMatcher disabled = ItemMatcher.detect(plugin);
            assertTrue(disabled.matches(packed, ItemRef.vanilla(Material.STONE)));
            assertFalse(disabled.matches(packed, ref("ia:museum:brush")));
        }
    }

    @Test public void itemsAdderCreationClonesTheApiTemplateAndReportsUnavailableItems() throws Exception {
        when(plugins.getPlugin("ItemsAdder")).thenReturn(enabled);
        ItemStack template = new ItemStack(Material.BRUSH, 2); Object custom = mock(stackApi);
        when(call(custom, "getItemStack")).thenReturn(template);
        try (var api = mockStatic(stackApi)) {
            api.when(() -> stackApi.getMethod("getInstance", String.class).invoke(null, "museum:brush")).thenReturn(custom);
            api.when(() -> stackApi.getMethod("getInstance", String.class).invoke(null, "museum:broken")).thenThrow(new IllegalStateException("reload failed"));
            ItemMatcher matcher = ItemMatcher.detect(plugin);
            ItemStack first = matcher.create(ref("ia:museum:brush")); first.setAmount(1);
            assertEquals(2, template.getAmount()); assertEquals(2, matcher.create(ref("ia:museum:brush")).getAmount());
            assertNotSame(template, first);
            assertEquals(Material.AIR, matcher.create(ref("ia:museum:missing")).getType());
            verify(logger).warning("Unknown ItemsAdder item: museum:missing");
            assertEquals(Material.AIR, matcher.create(ref("ia:museum:broken")).getType());
            verify(logger).warning(startsWith("Could not create ItemsAdder item museum:broken:"));
            when(call(custom, "getItemStack")).thenReturn(new ItemStack(Material.AIR));
            assertEquals(Material.AIR, matcher.create(ref("ia:museum:brush")).getType());
        }
    }

    @Test public void itemsAdderSupportsFurnitureEntitiesAndFallsBackToCustomBlocks() throws Exception {
        when(plugins.getPlugin("ItemsAdder")).thenReturn(enabled);
        Block furnitureBlock = mock(Block.class), customBlock = mock(Block.class); Entity entity = mock(Entity.class);
        Object furniture = mock(furnitureApi), placed = mock(blockApi);
        when(call(furniture, "getNamespacedID")).thenReturn("Museum:Cabinet");
        when(call(placed, "getNamespacedID")).thenReturn("museum:stone_cabinet");
        try (var furnitureStatic = mockStatic(furnitureApi); var blockStatic = mockStatic(blockApi)) {
            furnitureStatic.when(() -> furnitureApi.getMethod("byAlreadySpawned", Block.class).invoke(null, furnitureBlock)).thenReturn(furniture);
            furnitureStatic.when(() -> furnitureApi.getMethod("byAlreadySpawned", Entity.class).invoke(null, entity)).thenReturn(furniture);
            blockStatic.when(() -> blockApi.getMethod("byAlreadyPlaced", Block.class).invoke(null, customBlock)).thenReturn(placed);
            ItemMatcher matcher = ItemMatcher.detect(plugin);
            assertTrue(matcher.matchesPlaced(furnitureBlock, ref("ia:museum:cabinet")));
            assertTrue(matcher.matchesEntity(entity, ref("ia:museum:cabinet")));
            assertTrue(matcher.matchesPlaced(customBlock, ref("ia:museum:stone_cabinet")));
            assertFalse(matcher.matchesPlaced(customBlock, ref("ia:museum:cabinet")));
            assertFalse(matcher.matchesEntity(entity, ref("ia:museum:other")));
            // A failed furniture lookup must still permit the custom-block API fallback.
            furnitureStatic.when(() -> furnitureApi.getMethod("byAlreadySpawned", Block.class).invoke(null, customBlock)).thenThrow(new IllegalStateException("unloading"));
            assertTrue(matcher.matchesPlaced(customBlock, ref("ia:museum:stone_cabinet")));
        }
    }

    @Test public void itemsAdderLookupFailuresDoNotCrashOrdinaryItemMatching() throws Exception {
        when(plugins.getPlugin("ItemsAdder")).thenReturn(enabled); ItemStack stack = new ItemStack(Material.STONE);
        try (var api = mockStatic(stackApi)) {
            api.when(() -> stackApi.getMethod("byItemStack", ItemStack.class).invoke(null, stack)).thenThrow(new IllegalStateException("registry rebuilding"));
            ItemMatcher matcher = ItemMatcher.detect(plugin);
            assertFalse(matcher.matches(stack, ref("ia:museum:stone"))); assertFalse(matcher.isCustom(stack));
            assertTrue(matcher.matches(stack, ItemRef.vanilla(Material.STONE)));
        }
    }

    @Test public void mmoItemsMatchingUsesBothTypeAndIdAndNeverAcceptsCustomAsVanilla() throws Exception {
        when(plugins.getPlugin("MMOItems")).thenReturn(enabled); singleton.set(null, mock(mmoApi));
        ItemStack stack = new ItemStack(Material.BRUSH);
        try (var api = mockStatic(mmoApi)) {
            api.when(() -> mmoApi.getMethod("getTypeName", ItemStack.class).invoke(null, stack)).thenReturn("TOOL");
            api.when(() -> mmoApi.getMethod("getID", ItemStack.class).invoke(null, stack)).thenReturn("BRUSH");
            ItemMatcher matcher = ItemMatcher.detect(plugin);
            assertTrue(matcher.matches(stack, ref("mi:tool:brush"))); assertTrue(matcher.isCustom(stack));
            assertFalse(matcher.matches(stack, ref("mi:sword:brush"))); assertFalse(matcher.matches(stack, ref("mi:tool:pick")));
            assertFalse(matcher.matches(stack, ItemRef.vanilla(Material.BRUSH)));
            api.when(() -> mmoApi.getMethod("getID", ItemStack.class).invoke(null, stack)).thenThrow(new IllegalStateException("unavailable"));
            assertFalse(matcher.matches(stack, ref("mi:tool:brush")));
            api.when(() -> mmoApi.getMethod("getTypeName", ItemStack.class).invoke(null, stack)).thenThrow(new IllegalStateException("unavailable"));
            assertFalse(matcher.isCustom(stack));
        }
    }

    @Test public void mmoItemsCreationUsesTheTypeApiAndReturnsIndependentClones() throws Exception {
        when(plugins.getPlugin("MMOItems")).thenReturn(enabled);
        Object mmo = mock(mmoApi), types = mock(Class.forName("net.Indyuce.mmoitems.manager.TypeManager")), type = mock(typeApi);
        singleton.set(null, mmo); when(call(mmo, "getTypes")).thenReturn(types);
        when(types.getClass().getMethod("get", String.class).invoke(types, "TOOL")).thenReturn(type);
        ItemStack template = new ItemStack(Material.BRUSH, 2);
        when(mmoApi.getMethod("getItem", typeApi, String.class).invoke(mmo, type, "BRUSH")).thenReturn(template);
        ItemMatcher matcher = ItemMatcher.detect(plugin);
        ItemStack created = matcher.create(ref("mi:TOOL:BRUSH")); assertEquals(Material.BRUSH, created.getType());
        created.setAmount(1); assertEquals(2, template.getAmount()); assertNotSame(template, created);
        assertEquals(2, matcher.create(ref("mi:TOOL:BRUSH")).getAmount());
        mmoApi.getMethod("getItem", typeApi, String.class).invoke(verify(mmo, times(2)), type, "BRUSH");
        // The real API also exposes getItem(String, String); discovery must never select that overload.
        mmoApi.getMethod("getItem", String.class, String.class).invoke(verify(mmo, never()), anyString(), anyString());
        assertEquals(Material.AIR, matcher.create(ref("mi:MISSING:BRUSH")).getType());
        verify(logger).warning("Unknown MMOItems type: MISSING");
        assertEquals(Material.AIR, matcher.create(ref("mi:TOOL:MISSING")).getType());
        verify(logger).warning("Unknown MMOItems item: TOOL:MISSING");
        when(mmoApi.getMethod("getItem", typeApi, String.class).invoke(mmo, type, "BRUSH")).thenThrow(new IllegalStateException("reload"));
        assertEquals(Material.AIR, matcher.create(ref("mi:TOOL:BRUSH")).getType());
        verify(logger).warning(startsWith("Could not create MMOItems item TOOL:BRUSH:"));
    }

    @Test public void emptyHandsAndOrdinaryItemsNeverPassForPackTools() throws Exception {
        when(plugins.getPlugin("ItemsAdder")).thenReturn(enabled); when(plugins.getPlugin("MMOItems")).thenReturn(enabled);
        singleton.set(null, mock(mmoApi));
        ItemStack dirt = new ItemStack(Material.DIRT);
        // Both APIs answer null for a stack they do not own.
        try (var ia = mockStatic(stackApi); var mi = mockStatic(mmoApi)) {
            ItemMatcher matcher = ItemMatcher.detect(plugin);
            for (ItemRef tool : new ItemRef[]{ref("ia:museum:brush"), ref("mi:tool:brush")}) {
                assertFalse(matcher.matches(null, tool)); assertFalse(matcher.matches(dirt, tool));
            }
            assertTrue(matcher.matches(null, ItemRef.air())); assertTrue(matcher.matches(dirt, ItemRef.vanilla(Material.DIRT)));
            assertFalse(matcher.isCustom(dirt));
            mi.when(() -> mmoApi.getMethod("getTypeName", ItemStack.class).invoke(null, dirt)).thenReturn("TOOL");
            assertFalse(matcher.matches(dirt, ref("mi:tool:brush")));
        }
    }

    @Test public void packObjectsWithoutAnIdAreNeitherCustomItemsNorCabinets() throws Exception {
        when(plugins.getPlugin("ItemsAdder")).thenReturn(enabled);
        ItemStack stack = new ItemStack(Material.STONE); Block block = mock(Block.class);
        Object custom = mock(stackApi), furniture = mock(furnitureApi);
        try (var api = mockStatic(stackApi); var furnitureStatic = mockStatic(furnitureApi); var blockStatic = mockStatic(blockApi)) {
            api.when(() -> stackApi.getMethod("byItemStack", ItemStack.class).invoke(null, stack)).thenReturn(custom);
            furnitureStatic.when(() -> furnitureApi.getMethod("byAlreadySpawned", Block.class).invoke(null, block)).thenReturn(furniture);
            ItemMatcher matcher = ItemMatcher.detect(plugin);
            assertFalse(matcher.isCustom(stack)); assertTrue(matcher.matches(stack, ItemRef.vanilla(Material.STONE)));
            assertFalse(matcher.matchesPlaced(block, ref("ia:museum:cabinet")));
        }
    }

    @Test public void packApisReturningNothingOrAirCreateNothing() throws Exception {
        when(plugins.getPlugin("ItemsAdder")).thenReturn(enabled); when(plugins.getPlugin("MMOItems")).thenReturn(enabled);
        Object custom = mock(stackApi), mmo = mock(mmoApi), types = mock(Class.forName("net.Indyuce.mmoitems.manager.TypeManager")), type = mock(typeApi);
        singleton.set(null, mmo); when(call(mmo, "getTypes")).thenReturn(types);
        when(types.getClass().getMethod("get", String.class).invoke(types, "TOOL")).thenReturn(type);
        when(mmoApi.getMethod("getItem", typeApi, String.class).invoke(mmo, type, "BRUSH")).thenReturn(new ItemStack(Material.AIR));
        try (var api = mockStatic(stackApi)) {
            api.when(() -> stackApi.getMethod("getInstance", String.class).invoke(null, "museum:brush")).thenReturn(custom);
            ItemMatcher matcher = ItemMatcher.detect(plugin);
            assertEquals(Material.AIR, matcher.create(ref("ia:museum:brush")).getType());
            assertEquals(Material.AIR, matcher.create(ref("mi:TOOL:BRUSH")).getType());
            verify(logger).warning("Unknown MMOItems item: TOOL:BRUSH");
        }
    }

    private ItemRef ref(String value) { return ItemRef.parse(plugin, value).orElseThrow(); }
    private static Object call(Object target, String method) throws Exception { return target.getClass().getMethod(method).invoke(target); }
}
