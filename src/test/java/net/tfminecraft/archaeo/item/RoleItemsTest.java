package net.tfminecraft.archaeo.item;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class RoleItemsTest {
    @Before public void setUp() { MockBukkit.mock(); }
    @After public void tearDown() { MockBukkit.unmock(); }

    @Test
    public void roleFactoriesRecognizeConfiguredItemsAndApplyReloads() {
        ItemRef first = ItemRef.vanilla(Material.COMPASS);
        ItemRef second = ItemRef.vanilla(Material.BRUSH);
        TrackerItem tracker = new TrackerItem(first);
        ProspectItem prospect = new ProspectItem(first);
        EstablishItem establish = new EstablishItem(first);
        BrushItem brush = new BrushItem(first);
        assertEquals(Material.COMPASS, tracker.create().getType());
        assertEquals(Material.COMPASS, prospect.create().getType());
        assertEquals(Material.COMPASS, establish.create().getType());
        assertEquals(Material.COMPASS, brush.create().getType());
        assertTrue(tracker.isTracker(new ItemStack(Material.COMPASS)));
        assertTrue(prospect.isProspect(new ItemStack(Material.COMPASS)));
        assertTrue(establish.isEstablish(new ItemStack(Material.COMPASS)));
        assertTrue(brush.isBrush(new ItemStack(Material.COMPASS)));
        assertFalse(tracker.isTracker(null));
        assertFalse(prospect.isProspect(null));
        assertFalse(establish.isEstablish(null));
        assertFalse(brush.isBrush(null));
        tracker.update(second); prospect.update(second); establish.update(second); brush.update(second);
        tracker.setMatcher(null); prospect.setMatcher(null); establish.setMatcher(null); brush.setMatcher(null);
        assertEquals(Material.BRUSH, tracker.create().getType());
        assertEquals(Material.BRUSH, prospect.create().getType());
        assertEquals(Material.BRUSH, establish.create().getType());
        assertEquals(Material.BRUSH, brush.create().getType());
        ItemMatcher matcher = mock(ItemMatcher.class);
        ItemStack custom = new ItemStack(Material.DIAMOND);
        when(matcher.create(second)).thenReturn(custom);
        when(matcher.matches(custom, second)).thenReturn(true);
        tracker.setMatcher(matcher); prospect.setMatcher(matcher); establish.setMatcher(matcher); brush.setMatcher(matcher);
        assertSame(custom, tracker.create()); assertSame(custom, prospect.create());
        assertSame(custom, establish.create()); assertSame(custom, brush.create());
        assertTrue(tracker.isTracker(custom)); assertTrue(prospect.isProspect(custom));
        assertTrue(establish.isEstablish(custom)); assertTrue(brush.isBrush(custom));
    }

    @Test
    public void vanillaMatcherChecksHandsBlocksAndUnavailablePackItems() {
        ItemMatcher matcher = ItemMatcher.vanillaOnly();
        ItemRef stone = ItemRef.vanilla(Material.STONE);
        ItemRef ia = new ItemRef(ItemRef.Kind.ITEMSADDER, "pack:pot", "");
        ItemRef mi = new ItemRef(ItemRef.Kind.MMOITEMS, "TOOL", "pick");
        assertTrue(matcher.matches(null, ItemRef.air()));
        assertTrue(matcher.matches(new ItemStack(Material.AIR), ItemRef.air()));
        assertFalse(matcher.matches(new ItemStack(Material.STONE), ItemRef.air()));
        assertTrue(matcher.matches(new ItemStack(Material.STONE), stone));
        assertFalse(matcher.matches(new ItemStack(Material.BRICK), stone));
        assertFalse(matcher.matches(new ItemStack(Material.STONE), ia));
        assertFalse(matcher.matches(new ItemStack(Material.STONE), mi));
        assertFalse(matcher.isCustom(null));
        assertEquals(Material.AIR, matcher.create(ia).getType());
        assertEquals(Material.AIR, matcher.create(mi).getType());
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(Material.STONE);
        assertTrue(matcher.matchesPlaced(block, stone));
        assertFalse(matcher.matchesPlaced(block, ItemRef.air()));
        assertFalse(matcher.matchesPlaced(null, stone));
        assertFalse(matcher.matchesPlaced(block, null));
        assertFalse(matcher.matchesPlaced(block, ia));
        assertFalse(matcher.matchesPlaced(block, mi));
        Entity entity = mock(Entity.class);
        assertFalse(matcher.matchesEntity(null, ia));
        assertFalse(matcher.matchesEntity(entity, null));
        assertFalse(matcher.matchesEntity(entity, stone));
        assertFalse(matcher.matchesEntity(entity, ia));
        assertTrue(matcher.matchesNamespacedId("PACK:POT", ia));
        assertFalse(matcher.matchesNamespacedId(null, ia));
        assertFalse(matcher.matchesNamespacedId("pack:other", ia));
        assertFalse(matcher.matchesNamespacedId("pack:pot", null));
        assertFalse(matcher.matchesNamespacedId("pack:pot", stone));
    }
}
