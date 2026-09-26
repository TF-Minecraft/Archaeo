package net.tfminecraft.archaeo.item;

import net.tfminecraft.archaeo.ArcheologyPlugin;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * ItemsAdder and MMOItems report enabled but their API classes cannot be loaded (a broken or
 * incompatible install). Holds with or without the pack-api-tests jars, because the loader hides them.
 */
public class MissingPackApiTest {
    private ArcheologyPlugin plugin;
    private PluginManager plugins;
    private Logger logger;
    private ClassLoader withoutPacks;

    @Before public void setup() {
        MockBukkit.mock();
        plugin = mock(ArcheologyPlugin.class); Server server = mock(Server.class); plugins = mock(PluginManager.class);
        logger = mock(Logger.class); Plugin enabled = mock(Plugin.class); when(enabled.isEnabled()).thenReturn(true);
        when(plugin.getServer()).thenReturn(server); when(server.getPluginManager()).thenReturn(plugins); when(plugin.getLogger()).thenReturn(logger);
        when(plugins.getPlugin("ItemsAdder")).thenReturn(enabled); when(plugins.getPlugin("MMOItems")).thenReturn(enabled);
        withoutPacks = new HiddenPackApiLoader("dev.lone.", "net.Indyuce.");
    }
    @After public void teardown() { MockBukkit.unmock(); }

    @Test public void unbindablePackApisWarnOnceEachAndLeaveVanillaMatchingWorking() throws Exception {
        Class<?> matcherType = withoutPacks.loadClass(ItemMatcher.class.getName()), refType = withoutPacks.loadClass(ItemRef.class.getName());
        Object matcher = matcherType.getMethod("detect", org.bukkit.plugin.java.JavaPlugin.class).invoke(null, plugin);
        verify(logger).log(eq(Level.WARNING), eq("ItemsAdder is enabled but its API could not be bound."), any(ClassNotFoundException.class));
        verify(logger).warning("ItemsAdder furniture API is missing; placed cabinets cannot match pack furniture.");
        verify(logger).log(eq(Level.WARNING), eq("MMOItems is enabled but its API could not be bound."), any(ClassNotFoundException.class));
        var matches = matcherType.getMethod("matches", ItemStack.class, refType);
        Object stone = refType.getMethod("vanilla", Material.class).invoke(null, Material.STONE);
        Object packTool = refType.getMethod("parse", org.bukkit.plugin.java.JavaPlugin.class, String.class).invoke(null, null, "ia:museum:brush");
        assertEquals(true, matches.invoke(matcher, new ItemStack(Material.STONE), stone));
        assertEquals(false, matches.invoke(matcher, new ItemStack(Material.BRUSH), ((java.util.Optional<?>) packTool).orElseThrow()));
        assertEquals(false, matcherType.getMethod("isCustom", ItemStack.class).invoke(matcher, new ItemStack(Material.BRUSH)));
    }

    @Test public void furnitureHookStaysSilentWhenItemsAdderEventsAreAbsent() throws Exception {
        withoutPacks.loadClass(PackPluginHook.class.getName()).getMethod("register", ArcheologyPlugin.class).invoke(null, plugin);
        verify(plugins, never()).registerEvent(any(), any(), any(), any(), any(), anyBoolean());
        verifyNoInteractions(logger);
    }
}
