package com.nowko.archeology.item;

import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.logging.Logger;

/**
 * Configured field sheet and pencil ({@code sketch.paper}, {@code sketch.pencil}).
 * Archaeo lore is the how-to; pack lore is replaced so the gesture stays readable.
 */
public class SketchSupplies {
    private static final List<String> PAPER_LORE = List.of(
            ChatColor.GRAY + "Click this onto a field pencil,",
            ChatColor.GRAY + "or hold it and right-click with the",
            ChatColor.GRAY + "pencil in your other hand.",
            ChatColor.DARK_GRAY + "The pencil is not used up.");
    private static final List<String> PENCIL_LORE = List.of(
            ChatColor.GRAY + "Click a field sheet onto this,",
            ChatColor.GRAY + "or hold the sheet and right-click",
            ChatColor.GRAY + "with this in your other hand.",
            ChatColor.DARK_GRAY + "This tool is not used up.");

    private final JavaPlugin plugin;
    private ItemRef paper;
    private ItemRef pencil;
    private ItemMatcher matcher = ItemMatcher.vanillaOnly();

    /**
     * @param plugin logger owner
     * @param paper {@code sketch.paper}
     * @param pencil {@code sketch.pencil}
     */
    public SketchSupplies(JavaPlugin plugin, ItemRef paper, ItemRef pencil) {
        this.plugin = plugin;
        this.paper = paper;
        this.pencil = pencil;
    }

    /**
     * @param matcher ItemsAdder / MMOItems lookup
     */
    public void setMatcher(ItemMatcher matcher) {
        this.matcher = matcher == null ? ItemMatcher.vanillaOnly() : matcher;
    }

    /**
     * @param paper {@code sketch.paper} after reload
     * @param pencil {@code sketch.pencil} after reload
     */
    public void update(ItemRef paper, ItemRef pencil) {
        this.paper = paper;
        this.pencil = pencil;
        if (paper != null && paper.equals(pencil)) {
            logger().warning("sketch.paper and sketch.pencil are the same item; combining them will be ambiguous.");
        }
    }

    /**
     * @return configured sheet with Archaeo instructions
     */
    public ItemStack createPaper() {
        return stamped(matcher.create(paper), true);
    }

    /**
     * @return configured pencil with Archaeo instructions
     */
    public ItemStack createPencil() {
        return stamped(matcher.create(pencil), false);
    }

    /**
     * @param stack candidate, or {@code null}
     * @return whether this is the configured sheet
     */
    public boolean isPaper(ItemStack stack) {
        return matcher.matches(stack, paper);
    }

    /**
     * @param stack candidate, or {@code null}
     * @return whether this is the configured pencil
     */
    public boolean isPencil(ItemStack stack) {
        return matcher.matches(stack, pencil);
    }

    /**
     * Rewrites Archaeo how-to lore on a live sheet or pencil. Pack plugins may stamp lore
     * again later; call this when the player holds, picks up, or opens the bag.
     *
     * @param stack possible kit item
     * @return whether lore was written
     */
    public boolean stampInstructions(ItemStack stack) {
        if (isPaper(stack)) {
            stamped(stack, true);
            return true;
        }
        if (isPencil(stack)) {
            stamped(stack, false);
            return true;
        }
        return false;
    }

    /**
     * @param inventory stacks to scan, including {@code null} holes
     */
    public void stampAll(ItemStack[] inventory) {
        if (inventory == null) {
            return;
        }
        for (ItemStack stack : inventory) {
            stampInstructions(stack);
        }
    }

    /**
     * @param stack pack or vanilla template
     * @param sheet whether this is the paper
     * @return the same stack, or air if create failed
     */
    private ItemStack stamped(ItemStack stack, boolean sheet) {
        if (stack == null || stack.getType().isAir()) {
            return stack;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        if (!meta.hasDisplayName()) {
            meta.setDisplayName(ChatColor.WHITE + (sheet ? "Field sheet" : "Field pencil"));
        }
        meta.setLore(sheet ? PAPER_LORE : PENCIL_LORE);
        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * @return plugin log
     */
    private Logger logger() {
        return plugin.getLogger();
    }
}
