package net.tfminecraft.archaeo.establish;

import net.tfminecraft.archaeo.item.EstablishItem;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.mockito.MockedStatic;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class EstablishListenerTest {
    private EstablishItem kit;
    private EstablishService service;
    private EstablishListener listener;
    private Player player;
    private ItemStack stack;
    private Block block;
    private World world;
    private MockedStatic<Bukkit> bukkit;

    @Before public void setup() {
        kit = mock(EstablishItem.class); service = mock(EstablishService.class);
        listener = new EstablishListener(kit, service); player = mock(Player.class);
        stack = mock(ItemStack.class); block = mock(Block.class);
        when(kit.isEstablish(stack)).thenReturn(true);
        // doDaylightCycle is off, so the day clock never moves; only the server tick does.
        world = mock(World.class); when(player.getWorld()).thenReturn(world); when(world.getFullTime()).thenReturn(100L);
        bukkit = mockStatic(Bukkit.class); bukkit.when(Bukkit::getCurrentTick).thenReturn(100);
        when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
    }

    @After public void teardown() { bukkit.close(); }

    @Test public void onlyKitInteractionsPlaceCampsAndExistingCampClicksPassThrough() {
        PlayerInventory inventory = mock(PlayerInventory.class); when(player.getInventory()).thenReturn(inventory);
        ItemStack mainKit = mock(ItemStack.class); when(inventory.getItemInMainHand()).thenReturn(mainKit); when(kit.isEstablish(mainKit)).thenReturn(true);
        listener.onInteract(click(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.OFF_HAND)); // The main-hand kit already acted.
        when(kit.isEstablish(stack)).thenReturn(false);
        listener.onInteract(click(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND));
        verifyNoInteractions(service);
        when(kit.isEstablish(stack)).thenReturn(true);
        when(service.isLockedCampBlock(block)).thenReturn(true);
        PlayerInteractEvent camp = click(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND);
        listener.onInteract(camp); assertFalse(camp.isCancelled());
        verify(service, never()).tryUseKit(any(), any());
        when(service.isLockedCampBlock(block)).thenReturn(false);
        for (Action action : new Action[]{Action.RIGHT_CLICK_BLOCK, Action.RIGHT_CLICK_AIR}) {
            PlayerInteractEvent event = click(action, EquipmentSlot.HAND);
            listener.onInteract(event); assertTrue(event.isCancelled());
        }
        verify(service).tryUseKit(player, block);
        verify(service).tryUseKit(player, null);
    }

    @Test public void leftClickCancelsMovesBeforeConsideringWoolAndRequiresSneakToCycle() {
        when(service.tryCancelMove(player)).thenReturn(true);
        PlayerInteractEvent moving = click(Action.LEFT_CLICK_AIR, EquipmentSlot.HAND);
        listener.onInteract(moving); assertTrue(moving.isCancelled());
        verify(service, never()).sneakHeld(any());
        when(service.tryCancelMove(player)).thenReturn(false);
        PlayerInteractEvent normal = click(Action.LEFT_CLICK_BLOCK, EquipmentSlot.HAND);
        listener.onInteract(normal); assertFalse(normal.isCancelled());
        when(service.sneakHeld(player)).thenReturn(true);
        PlayerInteractEvent cycling = click(Action.LEFT_CLICK_BLOCK, EquipmentSlot.HAND);
        listener.onInteract(cycling); assertTrue(cycling.isCancelled());
        verify(service).tryCycleWool(player);
        clearInvocations(service);
        listener.onInteract(click(Action.PHYSICAL, EquipmentSlot.HAND)); verifyNoInteractions(service);
    }

    @Test public void hotbarMovementAbortsRelocationAndSneakScrollingCyclesOnlyTheHeldKit() {
        PlayerInventory inventory = mock(PlayerInventory.class); when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItem(2)).thenReturn(stack);
        when(service.isRelocating(player)).thenReturn(true);
        PlayerItemHeldEvent moving = new PlayerItemHeldEvent(player, 2, 3); listener.onHeld(moving);
        verify(service).tryCancelMove(player); assertFalse(moving.isCancelled());
        when(service.isRelocating(player)).thenReturn(false);
        PlayerItemHeldEvent ordinary = new PlayerItemHeldEvent(player, 2, 3); listener.onHeld(ordinary);
        assertFalse(ordinary.isCancelled()); verify(service, never()).cycleWool(player);
        when(service.sneakHeld(player)).thenReturn(true); when(kit.isEstablish(stack)).thenReturn(false);
        listener.onHeld(ordinary); assertFalse(ordinary.isCancelled());
        when(kit.isEstablish(stack)).thenReturn(true);
        listener.onHeld(ordinary); assertTrue(ordinary.isCancelled()); verify(service).cycleWool(player);
    }

    @Test public void disconnectAndWorldChangeClearSessionAndRestoreTheCorrectWorld() {
        listener.onQuit(new PlayerQuitEvent(player, "bye"));
        verify(service).clearSession(player); verify(service).clearPreview(player); verify(service).hideHud(player);
        clearInvocations(service); World old = mock(World.class);
        listener.onWorldChange(new PlayerChangedWorldEvent(player, old));
        verify(service).clearSession(player); verify(service).clearPreviewFromWorld(player, old);
        verify(service, never()).clearPreview(player);
    }

    @Test public void aKitHeldOnlyInTheOffHandPlantsTheCampItsGhostPromised() {
        PlayerInventory inventory = mock(PlayerInventory.class); when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(mock(ItemStack.class)); // An empty or ordinary main hand.
        PlayerInteractEvent offHand = click(Action.RIGHT_CLICK_AIR, EquipmentSlot.OFF_HAND);
        listener.onInteract(offHand); assertTrue(offHand.isCancelled()); verify(service).tryUseKit(player, null);
        when(kit.isEstablish(stack)).thenReturn(false); clearInvocations(service);
        listener.onInteract(click(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.OFF_HAND)); verifyNoInteractions(service);
    }

    @Test public void spendingTheLastMainHandKitDoesNotReplayTheClickFromAnOffHandKit() {
        PlayerInventory inventory = mock(PlayerInventory.class); when(player.getInventory()).thenReturn(inventory);
        ItemStack empty = mock(ItemStack.class); when(inventory.getItemInMainHand()).thenReturn(stack);
        // The kit is a stick, so the client sends the off-hand packet too; the main-hand plant spends the last kit.
        doAnswer(call -> { when(inventory.getItemInMainHand()).thenReturn(empty); return null; })
                .when(service).tryUseKit(player, block);
        listener.onInteract(click(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND));
        PlayerInteractEvent offHand = click(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.OFF_HAND);
        listener.onInteract(offHand);
        verify(service, times(1)).tryUseKit(any(), any()); assertFalse(offHand.isCancelled());
        bukkit.when(Bukkit::getCurrentTick).thenReturn(101); // Next click: only the off-hand kit remains.
        PlayerInteractEvent later = click(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.OFF_HAND);
        listener.onInteract(later); assertTrue(later.isCancelled()); verify(service, times(2)).tryUseKit(player, block);
    }

    @Test public void aMainHandKitClickWithoutAnOffHandPacketDoesNotBlockALaterOffHandPlant() {
        PlayerInventory inventory = mock(PlayerInventory.class); when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(stack);
        // The client skips the off-hand packet when the main-hand click was handled on its side.
        listener.onInteract(click(Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND));
        when(inventory.getItemInMainHand()).thenReturn(mock(ItemStack.class)); bukkit.when(Bukkit::getCurrentTick).thenReturn(160);
        PlayerInteractEvent offHand = click(Action.RIGHT_CLICK_AIR, EquipmentSlot.OFF_HAND);
        listener.onInteract(offHand); assertTrue(offHand.isCancelled()); verify(service, times(2)).tryUseKit(player, null);
    }

    private PlayerInteractEvent click(Action action, EquipmentSlot hand) {
        Block clicked = action == Action.RIGHT_CLICK_AIR || action == Action.LEFT_CLICK_AIR ? null : block;
        return new PlayerInteractEvent(player, action, stack, clicked, org.bukkit.block.BlockFace.UP, hand);
    }
}
