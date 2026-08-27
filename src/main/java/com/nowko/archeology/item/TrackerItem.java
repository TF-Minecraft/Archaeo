package com.nowko.archeology.item;

import com.nowko.archeology.config.TrackerSettings;
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
 * Builds and recognizes the archaeological tracker item (PDC, not vanilla compass behaviour).
 */
public class TrackerItem {
    private static final byte MARKER = 1;

    private final NamespacedKey key;
    private TrackerSettings settings;
    private Material material;

    /**
     * @param plugin owner of the PDC namespaced key
     * @param settings display name and lore
     * @param material Bukkit type from {@code items.tracker}
     */
    public TrackerItem(JavaPlugin plugin, TrackerSettings settings, Material material) {
        this.key = new NamespacedKey(plugin, "tracker");
        this.settings = settings;
        this.material = material;
    }

    /**
     * @param settings display name and lore after reload
     * @param material Bukkit type after reload
     */
    public void update(TrackerSettings settings, Material material) {
        this.settings = settings;
        this.material = material;
    }

    /**
     * @return a new tracker stack
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
     * Matches by PDC so a renamed vanilla compass is not treated as a tracker.
     *
     * @param stack item in a hand, or {@code null}
     * @return whether this is an Archaeo tracker
     */
    public boolean isTracker(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return false;
        }
        Byte mark = stack.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return mark != null && mark == MARKER;
    }
}
