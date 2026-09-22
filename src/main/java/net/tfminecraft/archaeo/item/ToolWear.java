package net.tfminecraft.archaeo.item;

import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Random;

/**
 * Spends durability on the tool in a digger's main hand.
 *
 * <p>The excavation freezes vanilla mining to decide the release itself, so a shovel could work a
 * whole campaign and come out untouched. This puts the wear back under the rules an admin already
 * expects from vanilla gear, so the excavation is not a loophole: unbreakable tools never suffer,
 * Unbreaking rolls every point, and a spent tool snaps with its sound.
 */
public final class ToolWear {
    private static final Random RANDOM = new Random();

    private ToolWear() {
    }

    /**
     * Charges the tool for work already done. Silently does nothing for bare hands or for items
     * with no durability, which is what the packaged hand profile uses.
     *
     * @param player worker whose main hand pays the cost
     * @param stack main-hand tool, possibly empty
     * @param points durability to spend; {@code 0} or less leaves the tool alone
     * @param unbreaking whether the Unbreaking enchantment may absorb points
     * @return whether the tool broke on this call
     */
    public static boolean spend(Player player, ItemStack stack, int points, boolean unbreaking) {
        if (points <= 0 || stack == null || stack.getType().getMaxDurability() <= 0) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof Damageable damageable) || meta.isUnbreakable()) {
            return false;
        }
        int applied = unbreaking ? afterUnbreaking(stack, points) : points;
        if (applied <= 0) {
            return false;
        }
        damageable.setDamage(damageable.getDamage() + applied);
        stack.setItemMeta(meta);
        if (damageable.getDamage() < stack.getType().getMaxDurability()) {
            return false;
        }
        player.getInventory().setItemInMainHand(null);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 1f, 1f);
        return true;
    }

    /**
     * Rolls each point the way vanilla does, so an Unbreaking III shovel lasts about four times
     * longer in the cut than a plain one.
     *
     * @param stack tool being charged
     * @param points durability the work would cost without enchantments
     * @return points that survive the rolls
     */
    private static int afterUnbreaking(ItemStack stack, int points) {
        int level = stack.getEnchantmentLevel(Enchantment.UNBREAKING);
        if (level <= 0) {
            return points;
        }
        int applied = 0;
        for (int i = 0; i < points; i++) {
            if (RANDOM.nextInt(level + 1) == 0) {
                applied++;
            }
        }
        return applied;
    }
}
