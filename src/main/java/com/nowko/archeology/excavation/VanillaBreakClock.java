package com.nowko.archeology.excavation;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.ToolComponent;

/**
 * Reads vanilla destroy progress without letting the client keep that speed.
 * {@link Block#getBreakSpeed(Player)} is the tick delta toward {@code 1.0} (a vanilla break).
 * The Hand Pick zeros {@link Attribute#BLOCK_BREAK_SPEED} so cracks never draw; this strips
 * that lock for the duration of one call so the clock still matches the live tool (and any
 * MMOItems / ItemsAdder speed already on the stack or player). YAML may override the tool
 * default mining speed for this sample only; the item is restored before the method returns.
 */
public final class VanillaBreakClock {
    private VanillaBreakClock() {
    }

    /**
     * How far vanilla mining would have advanced this tick, from {@code 0} to {@code 1+} (instant).
     *
     * @param player miner whose Hand Pick lock may be present
     * @param block cell being held
     * @param lockKey modifier Archaeo added to {@link Attribute#BLOCK_BREAK_SPEED}
     * @param miningSpeed tool {@code defaultMiningSpeed} for this sample, or {@code null} to keep the stack
     * @param miningSpeedMultiplier extra scale after {@code getBreakSpeed}, or {@code null} for {@code 1}
     * @return {@link Block#getBreakSpeed(Player)} with the lock removed and optional YAML scale applied
     */
    public static float tickProgress(
            Player player,
            Block block,
            NamespacedKey lockKey,
            Float miningSpeed,
            Float miningSpeedMultiplier
    ) {
        AttributeInstance speed = player.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        AttributeModifier lock = takeLock(speed, lockKey);
        ItemStack held = player.getInventory().getItemInMainHand();
        ItemMeta restored = snapshotTool(held, miningSpeed);
        try {
            float step = block.getBreakSpeed(player);
            if (miningSpeedMultiplier != null) {
                step *= miningSpeedMultiplier;
            }
            return step;
        } finally {
            restoreTool(held, restored);
            if (speed != null && lock != null) {
                speed.addModifier(lock);
            }
        }
    }

    /**
     * Writes {@code miningSpeed} onto the live stack for one {@code getBreakSpeed} call.
     *
     * @param held main-hand stack
     * @param miningSpeed YAML default mining speed, or {@code null}
     * @return meta to put back, or {@code null} when the stack was not patched
     */
    private static ItemMeta snapshotTool(ItemStack held, Float miningSpeed) {
        if (miningSpeed == null || held == null || held.getType().isAir()) {
            return null;
        }
        ItemMeta current = held.getItemMeta();
        if (current == null) {
            return null;
        }
        ItemMeta backup = current.clone();
        ToolComponent tool = current.getTool();
        tool.setDefaultMiningSpeed(miningSpeed);
        current.setTool(tool);
        held.setItemMeta(current);
        return backup;
    }

    /**
     * @param held main-hand stack that may have been patched
     * @param restored meta from {@link #snapshotTool}, or {@code null}
     */
    private static void restoreTool(ItemStack held, ItemMeta restored) {
        if (held == null || restored == null) {
            return;
        }
        held.setItemMeta(restored);
    }

    /**
     * @param speed player instance, or {@code null}
     * @param lockKey Archaeo modifier
     * @return the lock if it was present (already removed)
     */
    private static AttributeModifier takeLock(AttributeInstance speed, NamespacedKey lockKey) {
        if (speed == null) {
            return null;
        }
        for (AttributeModifier modifier : speed.getModifiers()) {
            if (lockKey.equals(modifier.getKey())) {
                speed.removeModifier(modifier);
                return modifier;
            }
        }
        return null;
    }
}
