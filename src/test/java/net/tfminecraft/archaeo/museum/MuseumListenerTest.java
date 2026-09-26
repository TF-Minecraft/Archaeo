package net.tfminecraft.archaeo.museum;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;
import org.junit.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import java.util.*;
import net.tfminecraft.archaeo.config.*;
import net.tfminecraft.archaeo.establish.CampFindBoard;
import net.tfminecraft.archaeo.item.*;
import net.tfminecraft.archaeo.model.*;
import net.tfminecraft.archaeo.site.SiteRepository;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.Assert.assertEquals;

public class MuseumListenerTest {
    private ServerMock server;
    private PlayerMock viewer;
    private JavaPlugin plugin;
    private SiteRepository sites;
    private CatalogRegistry catalogs;
    private RecoveredFindItem recovered;
    private MuseumListener listener;
    private Site site;
    private BuriedFind find;
    private ItemStack piece;

    @Before public void setUp() {
        server = MockBukkit.mock(); plugin = MockBukkit.createMockPlugin(); viewer = server.addPlayer();
        sites = mock(SiteRepository.class); catalogs = mock(CatalogRegistry.class);
        when(catalogs.museum()).thenReturn(MuseumSettings.defaults()); when(catalogs.pick()).thenReturn(PickSettings.defaults());
        ArtifactTemplate template = new ArtifactTemplate("pot", "Exhibited pot", 1, 1, "ceramic", null, false, 1, Set.of(), Set.of(), FindProfile.OBJECT, List.of(ItemRef.vanilla(Material.BRICK)), "");
        when(catalogs.artifact("pot")).thenReturn(template);
        when(catalogs.materialOf("ceramic")).thenReturn(new FindMaterial("ceramic", "Ceramic", 1, Material.WHITE_STAINED_GLASS_PANE, List.of("soil")));
        recovered = new RecoveredFindItem(plugin); listener = new MuseumListener(sites, catalogs, recovered);
        site = new Site(); site.setId(UUID.randomUUID()); site.setName("Quarry"); site.setStatus(SiteStatus.CLOSED);
        find = new BuriedFind(); find.setId(UUID.randomUUID()); find.setArtifactId("pot"); find.setStratumId("I"); find.setFindNumber(3); find.setState(FindState.RECOVERED);
        site.getFinds().add(find); when(sites.findById(site.getId())).thenReturn(Optional.of(site));
        piece = recovered.create(template, site, find, null, "Good", false, catalogs);
    }
    @After public void tearDown() { MockBukkit.unmock(); }

    @Test
    public void selectsAllThreeSlotsFromEachFacing() {
        for (int slot = 0; slot < 3; slot++) {
            double horizontal = (slot + 0.5) / 3;
            assertEquals(slot, MuseumListener.shelfSlot(BlockFace.NORTH, new Vector(1 - horizontal, 0.5, 0)));
            assertEquals(slot, MuseumListener.shelfSlot(BlockFace.SOUTH, new Vector(horizontal, 0.5, 1)));
            assertEquals(slot, MuseumListener.shelfSlot(BlockFace.WEST, new Vector(0, 0.5, horizontal)));
            assertEquals(slot, MuseumListener.shelfSlot(BlockFace.EAST, new Vector(1, 0.5, 1 - horizontal)));
        }
    }

    @Test
    public void keepsEdgesInsideTheInventoryAndSplitsAtThirds() {
        double[] positions = {0, 1.0 / 3 - 0.001, 1.0 / 3, 2.0 / 3 - 0.001, 2.0 / 3, 1};
        int[] slots = {0, 0, 1, 1, 2, 2};
        for (int i = 0; i < positions.length; i++) {
            assertEquals(slots[i], MuseumListener.shelfSlot(BlockFace.SOUTH, new Vector(positions[i], 0, 1)));
        }
    }

    @Test
    public void rejectsPositionsOutsideTheBlock() {
        assertEquals(-1, MuseumListener.shelfSlot(BlockFace.SOUTH, null));
        assertEquals(-1, MuseumListener.shelfSlot(BlockFace.UP, new Vector(0.5, 0.5, 0.5)));
        for (double invalid : new double[] {-0.01, 1.01, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertEquals(-1, MuseumListener.shelfSlot(BlockFace.SOUTH, new Vector(invalid, 0.5, 0.5)));
            assertEquals(-1, MuseumListener.shelfSlot(BlockFace.SOUTH, new Vector(0.5, invalid, 0.5)));
            assertEquals(-1, MuseumListener.shelfSlot(BlockFace.SOUTH, new Vector(0.5, 0.5, invalid)));
        }
    }

    @Test public void framePlaquesArePublicForClosedExcavationsAndNormalClicksStayVanilla() {
        ItemFrame frame = mock(ItemFrame.class); when(frame.getType()).thenReturn(EntityType.ITEM_FRAME); when(frame.getItem()).thenReturn(piece);
        PlayerInteractEntityEvent normal = entityEvent(frame, EquipmentSlot.HAND); listener.onEntity(normal); assertFalse(normal.isCancelled());
        viewer.setSneaking(true);
        PlayerInteractEntityEvent offhand = entityEvent(frame, EquipmentSlot.OFF_HAND); listener.onEntity(offhand); assertFalse(offhand.isCancelled());
        PlayerInteractEntityEvent event = entityEvent(frame, EquipmentSlot.HAND); listener.onEntity(event); assertTrue(event.isCancelled());
        assertPlaque(); assertFalse(site.onStaff(viewer.getUniqueId()));
        assertEquals(piece, frame.getItem()); verify(frame, never()).setItem(any()); verify(sites, never()).save(any());
    }

    @Test public void emptyVanillaAndDisabledDisplaysDoNotInterceptSneakInteractions() {
        viewer.setSneaking(true);
        ItemFrame frame = mock(ItemFrame.class); when(frame.getType()).thenReturn(EntityType.ITEM_FRAME);
        for (ItemStack ordinary : new ItemStack[]{null, new ItemStack(Material.AIR), new ItemStack(Material.BRICK)}) {
            when(frame.getItem()).thenReturn(ordinary); PlayerInteractEntityEvent event = entityEvent(frame, EquipmentSlot.HAND);
            listener.onEntity(event); assertFalse(event.isCancelled());
        }
        when(frame.getItem()).thenReturn(piece); when(catalogs.museum()).thenReturn(new MuseumSettings(List.of()));
        PlayerInteractEntityEvent disabled = entityEvent(frame, EquipmentSlot.HAND); listener.onEntity(disabled); assertFalse(disabled.isCancelled());
        assertNull(viewer.nextMessage()); assertNoPlaque();
    }

    @Test public void sneakingPoseAlsoOpensGlowFramePlaques() {
        viewer = spy(viewer); doReturn(false).when(viewer).isSneaking(); doReturn(Pose.SNEAKING).when(viewer).getPose();
        ItemFrame glow = mock(ItemFrame.class); when(glow.getType()).thenReturn(EntityType.GLOW_ITEM_FRAME); when(glow.getItem()).thenReturn(piece);
        PlayerInteractEntityEvent glowEvent = entityEvent(glow, EquipmentSlot.HAND); listener.onEntity(glowEvent); assertTrue(glowEvent.isCancelled()); assertPlaque();
    }

    @Test public void lecternReadsTheDisplayedArtifactAndDeniesBookAndHeldItemUse() {
        viewer.setSneaking(true); Lectern lectern = mock(Lectern.class); Block block = mock(Block.class);
        when(block.getType()).thenReturn(Material.LECTERN); when(block.getState()).thenReturn(lectern);
        var inventory = mock(org.bukkit.inventory.LecternInventory.class); when(lectern.getInventory()).thenReturn(inventory); when(inventory.getItem(0)).thenReturn(piece);
        PlayerInteractEvent event = blockEvent(block, new Vector(.5, .5, .5)); listener.onBlock(event);
        assertTrue(event.isCancelled()); assertEquals(Event.Result.DENY, event.useInteractedBlock()); assertEquals(Event.Result.DENY, event.useItemInHand()); assertPlaque();
        viewer.closeInventory();
        PlayerInteractEvent left = blockEvent(block, null, Action.LEFT_CLICK_BLOCK, EquipmentSlot.HAND); listener.onBlock(left); assertVanilla(left);
        PlayerInteractEvent off = blockEvent(block, null, Action.RIGHT_CLICK_BLOCK, EquipmentSlot.OFF_HAND); listener.onBlock(off); assertVanilla(off);
        when(inventory.getItem(0)).thenReturn(new ItemStack(Material.WRITTEN_BOOK));
        PlayerInteractEvent ordinary = blockEvent(block, null); listener.onBlock(ordinary); assertVanilla(ordinary);
        assertNoPlaque();
    }

    @Test public void shelfPlaqueUsesThePointedColumnRatherThanAnotherOccupiedSlot() {
        viewer.setSneaking(true); Shelf shelf = mock(Shelf.class); Block block = mock(Block.class);
        when(block.getType()).thenReturn(Material.OAK_SHELF); when(block.getState()).thenReturn(shelf);
        org.bukkit.block.data.type.Shelf data = mock(org.bukkit.block.data.type.Shelf.class); when(data.getFacing()).thenReturn(BlockFace.SOUTH); when(shelf.getBlockData()).thenReturn(data);
        org.bukkit.inventory.ShelfInventory inventory = mock(org.bukkit.inventory.ShelfInventory.class); when(shelf.getInventory()).thenReturn(inventory);
        when(inventory.getItem(1)).thenReturn(piece); when(inventory.getItem(0)).thenReturn(new ItemStack(Material.BRICK));
        PlayerInteractEvent left = blockEvent(block, new Vector(.1, .5, 1)); listener.onBlock(left); assertVanilla(left); // An ordinary brick is swapped as usual.
        assertNoPlaque();
        PlayerInteractEvent middle = blockEvent(block, new Vector(.5, .5, 1)); listener.onBlock(middle); assertTrue(middle.isCancelled()); assertPlaque();
        viewer.closeInventory();
        PlayerInteractEvent outside = blockEvent(block, new Vector(-.1, .5, 1)); listener.onBlock(outside); assertVanilla(outside);
        assertNoPlaque();
    }

    @Test public void armorStandManipulationReadsTheTargetSlotWithoutSwappingEquipment() {
        viewer.setSneaking(true); ArmorStand stand = mock(ArmorStand.class);
        PlayerArmorStandManipulateEvent event = standEvent(stand, EquipmentSlot.HAND); listener.onStand(event); assertTrue(event.isCancelled()); assertPlaque();
        viewer.closeInventory();
        PlayerArmorStandManipulateEvent boots = standEvent(stand, EquipmentSlot.HAND, new ItemStack(Material.LEATHER_BOOTS));
        listener.onStand(boots); assertFalse(boots.isCancelled()); assertNoPlaque();
        EntityEquipment equipment = mock(EntityEquipment.class); when(stand.getEquipment()).thenReturn(equipment); when(equipment.getBoots()).thenReturn(piece);
        PlayerInteractEntityEvent generic = entityEvent(stand, EquipmentSlot.HAND); listener.onEntity(generic); assertTrue(generic.isCancelled()); assertPlaque();
    }

    @Test public void missingOrHiddenArchiveRecordsExplainFailureWithoutTakingTheExhibit() {
        viewer.setSneaking(true); ItemFrame frame = mock(ItemFrame.class); when(frame.getType()).thenReturn(EntityType.ITEM_FRAME); when(frame.getItem()).thenReturn(piece);
        when(sites.findById(site.getId())).thenReturn(Optional.empty());
        PlayerInteractEntityEvent missing = entityEvent(frame, EquipmentSlot.HAND); listener.onEntity(missing); assertTrue(missing.isCancelled());
        assertEquals("That excavation record is missing.", viewer.nextMessage());
        when(sites.findById(site.getId())).thenReturn(Optional.of(site)); site.setStatus(SiteStatus.HIDDEN);
        listener.onEntity(entityEvent(frame, EquipmentSlot.HAND)); assertEquals("That excavation record is missing.", viewer.nextMessage());
        site.setStatus(SiteStatus.ESTABLISHED); site.getFinds().clear();
        listener.onEntity(entityEvent(frame, EquipmentSlot.HAND)); assertEquals("That excavation record is missing.", viewer.nextMessage());
        verify(frame, never()).setItem(any());
    }

    @Test public void configuredFurnitureSupportResolvesExhibitsFromEquipmentOrShelfContents() {
        ItemRef furniture = new ItemRef(ItemRef.Kind.ITEMSADDER, "pack:case", "");
        when(catalogs.museum()).thenReturn(new MuseumSettings(List.of(furniture)));
        ItemMatcher matcher = mock(ItemMatcher.class); when(matcher.matchesNamespacedId("pack:case", furniture)).thenReturn(true); listener.setMatcher(matcher);
        ArmorStand root = mock(ArmorStand.class); EntityEquipment equipment = mock(EntityEquipment.class); when(root.getEquipment()).thenReturn(equipment); when(equipment.getHelmet()).thenReturn(piece);
        assertFalse(listener.tryOpenFromSupport(viewer, "pack:case", root, null, false));
        assertTrue(listener.tryOpenFromSupport(viewer, "pack:case", root, null, true)); assertPlaque();
        // A furniture hitbox forwards its visual root; display entities cannot receive vanilla clicks.
        ItemDisplay display = mock(ItemDisplay.class); when(display.getItemStack()).thenReturn(piece);
        assertTrue(listener.tryOpenFromSupport(viewer, "pack:case", display, null, true)); assertPlaque();
        verify(display, never()).setItemStack(any());
        Shelf shelf = mock(Shelf.class); org.bukkit.inventory.ShelfInventory inventory = mock(org.bukkit.inventory.ShelfInventory.class);
        when(shelf.getInventory()).thenReturn(inventory); when(inventory.getContents()).thenReturn(new ItemStack[]{null, new ItemStack(Material.STONE), piece});
        Block block = mock(Block.class); when(block.getState()).thenReturn(shelf); when(block.getType()).thenReturn(Material.OAK_SHELF);
        assertTrue(listener.tryOpenFromSupport(viewer, "pack:case", null, block, true)); assertPlaque();
        when(inventory.getContents()).thenReturn(new ItemStack[]{null, new ItemStack(Material.STONE)});
        assertFalse(listener.tryOpenFromSupport(viewer, "pack:case", null, block, true));
        assertFalse(listener.tryOpenFromSupport(viewer, "pack:other", null, block, true));
    }

    @Test public void standingSneakAndUnlistedSupportsLeaveBlockAndStandUseVanilla() {
        // MockBukkit players start with no top inventory; give the viewer an ordinary window to compare with.
        viewer.openInventory(server.createInventory(null, 9));
        Lectern lectern = mock(Lectern.class); Block block = mock(Block.class); when(block.getType()).thenReturn(Material.LECTERN); when(block.getState()).thenReturn(lectern);
        var inventory = mock(org.bukkit.inventory.LecternInventory.class); when(lectern.getInventory()).thenReturn(inventory); when(inventory.getItem(0)).thenReturn(piece);
        PlayerInteractEvent standing = blockEvent(block, null); listener.onBlock(standing); assertVanilla(standing);
        viewer.setSneaking(true);
        Chest chest = mock(Chest.class); Block crate = mock(Block.class); when(crate.getType()).thenReturn(Material.CHEST); when(crate.getState()).thenReturn(chest);
        PlayerInteractEvent storage = blockEvent(crate, null); listener.onBlock(storage); assertVanilla(storage);
        ArmorStand stand = mock(ArmorStand.class); PlayerArmorStandManipulateEvent offhand = standEvent(stand, EquipmentSlot.OFF_HAND);
        listener.onStand(offhand); assertFalse(offhand.isCancelled());
        when(catalogs.museum()).thenReturn(new MuseumSettings(List.of(ItemRef.vanilla(Material.ITEM_FRAME))));
        PlayerArmorStandManipulateEvent unlisted = standEvent(stand, EquipmentSlot.HAND); listener.onStand(unlisted); assertFalse(unlisted.isCancelled());
        viewer.setSneaking(false); when(catalogs.museum()).thenReturn(MuseumSettings.defaults());
        PlayerArmorStandManipulateEvent swap = standEvent(stand, EquipmentSlot.HAND); listener.onStand(swap); assertFalse(swap.isCancelled());
        assertNull(viewer.nextMessage()); assertNull(viewer.getOpenInventory().getTopInventory().getHolder());
    }

    @Test public void sneakUseOnOtherEntitiesStaysVanillaEvenWhenTheyCarryAnExhibit() {
        viewer.setSneaking(true);
        Villager villager = mock(Villager.class); PlayerInteractEntityEvent trade = entityEvent(villager, EquipmentSlot.HAND);
        listener.onEntity(trade); assertFalse(trade.isCancelled());
        ItemDisplay display = mock(ItemDisplay.class); when(display.getItemStack()).thenReturn(piece);
        when(catalogs.museum()).thenReturn(new MuseumSettings(List.of(ItemRef.vanilla(Material.ITEM_FRAME))));
        PlayerInteractEntityEvent unlisted = entityEvent(display, EquipmentSlot.HAND); listener.onEntity(unlisted); assertFalse(unlisted.isCancelled());
        assertNull(viewer.nextMessage());
    }

    @Test public void listedVanillaSupportThatHoldsNoItemsNeverOpensAPlaque() {
        // An operator may list any block; only lecterns and shelves expose a displayed stack.
        viewer.setSneaking(true); when(catalogs.museum()).thenReturn(new MuseumSettings(List.of(ItemRef.vanilla(Material.CHEST))));
        Chest chest = mock(Chest.class); Block crate = mock(Block.class); when(crate.getType()).thenReturn(Material.CHEST); when(crate.getState()).thenReturn(chest);
        PlayerInteractEvent event = blockEvent(crate, new Vector(.5, .5, .5)); listener.onBlock(event); assertVanilla(event);
        assertFalse(listener.tryOpenFromSupport(viewer, null, null, crate, true));
        verifyNoInteractions(chest);
    }

    @Test public void furnitureMatchedByItsEntityOrPlacedBlockOpensWithoutAnEventId() {
        ItemRef furniture = new ItemRef(ItemRef.Kind.ITEMSADDER, "pack:case", "");
        when(catalogs.museum()).thenReturn(new MuseumSettings(List.of(ItemRef.vanilla(Material.LECTERN), furniture)));
        ItemMatcher matcher = mock(ItemMatcher.class); listener.setMatcher(matcher);
        ArmorStand root = mock(ArmorStand.class); EntityEquipment equipment = mock(EntityEquipment.class); when(root.getEquipment()).thenReturn(equipment); when(equipment.getItemInMainHand()).thenReturn(piece);
        // ItemsAdder hides furniture roots as armor stands; the vanilla ARMOR_STAND entry is not listed here.
        assertFalse(listener.tryOpenFromSupport(viewer, null, root, null, true));
        when(matcher.matchesEntity(root, furniture)).thenReturn(true);
        assertTrue(listener.tryOpenFromSupport(viewer, null, root, null, true)); assertPlaque();
        Shelf shelf = mock(Shelf.class); org.bukkit.inventory.ShelfInventory inventory = mock(org.bukkit.inventory.ShelfInventory.class);
        when(shelf.getInventory()).thenReturn(inventory); when(inventory.getContents()).thenReturn(new ItemStack[]{piece});
        Block placed = mock(Block.class); when(placed.getType()).thenReturn(Material.BARRIER); when(placed.getState()).thenReturn(shelf);
        when(matcher.matchesPlaced(placed, furniture)).thenReturn(true);
        assertTrue(listener.tryOpenFromSupport(viewer, null, null, placed, true)); assertPlaque();
    }

    @Test public void exhibitWhoseSiteTagWasStrippedExplainsTheMissingRecord() {
        viewer.setSneaking(true);
        ItemStack stripped = piece.clone(); ItemMeta meta = stripped.getItemMeta();
        meta.getPersistentDataContainer().remove(new NamespacedKey(plugin, "site_id")); stripped.setItemMeta(meta);
        assertTrue(recovered.isRecovered(stripped));
        ItemFrame frame = mock(ItemFrame.class); when(frame.getType()).thenReturn(EntityType.ITEM_FRAME); when(frame.getItem()).thenReturn(stripped);
        PlayerInteractEntityEvent event = entityEvent(frame, EquipmentSlot.HAND); listener.onEntity(event); assertTrue(event.isCancelled());
        assertEquals("That excavation record is missing.", viewer.nextMessage());
        verify(frame, never()).setItem(any());
    }

    private PlayerArmorStandManipulateEvent standEvent(ArmorStand stand, EquipmentSlot hand) { return standEvent(stand, hand, piece); }
    private PlayerArmorStandManipulateEvent standEvent(ArmorStand stand, EquipmentSlot hand, ItemStack worn) {
        return new PlayerArmorStandManipulateEvent(viewer, stand, new ItemStack(Material.AIR), worn, EquipmentSlot.FEET, hand);
    }
    private void assertNoPlaque() {
        // MockBukkit players start with no top inventory at all.
        Inventory top = viewer.getOpenInventory().getTopInventory();
        assertFalse(top != null && top.getHolder() instanceof CampFindBoard);
    }
    private void assertPlaque() {
        assertTrue(viewer.getOpenInventory().getTopInventory().getHolder() instanceof CampFindBoard);
        CampFindBoard board = (CampFindBoard) viewer.getOpenInventory().getTopInventory().getHolder();
        assertTrue(board.isMuseum()); assertEquals(site.getId(), board.siteId()); assertEquals(find.getId(), board.findId());
        assertEquals("Exhibited pot", ChatColor.stripColor(board.getInventory().getItem(4).getItemMeta().getDisplayName()));
        assertTrue(board.getInventory().getItem(4).getItemMeta().getLore().stream().noneMatch(line -> line.contains("cabinet")));
    }
    private PlayerInteractEntityEvent entityEvent(Entity entity, EquipmentSlot hand) { return new PlayerInteractEntityEvent(viewer, entity, hand); }
    private PlayerInteractEvent blockEvent(Block block, Vector at) { return blockEvent(block, at, Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND); }
    private PlayerInteractEvent blockEvent(Block block, Vector at, Action action, EquipmentSlot hand) {
        return new PlayerInteractEvent(viewer, action, null, block, BlockFace.SOUTH, hand, at);
    }
    /** Neither cancelled nor denied through either use result: Minecraft handles the click. */
    private static void assertVanilla(PlayerInteractEvent event) {
        assertFalse(event.isCancelled()); assertEquals(Event.Result.ALLOW, event.useInteractedBlock()); assertEquals(Event.Result.DEFAULT, event.useItemInHand());
    }
}
