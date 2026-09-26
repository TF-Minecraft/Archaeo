package net.tfminecraft.archaeo.sketch;

import net.tfminecraft.archaeo.config.CatalogRegistry;
import net.tfminecraft.archaeo.config.PickSettings;
import net.tfminecraft.archaeo.item.RecoveredFindItem;
import net.tfminecraft.archaeo.model.*;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.junit.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SketchCabinetTest {
    private PlayerMock player;
    private SketchCabinet cabinet;
    private RecoveredFindItem recovered;
    private Site site;
    private BuriedFind find;

    @Before public void setUp() {
        MockBukkit.mock(); player = MockBukkit.getMock().addPlayer();
        recovered = new RecoveredFindItem(MockBukkit.createMockPlugin());
        CatalogRegistry catalogs = mock(CatalogRegistry.class); when(catalogs.pick()).thenReturn(PickSettings.defaults());
        site = new Site(); site.setId(UUID.randomUUID()); site.setName("River camp");
        find = new BuriedFind(); find.setId(UUID.randomUUID()); find.setState(FindState.RECOVERED);
        find.setLabCleaned(true); find.setStratumId("I"); site.getFinds().add(find);
        player.getInventory().setItemInMainHand(new ItemStack(Material.BRICK));
        cabinet = new SketchCabinet(); cabinet.open(player, site, find, null, recovered, catalogs);
    }
    @After public void tearDown() { MockBukkit.unmock(); }

    @Test public void registrationWindowUsesOnlyDisplayCopiesAndExplainsWhereToPlaceDrawing() {
        assertEquals(InventoryType.FURNACE, cabinet.getInventory().getType());
        assertSame(cabinet.getInventory(), player.getOpenInventory().getTopInventory());
        assertEquals(site.getId(), cabinet.siteId()); assertEquals(find.getId(), cabinet.findId());
        assertNull(cabinet.getInventory().getItem(SketchCabinet.SLOT_SKETCH));
        assertNull(recovered.findIdOf(cabinet.getInventory().getItem(SketchCabinet.SLOT_FIND)));
        ItemStack control = cabinet.getInventory().getItem(SketchCabinet.SLOT_REGISTER);
        assertEquals("Register", ChatColor.stripColor(control.getItemMeta().getDisplayName()));
        assertTrue(String.join(" ", control.getItemMeta().getLore()).contains("top slot"));
        assertFalse(SketchCabinet.locked(SketchCabinet.SLOT_SKETCH));
        assertTrue(SketchCabinet.locked(SketchCabinet.SLOT_FIND)); assertTrue(SketchCabinet.locked(SketchCabinet.SLOT_REGISTER));
        assertEquals(Material.BRICK, player.getInventory().getItemInMainHand().getType());
    }

    @Test public void closingReturnsDrawingExactlyOnceAndNeverReturnsDisplayItems() {
        ItemStack drawing = drawing(); cabinet.getInventory().setItem(SketchCabinet.SLOT_SKETCH, drawing);
        cabinet.returnContents(player); cabinet.returnContents(player);
        assertEquals(1, player.getInventory().all(Material.FILLED_MAP).size());
        assertEquals(drawing, player.getInventory().all(Material.FILLED_MAP).values().iterator().next());
        assertEquals(1, player.getInventory().all(Material.BRICK).size());
        assertFalse(player.getInventory().contains(Material.WRITABLE_BOOK));
        assertTrue(cabinet.getInventory().isEmpty());
    }

    @Test public void closingWithFullInventoryDropsDrawingAndEmptyWindowCreatesNothing() {
        for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, new ItemStack(Material.STONE, 64));
        // MockBukkit also scans equipment when adding items; use a valid fully equipped inventory.
        player.getInventory().setHelmet(new ItemStack(Material.IRON_HELMET));
        player.getInventory().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
        player.getInventory().setLeggings(new ItemStack(Material.IRON_LEGGINGS));
        player.getInventory().setBoots(new ItemStack(Material.IRON_BOOTS));
        player.getInventory().setItemInOffHand(new ItemStack(Material.TORCH, 64));
        ItemStack drawing = drawing(); cabinet.getInventory().setItem(SketchCabinet.SLOT_SKETCH, drawing);
        PlayerMock receiver = spy(player); var fullStorage = spy(player.getInventory());
        doReturn(fullStorage).when(receiver).getInventory();
        // Bukkit addItem uses storage only; MockBukkit also searches two phantom player slots.
        doAnswer(call -> {
            var overflow = new java.util.HashMap<Integer, ItemStack>();
            overflow.put(0, call.getArgument(0)); return overflow;
        }).when(fullStorage).addItem(any(ItemStack.class));
        cabinet.returnContents(receiver); cabinet.returnContents(receiver);
        var dropped = player.getWorld().getEntitiesByClass(Item.class);
        assertEquals(1, dropped.size()); assertEquals(drawing, dropped.iterator().next().getItemStack());
        assertTrue(cabinet.getInventory().isEmpty());
    }

    private ItemStack drawing() {
        ItemStack drawing = new ItemStack(Material.FILLED_MAP); var meta = drawing.getItemMeta();
        meta.setDisplayName("My signed field drawing"); drawing.setItemMeta(meta); return drawing;
    }
}
