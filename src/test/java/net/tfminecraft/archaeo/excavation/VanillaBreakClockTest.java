package net.tfminecraft.archaeo.excavation;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.ToolComponent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.inventory.meta.ItemMetaMock;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class VanillaBreakClockTest {
    private static final NamespacedKey LOCK = new NamespacedKey("archaeo", "hold");
    private Player player;
    private PlayerInventory inventory;
    private Block block;

    @Before
    public void setup() {
        MockBukkit.mock();
        player = mock(Player.class);
        inventory = mock(PlayerInventory.class);
        block = mock(Block.class);
        when(player.getInventory()).thenReturn(inventory);
        when(block.getBreakSpeed(player)).thenReturn(0.125f);
    }

    @After
    public void teardown() {
        MockBukkit.unmock();
    }

    @Test
    public void absentAttributeOrHeldStackStillSamplesVanillaAndScales() {
        assertEquals(0.125f, VanillaBreakClock.tickProgress(player, block, LOCK, null, null), 0);
        assertEquals(0.25f, VanillaBreakClock.tickProgress(player, block, LOCK, 5f, 2f), 0);
        assertEquals(0f, VanillaBreakClock.tickProgress(player, block, LOCK, null, 0f), 0);
        when(block.getBreakSpeed(player)).thenReturn(1.5f);
        assertEquals(1.5f, VanillaBreakClock.tickProgress(player, block, LOCK, null, null), 0);
    }

    @Test
    public void airAndMetadataLessStacksAreNeverPatched() {
        ItemStack air = new ItemStack(Material.AIR);
        when(inventory.getItemInMainHand()).thenReturn(air);
        assertEquals(0.125f, VanillaBreakClock.tickProgress(player, block, LOCK, 8f, null), 0);
        assertEquals(Material.AIR, air.getType());
        ItemStack withoutMeta = mock(ItemStack.class);
        when(withoutMeta.getType()).thenReturn(Material.STONE);
        when(inventory.getItemInMainHand()).thenReturn(withoutMeta);
        assertEquals(0.125f, VanillaBreakClock.tickProgress(player, block, LOCK, 8f, null), 0);
        verify(withoutMeta, never()).setItemMeta(any());
    }

    @Test
    public void nullOverridePreservesTheHeldMetadata() {
        ItemStack held = tool();
        ItemMeta original = held.getItemMeta();
        when(inventory.getItemInMainHand()).thenReturn(held);
        when(block.getBreakSpeed(player)).thenAnswer(invocation -> {
            assertEquals(original, held.getItemMeta());
            return 0.125f;
        });
        assertEquals(0.125f, VanillaBreakClock.tickProgress(player, block, LOCK, null, null), 0);
        assertEquals(original, held.getItemMeta());
    }

    @Test
    public void overrideExistsDuringSampleAndOriginalToolIsRestored() {
        ItemStack held = tool();
        ItemMeta original = held.getItemMeta().clone();
        when(inventory.getItemInMainHand()).thenReturn(held);
        when(block.getBreakSpeed(player)).thenAnswer(invocation -> {
            assertEquals(9f, held.getItemMeta().getTool().getDefaultMiningSpeed(), 0);
            assertEquals(original.getDisplayName(), held.getItemMeta().getDisplayName());
            return 0.2f;
        });
        assertEquals(0.6f, VanillaBreakClock.tickProgress(player, block, LOCK, 9f, 3f), 0.000001f);
        assertEquals(original, held.getItemMeta());
        assertEquals(2f, held.getItemMeta().getTool().getDefaultMiningSpeed(), 0);
    }

    @Test
    public void onlyMatchingLockIsRemovedAndItIsRestoredAfterSampling() {
        AttributeInstance speed = mock(AttributeInstance.class);
        AttributeModifier unrelated = modifier("other", "speed");
        AttributeModifier lock = modifier("archaeo", "hold");
        when(player.getAttribute(Attribute.BLOCK_BREAK_SPEED)).thenReturn(speed);
        when(speed.getModifiers()).thenReturn(List.of(unrelated, lock));
        when(block.getBreakSpeed(player)).thenAnswer(invocation -> {
            verify(speed).removeModifier(lock);
            verify(speed, never()).removeModifier(unrelated);
            verify(speed, never()).addModifier(lock);
            return 0.125f;
        });
        assertEquals(0.125f, VanillaBreakClock.tickProgress(player, block, LOCK, null, null), 0);
        verify(speed).addModifier(lock);
        verify(speed, never()).addModifier(unrelated);
    }

    @Test
    public void noMatchingLockLeavesAllAttributesUntouched() {
        AttributeInstance speed = mock(AttributeInstance.class);
        when(player.getAttribute(Attribute.BLOCK_BREAK_SPEED)).thenReturn(speed);
        when(speed.getModifiers()).thenReturn(List.of(modifier("other", "speed")));
        assertEquals(0.125f, VanillaBreakClock.tickProgress(player, block, LOCK, null, null), 0);
        verify(speed, never()).removeModifier(any(AttributeModifier.class));
        verify(speed, never()).addModifier(any(AttributeModifier.class));
    }

    @Test
    public void failedSampleRestoresBothToolAndLockAndPropagatesOriginalFailure() {
        ItemStack held = tool();
        ItemMeta original = held.getItemMeta().clone();
        when(inventory.getItemInMainHand()).thenReturn(held);
        AttributeInstance speed = mock(AttributeInstance.class);
        AttributeModifier lock = modifier("archaeo", "hold");
        when(player.getAttribute(Attribute.BLOCK_BREAK_SPEED)).thenReturn(speed);
        when(speed.getModifiers()).thenReturn(List.of(lock));
        IllegalStateException failure = new IllegalStateException("sample failed");
        when(block.getBreakSpeed(player)).thenAnswer(invocation -> {
            assertEquals(7f, held.getItemMeta().getTool().getDefaultMiningSpeed(), 0);
            verify(speed).removeModifier(lock);
            throw failure;
        });
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> VanillaBreakClock.tickProgress(player, block, LOCK, 7f, 2f)));
        assertEquals(original, held.getItemMeta());
        assertEquals(2f, held.getItemMeta().getTool().getDefaultMiningSpeed(), 0);
        verify(speed).addModifier(lock);
    }

    private static AttributeModifier modifier(String namespace, String key) {
        return new AttributeModifier(new NamespacedKey(namespace, key), -1,
                AttributeModifier.Operation.MULTIPLY_SCALAR_1);
    }

    private static ItemStack tool() {
        // MockBukkit does not implement tool components yet; model the API snapshot contract.
        ItemStack held = mock(ItemStack.class);
        ToolMeta original = new ToolMeta(2f);
        original.setDisplayName("Survey pick");
        AtomicReference<ToolMeta> installed = new AtomicReference<>(original);
        when(held.getType()).thenReturn(Material.IRON_PICKAXE);
        when(held.getItemMeta()).thenAnswer(invocation -> installed.get().clone());
        when(held.setItemMeta(any())).thenAnswer(invocation -> {
            ToolMeta replacement = invocation.getArgument(0);
            installed.set(replacement.clone());
            return true;
        });
        return held;
    }

    private static class ToolMeta extends ItemMetaMock {
        private float speed;
        ToolMeta(float speed) { this.speed = speed; }
        ToolMeta(ToolMeta source) { super(source); this.speed = source.speed; }
        @Override public ToolComponent getTool() { return toolComponent(speed); }
        @Override public void setTool(ToolComponent component) { speed = component.getDefaultMiningSpeed(); }
        @Override public ToolMeta clone() { return new ToolMeta(this); }
    }

    private static ToolComponent toolComponent(float initialSpeed) {
        ToolComponent component = mock(ToolComponent.class);
        AtomicReference<Float> speed = new AtomicReference<>(initialSpeed);
        when(component.getDefaultMiningSpeed()).thenAnswer(invocation -> speed.get());
        doAnswer(invocation -> {
            speed.set(invocation.getArgument(0));
            return null;
        }).when(component).setDefaultMiningSpeed(anyFloat());
        return component;
    }
}
