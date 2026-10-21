package net.tfminecraft.archaeo.sketch;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.List;

/** Nine pigment columns, with light, normal, and dark shades from top to bottom. */
final class SketchPalette implements InventoryHolder {
    private static final SketchInk[][] SHADES = {
            {SketchInk.LIGHT_CHARCOAL, SketchInk.LIGHT_RED, SketchInk.LIGHT_OCHRE,
                    SketchInk.LIGHT_BROWN, SketchInk.LIGHT_SLATE, SketchInk.LIGHT_MOSS,
                    SketchInk.LIGHT_IVORY, SketchInk.LIGHT_TURQUOISE, SketchInk.LIGHT_VIOLET},
            {SketchInk.CHARCOAL, SketchInk.RED, SketchInk.OCHRE,
                    SketchInk.BROWN, SketchInk.SLATE, SketchInk.MOSS,
                    SketchInk.IVORY, SketchInk.TURQUOISE, SketchInk.VIOLET},
            {SketchInk.DARK_CHARCOAL, SketchInk.DARK_RED, SketchInk.DARK_OCHRE,
                    SketchInk.DARK_BROWN, SketchInk.DARK_SLATE, SketchInk.DARK_MOSS,
                    SketchInk.DARK_IVORY, SketchInk.DARK_TURQUOISE, SketchInk.DARK_VIOLET}
    };

    private final SketchSession session;
    private Inventory inventory;

    SketchPalette(SketchSession session) {
        this.session = session;
    }

    /** A palette only applies to the exact editing session that opened it. */
    boolean belongsTo(SketchSession current) {
        return session == current;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /** Opens dyed leather swatches; every icon uses the pigment's RGB colour. */
    void open(Player player) {
        inventory = Bukkit.createInventory(this, 27, "Sketch palette");
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            SketchInk ink = inkAt(slot);
            if (ink != null) {
                inventory.setItem(slot, swatch(ink, session.ink() == ink));
            }
        }
        player.openInventory(inventory);
    }

    /** @return pigment at a swatch slot, or null outside the palette */
    static SketchInk inkAt(int slot) {
        if (slot < 0 || slot >= 27) {
            return null;
        }
        return SHADES[slot / 9][slot % 9];
    }

    private static ItemStack swatch(SketchInk ink, boolean selected) {
        ItemStack stack = new ItemStack(Material.LEATHER_CHESTPLATE);
        LeatherArmorMeta meta = (LeatherArmorMeta) stack.getItemMeta();
        java.awt.Color rgb = ink.color();
        meta.setColor(org.bukkit.Color.fromRGB(rgb.getRed(), rgb.getGreen(), rgb.getBlue()));
        meta.setDisplayName((selected ? ChatColor.GOLD : ChatColor.WHITE) + ink.label());
        meta.setLore(List.of(selected ? ChatColor.GOLD + "Current ink." : ChatColor.GRAY + "Click to use this ink."));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_DYE);
        stack.setItemMeta(meta);
        return stack;
    }

}
