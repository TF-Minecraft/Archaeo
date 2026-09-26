package net.tfminecraft.archaeo.item;

import org.bukkit.Material;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ItemRefTest {
    @Before public void setUp() { MockBukkit.mock(); }
    @After public void tearDown() { MockBukkit.unmock(); }
    @Test
    public void parsesVanillaAndEmptyHandAliases() {
        for (String token : List.of("AIR", "hand", " empty ", "EMPTY_HAND", "minecraft:air")) {
            assertEquals(ItemRef.air(), ItemRef.parse(null, token).orElseThrow());
        }
        assertEquals(ItemRef.air(), ItemRef.vanilla(null));
        assertEquals(ItemRef.air(), ItemRef.vanilla(Material.CAVE_AIR));
        for (String token : List.of("STONE", " stone ", "minecraft:stone")) {
            ItemRef ref = ItemRef.parse(null, token).orElseThrow();
            assertEquals(ItemRef.Kind.VANILLA, ref.kind());
            assertEquals(Material.STONE, ref.vanillaMaterial());
            assertEquals("STONE", ref.commandToken());
            assertFalse(ref.isAir());
        }
        assertTrue(ItemRef.air().isAir());
        for (String special : List.of("ITEM_DISPLAY", "SHELF")) {
            assertEquals(special, ItemRef.parse(null, special).orElseThrow().commandToken());
            assertEquals(Material.AIR, ItemRef.parse(null, special).orElseThrow().vanillaMaterial());
        }
    }

    @Test
    public void parsesPackIdsWithAliasesAndPreservesMmoItemCase() {
        for (String token : List.of("itemsadder:Pack:Pot", "IA:Pack:Pot", "Pack:Pot")) {
            ItemRef ref = ItemRef.parse(null, token).orElseThrow();
            assertEquals(new ItemRef(ItemRef.Kind.ITEMSADDER, "pack:pot", ""), ref);
            assertEquals("itemsadder:pack:pot", ref.commandToken());
            assertEquals(Material.AIR, ref.vanillaMaterial());
            assertFalse(ref.isAir());
        }
        for (String token : List.of("mmoitems:tool:FinePick", "MI: tool : FinePick")) {
            ItemRef ref = ItemRef.parse(null, token).orElseThrow();
            assertEquals(new ItemRef(ItemRef.Kind.MMOITEMS, "TOOL", "FinePick"), ref);
            assertEquals("mmoitems:TOOL:FinePick", ref.commandToken());
        }
    }

    @Test
    public void packIdsWhoseLettersSpellAVanillaMaterialStayPackItems() {
        // Regression: Material.matchMaterial drops the colon, so these once parsed as GLOWSTONE,
        // SANDSTONE and SNOWBALL and let the vanilla item stand in for the pack one.
        for (String token : List.of("glow:stone", "Sand:Stone", "snow:ball")) {
            ItemRef ref = ItemRef.parse(null, token).orElseThrow();
            assertEquals(token, ItemRef.Kind.ITEMSADDER, ref.kind());
            assertEquals(token.toLowerCase(java.util.Locale.ROOT), ref.primary());
            assertEquals(Material.AIR, ref.vanillaMaterial());
        }
        assertEquals(Material.GLOWSTONE, ItemRef.parse(null, "minecraft:glowstone").orElseThrow().vanillaMaterial());
        JavaPlugin plugin = mock(JavaPlugin.class);
        Logger logger = mock(Logger.class);
        when(plugin.getLogger()).thenReturn(logger);
        for (String token : List.of("stone:", "ia:pack:", "itemsadder::pot")) {
            assertTrue(token, ItemRef.parse(plugin, token).isEmpty());
        }
        verify(logger, times(3)).warning(startsWith("Invalid ItemsAdder id"));
    }

    @Test
    public void invalidItemsAreOmittedAndWarningsAreOptional() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Logger logger = mock(Logger.class);
        when(plugin.getLogger()).thenReturn(logger);
        for (String token : List.of("missing_material", "ia:", "itemsadder:pot", "mi:", "mi::pot", "mi:tool", "mi:tool:")) {
            assertTrue(token, ItemRef.parse(null, token).isEmpty());
            assertTrue(token, ItemRef.parse(plugin, token).isEmpty());
        }
        verify(logger, times(7)).warning(anyString());
        assertTrue(ItemRef.parse(plugin, null).isEmpty());
        assertTrue(ItemRef.parse(plugin, " ").isEmpty());
        ItemRef fallback = ItemRef.vanilla(Material.BRICK);
        assertSame(fallback, ItemRef.parseOr(null, "missing", fallback));
        assertEquals(ItemRef.vanilla(Material.STONE), ItemRef.parseOr(null, "STONE", fallback));
    }

    @Test
    public void listParsersFilterInvalidEntriesAndOnlyParseAllAddsDefaults() {
        assertTrue(ItemRef.parseList(null, null).isEmpty());
        assertTrue(ItemRef.parseList(null, List.of()).isEmpty());
        assertTrue(ItemRef.parseYamlList(null, null).isEmpty());
        assertTrue(ItemRef.parseYamlList(null, List.of()).isEmpty());
        assertEquals(ItemRef.excavationDefaults(), ItemRef.parseAll(null, List.of("unknown")));
        List<ItemRef> expected = List.of(ItemRef.vanilla(Material.STONE), ItemRef.air());
        assertEquals(expected, ItemRef.parseAll(null, Arrays.asList("STONE", null, "unknown", "hand")));
        assertEquals(expected, ItemRef.parseYamlList(null, Arrays.asList("STONE", null, "unknown", "hand")));
        assertThrows(UnsupportedOperationException.class, () -> ItemRef.parseList(null, List.of("STONE")).clear());
        assertEquals(7, ItemRef.pickaxes().size());
        assertEquals(7, ItemRef.shovels().size());
        assertTrue(ItemRef.pickaxes().stream().allMatch(r -> r.primary().endsWith("_PICKAXE")));
        assertTrue(ItemRef.shovels().stream().allMatch(r -> r.primary().endsWith("_SHOVEL")));
        assertEquals(15, ItemRef.excavationDefaults().size());
        assertEquals(ItemRef.air(), ItemRef.excavationDefaults().getFirst());
        assertThrows(UnsupportedOperationException.class, () -> ItemRef.excavationDefaults().clear());
    }

    @Test
    public void reconstructsScalarAndNestedYamlTokens() {
        assertNull(ItemRef.yamlToken(null));
        assertNull(ItemRef.yamlToken("  "));
        assertEquals("stone", ItemRef.yamlToken(" stone "));
        assertEquals("12", ItemRef.yamlToken(12));
        assertEquals("true", ItemRef.yamlToken(true));
        assertNull(ItemRef.yamlToken(Map.of()));
        assertNull(ItemRef.yamlToken(Map.of("one", "a", "two", "b")));
        assertNull(ItemRef.yamlToken(Map.of(" ", " ")));
        assertEquals("STONE", ItemRef.yamlToken(Map.of(" STONE ", " ")));
        assertEquals("mi:TOOL:pick", ItemRef.yamlToken(Map.of("mi", Map.of("TOOL", "pick"))));
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("mi.TOOL", "pick");
        assertEquals("mi:TOOL:pick", ItemRef.yamlToken(config));
        assertEquals("custom", ItemRef.yamlToken(new Object() {
            @Override public String toString() { return " custom "; }
        }));
        assertEquals(List.of(new ItemRef(ItemRef.Kind.MMOITEMS, "TOOL", "pick")),
                ItemRef.parseYamlList(null, List.of(Map.of("mi", Map.of("TOOL", "pick")))));
    }
}
