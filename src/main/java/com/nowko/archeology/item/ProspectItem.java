package com.nowko.archeology.item;

import org.bukkit.inventory.ItemStack;

/**
 * Builds and recognizes the prospecting kit ({@code prospect.item}).
 * Any matching stack works, including a crafted vanilla item of that type.
 */
public class ProspectItem {
    private ItemRef ref;
    private ItemMatcher matcher = ItemMatcher.vanillaOnly();

    /**
     * @param ref {@code prospect.item}
     */
    public ProspectItem(ItemRef ref) {
        this.ref = ref;
    }

    /**
     * @param matcher ItemsAdder / MMOItems lookup
     */
    public void setMatcher(ItemMatcher matcher) {
        this.matcher = matcher == null ? ItemMatcher.vanillaOnly() : matcher;
    }

    /**
     * @param ref {@code prospect.item} after reload
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
     * @param stack item in hand, or {@code null}
     * @return whether this stack is the configured prospecting kit
     */
    public boolean isProspect(ItemStack stack) {
        return matcher.matches(stack, ref);
    }
}
