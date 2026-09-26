package net.tfminecraft.archaeo.sketch;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.*;
import net.tfminecraft.archaeo.config.*;
import net.tfminecraft.archaeo.establish.*;
import net.tfminecraft.archaeo.excavation.HandPickService;
import net.tfminecraft.archaeo.excavation.PrismOutlineService;
import org.bukkit.event.inventory.*;
import net.tfminecraft.archaeo.item.*;
import net.tfminecraft.archaeo.model.*;
import net.tfminecraft.archaeo.site.SiteRepository;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.*;
import org.mockito.MockedStatic;
import org.bukkit.map.MapView;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

public class SketchServiceTest {
    private ServerMock server;
    private JavaPlugin plugin;
    private PlayerMock player;
    private SketchService service;
    private SketchSupplies supplies;
    private RecoveredFindItem recovered;
    private SiteRepository sites;
    private CatalogRegistry catalogs;
    private MockedStatic<Bukkit> mapApi;

    @Before public void setUp() {
        server = MockBukkit.mock(); plugin = MockBukkit.createMockPlugin(); player = server.addPlayer();
        supplies = new SketchSupplies(plugin, ItemRef.vanilla(Material.PAPER), ItemRef.vanilla(Material.FEATHER), 5);
        recovered = mock(RecoveredFindItem.class); sites = mock(SiteRepository.class); catalogs = mock(CatalogRegistry.class);
        when(catalogs.pick()).thenReturn(PickSettings.defaults());
        service = new SketchService(plugin, supplies, recovered, sites, catalogs, SketchSettings.defaults());
        // MockBukkit does not implement these map-rendering flags; keep real map IDs/meta/storage.
        Map<Integer, MapView> views = new HashMap<>();
        mapApi = mockStatic(Bukkit.class, call -> {
            // MockBukkit typed inventory holders cast plugin GUI owners to Furnace/BrewingStand.
            if (call.getMethod().getName().equals("createInventory") && call.getArguments().length == 3
                    && call.getArgument(1) instanceof InventoryType type
                    && (type == InventoryType.FURNACE || type == InventoryType.BREWING)) {
                return server.createInventory(call.getArgument(0), 9, (String) call.getArgument(2));
            }
            return call.callRealMethod();
        });
        mapApi.when(() -> Bukkit.createMap(player.getWorld())).thenAnswer(call -> {
            MapView view = spy(server.createMap(player.getWorld()));
            doNothing().when(view).setTrackingPosition(anyBoolean());
            doNothing().when(view).setUnlimitedTracking(anyBoolean());
            views.put(view.getId(), view);
            return view;
        });
        mapApi.when(() -> Bukkit.getMap(anyInt())).thenAnswer(call -> {
            int id = call.getArgument(0);
            return views.containsKey(id) ? views.get(id) : server.getMap(id);
        });
    }
    @After public void tearDown() { try { service.stop(); } finally { if (mapApi != null) mapApi.close(); MockBukkit.unmock(); } }

    @Test public void blankMapsAreUnstackableAndCanBeEditedSavedAndRehydrated() {
        ItemStack map = service.createUnsigned(player.getWorld());
        assertTrue(service.isSketchMap(map)); assertFalse(service.isSigned(map));
        assertEquals(1, map.getItemMeta().getMaxStackSize());
        player.getInventory().setItemInMainHand(map);
        service.syncHand(player);
        assertTrue(service.editing(player));
        assertTrue(player.getAttribute(Attribute.MOVEMENT_SPEED).getValue() < .001);
        SketchSession session = service.session(player); session.paint(); session.move(1, 0); session.paint();
        service.leave(player, true, map);
        assertFalse(service.editing(player));
        assertTrue(player.getAttribute(Attribute.MOVEMENT_SPEED).getValue() > 0);
        assertTrue(SketchSheet.fromBytes(cells(map)).hasInk());
        SketchService reloaded = new SketchService(plugin, supplies, recovered, sites, catalogs, null);
        reloaded.hydrate(map);
        assertArrayEquals(cells(map), reloaded.sheetOf(((MapMeta) map.getItemMeta()).getMapView()).toBytes());
        assertEquals(1, ((MapMeta) map.getItemMeta()).getMapView().getRenderers().size());
        reloaded.stop();
    }

    @Test public void signingRequiresInkAndConfirmationThenPermanentlyLocksTheDrawing() {
        ItemStack map = startDrawing();
        service.askToSign(player);
        assertFalse(service.session(player).awaitingSign());
        assertFalse(service.handleSignChat(player, "sign"));
        service.session(player).paint(); service.askToSign(player);
        assertTrue(service.session(player).awaitingSign());
        assertTrue(service.handleSignChat(player, "not yet")); assertTrue(service.editing(player));
        assertTrue(service.handleSignChat(player, "CANCEL")); assertFalse(service.session(player).awaitingSign());
        service.askToSign(player); service.erase(player);
        assertTrue(service.handleSignChat(player, "sign")); assertFalse(service.isSigned(map));
        service.session(player).paint(); service.askToSign(player);
        assertTrue(service.handleSignChat(player, "SIGN"));
        assertFalse(service.editing(player));
        ItemStack signed = player.getInventory().getItemInMainHand();
        assertTrue(service.isSigned(signed));
        assertEquals(player.getName(), signed.getItemMeta().getPersistentDataContainer().get(key("sketch_author"), PersistentDataType.STRING));
        service.enter(player, signed); assertFalse(service.editing(player));
        service.begin(player); assertFalse(service.editing(player));
    }

    @Test public void switchingDroppingAndStoppingSaveTheLiveDrawingToItsActualStack() {
        ItemStack map = startDrawing(); service.session(player).paint();
        player.getInventory().setItemInMainHand(new ItemStack(Material.STONE));
        service.leave(player, true, List.of(new ItemStack(Material.PAPER), map));
        assertTrue(SketchSheet.fromBytes(cells(map)).hasInk()); assertFalse(service.editing(player));
        player.getInventory().setItemInMainHand(map); service.syncHand(player);
        service.session(player).move(2, 0); service.session(player).paint();
        byte[] expected = service.session(player).sheet().toBytes();
        player.getInventory().setItemInMainHand(new ItemStack(Material.STONE)); player.getInventory().setItemInOffHand(map);
        service.syncHand(player);
        assertArrayEquals(expected, cells(player.getInventory().getItemInOffHand()));
        player.getInventory().setItemInMainHand(map); player.getInventory().setItemInOffHand(null); service.syncHand(player);
        service.session(player).move(0, 1); service.session(player).paint();
        expected = service.session(player).sheet().toBytes(); service.stop();
        assertArrayEquals(expected, cells(player.getInventory().getItemInMainHand())); assertFalse(service.editing(player));
    }

    @Test public void paperAndPencilCraftExactlyOneMapInEitherHandAndWearThePencil() {
        player.getInventory().setItemInMainHand(supplies.createPaper()); player.getInventory().setItemInOffHand(supplies.createPencil());
        assertTrue(service.tryStartFromHands(player)); assertTrue(service.editing(player));
        assertTrue(service.isSketchMap(player.getInventory().getItemInMainHand()));
        assertEquals(1, ((org.bukkit.inventory.meta.Damageable) player.getInventory().getItemInOffHand().getItemMeta()).getDamage());
        assertFalse(service.tryStartFromHands(player)); service.leave(player, false);
        player.getInventory().clear();
        player.getInventory().setItemInOffHand(supplies.createPaper()); player.getInventory().setItemInMainHand(supplies.createPencil());
        assertTrue(service.tryStartFromHands(player)); assertFalse(service.editing(player));
        assertTrue(service.isSketchMap(player.getInventory().getItemInOffHand()));
        player.getInventory().clear();
        ItemStack paper = supplies.createPaper(); paper.setAmount(3);
        player.getInventory().setItemInMainHand(paper); player.getInventory().setItemInOffHand(supplies.createPencil());
        assertTrue(service.tryStartFromHands(player)); assertEquals(2, player.getInventory().getItemInMainHand().getAmount());
        assertEquals(1, Arrays.stream(player.getInventory().getContents()).filter(service::isSketchMap).count());
    }

    @Test public void bagCraftSupportsBothPairOrdersAndRejectsSpentOrUnrelatedTools() {
        ItemStack paper = supplies.createPaper(); ItemStack pencil = supplies.createPencil();
        assertTrue(service.tryCraftOnClick(player, paper, pencil)); assertTrue(service.isSketchMap(paper)); assertFalse(service.editing(player));
        ItemStack second = supplies.createPaper();
        assertTrue(service.tryCraftOnClick(player, pencil, second)); assertTrue(service.isSketchMap(second));
        ItemStack bundle = supplies.createPaper(); bundle.setAmount(2);
        assertTrue(service.tryCraftOnClick(player, bundle, pencil)); assertEquals(1, bundle.getAmount()); assertEquals(Material.PAPER, bundle.getType());
        ItemStack spent = supplies.createPencil();
        var meta = (org.bukkit.inventory.meta.Damageable) spent.getItemMeta(); meta.setDamage(meta.getMaxDamage()); spent.setItemMeta(meta);
        ItemStack untouched = supplies.createPaper();
        assertTrue(service.tryCraftOnClick(player, spent, untouched)); assertEquals(Material.PAPER, untouched.getType());
        assertTrue(service.tryCraftOnClick(player, untouched, spent)); assertEquals(Material.PAPER, untouched.getType());
        assertFalse(service.tryCraftOnClick(player, new ItemStack(Material.STONE), pencil));
        assertFalse(service.tryCraftOnClick(null, paper, pencil));
    }

    @Test public void splittingOldMapStacksCreatesIndependentViewsWithoutLosingSavedPixels() {
        ItemStack map = startDrawing(); service.session(player).paint(); service.leave(player, false, map);
        map.setAmount(3); player.getInventory().setItemInMainHand(map);
        service.unstackCarried(player);
        List<ItemStack> maps = Arrays.stream(player.getInventory().getContents()).filter(service::isSketchMap).toList();
        assertEquals(3, maps.size());
        Set<Integer> ids = new HashSet<>();
        for (ItemStack copy : maps) {
            assertEquals(1, copy.getAmount()); assertTrue(SketchSheet.fromBytes(cells(copy)).hasInk());
            ids.add(((MapMeta) copy.getItemMeta()).getMapView().getId());
        }
        assertEquals(3, ids.size());
    }

    @Test public void delayedBagCraftAndHandSyncPreserveCursorDrawing() {
        ItemStack map = startDrawing(); service.session(player).paint();
        player.getInventory().setItemInMainHand(new ItemStack(Material.STONE));
        player.getOpenInventory().setCursor(map);
        service.syncHandLater(player, map); server.getScheduler().performOneTick();
        assertFalse(service.editing(player)); assertTrue(SketchSheet.fromBytes(cells(map)).hasInk());
        ItemStack paper = supplies.createPaper();
        service.afterBagCraft(player, player.getInventory(), 4, map, paper); server.getScheduler().performOneTick();
        assertEquals(map, player.getOpenInventory().getCursor()); assertEquals(Material.PAPER, player.getInventory().getItem(4).getType());
        service.stampKitsLater(player); server.getScheduler().performOneTick();
        assertTrue(player.getInventory().getItem(4).getItemMeta().hasLore());
    }

    @Test public void cabinetAcceptsOnlySignedDrawingsAndDoesNotOverwriteExistingDeposit() {
        Inventory cabinet = server.createInventory(null, 9);
        ItemStack unsigned = service.createUnsigned(player.getWorld());
        assertFalse(service.tryDepositCabinet(player, cabinet, unsigned)); assertFalse(service.tryDepositCabinet(player, cabinet, new ItemStack(Material.STONE)));
        ItemStack signed = signedDrawing();
        assertTrue(service.tryDepositCabinet(player, cabinet, signed)); assertTrue(signed.getType().isAir());
        ItemStack deposited = cabinet.getItem(SketchCabinet.SLOT_SKETCH);
        assertTrue(service.isSigned(deposited));
        ItemStack another = signedDrawing(); assertFalse(service.tryDepositCabinet(player, cabinet, another));
        ItemStack returned = service.handleCabinetClick(player, cabinet, SketchCabinet.SLOT_SKETCH, new ItemStack(Material.AIR));
        assertEquals(deposited, returned); assertNull(cabinet.getItem(SketchCabinet.SLOT_SKETCH));
        assertTrue(service.handleCabinetClick(player, cabinet, SketchCabinet.SLOT_SKETCH, another).getType().isAir());
        ItemStack locked = new ItemStack(Material.STONE);
        assertSame(locked, service.handleCabinetClick(player, cabinet, SketchCabinet.SLOT_FIND, locked));
    }

    @Test public void cabinetRequiresTheConfiguredFurnitureAndAnArchivedArtifact() {
        Block block = player.getWorld().getBlockAt(0, 65, 0); block.setType(Material.CARTOGRAPHY_TABLE);
        assertTrue(service.isCabinet(block)); assertFalse(service.tryOpenCabinet(player, block, true));
        assertTrue(service.tryOpenCabinet(player, block, false));
        assertTrue(messages().stream().anyMatch(message -> message.contains("Bring an artifact")));
        ItemStack piece = new ItemStack(Material.BRICK); player.getInventory().setItemInMainHand(piece);
        when(recovered.isRecovered(any())).thenReturn(true);
        when(recovered.findIdOf(any())).thenReturn(UUID.randomUUID()); when(recovered.siteIdOf(any())).thenReturn(UUID.randomUUID());
        assertTrue(service.tryOpenCabinet(player, block, false));
        assertTrue(messages().stream().anyMatch(message -> message.contains("not in the excavation archive")));
        block.setType(Material.STONE); assertFalse(service.tryOpenCabinet(player, block, false));
        ItemRef furniture = new ItemRef(ItemRef.Kind.ITEMSADDER, "pack:cabinet", "");
        service.setSettings(new SketchSettings(5, furniture, LabSettings.defaults()));
        ItemMatcher matcher = mock(ItemMatcher.class); when(matcher.matchesNamespacedId("pack:cabinet", furniture)).thenReturn(true); service.setMatcher(matcher);
        assertFalse(service.tryOpenCabinet(player, block, false));
        assertTrue(service.tryOpenCabinet(player, "pack:cabinet", null, block, false));
    }

    @Test public void registrationBindsSignedDrawingAndRefreshesArchiveWithoutDuplicatingArtifact() {
        ItemStack drawing = signedDrawing();
        Site site = new Site(); site.setId(UUID.randomUUID()); site.setName("Quarry");
        BuriedFind find = new BuriedFind(); find.setId(UUID.randomUUID()); find.setArtifactId("pot"); find.setFindNumber(2); find.setLabCleaned(true);
        site.getFinds().add(find); when(sites.findById(site.getId())).thenReturn(Optional.of(site));
        when(recovered.isInMainHand(player, find.getId())).thenReturn(true);
        when(recovered.standIn(any(), eq(site), eq(find), eq(catalogs))).thenReturn(new ItemStack(Material.BRICK));
        when(recovered.labelOf(any())).thenReturn("Pot");
        player.getInventory().setItemInMainHand(new ItemStack(Material.BRICK));
        // MockBukkit's furnace holder accessor assumes a block Furnace, unlike plugin GUI holders.
        SketchCabinet cabinet = mock(SketchCabinet.class);
        when(cabinet.siteId()).thenReturn(site.getId()); when(cabinet.findId()).thenReturn(find.getId());
        Inventory top = server.createInventory(cabinet, 9); when(cabinet.getInventory()).thenReturn(top);
        top.setItem(SketchCabinet.SLOT_SKETCH, drawing);
        service.tryRegisterCabinet(player, top);
        assertTrue(find.hasFieldSketch()); verify(sites).save(site);
        assertEquals(Material.BRICK, player.getInventory().getItemInMainHand().getType());
        assertEquals(1, Arrays.stream(player.getInventory().getContents()).filter(service::isSketchMap).count());
        verify(recovered).refreshCarried(eq(player), eq(site), eq(find), isNull(), anyString(), eq(catalogs));
        assertNull(cabinet.getInventory().getItem(SketchCabinet.SLOT_SKETCH));
        ItemStack bound = Arrays.stream(player.getInventory().getContents()).filter(service::isSketchMap).filter(stack -> site.getId().equals(service.siteIdOf(stack))).findFirst().orElseThrow();
        assertTrue(service.isSigned(bound));
        assertTrue(ChatColor.stripColor(bound.getItemMeta().getLore().get(0)).contains("#Quarry-2"));
        site.setName("River"); assertTrue(service.retitle(bound, site)); assertFalse(service.retitle(bound, site));
        assertEquals("#River-2", ChatColor.stripColor(bound.getItemMeta().getLore().get(0)));
    }

    @Test public void inputLoopPaintsCyclesInkOncePerPressAndAutosavesBeforeStop() {
        player = spy(player);
        Input input = mock(Input.class); doReturn(input).when(player).getCurrentInput();
        mapApi.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
        mapApi.when(() -> Bukkit.getPlayer(player.getUniqueId())).thenReturn(player);
        ItemStack map = service.createUnsigned(player.getWorld()); player.getInventory().setItemInMainHand(map);
        when(input.isSneak()).thenReturn(true); when(input.isRight()).thenReturn(true); when(input.isJump()).thenReturn(true);
        service.start();
        assertTrue(service.editing(player));
        server.getScheduler().performTicks(42);
        // The checkpoint lands on disk; the held map is only rewritten when the session ends.
        int mapId = ((MapMeta) map.getItemMeta()).getMapView().getId();
        SketchAutosaveStore store = new SketchAutosaveStore(plugin.getDataFolder().toPath());
        try { assertTrue(SketchSheet.fromBytes(store.load(mapId).cells()).hasInk()); } catch (java.io.IOException e) { throw new AssertionError(e); }
        assertFalse(SketchSheet.fromBytes(cells(player.getInventory().getItemInMainHand())).hasInk());
        SketchInk first = service.session(player).ink();
        server.getScheduler().performTicks(4); assertEquals(first, service.session(player).ink());
        when(input.isJump()).thenReturn(false); server.getScheduler().performTicks(2);
        when(input.isJump()).thenReturn(true); server.getScheduler().performTicks(2);
        assertNotEquals(first, service.session(player).ink());
        service.stop(); assertFalse(service.editing(player));
        assertTrue(SketchSheet.fromBytes(cells(player.getInventory().getItemInMainHand())).hasInk());
        try { assertNull(store.load(mapId)); } catch (java.io.IOException e) { throw new AssertionError(e); }
        server.getScheduler().performTicks(4); assertFalse(service.editing(player));
    }

    @Test public void registrationRefusalsPreserveDrawingUntilArtifactIsHeldAndClean() {
        ItemStack drawing = signedDrawing();
        Site site = new Site(); site.setId(UUID.randomUUID());
        BuriedFind find = new BuriedFind(); find.setId(UUID.randomUUID()); site.getFinds().add(find);
        when(sites.findById(site.getId())).thenReturn(Optional.of(site));
        SketchCabinet holder = mock(SketchCabinet.class);
        when(holder.siteId()).thenReturn(site.getId()); when(holder.findId()).thenReturn(find.getId());
        Inventory top = server.createInventory(holder, 9); top.setItem(0, drawing);
        service.tryRegisterCabinet(player, top);
        assertTrue(messages().stream().anyMatch(line -> line.contains("Keep the artifact in your hand")));
        when(recovered.isInMainHand(player, find.getId())).thenReturn(true);
        player.getInventory().setItemInMainHand(new ItemStack(Material.BRICK));
        service.tryRegisterCabinet(player, top);
        assertTrue(messages().stream().anyMatch(line -> line.contains("Clean the piece")));
        find.setLabCleaned(true); top.setItem(0, service.createUnsigned(player.getWorld()));
        service.tryRegisterCabinet(player, top);
        assertTrue(messages().stream().anyMatch(line -> line.contains("Place the drawing")));
        top.setItem(0, drawing); site.getFinds().clear();
        service.tryRegisterCabinet(player, top);
        assertTrue(messages().stream().anyMatch(line -> line.contains("not in the excavation archive")));
        assertEquals(drawing, top.getItem(0)); assertFalse(find.hasFieldSketch()); verify(sites, never()).save(any());
    }

    @Test public void cabinetCarriesARealArtifactThroughCleaningDrawingAndSignedClassification() {
        Exhibit exhibit = exhibit(false);
        server.getPluginManager().registerEvents(new SketchListener(service), plugin);
        assertTrue(service.tryOpenCabinet(player, exhibit.cabinet(), false));
        CabinetLabBoard lab = (CabinetLabBoard) player.getOpenInventory().getTopInventory().getHolder();
        ItemStack brush = lab.copyTool(22); assertTrue(service.isLabItem(brush));
        for (int slot = 0; slot < LabSettings.FIELD_SLOTS; slot++) {
            if (lab.isDirty(slot)) service.handleLabClick(player, lab, slot, brush);
        }
        assertTrue(exhibit.find().isLabCleaned()); assertFalse(exhibit.find().hasFieldSketch());
        server.getScheduler().performOneTick();
        ItemStack artifact = player.getInventory().getItemInMainHand();
        ItemStack drawing = signedDrawing(); player.getInventory().setItemInMainHand(artifact);
        assertTrue(service.tryOpenCabinet(player, exhibit.cabinet(), false));
        Inventory register = player.getOpenInventory().getTopInventory(); assertTrue(register.getHolder() instanceof SketchCabinet);
        assertTrue(service.tryDepositCabinet(player, register, drawing));
        service.tryRegisterCabinet(player, register);
        assertTrue(exhibit.find().hasFieldSketch()); server.getScheduler().performOneTick();
        assertTrue(service.tryOpenCabinet(player, exhibit.cabinet(), false)); server.getScheduler().performOneTick();
        Inventory station = player.getOpenInventory().getTopInventory(); assertTrue(station.getHolder() instanceof CampIdentifyBoard);
        CampIdentifyBoard board = (CampIdentifyBoard) station.getHolder();
        assertTrue(board.atCabinet()); assertEquals(exhibit.find().getId(), board.findId());
        assertEquals("Storage vessel", ChatColor.stripColor(station.getItem(0).getItemMeta().getDisplayName()));
        CampListener camp = new CampListener(plugin, sites, catalogs, mock(EstablishItem.class), mock(EstablishService.class),
                mock(HandPickService.class), mock(PrismOutlineService.class), mock(BrushItem.class), recovered, mock(CampClosure.class));
        InventoryClickEvent choice = mock(InventoryClickEvent.class);
        when(choice.getInventory()).thenReturn(station); when(choice.getWhoClicked()).thenReturn(player);
        when(choice.getClickedInventory()).thenReturn(station); when(choice.getView()).thenReturn(player.getOpenInventory()); when(choice.getRawSlot()).thenReturn(0);
        camp.onIdentifyClick(choice);
        assertTrue(exhibit.find().isCatalogued()); assertEquals(player.getUniqueId(), exhibit.find().getInterpretations().get(0).author());
        assertEquals("vessel", exhibit.find().getInterpretations().get(0).interpretationId()); verify(sites, times(3)).save(exhibit.site());
        server.getScheduler().performOneTick(); messages();
        assertTrue(service.tryOpenCabinet(player, exhibit.cabinet(), false));
        assertTrue(messages().stream().anyMatch(line -> line.contains("record on this piece is complete")));
        assertEquals(exhibit.find().getId(), recovered.findIdOf(player.getInventory().getItemInMainHand()));
        assertEquals(1, Arrays.stream(player.getInventory().getContents()).filter(service::isSketchMap).count());
    }

    @Test public void readingHandoffRequiresRosterAndHeldArtifactAndReturnsTheDepositedDrawing() {
        Exhibit exhibit = exhibit(true);
        exhibit.site().setDirector(UUID.randomUUID()); exhibit.site().getExcavators().clear();
        assertTrue(service.tryOpenCabinet(player, exhibit.cabinet(), false));
        assertTrue(messages().stream().anyMatch(line -> line.contains("not authorised")));
        exhibit.site().setDirector(player.getUniqueId());
        SketchCabinet register = new SketchCabinet(); register.open(player, exhibit.site(), exhibit.find(), catalogs.artifact("pot"), recovered, catalogs);
        ItemStack artifact = player.getInventory().getItemInMainHand(); player.getInventory().setItemInMainHand(new ItemStack(Material.STONE));
        service.tryRegisterCabinet(player, register.getInventory());
        assertTrue(messages().stream().anyMatch(line -> line.contains("Keep the artifact")));
        player.getInventory().setItemInMainHand(artifact);
        ItemStack drawing = service.createUnsigned(player.getWorld()); register.getInventory().setItem(0, drawing);
        service.tryRegisterCabinet(player, register.getInventory());
        assertNull(register.getInventory().getItem(0));
        server.getScheduler().performOneTick();
        assertTrue(player.getOpenInventory().getTopInventory().getHolder() instanceof CampIdentifyBoard);
        assertEquals(1, Arrays.stream(player.getInventory().getContents()).filter(service::isSketchMap).count());
        verify(sites, never()).save(any());
    }

    @Test public void scheduledReadingDoesNotOpenAfterPlayerLeavesOrArchiveDisappears() {
        Exhibit exhibit = exhibit(true);
        assertTrue(service.tryOpenCabinet(player, exhibit.cabinet(), false));
        when(sites.findById(exhibit.site().getId())).thenReturn(Optional.empty()); server.getScheduler().performOneTick();
        Inventory top = player.getOpenInventory().getTopInventory(); assertFalse(top != null && top.getHolder() instanceof CampIdentifyBoard);
        when(sites.findById(exhibit.site().getId())).thenReturn(Optional.of(exhibit.site()));
        assertTrue(service.tryOpenCabinet(player, exhibit.cabinet(), false)); player.disconnect(); server.getScheduler().performOneTick();
        verify(sites, never()).save(any());
    }

    @Test public void commandCreatedSketchPreservesHeldItemsAndQueuedSigningRunsOnMainThread() {
        ItemStack prior = new ItemStack(Material.STONE, 12); player.getInventory().setItemInMainHand(prior);
        service.begin(player); assertTrue(service.editing(player));
        assertEquals(12, Arrays.stream(player.getInventory().getContents()).filter(Objects::nonNull).filter(stack -> stack.getType() == Material.STONE).mapToInt(ItemStack::getAmount).sum());
        service.begin(player); assertEquals(1, Arrays.stream(player.getInventory().getContents()).filter(service::isSketchMap).count());
        service.session(player).paint(); service.askToSign(player); service.handleSignChatLater(player, "sign");
        assertFalse(service.isSigned(player.getInventory().getItemInMainHand()));
        server.getScheduler().performOneTick();
        assertTrue(service.isSigned(player.getInventory().getItemInMainHand())); assertFalse(service.editing(player));
    }

    @Test public void saveSearchFindsMapsMovedToBagAndCursorWithoutEventExtras() {
        for (boolean cursor : new boolean[]{false, true}) {
            ItemStack map = startDrawing(); service.session(player).paint();
            byte[] expected = service.session(player).sheet().toBytes(); player.getInventory().setItemInMainHand(new ItemStack(Material.STONE));
            PlayerMock holder = player;
            if (cursor) {
                player.setItemOnCursor(map);
                // Paper 1.21.10 CraftHumanEntity#getItemOnCursor returns a live CraftItemStack mirror;
                // MockBukkit clones it. Model that server boundary without changing the save code.
                holder = spy(player);
                InventoryView view = spy(player.getOpenInventory());
                doReturn(map).when(view).getCursor();
                doReturn(view).when(holder).getOpenInventory();
                doReturn(map).when(holder).getItemOnCursor();
            } else player.getInventory().setItem(10, map);
            service.leave(holder, true);
            ItemStack stored = cursor ? holder.getItemOnCursor() : player.getInventory().getItem(10);
            assertArrayEquals(expected, cells(stored)); assertFalse(service.editing(player));
            player.setItemOnCursor(null); player.getInventory().clear();
        }
    }

    @Test public void quittingEditorSavesThroughTheRegisteredListenerAndStopsLabFacadeCleanly() {
        server.getPluginManager().registerEvents(new SketchListener(service), plugin);
        ItemStack map = startDrawing(); service.session(player).paint(); byte[] expected = service.session(player).sheet().toBytes();
        player.disconnect();
        assertFalse(service.editing(player)); assertArrayEquals(expected, cells(player.getInventory().getItemInMainHand()));
        assertTrue(player.getAttribute(Attribute.MOVEMENT_SPEED).getValue() > 0);
    }

    @Test public void serviceCancellationClosesLabAndRemovesItsCursorToolWithoutMarkingClean() {
        Exhibit exhibit = exhibit(false); server.getPluginManager().registerEvents(new SketchListener(service), plugin);
        assertTrue(service.tryOpenCabinet(player, exhibit.cabinet(), false));
        CabinetLabBoard board = (CabinetLabBoard) player.getOpenInventory().getTopInventory().getHolder();
        player.setItemOnCursor(service.handleLabClick(player, board, 22, new ItemStack(Material.AIR)));
        service.cancelLab(player);
        assertFalse(exhibit.find().isLabCleaned()); assertTrue(player.getItemOnCursor() == null || player.getItemOnCursor().getType().isAir());
        assertNotSame(board.getInventory(), player.getOpenInventory().getTopInventory()); verify(sites, never()).save(any());
    }

    @Test public void drawingFiledForOneFindCannotBeReusedForAnotherFind() {
        Exhibit first = exhibit(false); first.find().setLabCleaned(true);
        ItemStack artifact = player.getInventory().getItemInMainHand();
        ItemStack drawing = signedDrawing(); player.getInventory().setItemInMainHand(artifact);
        assertTrue(service.tryOpenCabinet(player, first.cabinet(), false));
        Inventory firstCabinet = player.getOpenInventory().getTopInventory();
        assertTrue(service.tryDepositCabinet(player, firstCabinet, drawing)); service.tryRegisterCabinet(player, firstCabinet);
        assertTrue(first.find().hasFieldSketch()); server.getScheduler().performOneTick();
        ItemStack filed = Arrays.stream(player.getInventory().getContents()).filter(service::isSketchMap).findFirst().orElseThrow();
        byte[] originalCells = cells(filed); String originalLabel = filed.getItemMeta().getDisplayName();
        BuriedFind second = new BuriedFind(); second.setId(UUID.randomUUID()); second.setArtifactId("pot"); second.setStratumId("I");
        second.setState(FindState.RECOVERED); second.setFindNumber(2); second.setLabCleaned(true); first.site().getFinds().add(second);
        ItemStack secondArtifact = recovered.create(catalogs.artifact("pot"), first.site(), second, player.getUniqueId(), "Good", false, catalogs);
        player.getInventory().addItem(artifact); player.getInventory().setItemInMainHand(secondArtifact);
        assertTrue(service.tryOpenCabinet(player, first.cabinet(), false));
        Inventory secondCabinet = player.getOpenInventory().getTopInventory();
        assertTrue(service.tryDepositCabinet(player, secondCabinet, filed)); messages();
        service.tryRegisterCabinet(player, secondCabinet);
        assertTrue(messages().stream().anyMatch(line -> line.contains("already records a different find")));
        assertFalse(second.hasFieldSketch()); verify(sites).save(first.site());
        ItemStack retained = secondCabinet.getItem(SketchCabinet.SLOT_SKETCH);
        assertTrue(service.isSigned(retained)); assertArrayEquals(originalCells, cells(retained));
        assertEquals(first.find().getId().toString(), retained.getItemMeta().getPersistentDataContainer().get(key("sketch_find_id"), PersistentDataType.STRING));
        assertEquals(originalLabel, retained.getItemMeta().getDisplayName());
        assertEquals(second.getId(), recovered.findIdOf(player.getInventory().getItemInMainHand()));
    }

    @Test public void creatingSketchWithFullBagDropsHeldValuableIntactAndKeepsOtherContents() {
        for (int slot = 0; slot < player.getInventory().getStorageContents().length; slot++)
            player.getInventory().setItem(slot, new ItemStack(Material.STONE, 64));
        player.getInventory().setArmorContents(new ItemStack[]{new ItemStack(Material.IRON_BOOTS),
                new ItemStack(Material.IRON_LEGGINGS), new ItemStack(Material.IRON_CHESTPLATE), new ItemStack(Material.IRON_HELMET)});
        player.getInventory().setItemInOffHand(new ItemStack(Material.TORCH, 64));
        ItemStack valuable = new ItemStack(Material.DIAMOND, 7);
        var meta = valuable.getItemMeta(); meta.setDisplayName("Field expedition reserve"); valuable.setItemMeta(meta);
        player.getInventory().setItemInMainHand(valuable);
        // MockBukkit's addItem scans 43 slots, including two non-storage slots unknown to its
        // PlayerInventoryMock. Model Bukkit's storage-only overflow contract for this full bag.
        PlayerInventory inventory = spy(player.getInventory());
        doAnswer(call -> {
            ItemStack[] offered = (ItemStack[]) call.getRawArguments()[0];
            assertEquals(1, offered.length); assertEquals(valuable, offered[0]);
            return new HashMap<>(Map.of(0, offered[0].clone()));
        }).when(inventory).addItem(any(ItemStack[].class));
        player = spy(player); doReturn(inventory).when(player).getInventory();
        service.begin(player);
        assertTrue(service.editing(player)); assertTrue(service.isSketchMap(player.getInventory().getItemInMainHand()));
        List<org.bukkit.entity.Item> drops = player.getWorld().getEntities().stream().filter(org.bukkit.entity.Item.class::isInstance)
                .map(org.bukkit.entity.Item.class::cast).toList();
        assertEquals(1, drops.size()); assertEquals(valuable, drops.getFirst().getItemStack());
        assertEquals(35 * 64, Arrays.stream(player.getInventory().getStorageContents()).filter(Objects::nonNull)
                .filter(stack -> stack.getType() == Material.STONE).mapToInt(ItemStack::getAmount).sum());
        assertEquals(1, Arrays.stream(player.getInventory().getStorageContents()).filter(service::isSketchMap).count());
    }

    @Test public void cabinetReturnsFiledDrawingAsOneIntactDropWhenPickupFillsItsFreedBagSlot() {
        Exhibit exhibit = exhibit(false); exhibit.find().setLabCleaned(true);
        ItemStack artifact = player.getInventory().getItemInMainHand(); ItemStack drawing = signedDrawing();
        byte[] pixels = cells(drawing); int mapId = ((MapMeta) drawing.getItemMeta()).getMapView().getId();
        String author = drawing.getItemMeta().getPersistentDataContainer().get(key("sketch_author"), PersistentDataType.STRING);
        player.getInventory().setItemInMainHand(artifact); player.getInventory().setItem(1, drawing);
        for (int slot = 2; slot < player.getInventory().getStorageContents().length; slot++)
            player.getInventory().setItem(slot, new ItemStack(Material.STONE, 64));
        assertTrue(service.tryOpenCabinet(player, exhibit.cabinet(), false));
        Inventory cabinet = player.getOpenInventory().getTopInventory();
        assertTrue(service.tryDepositCabinet(player, cabinet, player.getInventory().getItem(1)));
        assertTrue(player.getInventory().getItem(1).isEmpty());
        // MockBukkit keeps the emptied mirror as an occupied AIR stack that addItem skips; Paper frees the slot.
        player.getInventory().setItem(1, null);
        // Items can be picked up while a cabinet is open, filling the slot the drawing just left.
        assertTrue(player.getInventory().addItem(new ItemStack(Material.STONE, 64)).isEmpty());
        assertTrue(Arrays.stream(player.getInventory().getStorageContents()).allMatch(stack -> stack != null && !stack.getType().isAir()));
        // MockBukkit also searches non-storage equipment slots; match Bukkit's full-storage result.
        PlayerInventory inventory = spy(player.getInventory());
        doAnswer(call -> {
            ItemStack[] offered = (ItemStack[]) call.getRawArguments()[0];
            assertEquals(1, offered.length); assertTrue(service.isSketchMap(offered[0]));
            return new HashMap<>(Map.of(0, offered[0].clone()));
        }).when(inventory).addItem(any(ItemStack[].class));
        player = spy(player); doReturn(inventory).when(player).getInventory();
        service.tryRegisterCabinet(player, cabinet); server.getScheduler().performOneTick();
        assertTrue(exhibit.find().hasFieldSketch()); verify(sites).save(exhibit.site());
        assertNull(cabinet.getItem(SketchCabinet.SLOT_SKETCH));
        List<org.bukkit.entity.Item> drops = player.getWorld().getEntities().stream().filter(org.bukkit.entity.Item.class::isInstance)
                .map(org.bukkit.entity.Item.class::cast).toList();
        assertEquals(1, drops.size()); ItemStack returned = drops.getFirst().getItemStack();
        assertEquals(1, returned.getAmount()); assertTrue(service.isSigned(returned)); assertArrayEquals(pixels, cells(returned));
        assertEquals(mapId, ((MapMeta) returned.getItemMeta()).getMapView().getId());
        assertEquals(author, returned.getItemMeta().getPersistentDataContainer().get(key("sketch_author"), PersistentDataType.STRING));
        assertEquals(exhibit.find().getId().toString(), returned.getItemMeta().getPersistentDataContainer().get(key("sketch_find_id"), PersistentDataType.STRING));
        assertEquals(exhibit.site().getId(), service.siteIdOf(returned));
        assertEquals(exhibit.find().getId(), recovered.findIdOf(player.getInventory().getItemInMainHand()));
        assertEquals(35 * 64, Arrays.stream(player.getInventory().getStorageContents()).filter(stack -> stack.getType() == Material.STONE).mapToInt(ItemStack::getAmount).sum());
        assertFalse(Arrays.stream(player.getInventory().getContents()).anyMatch(service::isSketchMap));
    }

    @Test public void renamingExcavationAfterRestartKeepsPixelsOfFiledDrawingInEnderChest() {
        Exhibit exhibit = exhibit(false); exhibit.find().setLabCleaned(true);
        ItemStack artifact = player.getInventory().getItemInMainHand(); ItemStack drawing = signedDrawing(); player.getInventory().setItemInMainHand(artifact);
        assertTrue(service.tryOpenCabinet(player, exhibit.cabinet(), false));
        Inventory cabinet = player.getOpenInventory().getTopInventory();
        assertTrue(service.tryDepositCabinet(player, cabinet, drawing)); service.tryRegisterCabinet(player, cabinet);
        int slot = player.getInventory().first(Material.FILLED_MAP); ItemStack filed = player.getInventory().getItem(slot);
        byte[] pixels = cells(filed); assertTrue(SketchSheet.fromBytes(pixels).hasInk());
        player.getEnderChest().setItem(3, filed); player.getInventory().setItem(slot, null);
        // Restart: join hydration covers the bag and off-hand, never ender chests or container blocks.
        service.stop(); service = new SketchService(plugin, supplies, recovered, sites, catalogs, SketchSettings.defaults());
        exhibit.site().setName("River"); new SiteLabelRefresh(plugin, recovered, catalogs, service).retitle(exhibit.site());
        ItemStack stored = player.getEnderChest().getItem(3);
        assertEquals("#River-1", ChatColor.stripColor(stored.getItemMeta().getLore().get(0)));
        assertArrayEquals(pixels, cells(stored)); assertTrue(service.isSigned(stored));
    }

    @Test public void signConfirmedAfterTheSketchLeftTheHandLocksThatSketchAndNeverTheHeldWorldMap() {
        ItemStack map = startDrawing(); service.session(player).paint(); byte[] pixels = service.session(player).sheet().toBytes(); service.askToSign(player);
        // The chat task can run before the deferred hand sync that would have closed the editor.
        player.getInventory().setItem(10, map);
        ItemStack worldMap = new ItemStack(Material.FILLED_MAP); MapMeta vanilla = (MapMeta) worldMap.getItemMeta();
        vanilla.setMapView(server.createMap(player.getWorld())); worldMap.setItemMeta(vanilla); player.getInventory().setItemInMainHand(worldMap);
        assertTrue(service.handleSignChat(player, "sign")); assertFalse(service.editing(player));
        ItemStack held = player.getInventory().getItemInMainHand();
        assertFalse(service.isSketchMap(held)); assertEquals(vanilla.getMapView().getId(), ((MapMeta) held.getItemMeta()).getMapView().getId());
        ItemStack drawing = player.getInventory().getItem(10);
        assertTrue(service.isSigned(drawing)); assertArrayEquals(pixels, cells(drawing));
        assertEquals(1, Arrays.stream(player.getInventory().getContents()).filter(service::isSketchMap).count());
    }

    @Test public void signConfirmedAfterStaffClearedTheSketchSignsNothingAndReleasesTheEditor() {
        startDrawing(); service.session(player).paint(); service.askToSign(player);
        player.getInventory().clear(); messages();
        assertTrue(service.handleSignChat(player, "sign"));
        assertFalse(service.editing(player)); assertTrue(player.getAttribute(Attribute.MOVEMENT_SPEED).getValue() > 0);
        assertTrue(messages().stream().anyMatch(line -> line.contains("no longer with you")));
        assertFalse(Arrays.stream(player.getInventory().getContents()).anyMatch(service::isSketchMap));
    }

    @Test public void queuedSignLineIsIgnoredWhenThePlayerSwitchedAwayBeforeTheMainThreadRan() {
        ItemStack map = startDrawing(); service.session(player).paint(); service.askToSign(player);
        service.handleSignChatLater(player, "sign");
        player.getInventory().setItemInMainHand(new ItemStack(Material.STONE)); player.getInventory().setItem(9, map); service.syncHand(player);
        server.getScheduler().performOneTick();
        assertFalse(service.isSigned(player.getInventory().getItem(9))); assertTrue(SketchSheet.fromBytes(cells(player.getInventory().getItem(9))).hasInk());
    }

    @Test public void staffClearingTheBagMidDrawingEndsTheEditorWithoutInventingAMap() {
        startDrawing(); service.session(player).paint();
        player.getInventory().clear(); service.syncHand(player);
        assertFalse(service.editing(player)); assertTrue(player.getAttribute(Attribute.MOVEMENT_SPEED).getValue() > 0);
        assertFalse(Arrays.stream(player.getInventory().getContents()).anyMatch(service::isSketchMap));
    }

    @Test public void handResyncsKeepTheOpenSheetAndOnlyEnterUnsignedDrawings() {
        ItemStack map = startDrawing(); service.session(player).paint(); SketchSession session = service.session(player);
        service.syncHand(player); service.syncHandLater(player); server.getScheduler().performOneTick();
        assertSame(session, service.session(player));
        ItemStack second = service.createUnsigned(player.getWorld()); service.enter(player, second); assertSame(session, service.session(player));
        byte[] expected = session.sheet().toBytes();
        player.getInventory().setItemInMainHand(new ItemStack(Material.STONE)); player.getInventory().setItem(12, map);
        service.syncHandLater(player, new ItemStack(Material.STONE)); server.getScheduler().performOneTick();
        assertFalse(service.editing(player)); assertArrayEquals(expected, cells(player.getInventory().getItem(12)));
        // Scrolling straight from one unsigned sketch to another saves the first and opens the second.
        ItemStack other = service.createUnsigned(player.getWorld()); player.getInventory().setItemInMainHand(player.getInventory().getItem(12)); service.syncHand(player);
        service.session(player).move(1, 0); service.session(player).paint(); byte[] moved = service.session(player).sheet().toBytes();
        player.getInventory().setItem(12, player.getInventory().getItemInMainHand()); player.getInventory().setItemInMainHand(other); service.syncHand(player);
        assertArrayEquals(moved, cells(player.getInventory().getItem(12)));
        assertEquals(((MapMeta) other.getItemMeta()).getMapView().getId(), service.session(player).view().getId()); service.leave(player, false);
        ItemStack signed = signedDrawing(); MapView view = ((MapMeta) signed.getItemMeta()).getMapView();
        service.stop(); service = new SketchService(plugin, supplies, recovered, sites, catalogs, SketchSettings.defaults());
        service.syncHand(player, signed);
        assertFalse(service.editing(player)); assertArrayEquals(cells(signed), service.sheetOf(view).toBytes());
    }

    @Test public void signedSketchHandedOverByAnotherPluginAfterRestartIsRenderedByTheInputLoop() {
        ItemStack signed = signedDrawing(); MapView view = ((MapMeta) signed.getItemMeta()).getMapView(); player.getInventory().clear();
        service.stop(); service = new SketchService(plugin, supplies, recovered, sites, catalogs, SketchSettings.defaults()); service.start();
        assertNull(service.sheetOf(view));
        player.getInventory().setItemInMainHand(new ItemStack(Material.STONE)); server.getScheduler().performTicks(2);
        assertFalse(service.editing(player)); assertNull(service.sheetOf(view));
        player.getInventory().setItemInMainHand(signed); server.getScheduler().performTicks(2);
        assertFalse(service.editing(player)); assertArrayEquals(cells(signed), service.sheetOf(view).toBytes());
    }

    @Test public void movementKeysSteerTheCursorAcrossTheSheet() {
        player = spy(player); Input input = mock(Input.class); doReturn(input).when(player).getCurrentInput();
        mapApi.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
        mapApi.when(() -> Bukkit.getPlayer(player.getUniqueId())).thenReturn(player);
        ItemStack map = service.createUnsigned(player.getWorld()); player.getInventory().setItemInMainHand(map); service.start();
        when(input.isLeft()).thenReturn(true); when(input.isForward()).thenReturn(true); server.getScheduler().performTicks(2);
        assertEquals(15, service.session(player).cursorX()); assertEquals(15, service.session(player).cursorY());
        when(input.isLeft()).thenReturn(false); when(input.isForward()).thenReturn(false); when(input.isBackward()).thenReturn(true);
        // A new direction steps at once, then waits out the key-repeat delay before repeating.
        server.getScheduler().performTicks(4);
        assertEquals(15, service.session(player).cursorX()); assertEquals(16, service.session(player).cursorY());
        server.getScheduler().performTicks(4);
        assertEquals(15, service.session(player).cursorX()); assertEquals(18, service.session(player).cursorY());
    }

    @Test public void freezingKeepsOtherPluginsMovementModifiersAndNeverStacksAfterACrash() {
        var speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        org.bukkit.attribute.AttributeModifier haste = new org.bukkit.attribute.AttributeModifier(new NamespacedKey("otherplugin", "haste"), 0.5,
                org.bukkit.attribute.AttributeModifier.Operation.ADD_SCALAR, EquipmentSlotGroup.ANY);
        speed.addModifier(haste);
        // A crash mid-edit leaves the persisted freeze modifier on the player's saved attributes.
        speed.addModifier(new org.bukkit.attribute.AttributeModifier(key("sketch_freeze"), -1.0,
                org.bukkit.attribute.AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
        startDrawing();
        assertEquals(1, speed.getModifiers().stream().filter(modifier -> modifier.getKey().equals(key("sketch_freeze"))).count());
        service.leave(player, false);
        assertEquals(List.of(haste), List.copyOf(speed.getModifiers())); assertTrue(speed.getValue() > 0);
        startDrawing(); speed.getModifiers().stream().filter(modifier -> modifier.getKey().equals(key("sketch_freeze"))).toList().forEach(speed::removeModifier);
        service.leave(player, false);
        assertEquals(List.of(haste), List.copyOf(speed.getModifiers())); assertFalse(service.editing(player));
    }

    @Test public void cabinetSlotClicksSwapDrawingsRefuseStrangersAndRegisterFromTheControl() {
        Exhibit exhibit = exhibit(false); exhibit.find().setLabCleaned(true);
        ItemStack artifact = player.getInventory().getItemInMainHand(); ItemStack first = signedDrawing(); ItemStack second = signedDrawing();
        player.getInventory().setItemInMainHand(artifact);
        assertTrue(service.tryOpenCabinet(player, exhibit.cabinet(), false)); Inventory cabinet = player.getOpenInventory().getTopInventory(); messages();
        ItemStack air = new ItemStack(Material.AIR);
        assertSame(air, service.handleCabinetClick(player, cabinet, SketchCabinet.SLOT_SKETCH, air)); assertTrue(messages().isEmpty());
        assertFalse(service.tryDepositCabinet(player, cabinet, new ItemStack(Material.AIR))); assertTrue(messages().isEmpty());
        ItemStack stone = new ItemStack(Material.STONE, 3);
        assertSame(stone, service.handleCabinetClick(player, cabinet, SketchCabinet.SLOT_SKETCH, stone));
        assertTrue(messages().stream().anyMatch(line -> line.contains("cannot be registered"))); assertNull(cabinet.getItem(SketchCabinet.SLOT_SKETCH));
        assertTrue(service.handleCabinetClick(player, cabinet, SketchCabinet.SLOT_SKETCH, first).getType().isAir());
        assertEquals(first, service.handleCabinetClick(player, cabinet, SketchCabinet.SLOT_SKETCH, second));
        assertEquals(second, cabinet.getItem(SketchCabinet.SLOT_SKETCH));
        assertSame(air, service.handleCabinetClick(player, cabinet, SketchCabinet.SLOT_REGISTER, air));
        assertTrue(exhibit.find().hasFieldSketch()); verify(sites).save(exhibit.site());
        assertTrue(Arrays.stream(player.getInventory().getContents()).filter(service::isSketchMap)
                .anyMatch(stack -> exhibit.find().getId().toString().equals(stack.getItemMeta().getPersistentDataContainer().get(key("sketch_find_id"), PersistentDataType.STRING))));
    }

    @Test public void registerClickAfterAnotherPlayerFiledOrFinishedTheRecordKeepsTheDrawingSafe() {
        Exhibit exhibit = exhibit(false); exhibit.find().setLabCleaned(true);
        assertTrue(service.tryOpenCabinet(player, exhibit.cabinet(), false)); Inventory empty = player.getOpenInventory().getTopInventory();
        exhibit.find().setFieldSketch(true);
        service.tryRegisterCabinet(player, empty); server.getScheduler().performOneTick();
        assertTrue(player.getOpenInventory().getTopInventory().getHolder() instanceof CampIdentifyBoard);
        assertTrue(player.getWorld().getEntitiesByClass(org.bukkit.entity.Item.class).isEmpty());
        exhibit.find().setFieldSketch(false);
        ItemStack artifact = player.getInventory().getItemInMainHand(); ItemStack drawing = signedDrawing(); player.getInventory().setItemInMainHand(artifact);
        assertTrue(service.tryOpenCabinet(player, exhibit.cabinet(), false)); Inventory register = player.getOpenInventory().getTopInventory();
        assertTrue(service.tryDepositCabinet(player, register, drawing)); ItemStack placed = register.getItem(SketchCabinet.SLOT_SKETCH);
        exhibit.find().setFieldSketch(true); exhibit.find().addInterpretation(new FindInterpretation("function", "vessel", player.getUniqueId(), java.time.Instant.EPOCH));
        messages(); service.tryRegisterCabinet(player, register);
        assertTrue(messages().stream().anyMatch(line -> line.contains("record on this piece is complete")));
        assertEquals(placed, register.getItem(SketchCabinet.SLOT_SKETCH)); verify(sites, never()).save(any());
    }

    @Test public void idleOrUnrelatedHandsDoNotCraftAndEditorsCannotCraftOrOpenTheCabinet() {
        player.getInventory().setItemInMainHand(supplies.createPaper()); player.getInventory().setItemInOffHand(new ItemStack(Material.STICK));
        assertFalse(service.tryStartFromHands(player));
        player.getInventory().setItemInMainHand(supplies.createPencil()); player.getInventory().setItemInOffHand(new ItemStack(Material.STONE));
        assertFalse(service.tryStartFromHands(player));
        player.getInventory().setItemInMainHand(new ItemStack(Material.STICK)); player.getInventory().setItemInOffHand(supplies.createPaper());
        assertFalse(service.tryStartFromHands(player));
        ItemStack paper = supplies.createPaper(); ItemStack pencil = supplies.createPencil(); ItemStack stone = new ItemStack(Material.STONE);
        assertFalse(service.tryCraftOnClick(player, paper, stone)); assertFalse(service.tryCraftOnClick(player, pencil, stone));
        assertEquals(Material.PAPER, paper.getType()); assertFalse(Arrays.stream(player.getInventory().getContents()).anyMatch(service::isSketchMap));
        Exhibit exhibit = exhibit(false); ItemStack artifact = player.getInventory().getItemInMainHand();
        player.getInventory().setItem(20, artifact); startDrawing();
        assertFalse(service.tryCraftOnClick(player, paper, pencil)); assertEquals(Material.PAPER, paper.getType());
        assertFalse(service.tryOpenCabinet(player, exhibit.cabinet(), false));
        assertFalse(player.getOpenInventory().getTopInventory() != null && player.getOpenInventory().getTopInventory().getHolder() instanceof CabinetLabBoard);
    }

    @Test public void furnitureCabinetCuesTheLabAtItsSupportOrAtThePlayerWhenTheEventHasNoEntity() {
        Exhibit exhibit = exhibit(false); server.getPluginManager().registerEvents(new SketchListener(service), plugin);
        ItemRef furniture = new ItemRef(ItemRef.Kind.ITEMSADDER, "pack:cabinet", "");
        service.setSettings(new SketchSettings(5, furniture, LabSettings.defaults()));
        org.bukkit.entity.Entity entity = mock(org.bukkit.entity.Entity.class);
        ItemMatcher matcher = mock(ItemMatcher.class); when(matcher.matchesEntity(entity, furniture)).thenReturn(true);
        when(matcher.matchesNamespacedId("pack:cabinet", furniture)).thenReturn(true); service.setMatcher(matcher);
        // PackPluginHook passes the block under the furniture entity alongside it.
        Block support = player.getWorld().getBlockAt(4, 66, 9);
        assertTrue(service.tryOpenCabinet(player, null, entity, support, false));
        CabinetLabBoard board = (CabinetLabBoard) player.getOpenInventory().getTopInventory().getHolder();
        assertEquals(support.getLocation().add(.5, 1.05, .5), board.cabinet()); assertEquals(exhibit.find().getId(), board.findId());
        service.cancelLab(player);
        assertTrue(service.tryOpenCabinet(player, "pack:cabinet", null, null, false));
        assertEquals(player.getLocation(), ((CabinetLabBoard) player.getOpenInventory().getTopInventory().getHolder()).cabinet());
    }

    @Test public void recoveredPieceMissingItsSiteStampIsRefusedAsUnarchived() {
        Exhibit exhibit = exhibit(false); ItemStack piece = player.getInventory().getItemInMainHand();
        var meta = piece.getItemMeta(); meta.getPersistentDataContainer().remove(key("site_id")); piece.setItemMeta(meta); player.getInventory().setItemInMainHand(piece);
        messages(); assertTrue(service.tryOpenCabinet(player, exhibit.cabinet(), false));
        assertTrue(messages().stream().anyMatch(line -> line.contains("not in the excavation archive")));
        assertFalse(player.getOpenInventory().getTopInventory() != null && player.getOpenInventory().getTopInventory().getHolder() instanceof CabinetLabBoard);
    }

    @Test public void registerClickAfterStaffPurgedTheSiteKeepsTheDrawingInTheWindow() {
        Exhibit exhibit = exhibit(false); exhibit.find().setLabCleaned(true);
        ItemStack artifact = player.getInventory().getItemInMainHand(); ItemStack drawing = signedDrawing(); player.getInventory().setItemInMainHand(artifact);
        assertTrue(service.tryOpenCabinet(player, exhibit.cabinet(), false)); Inventory cabinet = player.getOpenInventory().getTopInventory();
        assertTrue(service.tryDepositCabinet(player, cabinet, drawing)); ItemStack placed = cabinet.getItem(SketchCabinet.SLOT_SKETCH);
        when(sites.findById(exhibit.site().getId())).thenReturn(Optional.empty()); messages();
        service.tryRegisterCabinet(player, cabinet);
        assertTrue(messages().stream().anyMatch(line -> line.contains("not in the excavation archive")));
        assertEquals(placed, cabinet.getItem(SketchCabinet.SLOT_SKETCH)); verify(sites, never()).save(any());
    }

    @Test public void heldDrawingWhoseSiteWasPurgedStillLoadsAfterARestartAndKeepsItsLabel() {
        Exhibit exhibit = exhibit(false); exhibit.find().setLabCleaned(true);
        ItemStack artifact = player.getInventory().getItemInMainHand(); ItemStack drawing = signedDrawing(); player.getInventory().setItemInMainHand(artifact);
        assertTrue(service.tryOpenCabinet(player, exhibit.cabinet(), false)); Inventory cabinet = player.getOpenInventory().getTopInventory();
        assertTrue(service.tryDepositCabinet(player, cabinet, drawing)); service.tryRegisterCabinet(player, cabinet);
        ItemStack filed = player.getInventory().getItem(player.getInventory().first(Material.FILLED_MAP)); String lore = filed.getItemMeta().getLore().get(0);
        // Staff wiped the dossier while the drawing sat in a chest; after a restart every hydrate retitles from the live site.
        when(sites.findById(exhibit.site().getId())).thenReturn(Optional.empty());
        service.stop(); service = restarted(); service.hydrate(filed);
        assertEquals(lore, filed.getItemMeta().getLore().get(0)); assertTrue(service.isSigned(filed));
        assertArrayEquals(cells(filed), service.sheetOf(((MapMeta) filed.getItemMeta()).getMapView()).toBytes());
    }

    @Test public void drawingFiledBeforeADossierRollbackCanBeFiledAgainForTheSameFind() {
        Exhibit exhibit = exhibit(false); exhibit.find().setLabCleaned(true);
        ItemStack artifact = player.getInventory().getItemInMainHand(); ItemStack drawing = signedDrawing(); player.getInventory().setItemInMainHand(artifact);
        assertTrue(service.tryOpenCabinet(player, exhibit.cabinet(), false)); Inventory first = player.getOpenInventory().getTopInventory();
        assertTrue(service.tryDepositCabinet(player, first, drawing)); service.tryRegisterCabinet(player, first);
        ItemStack filed = player.getInventory().getItem(player.getInventory().first(Material.FILLED_MAP)); byte[] pixels = cells(filed);
        exhibit.find().setFieldSketch(false); server.getScheduler().performOneTick();
        assertTrue(service.tryOpenCabinet(player, exhibit.cabinet(), false)); Inventory again = player.getOpenInventory().getTopInventory();
        assertTrue(service.tryDepositCabinet(player, again, filed)); messages(); service.tryRegisterCabinet(player, again);
        assertTrue(exhibit.find().hasFieldSketch()); assertFalse(messages().stream().anyMatch(line -> line.contains("different find")));
        verify(sites, times(2)).save(exhibit.site());
        assertArrayEquals(pixels, cells(player.getInventory().getItem(player.getInventory().first(Material.FILLED_MAP))));
        player.disconnect(); server.getScheduler().performOneTick(); assertTrue(exhibit.find().hasFieldSketch());
    }

    @Test public void finalPencilBagCraftClearsTheCursorAndDeferredWritesSkipPlayersWhoLeft() {
        supplies.update(ItemRef.vanilla(Material.PAPER), ItemRef.vanilla(Material.FEATHER), 1);
        ItemStack pencil = supplies.createPencil(); ItemStack paper = supplies.createPaper();
        player.getOpenInventory().setCursor(pencil); player.getInventory().setItem(4, paper);
        assertTrue(service.tryCraftOnClick(player, pencil, paper)); assertTrue(pencil.getType().isAir());
        service.afterBagCraft(player, player.getInventory(), 4, pencil, paper); server.getScheduler().performOneTick();
        assertTrue(player.getOpenInventory().getCursor() == null || player.getOpenInventory().getCursor().getType().isAir());
        assertTrue(service.isSketchMap(player.getInventory().getItem(4)));
        ItemStack lastPencil = supplies.createPencil(); ItemStack onCursor = supplies.createPaper(); player.getInventory().setItem(5, lastPencil);
        assertTrue(service.tryCraftOnClick(player, onCursor, lastPencil)); assertTrue(lastPencil.getType().isAir());
        service.afterBagCraft(player, player.getInventory(), 5, onCursor, lastPencil); server.getScheduler().performOneTick();
        assertNull(player.getInventory().getItem(5)); assertTrue(service.isSketchMap(player.getOpenInventory().getCursor()));
        ItemStack sheet = supplies.createPaper(); var meta = sheet.getItemMeta(); meta.setLore(null); sheet.setItemMeta(meta); player.getInventory().setItem(6, sheet);
        service.afterBagCraft(player, player.getInventory(), 6, null, null); service.stampKitsLater(player); player.disconnect();
        server.getScheduler().performOneTick();
        assertEquals(Material.PAPER, player.getInventory().getItem(6).getType()); assertFalse(player.getInventory().getItem(6).getItemMeta().hasLore());
    }

    @Test public void renamingLeavesOtherSitesDrawingsPurgedFindsAndOrdinaryItemsUntouched() {
        Exhibit exhibit = exhibit(false); exhibit.find().setLabCleaned(true);
        ItemStack artifact = player.getInventory().getItemInMainHand(); ItemStack drawing = signedDrawing(); player.getInventory().setItemInMainHand(artifact);
        assertTrue(service.tryOpenCabinet(player, exhibit.cabinet(), false)); Inventory cabinet = player.getOpenInventory().getTopInventory();
        assertTrue(service.tryDepositCabinet(player, cabinet, drawing)); service.tryRegisterCabinet(player, cabinet);
        ItemStack filed = player.getInventory().getItem(player.getInventory().first(Material.FILLED_MAP)); String lore = filed.getItemMeta().getLore().get(0);
        Site other = new Site(); other.setId(UUID.randomUUID()); other.setName("Elsewhere");
        assertFalse(service.retitle(filed, other)); assertFalse(service.retitle(new ItemStack(Material.PAPER), exhibit.site()));
        assertNull(service.siteIdOf(new ItemStack(Material.PAPER))); assertNull(service.siteIdOf(service.createUnsigned(player.getWorld())));
        // A drawing signed by an older release carries no author; renaming must keep it signed.
        var legacy = filed.getItemMeta(); legacy.getPersistentDataContainer().remove(key("sketch_author")); filed.setItemMeta(legacy);
        exhibit.site().setName("Harbour"); assertTrue(service.retitle(filed, exhibit.site()));
        assertEquals("#Harbour-1", ChatColor.stripColor(filed.getItemMeta().getLore().get(0))); assertTrue(service.isSigned(filed));
        assertNull(filed.getItemMeta().getPersistentDataContainer().get(key("sketch_author"), PersistentDataType.STRING));
        lore = filed.getItemMeta().getLore().get(0);
        ItemStack damaged = filed.clone(); var broken = damaged.getItemMeta();
        broken.getPersistentDataContainer().set(key("sketch_find_id"), PersistentDataType.STRING, "not-a-uuid"); damaged.setItemMeta(broken);
        exhibit.site().setName("Delta"); assertFalse(service.retitle(damaged, exhibit.site())); assertEquals(lore, damaged.getItemMeta().getLore().get(0));
        exhibit.site().getFinds().clear(); exhibit.site().setName("River");
        assertFalse(service.retitle(filed, exhibit.site())); assertEquals(lore, filed.getItemMeta().getLore().get(0));
    }

    @Test public void corruptStampsAndMissingPixelsFromStoredDataAreHandledWithoutLosingTheItem() {
        Exhibit exhibit = exhibit(false); exhibit.find().setLabCleaned(true);
        ItemStack artifact = player.getInventory().getItemInMainHand(); ItemStack drawing = signedDrawing(); ItemStack blank = signedDrawing();
        player.getInventory().setItemInMainHand(artifact);
        var meta = drawing.getItemMeta(); var pdc = meta.getPersistentDataContainer();
        pdc.set(key("sketch_find_id"), PersistentDataType.STRING, "not-a-uuid"); pdc.set(key("sketch_site_id"), PersistentDataType.STRING, "not-a-uuid"); drawing.setItemMeta(meta);
        assertNull(service.siteIdOf(drawing));
        var blankMeta = blank.getItemMeta(); blankMeta.getPersistentDataContainer().remove(key("sketch_cells")); blank.setItemMeta(blankMeta);
        // Restart so no live buffer masks what is stored on the items.
        service.stop(); service = new SketchService(plugin, supplies, recovered, sites, catalogs, SketchSettings.defaults());
        assertTrue(service.tryOpenCabinet(player, exhibit.cabinet(), false)); Inventory cabinet = player.getOpenInventory().getTopInventory(); messages();
        assertFalse(service.tryDepositCabinet(player, cabinet, blank));
        assertTrue(messages().stream().anyMatch(line -> line.contains("still blank"))); assertTrue(service.isSigned(blank));
        assertTrue(service.tryDepositCabinet(player, cabinet, drawing)); service.tryRegisterCabinet(player, cabinet);
        assertTrue(exhibit.find().hasFieldSketch());
        ItemStack filed = player.getInventory().getItem(player.getInventory().first(Material.FILLED_MAP));
        assertEquals(exhibit.site().getId(), service.siteIdOf(filed));
        assertEquals(exhibit.find().getId().toString(), filed.getItemMeta().getPersistentDataContainer().get(key("sketch_find_id"), PersistentDataType.STRING));
    }

    @Test public void strippedMapLinkIsRestoredFromTheStoredIdAndVanillaRenderersAreReplaced() {
        ItemStack signed = signedDrawing(); MapView view = ((MapMeta) signed.getItemMeta()).getMapView(); byte[] pixels = cells(signed);
        // Another plugin or a data fix rewrote the map component without its id.
        MapMeta stripped = (MapMeta) signed.getItemMeta(); stripped.setMapView(null); signed.setItemMeta(stripped);
        org.bukkit.map.MapRenderer terrain = mock(org.bukkit.map.MapRenderer.class); view.addRenderer(terrain);
        service.stop(); service = new SketchService(plugin, supplies, recovered, sites, catalogs, SketchSettings.defaults());
        service.hydrate(signed);
        assertEquals(view.getId(), ((MapMeta) signed.getItemMeta()).getMapView().getId());
        assertEquals(1, view.getRenderers().size()); assertTrue(view.getRenderers().get(0) instanceof SketchRenderer);
        assertArrayEquals(pixels, service.sheetOf(view).toBytes());
    }

    @Test public void sketchWhoseMapDataIsGoneStaysInertButCanStillBeFiledFromItsStoredPixels() {
        Exhibit exhibit = exhibit(false); exhibit.find().setLabCleaned(true);
        ItemStack artifact = player.getInventory().getItemInMainHand(); ItemStack drawing = signedDrawing(); byte[] pixels = cells(drawing);
        ItemStack unsigned = service.createUnsigned(player.getWorld()); ItemStack unlinked = service.createUnsigned(player.getWorld());
        for (ItemStack stack : List.of(drawing, unsigned, unlinked)) {
            MapMeta meta = (MapMeta) stack.getItemMeta(); meta.setMapView(null);
            if (stack == unlinked) meta.getPersistentDataContainer().remove(key("sketch_map_id"));
            else meta.getPersistentDataContainer().set(key("sketch_map_id"), PersistentDataType.INTEGER, 987654);
            stack.setItemMeta(meta);
        }
        service.stop(); service = new SketchService(plugin, supplies, recovered, sites, catalogs, SketchSettings.defaults());
        for (ItemStack inert : List.of(unsigned, unlinked)) {
            player.getInventory().setItemInMainHand(inert); service.syncHand(player); assertFalse(service.editing(player));
            service.hydrate(inert); assertNull(((MapMeta) inert.getItemMeta()).getMapView());
        }
        ItemStack live = startDrawing(); service.session(player).paint(); byte[] strokes = service.session(player).sheet().toBytes();
        player.getInventory().setItem(15, live); player.getInventory().setItemInMainHand(unsigned); service.syncHand(player);
        assertFalse(service.editing(player)); assertArrayEquals(strokes, cells(player.getInventory().getItem(15)));
        player.getInventory().setItemInMainHand(artifact);
        assertTrue(service.tryOpenCabinet(player, exhibit.cabinet(), false)); Inventory cabinet = player.getOpenInventory().getTopInventory();
        assertTrue(service.tryDepositCabinet(player, cabinet, drawing)); service.tryRegisterCabinet(player, cabinet);
        assertTrue(exhibit.find().hasFieldSketch());
        ItemStack filed = Arrays.stream(player.getInventory().getContents()).filter(service::isSigned).findFirst().orElseThrow();
        assertArrayEquals(pixels, cells(filed)); assertEquals(exhibit.site().getId(), service.siteIdOf(filed));
    }

    @Test public void lastPencilUseProducesOneDrawingAndRemovesBrokenPencilInEitherHand() {
        supplies.update(ItemRef.vanilla(Material.PAPER), ItemRef.vanilla(Material.FEATHER), 1);
        for (boolean paperInMain : new boolean[]{true, false}) {
            player.getInventory().clear();
            ItemStack paper = supplies.createPaper(); paper.setAmount(paperInMain ? 1 : 2);
            player.getInventory().setItemInMainHand(paperInMain ? paper : supplies.createPencil());
            player.getInventory().setItemInOffHand(paperInMain ? supplies.createPencil() : paper);
            assertTrue(service.tryStartFromHands(player));
            assertEquals(1, Arrays.stream(player.getInventory().getContents()).filter(service::isSketchMap).count());
            assertFalse(Arrays.stream(player.getInventory().getContents()).anyMatch(supplies::isPencil));
            if (paperInMain) {
                assertTrue(service.editing(player)); assertTrue(player.getInventory().getItemInOffHand().getType().isAir());
            } else {
                assertTrue(service.editing(player)); assertTrue(service.isSketchMap(player.getInventory().getItemInMainHand())); assertEquals(1, player.getInventory().getItemInOffHand().getAmount());
                assertTrue(supplies.isPaper(player.getInventory().getItemInOffHand()));
            }
            service.leave(player, false);
        }
    }

    @Test public void reducingPencilDurabilityOnReloadRefusesFurtherCraftsWithoutConsumingPaper() {
        ItemStack paper = supplies.createPaper(); paper.setAmount(4);
        player.getInventory().setItemInMainHand(paper); player.getInventory().setItemInOffHand(supplies.createPencil());
        assertTrue(service.tryStartFromHands(player)); assertTrue(service.tryStartFromHands(player));
        assertEquals(2, player.getInventory().getItemInMainHand().getAmount());
        supplies.update(ItemRef.vanilla(Material.PAPER), ItemRef.vanilla(Material.FEATHER), 2);
        supplies.stampInstructions(player.getInventory().getItemInOffHand());
        assertTrue(supplies.isSpent(player.getInventory().getItemInOffHand()));
        messages(); assertFalse(service.tryStartFromHands(player));
        assertTrue(messages().stream().anyMatch(line -> line.contains("pencil is spent")));
        assertEquals(2, player.getInventory().getItemInMainHand().getAmount());
        assertEquals(2, Arrays.stream(player.getInventory().getContents()).filter(service::isSketchMap).count()); assertFalse(service.editing(player));
    }

    @Test public void inputLoopRecoversMissedHandEventsAndSavesMapMovedByAnotherPlugin() {
        player = spy(player); doReturn(mock(Input.class)).when(player).getCurrentInput();
        mapApi.when(Bukkit::getOnlinePlayers).thenAnswer(call -> server.getOnlinePlayers().stream()
                .map(online -> online.getUniqueId().equals(player.getUniqueId()) ? player : online).toList());
        mapApi.when(() -> Bukkit.getPlayer(player.getUniqueId())).thenReturn(player);
        player.getInventory().setItemInMainHand(new ItemStack(Material.STONE)); service.start();
        ItemStack map = service.createUnsigned(player.getWorld()); player.getInventory().setItemInMainHand(map);
        assertFalse(service.editing(player)); server.getScheduler().performTicks(2); assertTrue(service.editing(player));
        service.session(player).paint(); byte[] expected = service.session(player).sheet().toBytes();
        service.askToSign(player); server.getScheduler().performTicks(2);
        player.getInventory().setItem(10, player.getInventory().getItemInMainHand()); player.getInventory().setItemInMainHand(new ItemStack(Material.STONE));
        server.getScheduler().performTicks(2);
        assertFalse(service.editing(player)); assertArrayEquals(expected, cells(player.getInventory().getItem(10)));
        assertTrue(player.getAttribute(Attribute.MOVEMENT_SPEED).getValue() > 0);
        player.getInventory().setItemInMainHand(player.getInventory().getItem(10)); player.getInventory().setItem(10, null);
        service.begin(player); assertTrue(service.editing(player)); assertArrayEquals(expected, service.session(player).sheet().toBytes());
        service.askToSign(player); service.handleSignChat(player, "sign");
        server.getScheduler().performTicks(2); assertFalse(service.editing(player)); assertTrue(service.isSigned(player.getInventory().getItemInMainHand()));
    }

    @Test public void movingDrawingIntoOwnCraftingGridStillSavesTheLiveStrokes() {
        // Bukkit cannot create the player's own crafting inventory; model its five real slots.
        Inventory crafting = new org.mockbukkit.mockbukkit.inventory.InventoryMock(player, 5, InventoryType.CRAFTING);
        player.openInventory(crafting);
        ItemStack map = startDrawing(); service.session(player).paint(); byte[] expected = service.session(player).sheet().toBytes();
        player.getInventory().setItemInMainHand(new ItemStack(Material.STONE)); crafting.setItem(1, map);
        service.syncHand(player);
        assertFalse(service.editing(player)); assertArrayEquals(expected, cells(crafting.getItem(1)));
        assertEquals(Material.STONE, player.getInventory().getItemInMainHand().getType());
    }

    @Test public void restartingSketchServiceHydratesFramedMapsAndRendersNewEditorStrokes() {
        ItemStack map = signedDrawing(); MapView view = ((MapMeta) map.getItemMeta()).getMapView();
        org.bukkit.entity.ItemFrame frame = player.getWorld().spawn(player.getLocation(), org.bukkit.entity.ItemFrame.class); frame.setItem(map);
        player.getInventory().clear(); service.stop();
        service = new SketchService(plugin, supplies, recovered, sites, catalogs, SketchSettings.defaults()); service.start();
        assertArrayEquals(cells(map), service.sheetOf(view).toBytes());
        ItemStack unsigned = service.createUnsigned(player.getWorld()); player.getInventory().setItemInMainHand(unsigned); service.begin(player);
        service.session(player).paint(); service.leave(player, false);
        MapView editableView = ((MapMeta) player.getInventory().getItemInMainHand().getItemMeta()).getMapView();
        service.stop(); service = new SketchService(plugin, supplies, recovered, sites, catalogs, SketchSettings.defaults()); service.start();
        service.session(player).move(-16, -16); service.session(player).cycleInk(); service.session(player).paint();
        service.leave(player, false);
        org.bukkit.map.MapCanvas canvas = mock(org.bukkit.map.MapCanvas.class);
        java.util.concurrent.atomic.AtomicReference<java.awt.Color> rendered = new java.util.concurrent.atomic.AtomicReference<>();
        doAnswer(call -> { rendered.set(call.getArgument(2)); return null; }).when(canvas).setPixelColor(eq(0), eq(0), any(java.awt.Color.class));
        for (var renderer : editableView.getRenderers()) renderer.render(editableView, canvas, player);
        assertEquals(SketchInk.RED.color(), rendered.get());
        assertEquals(1, editableView.getRenderers().size());
    }

    @Test public void checkpointNewerThanTheSavedItemRestoresStrokesLostInACrashOnTheNextStart() throws Exception {
        Input input = liveInput(); ItemStack map = service.createUnsigned(player.getWorld()); player.getInventory().setItemInMainHand(map); service.start();
        stroke(input, false); service.leave(player, false);
        assertEquals(Long.valueOf(1), revisionOf(hand()));
        // The next tick reopens the held sheet; a second stroke then only reaches the disk checkpoint.
        stroke(input, true); server.getScheduler().performTicks(40);
        int id = mapId(map); assertEquals(2, autosaves().load(id).revision());
        assertEquals(SketchInk.PAPER, SketchSheet.fromBytes(cells(hand())).at(17, 16));
        // Crash: the input loop dies without stop() writing the sheet back onto the held map.
        server.getScheduler().cancelTasks(plugin);
        service = restarted(); service.start();
        SketchSheet reopened = service.session(player).sheet();
        assertEquals(SketchInk.CHARCOAL, reopened.at(16, 16)); assertEquals(SketchInk.CHARCOAL, reopened.at(17, 16)); assertEquals(2, reopened.revision());
        service.leave(player, false);
        assertEquals(SketchInk.CHARCOAL, SketchSheet.fromBytes(cells(hand())).at(17, 16)); assertEquals(Long.valueOf(2), revisionOf(hand()));
        assertNull(autosaves().load(id));
    }

    @Test public void legacyMapWithoutAStoredRevisionTakesItsCheckpointAfterACrash() throws Exception {
        Input input = liveInput(); ItemStack map = startDrawing(); service.session(player).paint(); service.stop();
        // Maps saved before revisions were stored carry pixels but no sketch_revision.
        ItemStack legacy = hand(); org.bukkit.inventory.meta.ItemMeta meta = legacy.getItemMeta();
        meta.getPersistentDataContainer().remove(key("sketch_revision")); legacy.setItemMeta(meta); player.getInventory().setItemInMainHand(legacy);
        service = restarted(); service.start();
        assertEquals(0, service.session(player).sheet().revision());
        stroke(input, true); server.getScheduler().performTicks(40);
        int id = mapId(map); assertEquals(1, autosaves().load(id).revision());
        server.getScheduler().cancelTasks(plugin);
        assertNull(revisionOf(hand())); assertEquals(SketchInk.PAPER, SketchSheet.fromBytes(cells(hand())).at(17, 16));
        service = restarted(); service.start();
        assertEquals(SketchInk.CHARCOAL, service.session(player).sheet().at(16, 16));
        assertEquals(SketchInk.CHARCOAL, service.session(player).sheet().at(17, 16));
    }

    @Test public void unremovableCheckpointIsLoggedAndTheDrawingIsStillSavedOntoTheItem() throws Exception {
        List<String> warnings = warnings(); Input input = liveInput();
        ItemStack map = service.createUnsigned(player.getWorld()); player.getInventory().setItemInMainHand(map); service.start();
        stroke(input, false); server.getScheduler().performTicks(40);
        int id = mapId(map); Path checkpoint = autosaveFolder().resolve(id + ".bin"); assertEquals(1, autosaves().load(id).revision());
        // Stands in for a disk fault: a non-empty directory on the checkpoint's name cannot be deleted, even by root.
        Files.delete(checkpoint); Files.createDirectories(checkpoint.resolve("squatter"));
        service.leave(player, false);
        assertTrue(warnings.stream().anyMatch(line -> line.contains("Could not remove field sketch autosave " + id + ": ")));
        assertFalse(service.editing(player));
        assertEquals(SketchInk.CHARCOAL, SketchSheet.fromBytes(cells(hand())).at(16, 16)); assertEquals(Long.valueOf(1), revisionOf(hand()));
    }

    @Test public void checkpointNoNewerThanTheSavedItemIsIgnoredAfterARestart() throws Exception {
        Input input = liveInput(); ItemStack map = service.createUnsigned(player.getWorld()); player.getInventory().setItemInMainHand(map); service.start();
        stroke(input, false); service.leave(player, false);
        int id = mapId(map); assertEquals(Long.valueOf(1), revisionOf(hand()));
        // A checkpoint left behind by a failed delete carries the item's own revision, or an older one.
        for (long stale : new long[]{1, 0}) {
            autosaves().save(id, SketchSheet.fromBytes(new SketchSheet().toBytes(), stale));
            server.getScheduler().cancelTasks(plugin); service = restarted(); service.start();
            assertEquals(SketchInk.CHARCOAL, service.session(player).sheet().at(16, 16)); assertEquals(1, service.session(player).sheet().revision());
            service.leave(player, false); assertNull(autosaves().load(id));
        }
    }

    @Test public void corruptCheckpointIsLoggedAndTheMapFallsBackToItsOwnPixelsUntilTheNextSaveClearsIt() throws Exception {
        List<String> warnings = warnings();
        ItemStack map = startDrawing(); service.session(player).paint(); service.leave(player, false, map);
        int id = mapId(map); Path checkpoint = autosaveFolder().resolve(id + ".bin");
        // A backup restore or disk fault can leave a short file; the store itself only ever renames complete copies.
        Files.createDirectories(autosaveFolder()); Files.write(checkpoint, new byte[100]);
        service.stop(); service = restarted(); service.hydrate(map);
        assertTrue(warnings.stream().anyMatch(line -> line.endsWith("Could not load field sketch autosave " + id + ": Invalid sketch autosave size for map " + id)));
        assertArrayEquals(cells(map), service.sheetOf(((MapMeta) map.getItemMeta()).getMapView()).toBytes());
        service.enter(player, map); assertEquals(SketchInk.CHARCOAL, service.session(player).sheet().at(16, 16));
        service.leave(player, false, map); assertFalse(Files.exists(checkpoint));
    }

    @Test public void unwritableAutosaveFolderIsLoggedAndTheSameStrokesAreCheckpointedOnceItRecovers() throws Exception {
        List<String> warnings = warnings(); Input input = liveInput();
        ItemStack map = service.createUnsigned(player.getWorld()); player.getInventory().setItemInMainHand(map); service.start();
        int id = mapId(map);
        // Stands in for a full or read-only disk: a plain file on the folder's name cannot be written into, even by root.
        Files.createDirectories(plugin.getDataFolder().toPath()); Files.write(autosaveFolder(), new byte[0]);
        stroke(input, false); server.getScheduler().performTicks(40);
        assertTrue(warnings.stream().anyMatch(line -> line.contains("Could not autosave field sketch " + id + ": ")));
        assertNull(autosaves().load(id));
        // The editor keeps drawing normally; moving the cursor does not change the sheet revision.
        assertTrue(service.editing(player)); stroke(input, true, false); assertEquals(17, service.session(player).cursorX());
        Files.delete(autosaveFolder());
        server.getScheduler().performTicks(40);
        SketchAutosaveStore.Snapshot saved = autosaves().load(id);
        assertEquals(1, saved.revision()); assertEquals(SketchInk.CHARCOAL, SketchSheet.fromBytes(saved.cells()).at(16, 16));
        try (var files = Files.list(autosaveFolder())) { assertEquals(List.of(id + ".bin"), files.map(file -> file.getFileName().toString()).toList()); }
        assertFalse(SketchSheet.fromBytes(cells(hand())).hasInk());
    }

    @Test public void editorWhoNeverDrawsWritesNoCheckpointAndAnIdleSheetIsNotRewritten() throws Exception {
        Input input = liveInput(); ItemStack map = service.createUnsigned(player.getWorld()); player.getInventory().setItemInMainHand(map); service.start();
        server.getScheduler().performTicks(80);
        int id = mapId(map); Path checkpoint = autosaveFolder().resolve(id + ".bin");
        assertTrue(service.editing(player)); assertFalse(Files.exists(checkpoint));
        stroke(input, false); server.getScheduler().performTicks(40); assertTrue(Files.exists(checkpoint));
        Files.setLastModifiedTime(checkpoint, FileTime.fromMillis(0));
        server.getScheduler().performTicks(80);
        assertEquals(FileTime.fromMillis(0), Files.getLastModifiedTime(checkpoint));
    }

    private Exhibit exhibit(boolean cleanAndSketched) {
        service.stop(); recovered = new RecoveredFindItem(plugin);
        service = new SketchService(plugin, supplies, recovered, sites, catalogs, SketchSettings.defaults());
        ArtifactTemplate template = new ArtifactTemplate("pot", "Old pot", 1, 1, "ceramic", null, false, 1, Set.of(), Set.of(), FindProfile.OBJECT, List.of(ItemRef.vanilla(Material.BRICK)), "");
        when(catalogs.artifact("pot")).thenReturn(template); when(catalogs.rarityLoreLine(template)).thenReturn("Rare");
        when(catalogs.materialDisplayName("ceramic")).thenReturn("Ceramic");
        when(catalogs.materialOf("ceramic")).thenReturn(new FindMaterial("ceramic", "Ceramic", 1, Material.WHITE_STAINED_GLASS_PANE, List.of("soil")));
        Site site = new Site(); site.setId(UUID.randomUUID()); site.setName("Quarry"); site.establish(player.getUniqueId(), 1, 1, 16, 65, 16);
        BuriedFind find = new BuriedFind(); find.setId(UUID.randomUUID()); find.setArtifactId("pot"); find.setStratumId("I"); find.setState(FindState.RECOVERED); find.setFindNumber(1);
        find.setLabCleaned(cleanAndSketched); find.setFieldSketch(cleanAndSketched); site.getFinds().add(find);
        when(sites.findById(site.getId())).thenReturn(Optional.of(site));
        InterpretationTemplate option = new InterpretationTemplate("vessel", "function", "Storage vessel", Set.of(), Set.of(), Set.of());
        InterpretationType type = new InterpretationType("function", "Function", "What was it for?", List.of(option));
        when(catalogs.nextOpenType(find)).thenAnswer(call -> find.isCatalogued() ? null : type);
        when(catalogs.stationOffers(eq("function"), eq(find.getId()), eq("pot"), anySet())).thenReturn(List.of(option));
        when(catalogs.interpretation("vessel")).thenReturn(option); when(catalogs.interpretationType("function")).thenReturn(type);
        ItemStack artifact = recovered.create(template, site, find, player.getUniqueId(), "Good", false, catalogs); player.getInventory().setItemInMainHand(artifact);
        Block cabinet = player.getWorld().getBlockAt(0, 65, 0); cabinet.setType(Material.CARTOGRAPHY_TABLE);
        return new Exhibit(site, find, artifact, cabinet);
    }
    private record Exhibit(Site site, BuriedFind find, ItemStack artifact, Block cabinet) {}

    private ItemStack startDrawing() {
        ItemStack map = service.createUnsigned(player.getWorld()); player.getInventory().setItemInMainHand(map); service.enter(player, map); return map;
    }
    private ItemStack signedDrawing() {
        ItemStack map = startDrawing(); service.session(player).paint(); service.askToSign(player); service.handleSignChat(player, "sign");
        return player.getInventory().getItemInMainHand();
    }
    private NamespacedKey key(String name) { return new NamespacedKey(plugin, name); }
    private byte[] cells(ItemStack stack) { return stack.getItemMeta().getPersistentDataContainer().get(key("sketch_cells"), PersistentDataType.BYTE_ARRAY); }
    /** Drives the real input loop for {@link #player} through a mocked key state. */
    private Input liveInput() {
        player = spy(player); Input input = mock(Input.class); doReturn(input).when(player).getCurrentInput();
        mapApi.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
        mapApi.when(() -> Bukkit.getPlayer(player.getUniqueId())).thenReturn(player);
        return input;
    }
    /** One tick of sneak (painting), optionally stepping right first, then keys released. */
    private void stroke(Input input, boolean right) { stroke(input, right, true); }
    private void stroke(Input input, boolean right, boolean sneak) {
        when(input.isRight()).thenReturn(right); when(input.isSneak()).thenReturn(sneak); server.getScheduler().performOneTick();
        when(input.isRight()).thenReturn(false); when(input.isSneak()).thenReturn(false);
    }
    private SketchService restarted() { return new SketchService(plugin, supplies, recovered, sites, catalogs, SketchSettings.defaults()); }
    private Path autosaveFolder() { return plugin.getDataFolder().toPath().resolve("sketch-autosaves"); }
    private SketchAutosaveStore autosaves() { return new SketchAutosaveStore(plugin.getDataFolder().toPath()); }
    private ItemStack hand() { return player.getInventory().getItemInMainHand(); }
    private int mapId(ItemStack map) { return ((MapMeta) map.getItemMeta()).getMapView().getId(); }
    private Long revisionOf(ItemStack stack) { return stack.getItemMeta().getPersistentDataContainer().get(key("sketch_revision"), PersistentDataType.LONG); }
    private List<String> warnings() {
        List<String> lines = new ArrayList<>();
        plugin.getLogger().addHandler(new java.util.logging.Handler() {
            @Override public void publish(java.util.logging.LogRecord record) { if (record.getLevel() == java.util.logging.Level.WARNING) lines.add(record.getMessage()); }
            @Override public void flush() {}
            @Override public void close() {}
        });
        return lines;
    }
    private List<String> messages() { List<String> lines = new ArrayList<>(); String message; while ((message = player.nextMessage()) != null) lines.add(ChatColor.stripColor(message)); return lines; }
}
