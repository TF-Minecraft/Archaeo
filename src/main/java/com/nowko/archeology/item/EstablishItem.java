package com.nowko.archeology.item;

import org.bukkit.inventory.ItemStack;

/**
 * Builds and recognizes the establishment kit ({@code establish.item}).
 * Any matching stack works, including a crafted vanilla item of that type.
 */
public class EstablishItem {
    private ItemRef ref;
    private ItemMatcher matcher = ItemMatcher.vanillaOnly();

    /**
     * @param ref {@code establish.item}
     */
    public EstablishItem(ItemRef ref) {
        this.ref = ref;
    }

    /**
     * @param matcher ItemsAdder / MMOItems lookup
     */
    public void setMatcher(ItemMatcher matcher) {
        this.matcher = matcher == null ? ItemMatcher.vanillaOnly() : matcher;
    }

    /**
     * @param ref {@code establish.item} after reload
     */
    public void update(ItemRef ref) {
        this.ref = ref;
    }

    /**
     * @return the configured kit (pack template or vanilla material, unchanged)
     */
    public ItemStack create() {
        return matcher.create(ref);
    }

    /**
     * @param stack item in a hand, or {@code null}
     * @return whether this stack is the configured establishment kit
     */
    public boolean isEstablish(ItemStack stack) {
        return matcher.matches(stack, ref);
    }
}
