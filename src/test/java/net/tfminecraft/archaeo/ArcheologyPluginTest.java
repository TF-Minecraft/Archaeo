package net.tfminecraft.archaeo;

import net.tfminecraft.archaeo.item.ItemMatcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.Assert.*;

public class ArcheologyPluginTest {
    private ServerMock server;

    @Before public void setUp() { server = MockBukkit.mock(); }
    @After public void tearDown() { MockBukkit.unmock(); }

    @Test
    public void loadsPackagedCatalogsBindsCommandsAndDisablesCleanly() {
        ArcheologyPlugin plugin = MockBukkit.load(ArcheologyPlugin.class);
        assertTrue(plugin.isEnabled());
        assertFalse(plugin.catalogs().artifacts().isEmpty());
        assertTrue(plugin.sites().all().isEmpty());
        assertNotNull(plugin.generator());
        assertNotNull(plugin.autoRuins());
        assertNotNull(plugin.trackerItem());
        assertNotNull(plugin.prospectItem());
        assertNotNull(plugin.establishItem());
        assertNotNull(plugin.sketch());
        assertNotNull(plugin.museum());
        assertNotNull(plugin.sketchSupplies());
        assertNotNull(plugin.getCommand("archaeo").getExecutor());
        assertNotNull(plugin.getCommand("archaeo").getTabCompleter());
        plugin.bindItemMatcher(null);
        plugin.bindItemMatcher(ItemMatcher.vanillaOnly());
        server.getScheduler().performTicks(40);
        server.getPluginManager().disablePlugin(plugin);
        assertFalse(plugin.isEnabled());
        server.getScheduler().performTicks(40);
        plugin.onDisable();
    }

    @Test
    public void brokenCatalogueFailsStartupAndTheFollowingDisableShutsDownCleanly() throws Exception {
        ArcheologyPlugin plugin = (ArcheologyPlugin) server.getPluginManager().loadPlugin(ArcheologyPlugin.class, new Object[0]);
        java.nio.file.Files.createDirectories(plugin.getDataFolder().toPath());
        java.nio.file.Files.writeString(new java.io.File(plugin.getDataFolder(), "interest.yml").toPath(), "interest-levels: {}\n");
        Throwable failure = assertThrows(Throwable.class, () -> server.getPluginManager().enablePlugin(plugin));
        while (failure.getCause() != null) failure = failure.getCause();
        assertEquals("Missing interest-levels.low", failure.getMessage());
        // Paper disables a plugin whose onEnable threw; nothing but the catalogue was built yet.
        server.getPluginManager().disablePlugin(plugin);
        assertFalse(plugin.isEnabled());
        assertNull(plugin.sites());
        assertFalse(plugin.getCommand("archaeo").getExecutor() instanceof net.tfminecraft.archaeo.command.ArchaeoCommand);
        server.getScheduler().performTicks(40);
    }
}
