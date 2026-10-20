package net.tfminecraft.archaeo.sketch;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.List;

/** Six pigment columns, with light, normal, and dark shades from top to bottom. */
final class SketchPalette implements InventoryHolder {
    static final int SLOT_HELP = 8;
    static final int SLOT_CLOSE = 26;
    private static final SketchInk[][] SHADES = {
            {SketchInk.LIGHT_CHARCOAL, SketchInk.LIGHT_RED, SketchInk.LIGHT_OCHRE,
                    SketchInk.LIGHT_BROWN, SketchInk.LIGHT_SLATE, SketchInk.LIGHT_MOSS},
            {SketchInk.CHARCOAL, SketchInk.RED, SketchInk.OCHRE,
                    SketchInk.BROWN, SketchInk.SLATE, SketchInk.MOSS},
            {SketchInk.DARK_CHARCOAL, SketchInk.DARK_RED, SketchInk.DARK_OCHRE,
                    SketchInk.DARK_BROWN, SketchInk.DARK_SLATE, SketchInk.DARK_MOSS}
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
        inventory.setItem(SLOT_HELP, button(Material.PAPER, "Choose an ink",
                "Columns: charcoal, red, ochre, brown, slate, moss.",
                "Rows: light, normal, dark.", "Click a colour to return to your sketch."));
        inventory.setItem(SLOT_CLOSE, button(Material.BARRIER, "Back to sketch",
                "Keep your current ink.", "Escape also returns to the drawing."));
        player.openInventory(inventory);
    }

    /** @return pigment at a swatch slot, or null for buttons, margins, and the bag */
    static SketchInk inkAt(int slot) {
        if (slot < 0 || slot >= 27) {
            return null;
        }
        int column = slot % 9 - 1;
        return column >= 0 && column < SHADES[0].length ? SHADES[slot / 9][column] : null;
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

    private static ItemStack button(Material material, String title, String... lines) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(ChatColor.WHITE + title);
        meta.setLore(java.util.Arrays.stream(lines).map(line -> ChatColor.GRAY + line).toList());
        stack.setItemMeta(meta);
        return stack;
    }
}
