package net.tfminecraft.archaeo.item;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.Assert.*;

public class ToolWearTest {
    private PlayerMock player;
    @Before public void setUp() { MockBukkit.mock(); player = MockBukkit.getMock().addPlayer(); }
    @After public void tearDown() { MockBukkit.unmock(); }

    @Test
    public void completedWorkSpendsDurabilityAndBreaksTheHeldToolAtItsLimit() {
        ItemStack tool = new ItemStack(Material.IRON_SHOVEL);
        player.getInventory().setItemInMainHand(tool);
        tool = player.getInventory().getItemInMainHand();
        assertFalse(ToolWear.spend(player, tool, 3, true));
        assertEquals(3, damage(tool));
        assertTrue(ToolWear.spend(player, tool, Material.IRON_SHOVEL.getMaxDurability() - 3, false));
        assertTrue(player.getInventory().getItemInMainHand().getType().isAir());
    }

    @Test
    public void freeActionsNondurableItemsAndUnbreakableToolsArePreserved() {
        assertFalse(ToolWear.spend(player, null, 1, false));
        ItemStack stone = new ItemStack(Material.STONE);
        assertFalse(ToolWear.spend(player, stone, 1, false));
        assertEquals(new ItemStack(Material.STONE), stone);
        ItemStack tool = new ItemStack(Material.DIAMOND_PICKAXE);
        assertFalse(ToolWear.spend(player, tool, 0, false));
        assertFalse(ToolWear.spend(player, tool, -10, false));
        assertEquals(0, damage(tool));
        Damageable meta = (Damageable) tool.getItemMeta(); meta.setUnbreakable(true); tool.setItemMeta(meta);
        assertFalse(ToolWear.spend(player, tool, 100, true));
        assertEquals(0, damage(tool));
    }

    @Test
    public void unbreakingNeverCostsMoreThanTheWorkAndCanBeDisabledByConfiguration() {
        ItemStack tool = new ItemStack(Material.DIAMOND_PICKAXE);
        tool.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
        assertFalse(ToolWear.spend(player, tool, 100, true));
        int rolled = damage(tool);
        assertTrue(rolled >= 0 && rolled <= 100);
        assertFalse(ToolWear.spend(player, tool, 100, false));
        assertEquals(rolled + 100, damage(tool));
        assertEquals(3, tool.getEnchantmentLevel(Enchantment.UNBREAKING));
    }

    @Test
    public void unbreakingCanAbsorbAWholeSinglePointChargeLeavingTheToolUntouched() {
        ItemStack tool = new ItemStack(Material.IRON_SHOVEL);
        tool.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
        player.getInventory().setItemInMainHand(tool);
        tool = player.getInventory().getItemInMainHand();
        int charges = 200, absorbed = 0;
        for (int i = 0; i < charges; i++) {
            int before = damage(tool);
            assertFalse(ToolWear.spend(player, tool, 1, true));
            if (damage(tool) == before) absorbed++;
            else assertEquals(before + 1, damage(tool));
        }
        // Each point survives Unbreaking III with chance 1/4, so both outcomes occur in 200 charges.
        assertTrue(absorbed > 0 && absorbed < charges);
        assertEquals(charges - absorbed, damage(tool));
        assertEquals(Material.IRON_SHOVEL, player.getInventory().getItemInMainHand().getType());
    }

    private static int damage(ItemStack stack) { return ((Damageable) stack.getItemMeta()).getDamage(); }
}
