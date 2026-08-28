package com.nowko.archeology.item;

import com.nowko.archeology.config.EstablishSettings;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds and recognizes the establishment kit (PDC), distinct from a vanilla stick.
 */
public class EstablishItem {
    private static final byte MARKER = 1;

    private final NamespacedKey key;
    private EstablishSettings settings;
    private Material material;

    /**
     * @param plugin owner of the PDC key
     * @param settings display name and lore
     * @param material Bukkit type from {@code items.establish}
     */
    public EstablishItem(JavaPlugin plugin, EstablishSettings settings, Material material) {
        this.key = new NamespacedKey(plugin, "establish");
        this.settings = settings;
        this.material = material;
    }

    /**
     * @param settings copy after reload
     * @param material type after reload
     */
    public void update(EstablishSettings settings, Material material) {
        this.settings = settings;
        this.material = material;
    }

    /**
     * @return a new kit stack
     */
    public ItemStack create() {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.setDisplayName(ChatColor.WHITE + settings.itemName());
        List<String> lore = new ArrayList<>();
        for (String line : settings.itemLore()) {
            lore.add(ChatColor.GRAY + line);
        }
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, MARKER);
        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * @param stack item in a hand, or {@code null}
     * @return whether this is an Archaeo establishment kit
     */
    public boolean isEstablish(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return false;
        }
        Byte mark = stack.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return mark != null && mark == MARKER;
    }
}
