package net.tfminecraft.archaeo.item;

import net.tfminecraft.archaeo.config.BreakShape;
import net.tfminecraft.archaeo.config.ExcavationTool;
import net.tfminecraft.archaeo.excavation.DigTools;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.ToolComponent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.inventory.meta.ItemMetaMock;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class DigToolsTest {
    private DigTools tools;

    @Before
    public void setup() {
        MockBukkit.mock();
        tools = new DigTools();
    }

    @After
    public void teardown() { MockBukkit.unmock(); }

    @Test
    public void firstMatchingProfileWinsAndConfiguredOrderIsSnapshot() {
        ExcavationTool first = profile("first", ItemRef.vanilla(Material.IRON_PICKAXE));
        ExcavationTool second = profile("second", ItemRef.vanilla(Material.IRON_PICKAXE));
        List<ExcavationTool> configured = new ArrayList<>(List.of(first, second));
        tools.setProfiles(configured);
        configured.clear();
        assertSame(first, tools.match(new ItemStack(Material.IRON_PICKAXE)).orElseThrow());
        assertFalse(tools.isAllowed(new ItemStack(Material.STICK)));
        assertFalse(tools.isAllowed(null));
    }

    @Test
    public void resettingProfilesRestoresPackagedEmptyHandAndTools() {
        tools.setProfiles(List.of(profile("limited", ItemRef.vanilla(Material.BRUSH))));
        assertFalse(tools.isAllowed(new ItemStack(Material.IRON_PICKAXE)));
        tools.setProfiles(List.of());
        assertTrue(tools.isAllowed(null));
        assertTrue(tools.isAllowed(new ItemStack(Material.IRON_PICKAXE)));
        tools.setProfiles(null);
        assertTrue(tools.isAllowed(new ItemStack(Material.WOODEN_SHOVEL)));
        assertFalse(tools.isAllowed(new ItemStack(Material.STICK)));
    }

    @Test
    public void giveTokensDeduplicateKeepOrderAndNeverOfferAir() {
        tools.setProfiles(List.of(profile("a", ItemRef.air(), ItemRef.vanilla(Material.IRON_PICKAXE)),
                profile("b", ItemRef.vanilla(Material.IRON_PICKAXE), ItemRef.vanilla(Material.WOODEN_SHOVEL))));
        assertEquals(List.of("IRON_PICKAXE", "WOODEN_SHOVEL"), tools.giveTokens());
        assertEquals(Material.IRON_PICKAXE, tools.sampleStack().getType());
        assertEquals(Material.WOODEN_SHOVEL, tools.createByToken(" wooden_shovel ").orElseThrow().getType());
        for (String token : new String[] {null, " ", "AIR", "DIAMOND_PICKAXE"}) {
            assertTrue(tools.createByToken(token).isEmpty());
        }
    }

    @Test
    public void unavailablePackToolsAreSkippedAndNeverGivenAsAir() {
        ItemRef unavailable = new ItemRef(ItemRef.Kind.ITEMSADDER, "pack:missing", "");
        tools.setProfiles(List.of(profile("pack", ItemRef.air(), unavailable),
                profile("fallback", ItemRef.vanilla(Material.IRON_PICKAXE))));
        assertEquals(Material.IRON_PICKAXE, tools.sampleStack().getType());
        assertTrue(tools.createByToken("itemsadder:pack:missing").isEmpty());
        tools.setProfiles(List.of(profile("only-pack", unavailable)));
        assertEquals(Material.STONE_PICKAXE, tools.sampleStack().getType());
    }

    @Test
    public void customMatcherServesConfiguredToolAndResetReturnsVanillaMatching() {
        ItemRef custom = new ItemRef(ItemRef.Kind.ITEMSADDER, "pack:pick", "");
        ItemMatcher matcher = mock(ItemMatcher.class);
        ItemStack stack = new ItemStack(Material.WOODEN_PICKAXE);
        when(matcher.create(custom)).thenReturn(stack);
        when(matcher.matches(stack, custom)).thenReturn(true);
        tools.setProfiles(List.of(profile("custom", custom)));
        tools.setMatcher(matcher);
        assertTrue(tools.isAllowed(stack));
        assertSame(stack, tools.sampleStack());
        assertSame(stack, tools.create(custom));
        assertSame(stack, tools.createByToken(custom.commandToken()).orElseThrow());
        tools.setMatcher(null);
        assertFalse(tools.isAllowed(stack));
        assertTrue(tools.createByToken(custom.commandToken()).isEmpty());
    }

    @Test
    public void restoringLegacyLockPreservesPackSpeedNameAndOtherPluginsAttributes() {
        ToolMeta original = new ToolMeta(13f);
        original.setDisplayName("Survey pick");
        AttributeModifier legacy = modifier("archaeo", "no_vanilla_mine", -1);
        AttributeModifier pack = modifier("mmoitems", "no_vanilla_mine", 0.5);
        AttributeModifier ownOther = modifier("archaeo", "other_bonus", 0.3);
        original.addAttributeModifier(Attribute.BLOCK_BREAK_SPEED, legacy);
        original.addAttributeModifier(Attribute.BLOCK_BREAK_SPEED, pack);
        original.addAttributeModifier(Attribute.BLOCK_BREAK_SPEED, ownOther);
        SnapshotStack stack = new SnapshotStack(Material.IRON_PICKAXE, original);
        tools.restoreVanillaSpeedIfSealed(stack);
        assertEquals(13f, stack.getItemMeta().getTool().getDefaultMiningSpeed(), 0);
        assertEquals("Survey pick", stack.getItemMeta().getDisplayName());
        assertEquals(List.of(pack, ownOther), List.copyOf(stack.getItemMeta().getAttributeModifiers(Attribute.BLOCK_BREAK_SPEED)));
        assertEquals(1, stack.writes);
        tools.restoreVanillaSpeedIfSealed(stack);
        assertEquals(1, stack.writes);
    }

    @Test
    public void sealedComponentRestoresMaterialDefaultAndRemainsStableOnLaterSyncs() {
        ToolMeta original = new ToolMeta(0f);
        original.setDisplayName("Old excavation pick");
        SnapshotStack stack = new SnapshotStack(Material.IRON_PICKAXE, original);
        // Paper delegates default metadata internally; MockBukkit lacks its ToolComponent.
        // Supply the metadata result of the vanilla prototype constructed by this API call.
        try (MockedConstruction<ItemStack> prototypes = mockConstruction(ItemStack.class, (prototype, context) -> {
            assertEquals(Material.IRON_PICKAXE, context.arguments().getFirst());
            when(prototype.getItemMeta()).thenReturn(new ToolMeta(6f));
        })) {
            tools.restoreVanillaSpeedIfSealed(stack);
            assertEquals(1, prototypes.constructed().size());
        }
        assertEquals(6f, stack.getItemMeta().getTool().getDefaultMiningSpeed(), 0);
        assertEquals("Old excavation pick", stack.getItemMeta().getDisplayName());
        assertEquals(1, stack.writes);
        tools.restoreVanillaSpeedIfSealed(stack);
        assertEquals(1, stack.writes);
    }

    @Test
    public void healthyOrUnconfiguredItemsAreNeverRewritten() {
        SnapshotStack healthy = new SnapshotStack(Material.IRON_PICKAXE, new ToolMeta(9f));
        SnapshotStack unrelated = new SnapshotStack(Material.STICK, new ToolMeta(0f));
        tools.restoreVanillaSpeedIfSealed(healthy);
        tools.restoreVanillaSpeedIfSealed(unrelated);
        tools.restoreVanillaSpeedIfSealed(null);
        tools.restoreVanillaSpeedIfSealed(new ItemStack(Material.AIR));
        assertEquals(0, healthy.writes);
        assertEquals(0, unrelated.writes);
        assertEquals(9f, healthy.getItemMeta().getTool().getDefaultMiningSpeed(), 0);
        assertEquals(0f, unrelated.getItemMeta().getTool().getDefaultMiningSpeed(), 0);
    }

    private static ExcavationTool profile(String id, ItemRef... refs) {
        return new ExcavationTool(id, List.of(refs), 2, null, 0, null, null,
                1, 2, BreakShape.DOWN, 1, 20);
    }

    private static AttributeModifier modifier(String namespace, String key, double amount) {
        return new AttributeModifier(new NamespacedKey(namespace, key), amount,
                AttributeModifier.Operation.MULTIPLY_SCALAR_1);
    }

    /** Item metadata and component reads return detached snapshots, as Bukkit requires. */
    private static class ToolMeta extends ItemMetaMock {
        private float speed;
        ToolMeta(float speed) { this.speed = speed; }
        ToolMeta(ToolMeta source) { super(source); this.speed = source.speed; }
        @Override public ToolComponent getTool() {
            ToolComponent component = mock(ToolComponent.class);
            when(component.getDefaultMiningSpeed()).thenReturn(speed);
            return component;
        }
        @Override public void setTool(ToolComponent component) { speed = component.getDefaultMiningSpeed(); }
        @Override public ToolMeta clone() { return new ToolMeta(this); }
    }

    private static class SnapshotStack extends ItemStack {
        private ToolMeta snapshot;
        private int writes;
        SnapshotStack(Material material, ToolMeta initial) {
            super(material);
            snapshot = initial.clone();
        }
        @Override public boolean hasItemMeta() { return snapshot != null; }
        @Override public ItemMeta getItemMeta() { return snapshot.clone(); }
        @Override public boolean setItemMeta(ItemMeta meta) {
            snapshot = ((ToolMeta) meta).clone();
            writes++;
            return true;
        }
    }
}
