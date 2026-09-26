package net.tfminecraft.archaeo.item;

import net.tfminecraft.archaeo.config.CatalogRegistry;
import net.tfminecraft.archaeo.model.Site;
import net.tfminecraft.archaeo.sketch.SketchService;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Rewrites {@code #name-n} on recovered pieces and filed sketches after an excavation is renamed.
 */
public final class SiteLabelRefresh {
    private final JavaPlugin plugin;
    private final RecoveredFindItem recovered;
    private final CatalogRegistry catalogs;
    private final SketchService sketch;

    /**
     * @param plugin worlds and online players
     * @param recovered recovered-find tags
     * @param catalogs grades used when lore is rebuilt
     * @param sketch registered drawings
     */
    public SiteLabelRefresh(
            JavaPlugin plugin,
            RecoveredFindItem recovered,
            CatalogRegistry catalogs,
            SketchService sketch
    ) {
        this.plugin = plugin;
        this.recovered = recovered;
        this.catalogs = catalogs;
        this.sketch = sketch;
    }

    /**
     * Updates every matching stack currently loaded: players, dropped items, frames, and tile inventories.
     * Chests in unloaded chunks catch up when a player opens them.
     *
     * @param site stored excavation whose public name just changed
     */
    public void retitle(Site site) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            PlayerInventory inventory = player.getInventory();
            retitleInventory(inventory, site);
            retitleInventory(player.getEnderChest(), site);
            ItemStack cursor = player.getItemOnCursor();
            if (retitleStack(cursor, site)) {
                player.setItemOnCursor(cursor);
            }
        }
        for (World world : plugin.getServer().getWorlds()) {
            for (Item item : world.getEntitiesByClass(Item.class)) {
                ItemStack stack = item.getItemStack();
                if (retitleStack(stack, site)) {
                    item.setItemStack(stack);
                }
            }
            for (ItemFrame frame : world.getEntitiesByClass(ItemFrame.class)) {
                ItemStack stack = frame.getItem();
                if (retitleStack(stack, site)) {
                    frame.setItem(stack);
                }
            }
            for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) {
                ItemStack stack = display.getItemStack();
                if (retitleStack(stack, site)) {
                    display.setItemStack(stack);
                }
            }
            for (ArmorStand stand : world.getEntitiesByClass(ArmorStand.class)) {
                retitleArmorStand(stand, site);
            }
            for (Chunk chunk : world.getLoadedChunks()) {
                for (BlockState state : chunk.getTileEntities()) {
                    if (state instanceof InventoryHolder holder) {
                        retitleInventory(holder.getInventory(), site);
                    }
                }
            }
        }
    }

    /**
     * @param stand display stand
     * @param site excavation
     */
    private void retitleArmorStand(ArmorStand stand, Site site) {
        EntityEquipment equipment = stand.getEquipment();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = equipment.getItem(slot);
            if (retitleStack(stack, site)) {
                equipment.setItem(slot, stack);
            }
        }
    }

    /**
     * @param inventory bag, chest, or similar
     * @param site excavation
     */
    private void retitleInventory(Inventory inventory, Site site) {
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            // getContents reports an empty slot as null, never as an air stack.
            if (stack == null) {
                continue;
            }
            boolean changed = retitleNested(stack, site);
            changed = retitleStack(stack, site) || changed;
            if (changed) {
                inventory.setItem(i, stack);
            }
        }
    }

    /**
     * Updates finds packed inside a shulker or similar block-state item.
     *
     * @param stack container item
     * @param site excavation
     * @return whether an inner inventory was rewritten
     */
    private boolean retitleNested(ItemStack stack, Site site) {
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof BlockStateMeta blockMeta)) {
            return false;
        }
        BlockState innerState = blockMeta.getBlockState();
        if (!(innerState instanceof InventoryHolder holder)) {
            return false;
        }
        Inventory inner = holder.getInventory();
        ItemStack[] contents = inner.getContents();
        boolean changed = false;
        for (int i = 0; i < contents.length; i++) {
            ItemStack nested = contents[i];
            if (nested == null) {
                continue;
            }
            if (retitleStack(nested, site)) {
                inner.setItem(i, nested);
                changed = true;
            }
        }
        if (!changed) {
            return false;
        }
        blockMeta.setBlockState(innerState);
        stack.setItemMeta(blockMeta);
        return true;
    }

    /**
     * @param stack candidate
     * @param site excavation
     * @return whether lore or sketch label was rewritten
     */
    private boolean retitleStack(ItemStack stack, Site site) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        return recovered.retitle(stack, site, catalogs) || sketch.retitle(stack, site);
    }
}
