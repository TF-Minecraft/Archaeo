package net.tfminecraft.archaeo.item;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SketchSuppliesTest {
    private ServerMock server;
    private JavaPlugin plugin;
    private SketchSupplies supplies;
    private final ItemRef paper = ItemRef.vanilla(Material.PAPER);
    private final ItemRef pencil = ItemRef.vanilla(Material.FEATHER);

    @Before public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        supplies = new SketchSupplies(plugin, paper, pencil, 2);
    }
    @After public void tearDown() { MockBukkit.unmock(); }

    @Test
    public void createdKitsHaveInstructionsWhileOrdinaryItemsKeepTheirTooltip() {
        ItemStack sheet = supplies.createPaper();
        ItemStack pen = supplies.createPencil();
        assertTrue(supplies.isPaper(sheet));
        assertTrue(supplies.isPencil(pen));
        assertEquals(3, sheet.getItemMeta().getLore().size());
        assertEquals(3, pen.getItemMeta().getLore().size());
        assertEquals(2, ((Damageable) pen.getItemMeta()).getMaxDamage());
        assertEquals(1, pen.getItemMeta().getMaxStackSize());
        assertFalse(supplies.stampInstructions(null));
        assertFalse(supplies.stampInstructions(new ItemStack(Material.PAPER)));
        ItemStack ordinary = new ItemStack(Material.FEATHER);
        ItemMeta ordinaryMeta = ordinary.getItemMeta();
        ordinaryMeta.setLore(List.of("Personal notes"));
        ordinary.setItemMeta(ordinaryMeta);
        assertFalse(supplies.stampInstructions(ordinary));
        assertEquals(List.of("Personal notes"), ordinary.getItemMeta().getLore());
        ItemMeta meta = sheet.getItemMeta();
        meta.setDisplayName("Custom name");
        meta.setLore(null);
        sheet.setItemMeta(meta);
        assertTrue(supplies.stampInstructions(sheet));
        assertEquals("Custom name", sheet.getItemMeta().getDisplayName());
        assertEquals(3, sheet.getItemMeta().getLore().size());
        assertTrue(supplies.stampInstructions(pen));
        supplies.stampAll(null);
        supplies.stampAll(new ItemStack[]{null, ordinary, sheet, pen});
    }

    @Test
    public void finitePencilsWearAndBreakWithoutRenamingThem() {
        ItemStack pen = supplies.createPencil();
        assertFalse(supplies.isSpent(pen));
        assertFalse(supplies.wear(null, pen));
        assertEquals(1, ((Damageable) pen.getItemMeta()).getDamage());
        assertTrue(supplies.wear(server.addPlayer(), pen));
        assertEquals(Material.AIR, pen.getType());
        ItemStack spent = supplies.createPencil();
        Damageable meta = (Damageable) spent.getItemMeta();
        meta.setDamage(2);
        spent.setItemMeta(meta);
        assertTrue(supplies.isSpent(spent));
        assertTrue(supplies.wear(null, spent));
        assertFalse(supplies.isSpent(null));
        assertFalse(supplies.isSpent(new ItemStack(Material.AIR)));
        assertFalse(supplies.wear(null, null));
        assertFalse(supplies.wear(null, new ItemStack(Material.AIR)));
    }

    @Test
    public void unbreakableAndInfinitePencilsDoNotWearAndReloadResizesBars() {
        ItemStack pen = supplies.createPencil();
        Damageable meta = (Damageable) pen.getItemMeta();
        meta.setUnbreakable(true);
        pen.setItemMeta(meta);
        assertFalse(supplies.isSpent(pen));
        assertFalse(supplies.wear(null, pen));
        assertEquals(0, ((Damageable) pen.getItemMeta()).getDamage());
        meta.setUnbreakable(false);
        meta.setDamage(2);
        pen.setItemMeta(meta);
        supplies.update(paper, pencil, 1);
        assertTrue(supplies.stampInstructions(pen));
        assertEquals(1, ((Damageable) pen.getItemMeta()).getDamage());
        supplies.update(paper, pencil, -1);
        assertTrue(supplies.stampInstructions(pen));
        assertFalse(((Damageable) pen.getItemMeta()).hasMaxDamage());
        assertFalse(pen.getItemMeta().hasMaxStackSize());
        assertEquals(0, ((Damageable) pen.getItemMeta()).getDamage());
        assertFalse(supplies.wear(null, pen));
        assertFalse(supplies.isSpent(pen));
        assertFalse(supplies.createPencil().getItemMeta().hasMaxStackSize());
        supplies.update(paper, paper, 0);
        assertTrue(supplies.isPencil(new ItemStack(Material.PAPER)));
    }

    @Test
    public void ordinaryFeatherWorksAsAPencilAndGainsTheConfiguredBarOnFirstUse() {
        ItemStack feather = new ItemStack(Material.FEATHER);
        // MockBukkit clamps a first damage value to the material's vanilla durability (0 for feathers),
        // ignoring max_damage as Paper honours it; an explicit zero lets the mock track wear like Paper.
        Damageable seed = (Damageable) feather.getItemMeta(); seed.setDamage(0); feather.setItemMeta(seed);
        assertTrue(supplies.isPencil(feather)); assertFalse(supplies.isSpent(feather));
        assertFalse(supplies.wear(null, feather));
        assertEquals(2, ((Damageable) feather.getItemMeta()).getMaxDamage()); assertEquals(1, ((Damageable) feather.getItemMeta()).getDamage());
        assertFalse(feather.getItemMeta().hasLore());
    }

    @Test
    public void unbreakingSavesSomePencilUsesButNotAll() {
        supplies.update(paper, pencil, 1000);
        ItemStack pen = supplies.createPencil(); pen.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.UNBREAKING, 3);
        for (int use = 0; use < 200; use++) supplies.wear(null, pen);
        int damage = ((Damageable) pen.getItemMeta()).getDamage();
        // Unbreaking III keeps a use with probability 3/4; 200 uses all kept or all spent is ~1e-25.
        assertTrue(damage > 0); assertTrue(damage < 200); assertFalse(supplies.isSpent(pen));
    }

    @Test
    public void packPencilGivenByArchaeoKeepsThePackTooltipWhenRestamped() {
        ItemRef packPencil = new ItemRef(ItemRef.Kind.ITEMSADDER, "pack:pencil", "");
        supplies.update(paper, packPencil, 0);
        ItemMatcher matcher = mock(ItemMatcher.class); ItemStack custom = new ItemStack(Material.STICK);
        ItemMeta meta = custom.getItemMeta(); meta.setLore(List.of("Pack pencil")); custom.setItemMeta(meta);
        when(matcher.create(packPencil)).thenReturn(custom); supplies.setMatcher(matcher);
        ItemStack given = supplies.createPencil(); when(matcher.matches(given, packPencil)).thenReturn(true);
        assertFalse(supplies.stampInstructions(given)); assertEquals(List.of("Pack pencil"), given.getItemMeta().getLore());
        // Switching sketch.pencil back to a vanilla stick must not overwrite pencils already handed out from the pack.
        ItemRef vanillaStick = ItemRef.vanilla(Material.STICK); supplies.update(paper, vanillaStick, 0);
        when(matcher.matches(given, vanillaStick)).thenReturn(true); when(matcher.isCustom(given)).thenReturn(true);
        assertFalse(supplies.stampInstructions(given)); assertEquals(List.of("Pack pencil"), given.getItemMeta().getLore());
    }

    @Test
    public void packItemsKeepNamesAndLoreAndMissingTemplatesStayMissing() {
        ItemRef customRef = new ItemRef(ItemRef.Kind.ITEMSADDER, "pack:paper", "");
        supplies.update(customRef, customRef, 0);
        ItemMatcher matcher = mock(ItemMatcher.class);
        ItemStack custom = new ItemStack(Material.PAPER);
        ItemMeta meta = custom.getItemMeta();
        meta.setDisplayName("Pack name");
        meta.setLore(List.of("Pack instructions"));
        custom.setItemMeta(meta);
        when(matcher.create(customRef)).thenReturn(custom);
        supplies.setMatcher(matcher);
        assertEquals("Pack name", supplies.createPaper().getItemMeta().getDisplayName());
        assertEquals(List.of("Pack instructions"), custom.getItemMeta().getLore());
        when(matcher.matches(custom, customRef)).thenReturn(true);
        assertFalse(supplies.stampInstructions(custom));
        when(matcher.create(customRef)).thenReturn(null);
        assertNull(supplies.createPaper());
        when(matcher.create(customRef)).thenReturn(new ItemStack(Material.AIR));
        assertEquals(Material.AIR, supplies.createPencil().getType());
        supplies.setMatcher(null);
        supplies.update(paper, pencil, 0);
        assertEquals(Material.PAPER, supplies.createPaper().getType());
    }
}
