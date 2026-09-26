package net.tfminecraft.archaeo.prospect;

import net.tfminecraft.archaeo.item.ItemRef;
import net.tfminecraft.archaeo.item.ProspectItem;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ProspectListenerTest {
    private PlayerMock player;
    private ProspectService service;
    private ProspectListener listener;
    @Before public void setUp() {
        MockBukkit.mock(); player = MockBukkit.getMock().addPlayer();
        service = mock(ProspectService.class);
        listener = new ProspectListener(new ProspectItem(ItemRef.vanilla(Material.IRON_HOE)), service);
    }
    @After public void tearDown() { MockBukkit.unmock(); }

    @Test
    public void interceptsSoilSamplesButLeavesChestsAndOtherHandsVanilla() {
        Block soil = player.getWorld().getBlockAt(0, 4, 0); soil.setType(Material.DIRT);
        when(service.isSampleGround(soil)).thenReturn(true);
        PlayerInteractEvent event = event(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND, Material.IRON_HOE, soil);
        listener.onInteract(event);
        assertTrue(event.isCancelled()); verify(service).begin(player, soil);
        clearInvocations(service);
        for (PlayerInteractEvent ignored : new PlayerInteractEvent[]{
                event(Action.LEFT_CLICK_BLOCK, EquipmentSlot.HAND, Material.IRON_HOE, soil),
                event(Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND, Material.IRON_HOE, null),
                event(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.OFF_HAND, Material.IRON_HOE, soil),
                event(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND, Material.STICK, soil),
                event(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND, Material.IRON_HOE, null)}) {
            listener.onInteract(ignored);
        }
        verifyNoInteractions(service);
        Block chest = player.getWorld().getBlockAt(1, 4, 0); chest.setType(Material.CHEST);
        PlayerInteractEvent open = event(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND, Material.IRON_HOE, chest);
        listener.onInteract(open);
        assertFalse(open.isCancelled());
        verify(service, never()).begin(player, chest);
        verify(service, never()).refuseWrongGround(player);
    }

    @Test
    public void nonSoilGetsOneRefusalAndQuittingCancelsTheChannel() {
        Block stone = player.getWorld().getBlockAt(0, 4, 0); stone.setType(Material.STONE);
        PlayerInteractEvent event = event(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND, Material.IRON_HOE, stone);
        listener.onInteract(event);
        assertTrue(event.isCancelled()); verify(service).refuseWrongGround(player);
        listener.onQuit(new PlayerQuitEvent(player, net.kyori.adventure.text.Component.empty()));
        verify(service).cancel(player);
        verify(service, never()).begin(any(), any());
    }

    private PlayerInteractEvent event(Action action, EquipmentSlot hand, Material material, Block block) {
        return new PlayerInteractEvent(player, action, new ItemStack(material), block, BlockFace.UP, hand);
    }
}
