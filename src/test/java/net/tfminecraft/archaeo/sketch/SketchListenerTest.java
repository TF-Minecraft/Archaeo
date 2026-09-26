package net.tfminecraft.archaeo.sketch;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.archaeo.item.SketchSupplies;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.*;
import org.junit.*;
import org.mockito.ArgumentCaptor;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

public class SketchListenerTest {
    private ServerMock server;
    private PlayerMock player;
    private SketchService service;
    private SketchListener listener;
    @Before public void setUp() {
        server = MockBukkit.mock(); player = server.addPlayer();
        service = mock(SketchService.class); listener = new SketchListener(service);
    }
    @After public void tearDown() { MockBukkit.unmock(); }

    @Test public void editorFreezesPositionButPreservesLookingAndLeavesOrdinaryMovementAlone() {
        Location from = new Location(player.getWorld(), 1, 65, 3, 0, 0);
        Location to = new Location(player.getWorld(), 2, 66, 3, 90, 20);
        PlayerMoveEvent ordinary = new PlayerMoveEvent(player, from, to);
        listener.onMove(ordinary); assertEquals(to, ordinary.getTo());
        when(service.editing(player)).thenReturn(true);
        PlayerMoveEvent moving = new PlayerMoveEvent(player, from, to);
        listener.onMove(moving);
        assertEquals(from.toVector(), moving.getTo().toVector()); assertEquals(90f, moving.getTo().getYaw(), 0); assertEquals(20f, moving.getTo().getPitch(), 0);
        Location looking = new Location(player.getWorld(), 1, 65, 3, 120, 40);
        PlayerMoveEvent look = new PlayerMoveEvent(player, from, looking); listener.onMove(look); assertEquals(looking, look.getTo());
    }

    @Test public void cabinetAndHandCraftConsumeOnlyTheirHandledMainHandInteractions() {
        PlayerInteractEvent cabinet = interact(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND);
        when(service.tryOpenCabinet(eq(player), any(), eq(false))).thenReturn(true);
        listener.onInteract(cabinet); assertDenied(cabinet);
        verify(service, never()).tryStartFromHands(player);
        when(service.tryOpenCabinet(eq(player), any(), eq(false))).thenReturn(false);
        when(service.tryStartFromHands(player)).thenReturn(true);
        PlayerInteractEvent craft = interact(Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND); listener.onInteract(craft); assertDenied(craft);
        clearInvocations(service);
        PlayerInteractEvent off = interact(Action.RIGHT_CLICK_AIR, EquipmentSlot.OFF_HAND); listener.onInteract(off);
        assertVanilla(off); verify(service, never()).tryStartFromHands(player);
    }

    @Test public void editorClicksMapToSignOrEraseWithoutUsingTheWorldOrDuplicatingOffhandInput() {
        when(service.editing(player)).thenReturn(true);
        for (Action action : List.of(Action.LEFT_CLICK_AIR, Action.LEFT_CLICK_BLOCK)) {
            PlayerInteractEvent event = interact(action, EquipmentSlot.HAND); listener.onInteract(event); assertDenied(event);
        }
        verify(service, times(2)).askToSign(player);
        for (Action action : List.of(Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK)) listener.onInteract(interact(action, EquipmentSlot.HAND));
        verify(service, times(2)).erase(player);
        PlayerInteractEvent off = interact(Action.RIGHT_CLICK_AIR, EquipmentSlot.OFF_HAND); listener.onInteract(off); assertDenied(off);
        verify(service, times(2)).erase(player);
    }

    @Test public void bagCombiningIsRestrictedToPlayerStorageAndWritesMutatedStacksBack() {
        InventoryClickEvent event = click(player.getInventory(), player.getInventory());
        ItemStack paper = new ItemStack(Material.PAPER); ItemStack pencil = new ItemStack(Material.FEATHER);
        when(event.getCursor()).thenReturn(paper); when(event.getCurrentItem()).thenReturn(pencil); when(event.getSlot()).thenReturn(4);
        when(service.tryCraftOnClick(player, paper, pencil)).thenReturn(true);
        listener.onCombine(event);
        verify(event).setCancelled(true); verify(event).setCurrentItem(pencil); verify(event.getView()).setCursor(paper);
        verify(service).afterBagCraft(player, player.getInventory(), 4, paper, pencil);
        clearInvocations(service);
        Inventory chest = server.createInventory(null, 9); listener.onCombine(click(chest, chest));
        verify(service, never()).tryCraftOnClick(any(), any(), any());
    }

    @Test public void finalPencilBagCraftClearsConsumedCursorOrSlotWithoutLeavingGhostItems() {
        for (boolean pencilOnCursor : new boolean[]{true, false}) {
            ItemStack pencil = new ItemStack(Material.FEATHER); ItemStack paper = new ItemStack(Material.PAPER);
            ItemStack cursor = pencilOnCursor ? pencil : paper; ItemStack slot = pencilOnCursor ? paper : pencil;
            InventoryClickEvent event = click(player.getInventory(), player.getInventory());
            when(event.getCursor()).thenReturn(cursor); when(event.getCurrentItem()).thenReturn(slot); when(event.getSlot()).thenReturn(4);
            when(service.tryCraftOnClick(player, cursor, slot)).thenAnswer(call -> {
                pencil.setType(Material.AIR); pencil.setAmount(0); paper.setType(Material.FILLED_MAP); return true;
            });
            listener.onCombine(event);
            verify(event).setCancelled(true);
            if (pencilOnCursor) { verify(event.getView()).setCursor(null); verify(event).setCurrentItem(paper); }
            else { verify(event).setCurrentItem(null); verify(event.getView()).setCursor(paper); }
            verify(service).afterBagCraft(player, player.getInventory(), 4, cursor, slot); clearInvocations(service);
        }
    }

    @Test public void cabinetLocksDisplaySlotsAndUsesValidatedShiftDepositsAndDragRules() {
        SketchCabinet holder = mock(SketchCabinet.class); Inventory top = server.createInventory(holder, 9);
        InventoryClickEvent event = click(top, top); ItemStack cursor = new ItemStack(Material.FILLED_MAP);
        when(event.getCursor()).thenReturn(cursor); when(event.getSlot()).thenReturn(0);
        ItemStack returned = new ItemStack(Material.AIR); when(service.handleCabinetClick(eq(player), eq(top), eq(0), any())).thenReturn(returned);
        listener.onCabinetClick(event); verify(event).setCancelled(true); verify(event.getView()).setCursor(returned);
        InventoryClickEvent shift = click(top, player.getInventory()); when(shift.isShiftClick()).thenReturn(true);
        when(shift.getCurrentItem()).thenReturn(cursor); when(service.tryDepositCabinet(player, top, cursor)).thenReturn(true);
        listener.onCabinetClick(shift); verify(shift).setCancelled(true); verify(shift).setCurrentItem(cursor);
        InventoryDragEvent blocked = drag(top, Set.of(0, 12)); listener.onCabinetDrag(blocked); verify(blocked).setCancelled(true);
        InventoryDragEvent bag = drag(top, Set.of(12, 13)); listener.onCabinetDrag(bag); verify(bag, never()).setCancelled(true); verify(bag, never()).setResult(any());
        InventoryCloseEvent close = close(top); listener.onCabinetClose(close); verify(holder).returnContents(player);
    }

    @Test public void labBlocksBagTransfersAndRoutesTopClicksAndCleanup() {
        CabinetLabBoard holder = mock(CabinetLabBoard.class); Inventory top = server.createInventory(holder, 27);
        InventoryClickEvent bag = click(top, player.getInventory()); listener.onLabClick(bag);
        verify(bag).setCancelled(true); verify(service, never()).handleLabClick(any(), any(), anyInt(), any());
        InventoryClickEvent field = click(top, top); when(field.getSlot()).thenReturn(5); when(field.getCursor()).thenReturn(new ItemStack(Material.AIR));
        ItemStack next = new ItemStack(Material.FEATHER); when(service.handleLabClick(eq(player), eq(holder), eq(5), any())).thenReturn(next);
        listener.onLabClick(field); verify(field).setCancelled(true); verify(field.getView()).setCursor(next);
        InventoryDragEvent drag = drag(top, Set.of(30)); listener.onLabDrag(drag); verify(drag).setCancelled(true);
        listener.onLabClose(close(top)); verify(service).handleLabClose(player, holder);
    }

    @Test public void editorKeepsOwnBagAvailableButBlocksExternalInventories() {
        for (InventoryType type : List.of(InventoryType.CRAFTING, InventoryType.CREATIVE, InventoryType.PLAYER, InventoryType.CHEST, InventoryType.MERCHANT)) {
            InventoryOpenEvent event = mock(InventoryOpenEvent.class); Inventory inventory = mock(Inventory.class);
            when(event.getPlayer()).thenReturn(player); when(event.getInventory()).thenReturn(inventory); when(inventory.getType()).thenReturn(type);
            when(service.editing(player)).thenReturn(false); listener.onInventory(event); verify(event, never()).setCancelled(true);
            when(service.editing(player)).thenReturn(true); listener.onInventory(event);
            if (type == InventoryType.CHEST || type == InventoryType.MERCHANT) verify(event).setCancelled(true);
            else verify(event, never()).setCancelled(true);
            listener.onInventoryOpened(event);
        }
        verify(service, times(5)).stampKitsLater(player);
    }

    @Test public void activeEditorCannotAttackMineLaunchProjectilesOrUseEntities() {
        EntityDamageByEntityEvent hit = mock(EntityDamageByEntityEvent.class); when(hit.getDamager()).thenReturn(player);
        BlockBreakEvent block = mock(BlockBreakEvent.class); when(block.getPlayer()).thenReturn(player);
        BlockDamageEvent damage = mock(BlockDamageEvent.class); when(damage.getPlayer()).thenReturn(player);
        Projectile projectile = mock(Projectile.class); when(projectile.getShooter()).thenReturn(player);
        ProjectileLaunchEvent launch = mock(ProjectileLaunchEvent.class); when(launch.getEntity()).thenReturn(projectile);
        PlayerInteractEntityEvent entity = mock(PlayerInteractEntityEvent.class); when(entity.getPlayer()).thenReturn(player); when(entity.getHand()).thenReturn(EquipmentSlot.HAND);
        HangingBreakByEntityEvent hanging = mock(HangingBreakByEntityEvent.class); when(hanging.getRemover()).thenReturn(player);
        listener.onHit(hit); listener.onBreak(block); listener.onBlockDamage(damage); listener.onLaunch(launch); listener.onEntity(entity); listener.onHanging(hanging);
        verify(hit, never()).setCancelled(true); verify(block, never()).setCancelled(true); verify(launch, never()).setCancelled(true);
        when(service.editing(player)).thenReturn(true);
        listener.onHit(hit); listener.onBreak(block); listener.onBlockDamage(damage); listener.onLaunch(launch); listener.onEntity(entity); listener.onHanging(hanging);
        verify(hit).setCancelled(true); verify(block).setCancelled(true); verify(damage).setCancelled(true); verify(launch).setCancelled(true); verify(entity).setCancelled(true); verify(hanging).setCancelled(true); verify(service).erase(player);
    }

    @Test public void droppingSavesRealSketchesButRejectsFakeLabItems() {
        PlayerDropItemEvent event = mock(PlayerDropItemEvent.class); Item entity = mock(Item.class); ItemStack map = new ItemStack(Material.FILLED_MAP);
        when(event.getPlayer()).thenReturn(player); when(event.getItemDrop()).thenReturn(entity); when(entity.getItemStack()).thenReturn(map);
        when(service.isLabItem(map)).thenReturn(true); listener.onDrop(event); verify(event).setCancelled(true); verify(service, never()).leave(eq(player), anyBoolean(), any(ItemStack.class));
        when(service.isLabItem(map)).thenReturn(false); when(service.editing(player)).thenReturn(true); when(service.isSketchMap(map)).thenReturn(true);
        listener.onDrop(event); verify(service).leave(player, true, map);
    }

    @Test public void hotbarSwapDeathQuitAndWorldChangesSaveOrCancelTheirSessions() {
        ItemStack map = new ItemStack(Material.FILLED_MAP); player.getInventory().setItem(0, map);
        PlayerItemHeldEvent held = new PlayerItemHeldEvent(player, 0, 1);
        when(service.editing(player)).thenReturn(true); listener.onHeld(held);
        verify(service).leave(player, true, map); verify(service).syncHand(player, player.getInventory().getItem(1));
        PlayerSwapHandItemsEvent swap = mock(PlayerSwapHandItemsEvent.class); when(swap.getPlayer()).thenReturn(player); listener.onSwap(swap);
        PlayerDeathEvent death = mock(PlayerDeathEvent.class); when(death.getEntity()).thenReturn(player); when(death.getDrops()).thenReturn(List.of(map)); listener.onDeath(death);
        verify(service).leave(player, false, List.of(map));
        PlayerQuitEvent quit = mock(PlayerQuitEvent.class); when(quit.getPlayer()).thenReturn(player); listener.onQuit(quit); verify(service).leave(player, false);
        PlayerChangedWorldEvent world = mock(PlayerChangedWorldEvent.class); when(world.getPlayer()).thenReturn(player); listener.onWorld(world);
        verify(service, times(3)).cancelLab(player); verify(service, times(2)).syncHandLater(player);
    }

    @Test public void signPromptConsumesOnlyPendingChatAndPassesTrimmedInputToMainThread() {
        AsyncPlayerChatEvent event = mock(AsyncPlayerChatEvent.class); when(event.getPlayer()).thenReturn(player); when(event.getMessage()).thenReturn(" sign ");
        listener.onChat(event); verify(event, never()).setCancelled(true);
        SketchSession session = mock(SketchSession.class); when(service.session(player)).thenReturn(session);
        listener.onChat(event); verify(event, never()).setCancelled(true);
        when(session.awaitingSign()).thenReturn(true); listener.onChat(event);
        verify(event).setCancelled(true); verify(service).handleSignChatLater(player, "sign");
    }

    @Test public void inventoryMovementPassesAllCandidateStacksToDeferredSave() {
        InventoryClickEvent click = click(player.getOpenInventory().getTopInventory(), player.getInventory());
        ItemStack cursor = new ItemStack(Material.FILLED_MAP), slot = new ItemStack(Material.PAPER), hotbar = new ItemStack(Material.FEATHER);
        when(click.getCursor()).thenReturn(cursor); when(click.getCurrentItem()).thenReturn(slot); when(click.getHotbarButton()).thenReturn(3); player.getInventory().setItem(3, hotbar);
        listener.onInventoryClick(click); verify(service).syncHandLater(player, cursor, slot, hotbar);
        InventoryDragEvent event = drag(player.getOpenInventory().getTopInventory(), Set.of(0)); when(event.getWhoClicked()).thenReturn(player);
        when(event.getCursor()).thenReturn(cursor); when(event.getOldCursor()).thenReturn(slot); when(event.getNewItems()).thenReturn(Map.of(0, hotbar));
        listener.onInventoryDrag(event); verify(service, times(2)).syncHandLater(player, cursor, slot, hotbar);
        InventoryCloseEvent close = close(player.getOpenInventory().getTopInventory()); when(close.getView().getCursor()).thenReturn(cursor);
        listener.onInventoryClose(close); verify(service).syncHandLater(player, cursor);
    }

    @Test public void joiningPickingUpAndLoadingFramesHydratesSavedMaps() {
        ItemStack map = new ItemStack(Material.FILLED_MAP); player.getInventory().setItem(2, map);
        PlayerJoinEvent join = mock(PlayerJoinEvent.class); when(join.getPlayer()).thenReturn(player); listener.onJoin(join); verify(service).hydrate(map);
        clearInvocations(service);
        EntityPickupItemEvent pickup = mock(EntityPickupItemEvent.class); Item item = mock(Item.class);
        when(pickup.getEntity()).thenReturn(player); when(pickup.getItem()).thenReturn(item); when(item.getItemStack()).thenReturn(map); listener.onPickup(pickup); verify(service).hydrate(map);
        clearInvocations(service);
        ChunkLoadEvent chunk = mock(ChunkLoadEvent.class); Chunk loaded = mock(Chunk.class); ItemFrame frame = mock(ItemFrame.class);
        when(chunk.getChunk()).thenReturn(loaded); when(loaded.getEntities()).thenReturn(new Entity[]{frame, mock(Entity.class)}); when(frame.getItem()).thenReturn(map);
        listener.onChunk(chunk); verify(service).hydrate(map);
    }

    @Test public void editingSuppressesVanillaUsesThatCouldConsumeItemsOrChangeTheWorld() {
        {
            PlayerBucketEmptyEvent event = mock(PlayerBucketEmptyEvent.class); when(event.getPlayer()).thenReturn(player);
            when(service.editing(player)).thenReturn(false); listener.onBucketEmpty(event); verify(event, never()).setCancelled(true);
            when(service.editing(player)).thenReturn(true); listener.onBucketEmpty(event); verify(event).setCancelled(true);
        }
        {
            PlayerBucketFillEvent event = mock(PlayerBucketFillEvent.class); when(event.getPlayer()).thenReturn(player);
            when(service.editing(player)).thenReturn(false); listener.onBucketFill(event); verify(event, never()).setCancelled(true);
            when(service.editing(player)).thenReturn(true); listener.onBucketFill(event); verify(event).setCancelled(true);
        }
        {
            PlayerBedEnterEvent event = mock(PlayerBedEnterEvent.class); when(event.getPlayer()).thenReturn(player);
            when(service.editing(player)).thenReturn(false); listener.onBed(event); verify(event, never()).setCancelled(true);
            when(service.editing(player)).thenReturn(true); listener.onBed(event); verify(event).setCancelled(true);
        }
        {
            PlayerItemConsumeEvent event = mock(PlayerItemConsumeEvent.class); when(event.getPlayer()).thenReturn(player);
            when(service.editing(player)).thenReturn(false); listener.onConsume(event); verify(event, never()).setCancelled(true);
            when(service.editing(player)).thenReturn(true); listener.onConsume(event); verify(event).setCancelled(true);
        }
        {
            PlayerTakeLecternBookEvent event = mock(PlayerTakeLecternBookEvent.class); when(event.getPlayer()).thenReturn(player);
            when(service.editing(player)).thenReturn(false); listener.onLectern(event); verify(event, never()).setCancelled(true);
            when(service.editing(player)).thenReturn(true); listener.onLectern(event); verify(event).setCancelled(true);
        }
        {
            PlayerHarvestBlockEvent event = mock(PlayerHarvestBlockEvent.class); when(event.getPlayer()).thenReturn(player);
            when(service.editing(player)).thenReturn(false); listener.onHarvest(event); verify(event, never()).setCancelled(true);
            when(service.editing(player)).thenReturn(true); listener.onHarvest(event); verify(event).setCancelled(true);
        }
        {
            PlayerShearEntityEvent event = mock(PlayerShearEntityEvent.class); when(event.getPlayer()).thenReturn(player);
            when(service.editing(player)).thenReturn(false); listener.onShear(event); verify(event, never()).setCancelled(true);
            when(service.editing(player)).thenReturn(true); listener.onShear(event); verify(event).setCancelled(true);
        }
        {
            PlayerFishEvent event = mock(PlayerFishEvent.class); when(event.getPlayer()).thenReturn(player);
            when(service.editing(player)).thenReturn(false); listener.onFish(event); verify(event, never()).setCancelled(true);
            when(service.editing(player)).thenReturn(true); listener.onFish(event); verify(event).setCancelled(true);
        }
        {
            PlayerInteractAtEntityEvent event = mock(PlayerInteractAtEntityEvent.class); when(event.getPlayer()).thenReturn(player);
            when(service.editing(player)).thenReturn(false); listener.onArmorStand(event); verify(event, never()).setCancelled(true);
            when(service.editing(player)).thenReturn(true); listener.onArmorStand(event); verify(event).setCancelled(true);
        }
        {
            PlayerArmorStandManipulateEvent event = mock(PlayerArmorStandManipulateEvent.class); when(event.getPlayer()).thenReturn(player);
            when(service.editing(player)).thenReturn(false); listener.onArmorStandEdit(event); verify(event, never()).setCancelled(true);
            when(service.editing(player)).thenReturn(true); listener.onArmorStandEdit(event); verify(event).setCancelled(true);
        }
        {
            PlayerToggleFlightEvent event = mock(PlayerToggleFlightEvent.class); when(event.getPlayer()).thenReturn(player);
            when(service.editing(player)).thenReturn(false); listener.onFlight(event); verify(event, never()).setCancelled(true);
            when(service.editing(player)).thenReturn(true); listener.onFlight(event); verify(event).setCancelled(true);
        }
        PlayerToggleSprintEvent sprint = mock(PlayerToggleSprintEvent.class); when(sprint.getPlayer()).thenReturn(player);
        listener.onSprint(sprint); verify(sprint, never()).setCancelled(true);
        when(sprint.isSprinting()).thenReturn(true); listener.onSprint(sprint); verify(sprint).setCancelled(true);
        PlayerAnimationEvent swing = mock(PlayerAnimationEvent.class); when(swing.getPlayer()).thenReturn(player);
        when(swing.getAnimationType()).thenReturn(PlayerAnimationType.ARM_SWING); listener.onSwing(swing); verify(swing).setCancelled(true);
    }

    @Test public void idleLifecycleEventsCancelOnlyTheLabWithoutSavingAnAbsentDrawingSession() {
        when(service.editing(player)).thenReturn(false);
        PlayerDeathEvent death = mock(PlayerDeathEvent.class); when(death.getEntity()).thenReturn(player);
        listener.onDeath(death);
        PlayerQuitEvent quit = mock(PlayerQuitEvent.class); when(quit.getPlayer()).thenReturn(player);
        listener.onQuit(quit);
        verify(service, times(2)).cancelLab(player);
        verify(service, never()).leave(eq(player), anyBoolean());
        verify(service, never()).leave(eq(player), anyBoolean(), any(Iterable.class));
    }

    @Test public void droppingUnrelatedItemsAndOffhandEntityUseCannotSaveOrEraseTheSketchTwice() {
        when(service.editing(player)).thenReturn(true);
        PlayerDropItemEvent drop = mock(PlayerDropItemEvent.class); Item entity = mock(Item.class);
        when(drop.getPlayer()).thenReturn(player); when(drop.getItemDrop()).thenReturn(entity);
        when(entity.getItemStack()).thenReturn(new ItemStack(Material.STONE));
        listener.onDrop(drop); verify(drop, never()).setCancelled(true);
        verify(service, never()).leave(eq(player), anyBoolean(), any(ItemStack.class));
        PlayerInteractEntityEvent offhand = mock(PlayerInteractEntityEvent.class);
        when(offhand.getPlayer()).thenReturn(player); when(offhand.getHand()).thenReturn(EquipmentSlot.OFF_HAND);
        listener.onEntity(offhand); verify(offhand).setCancelled(true); verify(service, never()).erase(player);
    }

    @Test public void editorIsPinnedWhenWalkingOrFallingAlongAnySingleAxis() {
        when(service.editing(player)).thenReturn(true);
        Location from = new Location(player.getWorld(), 1, 65, 3, 10, 5);
        for (Location to : List.of(new Location(player.getWorld(), 1.4, 65, 3, 30, 5), new Location(player.getWorld(), 1, 64.2, 3, 10, 5),
                new Location(player.getWorld(), 1, 65, 3.6, 10, -15))) {
            PlayerMoveEvent move = new PlayerMoveEvent(player, from, to); listener.onMove(move);
            assertEquals(from.toVector(), move.getTo().toVector()); assertEquals(to.getYaw(), move.getTo().getYaw(), 0); assertEquals(to.getPitch(), move.getTo().getPitch(), 0);
        }
    }

    @Test public void offhandAndUnhandledClicksKeepVanillaUseAndPhysicalTriggersNeverEditTheSheet() {
        PlayerInteractEvent offhandBlock = interact(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.OFF_HAND); listener.onInteract(offhandBlock);
        verify(service, never()).tryOpenCabinet(any(), any(), anyBoolean()); assertVanilla(offhandBlock);
        when(service.tryStartFromHands(player)).thenReturn(true);
        PlayerInteractEvent tableCraft = interact(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND); listener.onInteract(tableCraft);
        verify(service).tryOpenCabinet(eq(player), eq(tableCraft.getClickedBlock()), eq(false)); assertDenied(tableCraft);
        when(service.tryStartFromHands(player)).thenReturn(false);
        for (Action action : List.of(Action.RIGHT_CLICK_AIR, Action.LEFT_CLICK_BLOCK)) {
            PlayerInteractEvent ordinary = interact(action, EquipmentSlot.HAND); listener.onInteract(ordinary);
            assertVanilla(ordinary);
        }
        when(service.editing(player)).thenReturn(true);
        PlayerInteractEvent plate = interact(Action.PHYSICAL, null); listener.onInteract(plate); assertDenied(plate); // Vanilla gives a pressure plate no hand.
        // Another plugin may fire a physical trigger naming the main hand; it still must not edit the sheet.
        PlayerInteractEvent pluginPlate = interact(Action.PHYSICAL, EquipmentSlot.HAND); listener.onInteract(pluginPlate); assertDenied(pluginPlate);
        verify(service, never()).erase(player); verify(service, never()).askToSign(player);
    }

    @Test public void bagCombiningCoversCreativeAndCraftingGridsButIgnoresOutsideAndEmptySlotClicks() {
        InventoryClickEvent outside = click(player.getInventory(), null); listener.onCombine(outside);
        verify(service, never()).tryCraftOnClick(any(), any(), any());
        ItemStack paper = new ItemStack(Material.PAPER); ItemStack pencil = new ItemStack(Material.FEATHER);
        for (InventoryType type : List.of(InventoryType.CREATIVE, InventoryType.CRAFTING)) {
            Inventory grid = mock(Inventory.class); when(grid.getType()).thenReturn(type);
            InventoryClickEvent event = click(player.getInventory(), grid);
            when(event.getCursor()).thenReturn(paper); when(event.getCurrentItem()).thenReturn(pencil); when(event.getSlot()).thenReturn(2);
            when(service.tryCraftOnClick(player, paper, pencil)).thenReturn(true);
            listener.onCombine(event); verify(event).setCancelled(true); verify(service).afterBagCraft(player, grid, 2, paper, pencil);
        }
        InventoryClickEvent empty = click(player.getInventory(), player.getInventory()); when(empty.getCursor()).thenReturn(pencil);
        ArgumentCaptor<ItemStack> slot = ArgumentCaptor.forClass(ItemStack.class);
        listener.onCombine(empty); verify(service).tryCraftOnClick(eq(player), eq(pencil), slot.capture());
        assertTrue(slot.getValue().getType().isAir()); verify(empty, never()).setCancelled(true); verify(empty, never()).setResult(any()); verify(empty, never()).setCurrentItem(any());
    }

    @Test public void cabinetWindowLeavesBagRearrangingAloneAndRefusedShiftDepositsKeepTheStack() {
        SketchCabinet holder = mock(SketchCabinet.class); Inventory top = server.createInventory(holder, 9);
        InventoryClickEvent bag = click(top, player.getInventory()); listener.onCabinetClick(bag);
        verify(bag, never()).setCancelled(true); verify(bag, never()).setResult(any()); verify(service, never()).handleCabinetClick(any(), any(), anyInt(), any());
        InventoryClickEvent emptyShift = click(top, player.getInventory()); when(emptyShift.isShiftClick()).thenReturn(true);
        listener.onCabinetClick(emptyShift); verify(emptyShift).setCancelled(true); verify(service, never()).tryDepositCabinet(any(), any(), any());
        ItemStack stone = new ItemStack(Material.STONE, 5);
        InventoryClickEvent refused = click(top, player.getInventory()); when(refused.isShiftClick()).thenReturn(true); when(refused.getCurrentItem()).thenReturn(stone);
        listener.onCabinetClick(refused); verify(refused).setCancelled(true); verify(refused, never()).setCurrentItem(any()); assertEquals(5, stone.getAmount());
    }

    @Test public void ordinaryContainersAreUntouchedByCabinetAndLabWindowRules() {
        Inventory chest = server.createInventory(null, 27);
        InventoryClickEvent click = click(chest, chest); listener.onCabinetClick(click); listener.onLabClick(click);
        verify(click, never()).setCancelled(true); verify(click, never()).setResult(any());
        InventoryDragEvent drag = drag(chest, Set.of(0, 1)); listener.onCabinetDrag(drag); listener.onLabDrag(drag);
        verify(drag, never()).setCancelled(true); verify(drag, never()).setResult(any());
        listener.onCabinetClose(close(chest)); listener.onLabClose(close(chest));
        verify(service, never()).handleCabinetClick(any(), any(), anyInt(), any()); verify(service, never()).handleLabClick(any(), any(), anyInt(), any());
        verify(service, never()).handleLabClose(any(), any());
    }

    @Test public void editorStillTakesMobDamageAndOtherEntitiesKeepTheirAttacksAndLaunches() {
        when(service.editing(player)).thenReturn(true);
        Zombie zombie = mock(Zombie.class);
        EntityDamageByEntityEvent hit = mock(EntityDamageByEntityEvent.class); when(hit.getDamager()).thenReturn(zombie);
        HangingBreakByEntityEvent hanging = mock(HangingBreakByEntityEvent.class); when(hanging.getRemover()).thenReturn(zombie);
        Projectile arrow = mock(Projectile.class); when(arrow.getShooter()).thenReturn(mock(Skeleton.class));
        ProjectileLaunchEvent launch = mock(ProjectileLaunchEvent.class); when(launch.getEntity()).thenReturn(arrow);
        listener.onHit(hit); listener.onHanging(hanging); listener.onLaunch(launch);
        verify(hit, never()).setCancelled(true); verify(hanging, never()).setCancelled(true); verify(launch, never()).setCancelled(true);
        EntityPickupItemEvent pickup = mock(EntityPickupItemEvent.class); when(pickup.getEntity()).thenReturn(zombie);
        listener.onPickup(pickup); verify(service, never()).hydrate(any()); verify(service, never()).syncHandLater(any(), any(ItemStack[].class));
    }

    @Test public void onlyAnEditorsMainArmSwingAndSprintStartAreSuppressed() {
        PlayerAnimationEvent offArm = mock(PlayerAnimationEvent.class); when(offArm.getPlayer()).thenReturn(player);
        when(offArm.getAnimationType()).thenReturn(PlayerAnimationType.OFF_ARM_SWING);
        PlayerAnimationEvent swing = mock(PlayerAnimationEvent.class); when(swing.getPlayer()).thenReturn(player);
        when(swing.getAnimationType()).thenReturn(PlayerAnimationType.ARM_SWING);
        PlayerToggleSprintEvent sprint = mock(PlayerToggleSprintEvent.class); when(sprint.getPlayer()).thenReturn(player); when(sprint.isSprinting()).thenReturn(true);
        listener.onSwing(swing); listener.onSprint(sprint); verify(swing, never()).setCancelled(true); verify(sprint, never()).setCancelled(true);
        when(service.editing(player)).thenReturn(true);
        listener.onSwing(offArm); verify(offArm, never()).setCancelled(true);
        PlayerToggleSprintEvent stop = mock(PlayerToggleSprintEvent.class); when(stop.getPlayer()).thenReturn(player);
        listener.onSprint(stop); verify(stop, never()).setCancelled(true);
    }

    @Test public void idleHotbarDropsAndPlainClicksOnlyResyncTheHandWithoutSavingASession() {
        PlayerDropItemEvent drop = mock(PlayerDropItemEvent.class); Item entity = mock(Item.class); ItemStack map = new ItemStack(Material.FILLED_MAP);
        when(drop.getPlayer()).thenReturn(player); when(drop.getItemDrop()).thenReturn(entity); when(entity.getItemStack()).thenReturn(map);
        listener.onDrop(drop); verify(drop, never()).setCancelled(true);
        player.getInventory().setItem(4, map); listener.onHeld(new PlayerItemHeldEvent(player, 0, 4));
        verify(service).syncHand(player, map); verify(service, never()).leave(eq(player), anyBoolean(), any(ItemStack.class));
        InventoryClickEvent click = click(player.getOpenInventory().getTopInventory(), player.getInventory());
        ItemStack cursor = new ItemStack(Material.AIR); when(click.getCursor()).thenReturn(cursor);
        when(click.getHotbarButton()).thenReturn(-1); when(click.getCurrentItem()).thenReturn(map);
        listener.onInventoryClick(click); verify(service).syncHandLater(player, cursor, map, null);
    }

    private PlayerInteractEvent interact(Action action, EquipmentSlot hand) {
        boolean air = action == Action.LEFT_CLICK_AIR || action == Action.RIGHT_CLICK_AIR;
        Block block = air ? null : player.getWorld().getBlockAt(0, 64, 0);
        return new PlayerInteractEvent(player, action, null, block, air ? null : BlockFace.UP, hand);
    }
    /** Untouched: an air click starts with the block use denied, a block click with it allowed, and the held item at its default. */
    private static void assertVanilla(PlayerInteractEvent event) {
        boolean onBlock = event.getClickedBlock() != null;
        assertEquals(onBlock ? Event.Result.ALLOW : Event.Result.DENY, event.useInteractedBlock());
        assertEquals(!onBlock, event.isCancelled()); // Bukkit reports an air click as cancelled from the start.
        assertEquals(Event.Result.DEFAULT, event.useItemInHand());
    }
    private static void assertDenied(PlayerInteractEvent event) {
        assertTrue(event.isCancelled()); assertEquals(Event.Result.DENY, event.useInteractedBlock()); assertEquals(Event.Result.DENY, event.useItemInHand());
    }
    private InventoryClickEvent click(Inventory top, Inventory clicked) {
        InventoryClickEvent event = mock(InventoryClickEvent.class); InventoryView view = mock(InventoryView.class);
        when(event.getView()).thenReturn(view); when(view.getTopInventory()).thenReturn(top); when(event.getWhoClicked()).thenReturn(player); when(event.getClickedInventory()).thenReturn(clicked); return event;
    }
    private InventoryDragEvent drag(Inventory top, Set<Integer> slots) {
        InventoryDragEvent event = mock(InventoryDragEvent.class); InventoryView view = mock(InventoryView.class);
        when(event.getView()).thenReturn(view); when(view.getTopInventory()).thenReturn(top); when(event.getRawSlots()).thenReturn(slots); return event;
    }
    private InventoryCloseEvent close(Inventory top) {
        InventoryCloseEvent event = mock(InventoryCloseEvent.class); InventoryView view = mock(InventoryView.class);
        when(event.getInventory()).thenReturn(top); when(event.getView()).thenReturn(view); when(event.getPlayer()).thenReturn(player); return event;
    }
}
