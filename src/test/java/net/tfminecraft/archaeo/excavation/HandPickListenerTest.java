package net.tfminecraft.archaeo.excavation;

import net.tfminecraft.archaeo.model.Site;
import net.tfminecraft.archaeo.site.SiteRepository;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class HandPickListenerTest {
    private ServerMock server;
    private HandPickService pick;
    private SiteRepository sites;
    private HandPickListener listener;
    private Player player;
    private PlayerInventory inventory;
    private Block block;
    private ItemStack tool;

    @Before
    public void setup() {
        server = MockBukkit.mock();
        pick = mock(HandPickService.class);
        sites = mock(SiteRepository.class);
        listener = new HandPickListener(pick, sites);
        player = mock(Player.class);
        inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        tool = new ItemStack(Material.IRON_PICKAXE);
        when(inventory.getItemInMainHand()).thenReturn(tool);
        when(pick.isExcavationTool(any())).thenAnswer(invocation -> {
            ItemStack stack = invocation.getArgument(0);
            return stack != null && stack.getType() == Material.IRON_PICKAXE;
        });
        block = server.addSimpleWorld("listener").getBlockAt(8, 40, 8);
        block.setType(Material.STONE);
        when(sites.findEstablishedPrism("listener", 8, 40, 8)).thenReturn(Optional.of(new Site()));
        when(player.getTargetBlockExact(6)).thenReturn(block);
        server.getPluginManager().registerEvents(listener, MockBukkit.createMockPlugin());
    }

    @After
    public void teardown() {
        MockBukkit.unmock();
    }

    @Test
    public void prismDamageDisablesInstantBreakAndStartsClockEvenIfAlreadyCancelled() {
        BlockDamageEvent event = new BlockDamageEvent(player, block, tool, true);
        event.setCancelled(true);
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled());
        assertFalse(event.getInstaBreak());
        verify(pick).syncHeldTool(player);
        verify(pick).noteMining(player, block);
    }

    @Test
    public void unlistedToolOrBlocksOutsidePrismKeepVanillaDamageBehavior() {
        when(inventory.getItemInMainHand()).thenReturn(new ItemStack(Material.STICK));
        BlockDamageEvent unlisted = new BlockDamageEvent(player, block, tool, true);
        listener.onDamage(unlisted);
        assertTrue(unlisted.getInstaBreak());
        when(inventory.getItemInMainHand()).thenReturn(tool);
        when(sites.findEstablishedPrism("listener", 8, 40, 8)).thenReturn(Optional.empty());
        BlockDamageEvent outside = new BlockDamageEvent(player, block, tool, true);
        listener.onDamage(outside);
        assertTrue(outside.getInstaBreak());
        verify(pick, never()).noteMining(any(), any());
        verify(pick, never()).syncHeldTool(any());
    }

    @Test
    public void predictedBreakIsSuppressedOnCutOrDuringAnExistingCycle() {
        BlockBreakEvent onCut = new BlockBreakEvent(block, player);
        server.getPluginManager().callEvent(onCut);
        assertTrue(onCut.isCancelled());
        verify(pick).suppressVanillaBreak(player, block);
        when(sites.findEstablishedPrism("listener", 8, 40, 8)).thenReturn(Optional.empty());
        when(pick.isCycling(player)).thenReturn(true);
        BlockBreakEvent cycling = new BlockBreakEvent(block, player);
        server.getPluginManager().callEvent(cycling);
        assertTrue(cycling.isCancelled());
        verify(pick, times(2)).suppressVanillaBreak(player, block);
    }

    @Test
    public void ordinaryBreakOutsideCutOrWithoutConfiguredToolIsNotCancelled() {
        when(sites.findEstablishedPrism("listener", 8, 40, 8)).thenReturn(Optional.empty());
        BlockBreakEvent outside = new BlockBreakEvent(block, player);
        listener.onBreak(outside);
        assertFalse(outside.isCancelled());
        when(inventory.getItemInMainHand()).thenReturn(new ItemStack(Material.STICK));
        when(pick.isCycling(player)).thenReturn(true);
        BlockBreakEvent unlisted = new BlockBreakEvent(block, player);
        listener.onBreak(unlisted);
        assertFalse(unlisted.isCancelled());
        verify(pick, never()).suppressVanillaBreak(any(), any());
    }

    @Test
    public void onlyMainArmSwingAimedAtPrismFillKeepsMiningAlive() {
        listener.onSwing(new PlayerAnimationEvent(player, PlayerAnimationType.ARM_SWING));
        listener.onSwing(new PlayerAnimationEvent(player, PlayerAnimationType.OFF_ARM_SWING));
        when(player.getTargetBlockExact(6)).thenReturn(null);
        listener.onSwing(new PlayerAnimationEvent(player, PlayerAnimationType.ARM_SWING));
        when(player.getTargetBlockExact(6)).thenReturn(block);
        block.setType(Material.AIR);
        listener.onSwing(new PlayerAnimationEvent(player, PlayerAnimationType.ARM_SWING));
        block.setType(Material.STONE);
        when(inventory.getItemInMainHand()).thenReturn(new ItemStack(Material.STICK));
        listener.onSwing(new PlayerAnimationEvent(player, PlayerAnimationType.ARM_SWING));
        verify(pick, times(1)).noteMining(player, block);
    }

    @Test
    public void leftClickStartsClockWhileRightClickPreventsTillingAndPathing() {
        PlayerInteractEvent left = click(Action.LEFT_CLICK_BLOCK, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(left);
        verify(pick).noteMining(player, block);
        assertFalse(left.isCancelled());
        PlayerInteractEvent right = click(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(right);
        assertTrue(right.isCancelled());
        assertEquals(Event.Result.DENY, right.useInteractedBlock());
        assertEquals(Event.Result.DENY, right.useItemInHand());
    }

    @Test
    public void offhandAirAndOutsideCutInteractionsRemainVanilla() {
        PlayerInteractEvent offhand = click(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.OFF_HAND);
        listener.onInteract(offhand);
        assertFalse(offhand.isCancelled());
        listener.onInteract(click(Action.LEFT_CLICK_AIR, EquipmentSlot.HAND));
        when(sites.findEstablishedPrism("listener", 8, 40, 8)).thenReturn(Optional.empty());
        PlayerInteractEvent outside = click(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND);
        listener.onInteract(outside);
        assertFalse(outside.isCancelled());
        verify(pick, never()).noteMining(any(), any());
    }

    @Test
    public void hotbarAndSwapFinishOldCycleAndSyncTheIncomingStack() {
        ItemStack next = new ItemStack(Material.IRON_PICKAXE);
        when(inventory.getItem(2)).thenReturn(next);
        server.getPluginManager().callEvent(new PlayerItemHeldEvent(player, 0, 2));
        verify(pick).finish(player);
        verify(pick).syncHeldTool(player, next);
        ItemStack offHand = new ItemStack(Material.STICK);
        server.getPluginManager().callEvent(new PlayerSwapHandItemsEvent(player, next, offHand));
        verify(pick, times(2)).finish(player);
        verify(pick).syncHeldTool(player, offHand);
    }

    @Test
    public void droppingToolFinishesButDroppingUnrelatedStackDoesNot() {
        Item item = mock(Item.class);
        when(item.getItemStack()).thenReturn(new ItemStack(Material.STICK));
        server.getPluginManager().callEvent(new PlayerDropItemEvent(player, item));
        verify(pick, never()).finish(player);
        when(item.getItemStack()).thenReturn(tool);
        server.getPluginManager().callEvent(new PlayerDropItemEvent(player, item));
        verify(pick).finish(player);
        verify(pick).syncHeldTool(player);
    }

    @Test
    public void cancelledInventoryChangesLeaveActiveCycleAlone() {
        PlayerItemHeldEvent held = new PlayerItemHeldEvent(player, 0, 1);
        held.setCancelled(true);
        PlayerSwapHandItemsEvent swap = new PlayerSwapHandItemsEvent(player, tool, tool);
        swap.setCancelled(true);
        PlayerDropItemEvent drop = new PlayerDropItemEvent(player, mock(Item.class));
        drop.setCancelled(true);
        server.getPluginManager().callEvent(held);
        server.getPluginManager().callEvent(swap);
        server.getPluginManager().callEvent(drop);
        verifyNoInteractions(pick);
    }

    @Test
    public void joinSyncsToolAndQuitFinishesThenUnlocksMining() {
        server.getPluginManager().callEvent(new PlayerJoinEvent(player, "joined"));
        verify(pick).syncHeldTool(player);
        server.getPluginManager().callEvent(new PlayerQuitEvent(player, "left"));
        var order = inOrder(pick);
        order.verify(pick).syncHeldTool(player);
        order.verify(pick).finish(player);
        order.verify(pick).syncHeldTool(player, null);
    }

    @Test
    public void unlistedItemsLeftClicksOutsideTheCutAndBlocklessClicksStayVanilla() {
        when(inventory.getItemInMainHand()).thenReturn(new ItemStack(Material.STICK));
        PlayerInteractEvent stick = click(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(stick);
        assertFalse(stick.isCancelled());
        when(inventory.getItemInMainHand()).thenReturn(tool);
        when(sites.findEstablishedPrism("listener", 8, 40, 8)).thenReturn(Optional.empty());
        server.getPluginManager().callEvent(click(Action.LEFT_CLICK_BLOCK, EquipmentSlot.HAND));
        when(sites.findEstablishedPrism("listener", 8, 40, 8)).thenReturn(Optional.of(new Site()));
        // Vanilla always sends a block with a block click, but other plugins call this event too and
        // the Bukkit constructor accepts a null block, so such a click must be ignored without throwing.
        for (Action action : new Action[] {Action.LEFT_CLICK_BLOCK, Action.RIGHT_CLICK_BLOCK}) {
            PlayerInteractEvent blockless = new PlayerInteractEvent(player, action, tool, null, BlockFace.UP, EquipmentSlot.HAND);
            server.getPluginManager().callEvent(blockless);
            // Bukkit already denies the missing block; the listener must not deny the item too.
            assertEquals(Event.Result.DEFAULT, blockless.useItemInHand());
        }
        verify(pick, never()).noteMining(any(), any());
    }

    private PlayerInteractEvent click(Action action, EquipmentSlot hand) {
        return new PlayerInteractEvent(player, action, tool, block, BlockFace.UP, hand);
    }
}
