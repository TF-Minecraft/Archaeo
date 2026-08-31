package com.nowko.archeology.excavation;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Reads vanilla destroy progress without letting the client keep that speed.
 * {@link Block#getBreakSpeed(Player)} is the tick delta toward {@code 1.0} (a vanilla break).
 * The Hand Pick zeros {@link Attribute#BLOCK_BREAK_SPEED} so cracks never draw; this strips
 * that lock for the duration of one call so the clock still matches an unmodified pickaxe.
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
     * @return {@link Block#getBreakSpeed(Player)} with the lock removed
     */
    public static float tickProgress(Player player, Block block, NamespacedKey lockKey) {
        AttributeInstance speed = player.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        AttributeModifier lock = takeLock(speed, lockKey);
        try {
            return block.getBreakSpeed(player);
        } finally {
            if (speed != null && lock != null) {
                speed.addModifier(lock);
            }
        }
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
