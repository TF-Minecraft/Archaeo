package net.tfminecraft.archaeo.establish;

import net.tfminecraft.archaeo.config.*;
import net.tfminecraft.archaeo.excavation.HandPickService;
import net.tfminecraft.archaeo.excavation.PrismOutlineService;
import net.tfminecraft.archaeo.item.*;
import net.tfminecraft.archaeo.model.*;
import net.tfminecraft.archaeo.site.SiteRepository;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Item;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.Vector;
import org.bukkit.event.Event;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.time.Instant;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CampListenerTest {
    private ServerMock server;
    private JavaPlugin plugin;
    private PlayerMock director, worker;
    private Site site;
    private Block camp, outside;
    private SiteRepository sites;
    private CatalogRegistry catalogs;
    private EstablishService establish;
    private HandPickService handPick;
    private PrismOutlineService outline;
    private RecoveredFindItem recovered;
    private CampClosure closure;
    private CampArchiveBook archive;
    private CampListener listener;

    @Before public void setup() {
        server = MockBukkit.mock(); plugin = MockBukkit.createMockPlugin();
        director = server.addPlayer("Director"); worker = server.addPlayer("Worker");
        site = new Site(); site.setId(UUID.randomUUID()); site.setName("River camp");
        site.setWorldName(director.getWorld().getName()); site.setCreatedAt(Instant.EPOCH); site.setInterest(InterestLevel.LOW);
        site.establish(director.getUniqueId(), 1, 0, 20, 5, 4);
        camp = director.getWorld().getBlockAt(20, 5, 4); camp.setType(Material.CAMPFIRE);
        outside = director.getWorld().getBlockAt(21, 5, 4); outside.setType(Material.STONE);
        sites = mock(SiteRepository.class); catalogs = mock(CatalogRegistry.class);
        establish = mock(EstablishService.class); handPick = mock(HandPickService.class); outline = mock(PrismOutlineService.class);
        recovered = mock(RecoveredFindItem.class); closure = mock(CampClosure.class); archive = new CampArchiveBook(plugin);
        when(closure.archiveBook()).thenReturn(archive);
        when(sites.findById(site.getId())).thenReturn(Optional.of(site));
        when(sites.findLockedCampBlock(site.getWorldName(), 20, 5, 4)).thenAnswer(call -> site.isCampLocked() ? Optional.of(site) : Optional.empty());
        when(catalogs.pick()).thenReturn(PickSettings.defaults()); when(catalogs.establish()).thenReturn(EstablishSettings.defaults());
        when(catalogs.staffPermission()).thenReturn("archaeo.admin");
        when(recovered.standIn(any(), any(), any(), any())).thenReturn(new ItemStack(Material.BRICK));
        listener = new CampListener(plugin, sites, catalogs, new EstablishItem(ItemRef.vanilla(Material.STICK)),
                establish, handPick, outline, new BrushItem(ItemRef.vanilla(Material.BRUSH)), recovered, closure);
    }
    @After public void teardown() { MockBukkit.unmock(); }

    @Test public void campInteractionDeniesVanillaUseAndOpensOnlyOneMainHandBoard() {
        PlayerInteractEvent off = interact(director, camp, EquipmentSlot.OFF_HAND, null);
        listener.onInteract(off); assertEquals(Event.Result.DENY, off.useInteractedBlock());
        assertNull(director.getOpenInventory().getTopInventory());
        when(establish.isRelocating(director)).thenReturn(true);
        PlayerInteractEvent event = interact(director, camp, EquipmentSlot.HAND, null);
        listener.onInteract(event);
        assertEquals(Event.Result.DENY, event.useItemInHand()); assertTrue(event.isCancelled());
        assertTrue(director.getOpenInventory().getTopInventory().getHolder() instanceof CampBoard);
        verify(establish).tryCancelMove(director); verify(handPick).ensureJornada(site, director.getWorld());
    }

    @Test public void campProtectionPreservesOnlyRegisteredBlocksDuringWorldEvents() {
        BlockBreakEvent broken = new BlockBreakEvent(camp, worker); listener.onBreak(broken); assertTrue(broken.isCancelled());
        BlockBreakEvent normal = new BlockBreakEvent(outside, worker); listener.onBreak(normal); assertFalse(normal.isCancelled());
        BlockBurnEvent burn = new BlockBurnEvent(camp, outside); listener.onBurn(burn); assertTrue(burn.isCancelled());
        BlockFadeEvent fade = new BlockFadeEvent(camp, camp.getState()); listener.onFade(fade); assertTrue(fade.isCancelled());
        SignChangeEvent sign = new SignChangeEvent(camp, worker, new String[]{"changed", "", "", ""});
        listener.onSignChange(sign); assertTrue(sign.isCancelled());
        List<Block> blast = new ArrayList<>(List.of(camp, outside));
        EntityExplodeEvent explosion = mock(EntityExplodeEvent.class); when(explosion.blockList()).thenReturn(blast);
        listener.onEntityExplode(explosion); assertEquals(List.of(outside), blast);
        BlockPistonExtendEvent extend = new BlockPistonExtendEvent(outside, List.of(camp), BlockFace.NORTH);
        listener.onPistonExtend(extend); assertTrue(extend.isCancelled());
        BlockPistonRetractEvent retract = new BlockPistonRetractEvent(outside, List.of(outside), BlockFace.NORTH);
        listener.onPistonRetract(retract); assertFalse(retract.isCancelled());
        site.setStatus(SiteStatus.CLOSED);
        BlockBreakEvent closed = new BlockBreakEvent(camp, worker); listener.onBreak(closed); assertFalse(closed.isCancelled());
    }

    @Test public void boardClicksCannotMoveItemsAndAuthorizedActionsRouteToTheirServices() {
        board(director, true, true); InventoryClickEvent bottom = click(director, 27);
        listener.onBoardClick(bottom); assertTrue(bottom.isCancelled()); verifyNoInteractions(establish);
        InventoryClickEvent rename = click(director, CampBoard.SLOT_RENAME); listener.onBoardClick(rename);
        assertTrue(rename.isCancelled()); verify(establish).beginRename(director, site);
        board(director, true, true); listener.onBoardClick(click(director, CampBoard.SLOT_MOVE));
        verify(establish).beginRelocate(director, site);
        board(director, true, true); listener.onBoardClick(click(director, CampBoard.SLOT_LIMITS)); verify(outline).show(director, site);
        board(director, true, true); listener.onBoardClick(click(director, CampBoard.SLOT_WOOL_PRIMARY));
        assertTrue(director.getOpenInventory().getTopInventory().getHolder() instanceof CampWoolPicker);
        listener.onWoolPickerClick(click(director, DyeColor.BLUE.ordinal()));
        verify(establish).applyCampWool(director, site, DyeColor.BLUE, CampWoolRole.PRIMARY);
        assertTrue(director.getOpenInventory().getTopInventory().getHolder() instanceof CampBoard);
    }

    @Test public void formerDirectorCannotUseAnAlreadyOpenBoardToEditTheCamp() {
        for (int slot : new int[]{CampBoard.SLOT_RENAME, CampBoard.SLOT_MOVE, CampBoard.SLOT_WOOL_PRIMARY, CampBoard.SLOT_WOOL_SECONDARY}) {
            site.setDirector(director.getUniqueId()); board(director, true, true);
            site.setDirector(worker.getUniqueId()); clearInvocations(establish);
            listener.onBoardClick(click(director, slot));
            verify(establish, never()).beginRename(any(), any()); verify(establish, never()).beginRelocate(any(), any());
            assertTrue(director.getOpenInventory().getTopInventory() == null
                    || !(director.getOpenInventory().getTopInventory().getHolder() instanceof CampWoolPicker));
        }
    }

    @Test public void closedCampCannotBeEditedThroughAnAlreadyOpenDirectorBoard() {
        for (int slot : new int[]{CampBoard.SLOT_RENAME, CampBoard.SLOT_MOVE, CampBoard.SLOT_WOOL_PRIMARY, CampBoard.SLOT_WOOL_SECONDARY}) {
            site.setStatus(SiteStatus.ESTABLISHED); board(director, true, true);
            // Another authorised player can close the camp while this board stays open.
            site.setStatus(SiteStatus.CLOSED); clearInvocations(establish);
            listener.onBoardClick(click(director, slot));
            verify(establish, never()).beginRename(any(), any()); verify(establish, never()).beginRelocate(any(), any());
            assertTrue(director.getOpenInventory().getTopInventory() == null
                    || !(director.getOpenInventory().getTopInventory().getHolder() instanceof CampWoolPicker));
        }
    }

    @Test public void viewersCanConsultButCannotRenameMoveOrChangeStaff() {
        board(worker, false, false);
        listener.onBoardClick(click(worker, CampBoard.SLOT_RENAME)); listener.onBoardClick(click(worker, CampBoard.SLOT_MOVE));
        verifyNoInteractions(establish);
        listener.onBoardClick(click(worker, CampBoard.SLOT_PERSONAL));
        assertTrue(worker.getOpenInventory().getTopInventory().getHolder() instanceof CampStaffBoard);
        listener.onStaffClick(click(worker, CampStaffBoard.SLOT_ADD));
        AsyncPlayerChatEvent chat = chat(worker, "Director"); listener.onChat(chat); assertFalse(chat.isCancelled());
        assertEquals(List.of(director.getUniqueId()), site.getExcavators());
        board(worker, false, false); listener.onBoardClick(click(worker, CampBoard.SLOT_DOCUMENTATION));
        assertTrue(worker.getOpenInventory().getTopInventory().getHolder() instanceof CampFindsBoard);
    }

    @Test public void invitesValidateKnownPlayersAndPersistOnlyAfterMainThreadConfirmation() {
        invite(); AsyncPlayerChatEvent unknown = chat(director, "Nobody"); listener.onChat(unknown);
        assertTrue(unknown.isCancelled()); server.getScheduler().performOneTick();
        assertEquals(List.of(director.getUniqueId()), site.getExcavators()); assertTrue(messages(director).contains("No player with that name has joined this server."));
        AsyncPlayerChatEvent accepted = chat(director, "  wOrKeR  "); listener.onChat(accepted);
        assertTrue(accepted.isCancelled()); assertEquals(List.of(director.getUniqueId()), site.getExcavators());
        server.getScheduler().performOneTick(); assertTrue(site.getExcavators().contains(worker.getUniqueId()));
        verify(sites).save(site); assertEquals("You may now excavate River camp.", worker.nextMessage());
        invite(); listener.onChat(chat(director, "cancel")); server.getScheduler().performOneTick();
        assertTrue(messages(director).contains("Add worker cancelled."));
        AsyncPlayerChatEvent ordinary = chat(director, "hello"); listener.onChat(ordinary); assertFalse(ordinary.isCancelled());
    }

    @Test public void pendingInvitesRecheckCapacityAndOwnershipAtSubmission() {
        invite();
        when(catalogs.establish()).thenReturn(new EstablishSettings(true, true, Material.RED_STAINED_GLASS,
                Material.LIGHT_BLUE_STAINED_GLASS, 1, 1));
        listener.onChat(chat(director, "Worker")); server.getScheduler().performOneTick();
        assertEquals(List.of(director.getUniqueId()), site.getExcavators()); verify(sites, never()).save(any());
        when(catalogs.establish()).thenReturn(EstablishSettings.defaults()); invite(); site.setDirector(worker.getUniqueId());
        listener.onChat(chat(director, "Worker")); server.getScheduler().performOneTick();
        assertTrue(messages(director).contains("That excavation is no longer yours to staff.")); verify(sites, never()).save(any());
    }

    @Test public void staffRolesAndDismissalUpdatePermissionsAndNotifyTheWorker() {
        site.grantExcavator(worker.getUniqueId());
        new CampWorkerBoard(site.getId(), worker.getUniqueId(), true).open(director, site);
        int roleSlot = java.util.stream.IntStream.range(0, 27).filter(i -> CampWorkerBoard.roleAt(i) == SiteRole.EXCAVATOR).findFirst().orElseThrow();
        listener.onWorkerClick(click(director, roleSlot)); assertEquals(SiteRole.EXCAVATOR, site.roleOf(worker.getUniqueId()));
        assertTrue(messages(worker).stream().anyMatch(s -> s.contains("You are now")));
        listener.onWorkerClick(click(director, CampWorkerBoard.SLOT_REMOVE));
        assertFalse(site.getExcavators().contains(worker.getUniqueId())); assertFalse(site.mayWork(worker.getUniqueId()));
        assertEquals("You may no longer work on River camp.", worker.nextMessage()); verify(sites, times(2)).save(site);
        new CampWorkerBoard(site.getId(), director.getUniqueId(), true).open(director, site);
        listener.onWorkerClick(click(director, CampWorkerBoard.SLOT_REMOVE));
        assertEquals(director.getUniqueId(), site.getDirector()); assertTrue(messages(director).contains("The director cannot be removed."));
    }

    @Test public void closePromptRequiresExplicitConfirmationAndRechecksAuthorization() {
        BuriedFind buried = find(FindState.HIDDEN); site.getFinds().add(buried);
        board(director, true, true); listener.onBoardClick(click(director, CampBoard.SLOT_CLOSE));
        assertTrue(messages(director).stream().anyMatch(s -> s.contains("0% complete")));
        listener.onChat(chat(director, "maybe")); server.getScheduler().performOneTick(); verify(closure, never()).close(any(), any());
        listener.onChat(chat(director, "cancel")); server.getScheduler().performOneTick(); verify(closure, never()).close(any(), any());
        board(director, true, true); listener.onBoardClick(click(director, CampBoard.SLOT_CLOSE));
        listener.onChat(chat(director, "confirm")); verify(closure, never()).close(any(), any());
        server.getScheduler().performOneTick(); verify(closure).close(director, site);
        clearInvocations(closure); board(director, true, true); listener.onBoardClick(click(director, CampBoard.SLOT_CLOSE));
        site.setDirector(worker.getUniqueId()); listener.onChat(chat(director, "confirm")); server.getScheduler().performOneTick();
        verify(closure, never()).close(any(), any()); assertTrue(messages(director).contains("That excavation can no longer be closed."));
    }

    @Test public void renameAndMoveChatAreScheduledAndUnrelatedChatRemainsPublic() {
        when(establish.isRenaming(director)).thenReturn(true);
        AsyncPlayerChatEvent rename = chat(director, " River name "); listener.onChat(rename); assertTrue(rename.isCancelled());
        verify(establish, never()).handleRenameChat(any(), any()); server.getScheduler().performOneTick();
        verify(establish).handleRenameChat(director, "River name");
        when(establish.isRelocating(director)).thenReturn(true);
        listener.onChat(chat(director, " cancel ")); server.getScheduler().performOneTick(); verify(establish).tryCancelMove(director);
        when(establish.isRenaming(director)).thenReturn(false); when(establish.isRelocating(director)).thenReturn(false);
        AsyncPlayerChatEvent ordinary = chat(director, "chat"); listener.onChat(ordinary); assertFalse(ordinary.isCancelled());
    }

    @Test public void archiveCopiesRetainTheirLiveRecordAndMissingRecordsDoNotOpenBoards() {
        site.setStatus(SiteStatus.CLOSED); ItemStack book = archive.create(director, site);
        PlayerInteractEvent event = interact(worker, null, EquipmentSlot.HAND, book); listener.onInteract(event);
        assertTrue(event.isCancelled()); assertTrue(worker.getOpenInventory().getTopInventory().getHolder() instanceof CampBoard);
        CampBoard board = (CampBoard) worker.getOpenInventory().getTopInventory().getHolder();
        for (int slot : new int[]{CampBoard.SLOT_RENAME, CampBoard.SLOT_MOVE, CampBoard.SLOT_CLOSE})
            assertEquals(Material.GRAY_STAINED_GLASS_PANE, board.getInventory().getItem(slot).getType());
        CraftingInventory inventory = mock(CraftingInventory.class); ItemStack vanilla = new ItemStack(Material.WRITTEN_BOOK);
        when(inventory.getResult()).thenReturn(vanilla); when(inventory.getMatrix()).thenReturn(new ItemStack[]{new ItemStack(Material.PAPER), book});
        PrepareItemCraftEvent craft = mock(PrepareItemCraftEvent.class); when(craft.getInventory()).thenReturn(inventory);
        listener.onCopyArchive(craft);
        var copy = org.mockito.ArgumentCaptor.forClass(ItemStack.class); verify(inventory).setResult(copy.capture());
        assertTrue(archive.isArchive(copy.getValue())); assertEquals(site.getId(), archive.siteIdOf(copy.getValue()));
        when(sites.findById(site.getId())).thenReturn(Optional.empty()); worker.closeInventory();
        listener.onInteract(interact(worker, null, EquipmentSlot.HAND, book));
        assertEquals("That excavation record is missing.", worker.nextMessage());
    }

    @Test public void stationSigningRequiresRoleAndHeldArtifactThenPersistsTheAuthorOnce() {
        BuriedFind find = find(FindState.RECOVERED); site.getFinds().add(find);
        InterpretationType type = new InterpretationType("purpose", "Purpose", "What was it for?", List.of(new InterpretationTemplate("vessel", "purpose", "Vessel", Set.of(), Set.of(), Set.of())));
        when(catalogs.nextOpenType(find)).thenAnswer(call -> find.isCatalogued() ? null : type);
        when(catalogs.stationOffers(anyString(), any(), any(), anySet())).thenReturn(List.of(
                new InterpretationTemplate("vessel", "purpose", "Vessel", Set.of(), Set.of(), Set.of())));
        // MockBukkit's brewing inventory wrongly casts custom holders to BrewingStand.
        // A chest inventory retains the same holder contract for listener routing.
        CampIdentifyBoard board = mock(CampIdentifyBoard.class);
        when(board.siteId()).thenReturn(site.getId()); when(board.findId()).thenReturn(find.getId());
        when(board.type()).thenReturn(type); when(board.atCabinet()).thenReturn(true);
        when(board.offerAt(CampIdentifyBoard.SLOT_OFFER_0)).thenReturn("vessel");
        worker.openInventory(Bukkit.createInventory(board, 9)); listener.onIdentifyClick(click(worker, CampIdentifyBoard.SLOT_OFFER_0));
        assertFalse(find.isCatalogued()); assertTrue(messages(worker).contains("You are not authorised to write this record."));
        site.grantExcavator(worker.getUniqueId()); site.assignRole(worker.getUniqueId(), SiteRole.ARCHAEOLOGIST);
        listener.onIdentifyClick(click(worker, CampIdentifyBoard.SLOT_OFFER_0));
        assertFalse(find.isCatalogued()); assertTrue(messages(worker).contains("Keep the artifact in your hand."));
        when(recovered.isInMainHand(worker, find.getId())).thenReturn(true);
        listener.onIdentifyClick(click(worker, CampIdentifyBoard.SLOT_OFFER_0));
        assertEquals(1, find.getInterpretations().size()); assertEquals("vessel", find.getInterpretations().getFirst().interpretationId());
        assertEquals(worker.getUniqueId(), find.getInterpretations().getFirst().author()); verify(sites).save(site);
        listener.onIdentifyClick(click(worker, CampIdentifyBoard.SLOT_OFFER_0));
        assertEquals(1, find.getInterpretations().size()); verify(sites).save(site);
        server.getScheduler().performOneTick();
        assertNull(worker.getOpenInventory().getTopInventory());
    }

    @Test public void reportIssuingRequiresDirectorAndExhaustedSiteAndPreservesOverflowBooks() {
        new CampFindsBoard(site.getId(), false, catalogs, recovered).open(worker, site);
        listener.onFindsClick(click(worker, CampFindsBoard.SLOT_REPORT));
        assertTrue(messages(worker).contains("Only the director may issue the report."));
        new CampFindsBoard(site.getId(), true, catalogs, recovered).open(director, site);
        listener.onFindsClick(click(director, CampFindsBoard.SLOT_REPORT));
        assertTrue(messages(director).contains("The report is issued when the cut is closed."));
        site.setStatus(SiteStatus.EXHAUSTED);
        for (int slot = 0; slot < 36; slot++) director.getInventory().setItem(slot, new ItemStack(Material.STONE, 64));
        director.getInventory().setArmorContents(new ItemStack[]{new ItemStack(Material.IRON_BOOTS),
                new ItemStack(Material.IRON_LEGGINGS), new ItemStack(Material.IRON_CHESTPLATE), new ItemStack(Material.IRON_HELMET)});
        director.getInventory().setItemInOffHand(new ItemStack(Material.TORCH, 64));
        PlayerMock receiver = spy(director); var fullStorage = spy(director.getInventory());
        doReturn(fullStorage).when(receiver).getInventory();
        // Bukkit addItem uses storage only; MockBukkit also searches two phantom player slots.
        doAnswer(call -> {
            var overflow = new HashMap<Integer, ItemStack>();
            overflow.put(0, call.getArgument(0)); return overflow;
        }).when(fullStorage).addItem(any(ItemStack.class));
        InventoryClickEvent reportClick = spy(click(director, CampFindsBoard.SLOT_REPORT));
        doReturn(receiver).when(reportClick).getWhoClicked();
        listener.onFindsClick(reportClick);
        assertTrue(director.getWorld().getEntities().stream().filter(Item.class::isInstance).map(Item.class::cast)
                .anyMatch(item -> new FindReportBook(plugin).isReport(item.getItemStack())));
    }

    @Test public void dragsCannotInsertItemsIntoCampBoardsAndStaleRecordsCloseTheView() {
        board(director, true, true);
        InventoryDragEvent drag = new InventoryDragEvent(director.getOpenInventory(), new ItemStack(Material.STONE),
                new ItemStack(Material.STONE, 2), false, Map.of(0, new ItemStack(Material.STONE)));
        listener.onBoardDrag(drag); assertTrue(drag.isCancelled());
        when(sites.findById(site.getId())).thenReturn(Optional.empty()); listener.onBoardClick(click(director, CampBoard.SLOT_RENAME));
        assertNull(director.getOpenInventory().getTopInventory()); verifyNoInteractions(establish);
    }

    @Test public void successiveStationQuestionsPersistDistinctAnswersAndRefreshTheHeldPiece() {
        BuriedFind find = find(FindState.RECOVERED); site.getFinds().add(find);
        site.grantExcavator(worker.getUniqueId()); site.assignRole(worker.getUniqueId(), SiteRole.ARCHAEOLOGIST);
        InterpretationTemplate purpose = new InterpretationTemplate("vessel", "purpose", "Vessel", Set.of(), Set.of(), Set.of());
        InterpretationTemplate epoch = new InterpretationTemplate("roman", "epoch", "Roman", Set.of(), Set.of(), Set.of());
        InterpretationType first = new InterpretationType("purpose", "Purpose", "What was it for?", List.of(purpose));
        InterpretationType second = new InterpretationType("epoch", "Epoch", "When was it made?", List.of(epoch));
        when(catalogs.nextOpenType(find)).thenAnswer(call -> switch (find.getInterpretations().size()) {
            case 0 -> first; case 1 -> second; default -> null;
        });
        when(catalogs.stationOffers(anyString(), any(), any(), anySet())).thenAnswer(call ->
                "purpose".equals(call.getArgument(0)) ? List.of(purpose) : List.of(epoch));
        ArtifactTemplate template = mock(ArtifactTemplate.class); when(template.tags()).thenReturn(Set.of());
        when(catalogs.artifact("pot")).thenReturn(template); when(recovered.isInMainHand(worker, find.getId())).thenReturn(true);
        // Adapt only the unsupported brewing inventory holder; keep boards and question transitions real.
        try (var bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
            bukkit.when(() -> Bukkit.createInventory(any(InventoryHolder.class), eq(InventoryType.BREWING), anyString()))
                    .thenAnswer(call -> server.createInventory(call.getArgument(0), 9, (String) call.getArgument(2)));
            assertTrue(new CampIdentifyBoard(site.getId(), find.getId(), catalogs, recovered, true).open(worker, site));
            InventoryClickEvent firstClick = click(worker, CampIdentifyBoard.SLOT_OFFER_0);
            listener.onIdentifyClick(firstClick);
            assertEquals("purpose", find.getInterpretations().getFirst().typeId());
            CampIdentifyBoard next = (CampIdentifyBoard) worker.getOpenInventory().getTopInventory().getHolder();
            assertEquals("epoch", next.type().id()); assertTrue(next.atCabinet());
            listener.onIdentifyClick(firstClick); // A delayed repeat of the first question must not file twice.
            assertEquals(1, find.getInterpretations().size());
            listener.onIdentifyClick(click(worker, CampIdentifyBoard.SLOT_OFFER_0));
            assertEquals(List.of("vessel", "roman"), find.getInterpretations().stream().map(FindInterpretation::interpretationId).toList());
            assertTrue(find.getInterpretations().stream().allMatch(answer -> worker.getUniqueId().equals(answer.author())));
            verify(sites, times(2)).save(site);
            verify(recovered, times(2)).refreshCarried(eq(worker), eq(site), eq(find), eq(template), anyString(), eq(catalogs));
            server.getScheduler().performOneTick(); assertNull(worker.getOpenInventory().getTopInventory());
        }
    }

    @Test public void stationBackReturnsToTheFindRecordOrLeavesTheCabinetWithoutChangingAnswers() {
        when(catalogs.materialOf(any())).thenReturn(new FindMaterial("ceramic", "Ceramic", 1.0, Material.WHITE_STAINED_GLASS_PANE, List.of()));
        BuriedFind find = find(FindState.RECOVERED); site.getFinds().add(find);
        for (boolean cabinet : new boolean[]{false, true}) {
            CampIdentifyBoard board = mock(CampIdentifyBoard.class);
            when(board.siteId()).thenReturn(site.getId()); when(board.findId()).thenReturn(find.getId());
            when(board.atCabinet()).thenReturn(cabinet);
            worker.openInventory(Bukkit.createInventory(board, 9));
            listener.onIdentifyClick(click(worker, CampIdentifyBoard.SLOT_BACK));
            if (cabinet) assertNull(worker.getOpenInventory().getTopInventory());
            else assertTrue(worker.getOpenInventory().getTopInventory().getHolder() instanceof CampFindBoard);
        }
        assertTrue(find.getInterpretations().isEmpty()); verify(sites, never()).save(any());
    }

    @Test public void findRegisterAndMuseumFilesNavigateWithoutLettingVisitorsTakeIcons() {
        BuriedFind find = find(FindState.RECOVERED); site.getFinds().add(find);
        when(catalogs.materialOf(any())).thenReturn(new FindMaterial("ceramic", "Ceramic", 1, Material.WHITE_STAINED_GLASS_PANE, List.of()));
        new CampFindsBoard(site.getId(), false, catalogs, recovered).open(worker, site);
        CampFindsBoard register = (CampFindsBoard) worker.getOpenInventory().getTopInventory().getHolder();
        int row = java.util.stream.IntStream.range(0, 27).filter(slot -> find.getId().equals(register.findAt(slot))).findFirst().orElseThrow();
        listener.onFindsClick(click(worker, row));
        Inventory file = worker.getOpenInventory().getTopInventory(); assertTrue(file.getHolder() instanceof CampFindBoard);
        ItemStack icon = file.getItem(CampFindBoard.SLOT_IDENTITY).clone();
        InventoryClickEvent identity = click(worker, CampFindBoard.SLOT_IDENTITY); listener.onFindFileClick(identity);
        assertTrue(identity.isCancelled()); assertEquals(icon, file.getItem(CampFindBoard.SLOT_IDENTITY));
        InventoryClickEvent bottom = click(worker, 27); listener.onFindFileClick(bottom); assertTrue(bottom.isCancelled());
        listener.onFindFileClick(click(worker, CampFindBoard.SLOT_BACK));
        assertTrue(worker.getOpenInventory().getTopInventory().getHolder() instanceof CampFindsBoard);
        listener.onFindsClick(click(worker, CampFindsBoard.SLOT_BACK));
        assertTrue(worker.getOpenInventory().getTopInventory().getHolder() instanceof CampBoard);
        new CampFindBoard(site.getId(), find.getId(), catalogs, true).open(worker, site);
        listener.onFindFileClick(click(worker, CampFindBoard.SLOT_BACK)); assertNull(worker.getOpenInventory().getTopInventory());
        new CampFindBoard(site.getId(), find.getId(), catalogs).open(worker, site);
        when(sites.findById(site.getId())).thenReturn(Optional.empty());
        listener.onFindFileClick(click(worker, CampFindBoard.SLOT_BACK)); assertNull(worker.getOpenInventory().getTopInventory());
        assertEquals(1, find.getFindNumber()); verify(sites).save(site); // Legacy recovered rows gain a stable register number once.
    }

    @Test public void relocationProxyUsesMainHandAndAttackOrSlotChangeCancelsOnlyActiveMoves() {
        Entity proxy = mock(Entity.class); Entity ordinary = mock(Entity.class);
        when(establish.tryFinishMoveOnAimProxy(director, proxy)).thenReturn(true);
        PlayerInteractAtEntityEvent at = new PlayerInteractAtEntityEvent(director, proxy, new Vector(), EquipmentSlot.HAND);
        listener.onAimProxyAt(at); assertTrue(at.isCancelled());
        PlayerInteractEntityEvent generic = new PlayerInteractEntityEvent(director, proxy, EquipmentSlot.HAND);
        listener.onAimProxy(generic); assertTrue(generic.isCancelled());
        PlayerInteractEntityEvent offhand = new PlayerInteractEntityEvent(director, proxy, EquipmentSlot.OFF_HAND);
        listener.onAimProxy(offhand); assertFalse(offhand.isCancelled());
        PlayerInteractAtEntityEvent offhandAt = new PlayerInteractAtEntityEvent(director, proxy, new Vector(), EquipmentSlot.OFF_HAND);
        listener.onAimProxyAt(offhandAt); assertFalse(offhandAt.isCancelled());
        verify(establish, times(2)).tryFinishMoveOnAimProxy(director, proxy);
        PlayerInteractEntityEvent other = new PlayerInteractEntityEvent(director, ordinary, EquipmentSlot.HAND);
        listener.onAimProxy(other); assertFalse(other.isCancelled());
        when(establish.handleMoveAimProxyAttack(director, proxy)).thenReturn(true);
        EntityDamageByEntityEvent attack = mock(EntityDamageByEntityEvent.class);
        when(attack.getDamager()).thenReturn(director); when(attack.getEntity()).thenReturn(proxy);
        listener.onAimProxyAttack(attack); verify(attack).setCancelled(true);
        listener.onHeld(new PlayerItemHeldEvent(director, 0, 1)); verify(establish, never()).tryCancelMove(any());
        when(establish.isRelocating(director)).thenReturn(true);
        listener.onHeld(new PlayerItemHeldEvent(director, 1, 2)); verify(establish).tryCancelMove(director);
        PlayerInteractEvent finish = interact(director, outside, EquipmentSlot.HAND, null);
        listener.onInteract(finish); assertTrue(finish.isCancelled()); verify(establish).tryFinishMove(director);
        when(establish.tryCancelMove(director)).thenReturn(true);
        PlayerInteractEvent abort = new PlayerInteractEvent(director, Action.LEFT_CLICK_AIR, null, null, BlockFace.SELF, EquipmentSlot.HAND);
        listener.onInteract(abort); assertTrue(abort.isCancelled()); verify(establish, times(2)).tryCancelMove(director);
    }

    @Test public void entitiesAndBlockExplosionsCannotAlterCampButUnrelatedBlocksRemainDestructible() {
        EntityChangeBlockEvent change = mock(EntityChangeBlockEvent.class); when(change.getBlock()).thenReturn(camp);
        listener.onEntityChange(change); verify(change).setCancelled(true);
        EntityChangeBlockEvent normal = mock(EntityChangeBlockEvent.class); when(normal.getBlock()).thenReturn(outside);
        listener.onEntityChange(normal); verify(normal, never()).setCancelled(anyBoolean());
        List<Block> blocks = new ArrayList<>(List.of(outside, camp));
        BlockExplodeEvent blast = mock(BlockExplodeEvent.class); when(blast.blockList()).thenReturn(blocks);
        listener.onExplode(blast); assertEquals(List.of(outside), blocks);
        BlockPistonExtendEvent head = new BlockPistonExtendEvent(outside, List.of(), BlockFace.WEST);
        listener.onPistonExtend(head); assertTrue(head.isCancelled());
        BlockPistonRetractEvent pull = new BlockPistonRetractEvent(outside, List.of(camp), BlockFace.WEST);
        listener.onPistonRetract(pull); assertTrue(pull.isCancelled());
    }

    @Test public void fullRosterDoesNotCaptureChatAndDuplicateInvitesNeverSave() {
        when(catalogs.establish()).thenReturn(new EstablishSettings(true, true, Material.RED_STAINED_GLASS, Material.LIGHT_BLUE_STAINED_GLASS, 1, 1));
        invite(); AsyncPlayerChatEvent normal = chat(director, "Worker"); listener.onChat(normal);
        assertFalse(normal.isCancelled()); assertTrue(director.getOpenInventory().getTopInventory().getHolder() instanceof CampStaffBoard);
        when(catalogs.establish()).thenReturn(EstablishSettings.defaults());
        for (String member : List.of("Director", "Worker")) {
            if (member.equals("Worker")) site.grantExcavator(worker.getUniqueId());
            invite(); listener.onChat(chat(director, member)); server.getScheduler().performOneTick();
            assertTrue(messages(director).stream().anyMatch(line -> line.contains("already on the staff")));
        }
        verify(sites, never()).save(any()); assertEquals(2, site.getExcavators().size());
    }

    @Test public void workerFilesRecheckDirectorAndCampBeforeChangingRolesOrDismissing() {
        site.grantExcavator(worker.getUniqueId());
        new CampStaffBoard(site.getId(), true, 18).open(director, site);
        CampStaffBoard roster = (CampStaffBoard) director.getOpenInventory().getTopInventory().getHolder();
        int workerSlot = java.util.stream.IntStream.range(0, 18).filter(slot -> worker.getUniqueId().equals(roster.playerAt(slot))).findFirst().orElseThrow();
        listener.onStaffClick(click(director, workerSlot)); assertTrue(director.getOpenInventory().getTopInventory().getHolder() instanceof CampWorkerBoard);
        listener.onWorkerClick(click(director, CampWorkerBoard.SLOT_BACK));
        listener.onStaffClick(click(director, CampStaffBoard.SLOT_BACK)); assertTrue(director.getOpenInventory().getTopInventory().getHolder() instanceof CampBoard);
        new CampWorkerBoard(site.getId(), worker.getUniqueId(), true).open(director, site);
        site.setDirector(UUID.randomUUID()); listener.onWorkerClick(click(director, CampWorkerBoard.SLOT_REMOVE));
        assertTrue(site.getExcavators().contains(worker.getUniqueId()));
        site.setDirector(director.getUniqueId()); site.setStatus(SiteStatus.CLOSED);
        listener.onWorkerClick(click(director, CampWorkerBoard.SLOT_REMOVE)); assertTrue(site.getExcavators().contains(worker.getUniqueId()));
        when(sites.findById(site.getId())).thenReturn(Optional.empty());
        listener.onWorkerClick(click(director, CampWorkerBoard.SLOT_REMOVE)); assertNull(director.getOpenInventory().getTopInventory());
        verify(sites, never()).save(any());
    }

    @Test public void offlineWorkersCanBeReassignedAndDismissedWithoutAnOnlineNotificationTarget() {
        site.grantExcavator(worker.getUniqueId()); worker.disconnect();
        new CampWorkerBoard(site.getId(), worker.getUniqueId(), true).open(director, site);
        int roleSlot = java.util.stream.IntStream.range(0, 27).filter(i -> CampWorkerBoard.roleAt(i) == SiteRole.EXCAVATOR).findFirst().orElseThrow();
        listener.onWorkerClick(click(director, roleSlot)); assertEquals(SiteRole.EXCAVATOR, site.roleOf(worker.getUniqueId()));
        listener.onWorkerClick(click(director, roleSlot)); verify(sites).save(site); // Selecting the current role must not dirty the dossier.
        listener.onWorkerClick(click(director, CampWorkerBoard.SLOT_REMOVE));
        assertFalse(site.mayWork(worker.getUniqueId())); verify(sites, times(2)).save(site);
    }

    @Test public void secondaryWoolPickerSupportsBackAndRejectsChoicesAfterTheCampCloses() {
        board(director, true, true); listener.onBoardClick(click(director, CampBoard.SLOT_WOOL_SECONDARY));
        assertTrue(director.getOpenInventory().getTopInventory().getHolder() instanceof CampWoolPicker);
        listener.onWoolPickerClick(click(director, DyeColor.YELLOW.ordinal()));
        verify(establish).applyCampWool(director, site, DyeColor.YELLOW, CampWoolRole.SECONDARY);
        listener.onBoardClick(click(director, CampBoard.SLOT_WOOL_SECONDARY));
        listener.onWoolPickerClick(click(director, CampWoolPicker.SLOT_BACK));
        assertTrue(director.getOpenInventory().getTopInventory().getHolder() instanceof CampBoard);
        listener.onBoardClick(click(director, CampBoard.SLOT_WOOL_PRIMARY)); assertTrue(site.closeCamp());
        clearInvocations(establish); listener.onWoolPickerClick(click(director, DyeColor.BLUE.ordinal()));
        verify(establish, never()).applyCampWool(any(), any(), any(), any());
        assertNull(director.getOpenInventory().getTopInventory());
    }

    @Test public void woolPickerRechecksTheDirectorWhenAnOpenCampChangesOwner() {
        board(director, true, true); listener.onBoardClick(click(director, CampBoard.SLOT_WOOL_PRIMARY));
        site.setDirector(worker.getUniqueId()); listener.onWoolPickerClick(click(director, DyeColor.BLUE.ordinal()));
        verify(establish, never()).applyCampWool(any(), any(), any(), any());
        assertNull(director.getOpenInventory().getTopInventory());
    }

    @Test public void staffCanCloseAnExhaustedCampAfterExplicitConfirmation() {
        site.setStatus(SiteStatus.EXHAUSTED); worker.addAttachment(plugin, "archaeo.admin", true);
        listener.onInteract(interact(worker, camp, EquipmentSlot.HAND, null));
        listener.onBoardClick(click(worker, CampBoard.SLOT_CLOSE));
        assertTrue(messages(worker).contains("Type confirm to close the camp. The record will move to a field book. Type cancel to keep it."));
        listener.onChat(chat(worker, "confirm")); verify(closure, never()).close(any(), any());
        server.getScheduler().performOneTick(); verify(closure).close(worker, site);
    }

    @Test public void breakingDuringSneakingRelocationCannotDamageTheAimedTerrain() {
        when(establish.isRelocating(director)).thenReturn(true); when(establish.sneakHeld(director)).thenReturn(true);
        BlockBreakEvent protectedBreak = new BlockBreakEvent(outside, director); listener.onBreak(protectedBreak);
        assertTrue(protectedBreak.isCancelled()); assertEquals(Material.STONE, outside.getType());
        when(establish.isRelocating(director)).thenReturn(false);
        BlockBreakEvent ordinaryBreak = new BlockBreakEvent(outside, director); listener.onBreak(ordinaryBreak);
        assertFalse(ordinaryBreak.isCancelled());
    }

    @Test public void ordinaryBlocksNextToTheCampRemainOpenToFireDecayPistonsAndSigns() {
        BlockBurnEvent burn = new BlockBurnEvent(outside, camp); listener.onBurn(burn); assertFalse(burn.isCancelled());
        BlockFadeEvent fade = new BlockFadeEvent(outside, outside.getState()); listener.onFade(fade); assertFalse(fade.isCancelled());
        SignChangeEvent sign = new SignChangeEvent(outside, worker, new String[]{"shop", "", "", ""});
        listener.onSignChange(sign); assertFalse(sign.isCancelled());
        Block beyond = outside.getRelative(BlockFace.EAST); beyond.setType(Material.STONE);
        BlockPistonExtendEvent push = new BlockPistonExtendEvent(beyond, List.of(beyond.getRelative(BlockFace.EAST)), BlockFace.EAST);
        listener.onPistonExtend(push); assertFalse(push.isCancelled());
        assertEquals(Material.CAMPFIRE, camp.getType());
    }

    @Test public void offHandAndTramplingEventsOnlyBlockVanillaUseOfCampPieces() {
        PlayerInteractEvent ordinary = interact(director, outside, EquipmentSlot.OFF_HAND, new ItemStack(Material.TORCH));
        listener.onInteract(ordinary); assertFalse(ordinary.isCancelled()); assertEquals(Event.Result.DEFAULT, ordinary.useItemInHand());
        PlayerInteractEvent air = interact(director, null, EquipmentSlot.OFF_HAND, new ItemStack(Material.BREAD));
        listener.onInteract(air); assertEquals(Event.Result.DEFAULT, air.useItemInHand());
        // Paper reports trampling (pressure plates, farmland) with no hand at all.
        PlayerInteractEvent trample = new PlayerInteractEvent(director, Action.PHYSICAL, null, camp, BlockFace.SELF, null);
        listener.onInteract(trample); assertFalse(trample.isCancelled());
        assertNull(director.getOpenInventory().getTopInventory()); verifyNoInteractions(establish);
    }

    @Test public void relocationIsOnlyConfirmedByRightClicksAndAnyLeftClickAbortsIt() {
        when(establish.isRelocating(director)).thenReturn(true);
        // The five-argument Bukkit constructor, used by plugins that fire synthetic trampling, defaults to the main hand.
        PlayerInteractEvent trample = new PlayerInteractEvent(director, Action.PHYSICAL, null, outside, BlockFace.SELF);
        listener.onInteract(trample); assertFalse(trample.isCancelled());
        PlayerInteractEvent kit = interact(director, null, EquipmentSlot.HAND, new ItemStack(Material.STICK));
        listener.onInteract(kit); assertEquals(Event.Result.DEFAULT, kit.useItemInHand()); // EstablishListener confirms kit clicks itself.
        verify(establish, never()).tryFinishMove(any());
        when(establish.tryCancelMove(director)).thenReturn(true);
        PlayerInteractEvent abort = new PlayerInteractEvent(director, Action.LEFT_CLICK_BLOCK, null, outside, BlockFace.UP, EquipmentSlot.HAND);
        listener.onInteract(abort); assertTrue(abort.isCancelled()); verify(establish).tryCancelMove(director);
        when(establish.isRelocating(director)).thenReturn(false); when(establish.tryCancelMove(director)).thenReturn(false);
        PlayerInteractEvent mining = new PlayerInteractEvent(director, Action.LEFT_CLICK_BLOCK, null, outside, BlockFace.UP, EquipmentSlot.HAND);
        listener.onInteract(mining); assertFalse(mining.isCancelled());
        PlayerInteractEvent eating = interact(director, null, EquipmentSlot.HAND, new ItemStack(Material.BREAD));
        listener.onInteract(eating); assertEquals(Event.Result.DEFAULT, eating.useItemInHand()); verify(establish, never()).tryFinishMove(any());
    }

    @Test public void chattingDuringAMoveStaysPublicUnlessItIsTheCancelWord() {
        when(establish.isRelocating(director)).thenReturn(true);
        AsyncPlayerChatEvent talk = chat(director, "where should it go?"); listener.onChat(talk);
        assertFalse(talk.isCancelled()); server.getScheduler().performOneTick(); verify(establish, never()).tryCancelMove(any());
        AsyncPlayerChatEvent cancel = chat(director, "Cancel"); listener.onChat(cancel);
        assertTrue(cancel.isCancelled()); // The cancel word never reaches public chat.
        verify(establish, never()).tryCancelMove(any()); // Chat is async; the move is abandoned on the main thread.
        server.getScheduler().performOneTick(); verify(establish).tryCancelMove(director);
        verify(establish, never()).handleRenameChat(any(), any());
    }

    @Test public void onlyFieldBookCopiesAreRestampedAndOtherCraftingIsUntouched() {
        CraftingInventory grid = mock(CraftingInventory.class); PrepareItemCraftEvent craft = mock(PrepareItemCraftEvent.class);
        when(craft.getInventory()).thenReturn(grid);
        listener.onCopyArchive(craft); // No recipe matches the grid yet.
        when(grid.getResult()).thenReturn(new ItemStack(Material.OAK_PLANKS, 4)); listener.onCopyArchive(craft);
        ItemStack diary = new ItemStack(Material.WRITTEN_BOOK);
        when(grid.getResult()).thenReturn(diary.clone()); when(grid.getMatrix()).thenReturn(new ItemStack[]{diary, new ItemStack(Material.WRITABLE_BOOK), null});
        listener.onCopyArchive(craft);
        verify(grid, never()).setResult(any());
    }

    @Test public void aimProxyHandlersIgnoreOrdinaryEntitiesAndMobAttacks() {
        Entity villager = mock(Entity.class);
        PlayerInteractAtEntityEvent trade = new PlayerInteractAtEntityEvent(director, villager, new Vector(), EquipmentSlot.HAND);
        listener.onAimProxyAt(trade); assertFalse(trade.isCancelled());
        EntityDamageByEntityEvent swing = mock(EntityDamageByEntityEvent.class);
        when(swing.getDamager()).thenReturn(director); when(swing.getEntity()).thenReturn(villager);
        listener.onAimProxyAttack(swing); verify(swing, never()).setCancelled(anyBoolean());
        EntityDamageByEntityEvent zombie = mock(EntityDamageByEntityEvent.class);
        when(zombie.getDamager()).thenReturn(mock(org.bukkit.entity.Zombie.class)); when(zombie.getEntity()).thenReturn(director);
        listener.onAimProxyAttack(zombie); verify(zombie, never()).setCancelled(anyBoolean());
        verify(establish, never()).handleMoveAimProxyAttack(any(), eq(director));
    }

    @Test public void everyCampWindowSwallowsStrayClicksAndClosesOnceItsRecordIsGoneOrUnreadable() {
        BuriedFind find = find(FindState.RECOVERED); find.setFindNumber(1); site.getFinds().add(find); site.grantExcavator(worker.getUniqueId());
        when(catalogs.materialOf(any())).thenReturn(new FindMaterial("ceramic", "Ceramic", 1, Material.WHITE_STAINED_GLASS_PANE, List.of()));
        CampIdentifyBoard station = mock(CampIdentifyBoard.class); when(station.siteId()).thenReturn(site.getId());
        List<Runnable> windows = List.of(() -> board(director, true, true),
                () -> new CampStaffBoard(site.getId(), true, 18).open(director, site),
                () -> new CampWorkerBoard(site.getId(), worker.getUniqueId(), true).open(director, site),
                () -> new CampFindsBoard(site.getId(), true, catalogs, recovered).open(director, site),
                () -> new CampFindBoard(site.getId(), find.getId(), catalogs).open(director, site),
                // MockBukkit cannot hold a custom holder in a brewing window; a chest keeps the routing contract.
                () -> director.openInventory(Bukkit.createInventory(station, 9)),
                () -> new CampWoolPicker(site.getId(), CampWoolRole.PRIMARY).open(director, site));
        for (Runnable window : windows) {
            for (int slot : new int[]{27, -999}) {
                site.setStatus(SiteStatus.ESTABLISHED); window.run(); Inventory open = director.getOpenInventory().getTopInventory();
                InventoryClickEvent stray = new InventoryClickEvent(director.getOpenInventory(), slot < 0 ? InventoryType.SlotType.OUTSIDE
                        : InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
                clickAll(stray); assertTrue(stray.isCancelled()); assertSame(open, director.getOpenInventory().getTopInventory());
            }
            window.run(); when(sites.findById(site.getId())).thenReturn(Optional.empty());
            clickAll(click(director, 0)); assertNull(director.getOpenInventory().getTopInventory());
            when(sites.findById(site.getId())).thenReturn(Optional.of(site)); window.run();
            site.setStatus(SiteStatus.HIDDEN); // A dossier reloaded without its status reads as an unclaimed ruin.
            clickAll(click(director, 0)); assertNull(director.getOpenInventory().getTopInventory());
        }
        director.openInventory(Bukkit.createInventory(null, 27)); InventoryClickEvent chest = click(director, 0);
        clickAll(chest); assertFalse(chest.isCancelled());
        verify(sites, never()).save(any()); verifyNoInteractions(establish, outline);
    }

    @Test public void dragsIntoAnyCampOrCabinetWindowAreCancelledButOrdinaryChestsAreNot() {
        BuriedFind find = find(FindState.RECOVERED); site.getFinds().add(find);
        when(catalogs.materialOf(any())).thenReturn(new FindMaterial("ceramic", "Ceramic", 1, Material.WHITE_STAINED_GLASS_PANE, List.of()));
        List<Runnable> windows = List.of(() -> board(director, true, true),
                () -> new CampWoolPicker(site.getId(), CampWoolRole.SECONDARY).open(director, site),
                () -> new CampStaffBoard(site.getId(), true, 18).open(director, site),
                () -> new CampWorkerBoard(site.getId(), director.getUniqueId(), true).open(director, site),
                () -> new CampFindsBoard(site.getId(), true, catalogs, recovered).open(director, site),
                () -> new CampFindBoard(site.getId(), find.getId(), catalogs).open(director, site),
                () -> director.openInventory(Bukkit.createInventory(mock(CampIdentifyBoard.class), 9)),
                () -> director.openInventory(Bukkit.createInventory(new net.tfminecraft.archaeo.sketch.SketchCabinet(), 9)));
        for (Runnable window : windows) { window.run(); assertTrue(drag().isCancelled()); }
        director.openInventory(Bukkit.createInventory(null, 27)); assertFalse(drag().isCancelled());
    }

    @Test public void campButtonsDoNothingWhereThereIsNoActionToTake() {
        site.setStatus(SiteStatus.CLOSED); board(director, false, false);
        listener.onBoardClick(click(director, CampBoard.SLOT_LIMITS)); // A filed camp shows its location instead of the prism.
        verify(outline, never()).show(any(), any()); assertTrue(director.getOpenInventory().getTopInventory().getHolder() instanceof CampBoard);
        site.setStatus(SiteStatus.ESTABLISHED); board(worker, false, false); listener.onBoardClick(click(worker, CampBoard.SLOT_CLOSE));
        assertTrue(messages(worker).isEmpty()); listener.onChat(chat(worker, "confirm")); server.getScheduler().performOneTick();
        verify(closure, never()).close(any(), any());
        board(director, true, true); listener.onBoardClick(click(director, 0));
        assertTrue(director.getOpenInventory().getTopInventory().getHolder() instanceof CampBoard);
        new CampStaffBoard(site.getId(), true, 18).open(director, site); listener.onStaffClick(click(director, 20));
        assertTrue(director.getOpenInventory().getTopInventory().getHolder() instanceof CampStaffBoard);
        site.grantExcavator(worker.getUniqueId()); new CampWorkerBoard(site.getId(), worker.getUniqueId(), true).open(director, site);
        listener.onWorkerClick(click(director, 0)); assertEquals(SiteRole.defaultRole(), site.roleOf(worker.getUniqueId()));
        new CampFindsBoard(site.getId(), true, catalogs, recovered).open(director, site); listener.onFindsClick(click(director, 0));
        assertTrue(director.getOpenInventory().getTopInventory().getHolder() instanceof CampFindsBoard);
        new CampWoolPicker(site.getId(), CampWoolRole.PRIMARY).open(director, site); listener.onWoolPickerClick(click(director, 20));
        assertTrue(director.getOpenInventory().getTopInventory().getHolder() instanceof CampWoolPicker);
        CampIdentifyBoard station = mock(CampIdentifyBoard.class); when(station.siteId()).thenReturn(site.getId());
        director.openInventory(Bukkit.createInventory(station, 9)); listener.onIdentifyClick(click(director, CampIdentifyBoard.SLOT_PIECE));
        verify(sites, never()).save(any()); verify(establish, never()).beginRename(any(), any());
        verify(establish, never()).beginRelocate(any(), any()); verify(establish, never()).applyCampWool(any(), any(), any(), any());
    }

    @Test public void stationRefusesToSignForAPieceTheReloadedDossierNoLongerHoldsAsRecovered() {
        BuriedFind find = find(FindState.RECOVERED); site.getFinds().add(find);
        InterpretationType type = new InterpretationType("purpose", "Purpose", "What was it for?", List.of());
        CampIdentifyBoard station = mock(CampIdentifyBoard.class);
        when(station.siteId()).thenReturn(site.getId()); when(station.findId()).thenReturn(find.getId());
        when(station.type()).thenReturn(type); when(station.offerAt(CampIdentifyBoard.SLOT_OFFER_0)).thenReturn("vessel");
        when(recovered.isInMainHand(director, find.getId())).thenReturn(true);
        director.openInventory(Bukkit.createInventory(station, 9));
        // Staff edit the dossier and run /archaeo reload while the station stays open.
        find.setState(FindState.LOST); listener.onIdentifyClick(click(director, CampIdentifyBoard.SLOT_OFFER_0));
        site.getFinds().clear(); listener.onIdentifyClick(click(director, CampIdentifyBoard.SLOT_OFFER_0));
        assertEquals(List.of("That find cannot be identified.", "That find cannot be identified."), messages(director));
        assertTrue(find.getInterpretations().isEmpty()); verify(sites, never()).save(any());
    }

    @Test public void staffInvitesRejectBlankNamesAndClosedCampsAndFileADoubleSubmissionOnce() {
        invite(); listener.onChat(chat(director, "   ")); server.getScheduler().performOneTick();
        assertTrue(messages(director).contains("No player with that name has joined this server."));
        listener.onChat(chat(director, "Worker")); listener.onChat(chat(director, "Worker")); // Two lines before the next tick.
        server.getScheduler().performOneTick();
        assertEquals(1, messages(director).stream().filter(line -> line.startsWith("Added")).count());
        assertEquals(2, site.getExcavators().size()); verify(sites).save(site);
        invite(); site.closeCamp(); listener.onChat(chat(director, "Worker")); server.getScheduler().performOneTick();
        assertTrue(messages(director).contains("That excavation is no longer yours to staff."));
        site.setStatus(SiteStatus.ESTABLISHED); invite(); when(sites.findById(site.getId())).thenReturn(Optional.empty());
        listener.onChat(chat(director, "Worker")); server.getScheduler().performOneTick();
        assertTrue(messages(director).contains("That excavation is no longer yours to staff.")); verify(sites).save(site);
    }

    @Test public void playersWhoHaveLeftTheServerCanStillBeHiredWithoutANotice() {
        PlayerMock returning = server.addPlayer("Surveyor"); returning.disconnect();
        invite(); listener.onChat(chat(director, "surveyor")); server.getScheduler().performOneTick();
        assertTrue(site.getExcavators().contains(returning.getUniqueId()));
        assertTrue(messages(director).contains("Added Surveyor to the excavation staff.")); verify(sites).save(site);
    }

    @Test public void fullRostersReportTheirPluralCapacityWithoutHiring() {
        when(catalogs.establish()).thenReturn(new EstablishSettings(true, true, Material.RED_STAINED_GLASS, Material.LIGHT_BLUE_STAINED_GLASS, 2, 1));
        invite(); site.grantExcavator(UUID.randomUUID()); // Another director session filled the last place meanwhile.
        listener.onChat(chat(director, "Worker")); server.getScheduler().performOneTick();
        assertTrue(messages(director).contains("This excavation already has 2 people on the staff."));
        assertFalse(site.getExcavators().contains(worker.getUniqueId())); verify(sites, never()).save(any());
    }

    @Test public void repeatedOrLateCloseConfirmationsNeverCloseTwiceOrCloseADeletedRecord() {
        board(director, true, true); listener.onBoardClick(click(director, CampBoard.SLOT_CLOSE));
        listener.onChat(chat(director, "confirm")); listener.onChat(chat(director, "confirm")); server.getScheduler().performOneTick();
        verify(closure, times(1)).close(director, site);
        clearInvocations(closure); board(director, true, true); listener.onBoardClick(click(director, CampBoard.SLOT_CLOSE));
        when(sites.findById(site.getId())).thenReturn(Optional.empty()); // Staff erased the ruin before the reply.
        listener.onChat(chat(director, "confirm")); server.getScheduler().performOneTick();
        verify(closure, never()).close(any(), any()); assertTrue(messages(director).contains("That excavation can no longer be closed."));
    }

    @Test public void damagedOrOrphanedFieldBooksReportAMissingRecord() {
        site.setStatus(SiteStatus.CLOSED); ItemStack book = archive.create(director, site);
        var meta = book.getItemMeta(); meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "archive_site_id"),
                org.bukkit.persistence.PersistentDataType.STRING, "not-a-uuid"); ItemStack damaged = book.clone(); damaged.setItemMeta(meta);
        PlayerInteractEvent open = interact(worker, null, EquipmentSlot.HAND, damaged); listener.onInteract(open);
        assertTrue(open.isCancelled()); assertEquals("That excavation record is missing.", worker.nextMessage());
        site.setStatus(SiteStatus.HIDDEN); listener.onInteract(interact(worker, null, EquipmentSlot.HAND, book));
        assertEquals("That excavation record is missing.", worker.nextMessage()); assertNull(worker.getOpenInventory().getTopInventory());
    }

    @Test public void onlyTheDirectorSeesTheReportButtonOnAnExhaustedRegister() {
        site.setStatus(SiteStatus.EXHAUSTED); site.grantExcavator(worker.getUniqueId());
        board(worker, false, false); listener.onBoardClick(click(worker, CampBoard.SLOT_DOCUMENTATION));
        assertNull(worker.getOpenInventory().getTopInventory().getItem(CampFindsBoard.SLOT_REPORT));
        board(director, true, true); listener.onBoardClick(click(director, CampBoard.SLOT_DOCUMENTATION));
        assertEquals(Material.WRITTEN_BOOK, director.getOpenInventory().getTopInventory().getItem(CampFindsBoard.SLOT_REPORT).getType());
    }

    private void clickAll(InventoryClickEvent event) {
        listener.onBoardClick(event); listener.onStaffClick(event); listener.onWorkerClick(event); listener.onFindsClick(event);
        listener.onFindFileClick(event); listener.onIdentifyClick(event); listener.onWoolPickerClick(event);
    }
    private InventoryDragEvent drag() {
        InventoryDragEvent drag = new InventoryDragEvent(director.getOpenInventory(), new ItemStack(Material.STONE),
                new ItemStack(Material.STONE, 2), false, Map.of(0, new ItemStack(Material.STONE)));
        listener.onBoardDrag(drag); return drag;
    }
    private void board(PlayerMock player, boolean director, boolean close) { new CampBoard(site.getId(), director, close, catalogs).open(player, site); }
    private void invite() {
        new CampStaffBoard(site.getId(), true, catalogs.establish().maxStaff()).open(director, site);
        listener.onStaffClick(click(director, CampStaffBoard.SLOT_ADD));
    }
    private static InventoryClickEvent click(PlayerMock player, int slot) {
        return new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER, slot,
                ClickType.LEFT, InventoryAction.PICKUP_ALL);
    }
    private static AsyncPlayerChatEvent chat(PlayerMock player, String message) { return new AsyncPlayerChatEvent(true, player, message, new HashSet<>()); }
    private static PlayerInteractEvent interact(PlayerMock player, Block block, EquipmentSlot hand, ItemStack item) {
        return new PlayerInteractEvent(player, block == null ? Action.RIGHT_CLICK_AIR : Action.RIGHT_CLICK_BLOCK, item, block, BlockFace.UP, hand);
    }
    private static BuriedFind find(FindState state) {
        BuriedFind find = new BuriedFind(); find.setId(UUID.randomUUID()); find.setArtifactId("pot"); find.setState(state); return find;
    }
    private static List<String> messages(PlayerMock player) {
        List<String> messages = new ArrayList<>(); String message;
        while ((message = player.nextMessage()) != null) messages.add(message); return messages;
    }
}
