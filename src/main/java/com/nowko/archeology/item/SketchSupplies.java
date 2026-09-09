package com.nowko.archeology.item;

import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

/**
 * Configured field sheet and pencil ({@code sketch.paper}, {@code sketch.pencil}).
 * Archaeo lore is the how-to; pack lore is replaced so the gesture stays readable.
 * When {@code sketch.pencil-uses} is positive, the pencil shows a vanilla durability bar
 * even if the material (a feather) has none of its own.
 */
public class SketchSupplies {
    private static final Random RANDOM = new Random();
    private static final List<String> PAPER_LORE = List.of(
            ChatColor.GRAY + "Click this onto a field pencil,",
            ChatColor.GRAY + "or hold it and right-click with the",
            ChatColor.GRAY + "pencil in your other hand.");
    private static final List<String> PENCIL_LORE = List.of(
            ChatColor.GRAY + "Click a field sheet onto this,",
            ChatColor.GRAY + "or hold the sheet and right-click",
            ChatColor.GRAY + "with this in your other hand.");

    private final JavaPlugin plugin;
    private ItemRef paper;
    private ItemRef pencil;
    private int pencilUses;
    private ItemMatcher matcher = ItemMatcher.vanillaOnly();

    /**
     * @param plugin logger owner
     * @param paper {@code sketch.paper}
     * @param pencil {@code sketch.pencil}
     * @param pencilUses {@code sketch.pencil-uses}; {@code 0} never wears
     */
    public SketchSupplies(JavaPlugin plugin, ItemRef paper, ItemRef pencil, int pencilUses) {
        this.plugin = plugin;
        this.paper = paper;
        this.pencil = pencil;
        this.pencilUses = Math.max(0, pencilUses);
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
     * @param pencilUses {@code sketch.pencil-uses} after reload
     */
    public void update(ItemRef paper, ItemRef pencil, int pencilUses) {
        this.paper = paper;
        this.pencil = pencil;
        this.pencilUses = Math.max(0, pencilUses);
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
     * @return configured pencil with Archaeo instructions and a durability bar when uses are finite
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
     * @param pencil live pencil stack
     * @return whether this pencil has no uses left
     */
    public boolean isSpent(ItemStack pencil) {
        if (pencilUses <= 0 || pencil == null || pencil.getType().isAir()) {
            return false;
        }
        stamped(pencil, false);
        ItemMeta meta = pencil.getItemMeta();
        if (!(meta instanceof Damageable damageable) || meta.isUnbreakable()) {
            return false;
        }
        int max = maxDamage(damageable, pencil);
        return max > 0 && damageable.getDamage() >= max;
    }

    /**
     * Spends one sketch on the pencil. Unbreaking can absorb the point the same way a tool would.
     * Does nothing when uses are infinite or the item is unbreakable.
     *
     * @param player holder, for the break sound
     * @param pencil live pencil stack (mutated; emptied if it snaps)
     * @return whether the pencil broke on this call
     */
    public boolean wear(Player player, ItemStack pencil) {
        if (pencilUses <= 0 || pencil == null || pencil.getType().isAir()) {
            return false;
        }
        stamped(pencil, false);
        ItemMeta meta = pencil.getItemMeta();
        if (!(meta instanceof Damageable damageable) || meta.isUnbreakable()) {
            return false;
        }
        int max = maxDamage(damageable, pencil);
        if (max <= 0) {
            return false;
        }
        int applied = afterUnbreaking(pencil, 1);
        if (applied <= 0) {
            return false;
        }
        damageable.setDamage(damageable.getDamage() + applied);
        pencil.setItemMeta(meta);
        if (damageable.getDamage() < max) {
            return false;
        }
        pencil.setAmount(0);
        pencil.setType(org.bukkit.Material.AIR);
        if (player != null) {
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 1f, 1f);
        }
        return true;
    }

    /**
     * Rewrites Archaeo how-to lore on a live sheet or pencil. Pack plugins may stamp lore
     * again later; call this when the player holds, picks up, or opens the bag.
     * Pencil damage is left alone so restamping does not refill the bar.
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
        if (sheet) {
            meta.setLore(PAPER_LORE);
        } else {
            applyPencilDurability(meta);
            meta.setLore(PENCIL_LORE);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * Gives a non-tool pencil a visible vanilla bar, or clears it when uses are infinite.
     *
     * @param meta pencil meta
     */
    private void applyPencilDurability(ItemMeta meta) {
        if (!(meta instanceof Damageable damageable)) {
            return;
        }
        if (pencilUses <= 0) {
            if (damageable.hasMaxDamage()) {
                damageable.setMaxDamage(null);
                damageable.setDamage(0);
            }
            if (meta.hasMaxStackSize()) {
                meta.setMaxStackSize(null);
            }
            return;
        }
        meta.setMaxStackSize(1);
        if (!damageable.hasMaxDamage() || damageable.getMaxDamage() != pencilUses) {
            int kept = damageable.getDamage();
            damageable.setMaxDamage(pencilUses);
            damageable.setDamage(Math.min(kept, pencilUses));
        }
    }

    /**
     * @param damageable pencil meta
     * @param stack pencil item
     * @return configured max, or the vanilla tool max if this material already has one
     */
    private int maxDamage(Damageable damageable, ItemStack stack) {
        if (damageable.hasMaxDamage()) {
            return damageable.getMaxDamage();
        }
        return stack.getType().getMaxDurability();
    }

    /**
     * @param stack pencil being charged
     * @param points durability the sketch would cost without enchantments
     * @return points that survive Unbreaking rolls
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

    /**
     * @return plugin log
     */
    private Logger logger() {
        return plugin.getLogger();
    }
}
