package net.tfminecraft.archaeo.excavation;

import net.tfminecraft.archaeo.item.BrushItem;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class RecoverListenerTest {
    private ServerMock server;
    private RecoverService recover;
    private RecoverListener listener;
    private Player player;
    private PlayerInventory inventory;
    private Block block;
    private ItemStack brushStack;

    @Before
    public void setup() {
        server = MockBukkit.mock();
        recover = mock(RecoverService.class);
        BrushItem brush = mock(BrushItem.class);
        listener = new RecoverListener(brush, recover);
        player = mock(Player.class);
        inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        block = server.addSimpleWorld("listener").getBlockAt(8, 40, 8);
        brushStack = new ItemStack(Material.BRUSH);
        when(brush.isBrush(any())).thenAnswer(invocation -> {
            ItemStack stack = invocation.getArgument(0);
            return stack != null && stack.getType() == Material.BRUSH;
        });
        server.getPluginManager().registerEvents(listener, MockBukkit.createMockPlugin());
    }

    @After
    public void teardown() {
        MockBukkit.unmock();
    }

    @Test
    public void mainHandBrushClickBeginsRecoveryWithoutCancellingVanillaUse() {
        PlayerInteractEvent event = click(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND, brushStack, block);
        server.getPluginManager().callEvent(event);
        verify(recover).begin(player, block);
        assertFalse(event.isCancelled());
    }

    @Test
    public void alreadyCancelledInteractionStillStartsRecoveryWithoutChangingCancellation() {
        PlayerInteractEvent event = click(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND, brushStack, block);
        event.setCancelled(true);
        server.getPluginManager().callEvent(event);
        verify(recover).begin(player, block);
        assertTrue(event.isCancelled());
    }

    @Test
    public void otherActionsOffhandAndNonBrushesDoNotStartRecovery() {
        listener.onInteract(click(Action.LEFT_CLICK_BLOCK, EquipmentSlot.HAND, brushStack, block));
        listener.onInteract(click(Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND, brushStack, null));
        listener.onInteract(click(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.OFF_HAND, brushStack, block));
        listener.onInteract(click(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND, new ItemStack(Material.STICK), block));
        listener.onInteract(click(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND, null, block));
        // Vanilla always sends a block with a block click, but other plugins call this event too and
        // the Bukkit constructor accepts a null block, so such a click must be ignored without throwing.
        listener.onInteract(click(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND, brushStack, null));
        verifyNoInteractions(recover);
    }

    @Test
    public void hotbarBrushStartsWatchingAndSwitchingAwayCancels() {
        when(inventory.getItem(1)).thenReturn(brushStack);
        server.getPluginManager().callEvent(new PlayerItemHeldEvent(player, 0, 1));
        verify(recover).watch(player);
        when(inventory.getItem(2)).thenReturn(new ItemStack(Material.STICK));
        server.getPluginManager().callEvent(new PlayerItemHeldEvent(player, 1, 2));
        verify(recover).cancel(player);
    }

    @Test
    public void cancelledHotbarChangeDoesNotInterruptTheChannel() {
        PlayerItemHeldEvent event = new PlayerItemHeldEvent(player, 0, 1);
        event.setCancelled(true);
        server.getPluginManager().callEvent(event);
        verifyNoInteractions(recover);
    }

    @Test
    public void swapsDropsAndQuitsCancelExistingChannel() {
        server.getPluginManager().callEvent(new PlayerSwapHandItemsEvent(player, brushStack, null));
        server.getPluginManager().callEvent(new PlayerDropItemEvent(player, mock(Item.class)));
        server.getPluginManager().callEvent(new PlayerQuitEvent(player, "left"));
        verify(recover, times(3)).cancel(player);
    }

    @Test
    public void cancelledSwapsAndDropsPreserveExistingChannel() {
        PlayerSwapHandItemsEvent swap = new PlayerSwapHandItemsEvent(player, brushStack, null);
        swap.setCancelled(true);
        PlayerDropItemEvent drop = new PlayerDropItemEvent(player, mock(Item.class));
        drop.setCancelled(true);
        server.getPluginManager().callEvent(swap);
        server.getPluginManager().callEvent(drop);
        verifyNoInteractions(recover);
    }

    private PlayerInteractEvent click(Action action, EquipmentSlot hand, ItemStack item, Block clicked) {
        return new PlayerInteractEvent(player, action, item, clicked, BlockFace.UP, hand);
    }
}
