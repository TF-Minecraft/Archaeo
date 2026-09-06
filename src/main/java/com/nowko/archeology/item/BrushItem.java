package com.nowko.archeology.item;

import org.bukkit.inventory.ItemStack;

/**
 * Field brush used to lift exposed finds. Matches {@code excavation.brush.item}.
 */
public class BrushItem {
    private ItemRef ref;
    private ItemMatcher matcher = ItemMatcher.vanillaOnly();

    /**
     * @param ref {@code excavation.brush.item}
     */
    public BrushItem(ItemRef ref) {
        this.ref = ref;
    }

    /**
     * @param matcher ItemsAdder / MMOItems lookup
     */
    public void setMatcher(ItemMatcher matcher) {
        this.matcher = matcher == null ? ItemMatcher.vanillaOnly() : matcher;
    }

    /**
     * @param ref {@code excavation.brush.item} after reload
     */
    public void update(ItemRef ref) {
        this.ref = ref;
    }

    /**
     * @return the configured brush (pack template or vanilla material, unchanged)
     */
    public ItemStack create() {
        return matcher.create(ref);
    }

    /**
     * @param stack main-hand stack, or {@code null}
     * @return whether this stack is the configured recovery tool
     */
    public boolean isBrush(ItemStack stack) {
        return matcher.matches(stack, ref);
    }
}
