package com.nowko.archeology.site;

import com.nowko.archeology.establish.CampArchiveBook;
import com.nowko.archeology.establish.CampBoard;
import com.nowko.archeology.establish.CampClosure;
import com.nowko.archeology.establish.CampFindBoard;
import com.nowko.archeology.establish.CampFindsBoard;
import com.nowko.archeology.establish.CampIdentifyBoard;
import com.nowko.archeology.establish.CampStaffBoard;
import com.nowko.archeology.establish.CampWoolPicker;
import com.nowko.archeology.establish.CampWorkerBoard;
import com.nowko.archeology.establish.EstablishService;
import com.nowko.archeology.establish.FindReportBook;
import com.nowko.archeology.excavation.FindDustService;
import com.nowko.archeology.excavation.RecoverService;
import com.nowko.archeology.item.RecoveredFindItem;
import com.nowko.archeology.model.BlockCell;
import com.nowko.archeology.model.Site;
import com.nowko.archeology.sketch.CabinetLabBoard;
import com.nowko.archeology.sketch.SketchCabinet;
import com.nowko.archeology.sketch.SketchService;
import org.bukkit.Chunk;
import org.bukkit.Material;
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

import java.util.UUID;

/**
 * Staff wipe of one ruin: dossier, camp blocks, tagged items, and auto-spawn memory.
 * Terrain holes in the cut are left; Archaeo never stored the original fill.
 */
public final class SitePurge {
    private final JavaPlugin plugin;
    private final SiteRepository sites;
    private final RuinAutoSpawner autoRuins;
    private final EstablishService establish;
    private final RecoverService recover;
    private final FindDustService findDust;
    private final RecoveredFindItem recovered;
    private final CampArchiveBook archiveBook;
    private final FindReportBook reportBook;
    private final SketchService sketch;

    /**
     * @param plugin world and player access
     * @param sites dossier store
     * @param autoRuins evaluation ledger for the ruin chunk
     * @param establish relocate / rename sessions
     * @param recover brush HUD channels
     * @param findDust leak loop, restarted after the site is gone
     * @param recovered recovered-find tags
     * @param campClosure field-book tags
     * @param sketch registered drawings
     */
    public SitePurge(
            JavaPlugin plugin,
            SiteRepository sites,
            RuinAutoSpawner autoRuins,
            EstablishService establish,
            RecoverService recover,
            FindDustService findDust,
            RecoveredFindItem recovered,
            CampClosure campClosure,
            SketchService sketch
    ) {
        this.plugin = plugin;
        this.sites = sites;
        this.autoRuins = autoRuins;
        this.establish = establish;
        this.recover = recover;
        this.findDust = findDust;
        this.recovered = recovered;
        this.archiveBook = campClosure.archiveBook();
        this.reportBook = new FindReportBook(plugin);
        this.sketch = sketch;
    }

    /**
     * Erases {@code site} from memory, disk, camp, and tagged items as if it had never been filed.
     *
     * @param site ruin or excavation in any status
     */
    public void erase(Site site) {
        if (site == null || site.getId() == null) {
            return;
        }
        UUID id = site.getId();
        establish.abortSessionsFor(id);
        recover.abortForSite(id);
        closeBoards(id);
        stripTaggedItems(id);
        removeCamp(site);
        autoRuins.forgetChunk(site.getWorldName(), site.getChunkX(), site.getChunkZ());
        sites.erase(site);
        findDust.syncTimer();
    }

    /**
     * Closes camp, cabinet, and plaque windows tied to this excavation.
     *
     * @param siteId excavation
     */
    private void closeBoards(UUID siteId) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
            if (siteId.equals(boardSiteId(holder))) {
                player.closeInventory();
            }
        }
    }

    /**
     * @param holder open window
     * @return excavation id, or {@code null}
     */
    private static UUID boardSiteId(InventoryHolder holder) {
        if (holder instanceof CampBoard board) {
            return board.siteId();
        }
        if (holder instanceof CampStaffBoard board) {
            return board.siteId();
        }
        if (holder instanceof CampWorkerBoard board) {
            return board.siteId();
        }
        if (holder instanceof CampFindsBoard board) {
            return board.siteId();
        }
        if (holder instanceof CampFindBoard board) {
            return board.siteId();
        }
        if (holder instanceof CampIdentifyBoard board) {
            return board.siteId();
        }
        if (holder instanceof CampWoolPicker board) {
            return board.siteId();
        }
        if (holder instanceof SketchCabinet cabinet) {
            return cabinet.siteId();
        }
        if (holder instanceof CabinetLabBoard board) {
            return board.siteId();
        }
        return null;
    }

    /**
     * Removes recovered finds, field books, reports, and registered sketches from players
     * and from loaded world inventories and displays.
     *
     * @param siteId excavation
     */
    private void stripTaggedItems(UUID siteId) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            PlayerInventory inventory = player.getInventory();
            stripInventory(inventory, siteId);
            stripInventory(player.getEnderChest(), siteId);
            if (belongs(player.getItemOnCursor(), siteId)) {
                player.setItemOnCursor(null);
            }
        }
        for (World world : plugin.getServer().getWorlds()) {
            for (Item item : world.getEntitiesByClass(Item.class)) {
                if (belongs(item.getItemStack(), siteId)) {
                    item.remove();
                }
            }
            for (ItemFrame frame : world.getEntitiesByClass(ItemFrame.class)) {
                if (belongs(frame.getItem(), siteId)) {
                    frame.setItem(null);
                }
            }
            for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) {
                if (belongs(display.getItemStack(), siteId)) {
                    display.setItemStack(null);
                }
            }
            for (ArmorStand stand : world.getEntitiesByClass(ArmorStand.class)) {
                stripArmorStand(stand, siteId);
            }
            for (Chunk chunk : world.getLoadedChunks()) {
                for (BlockState state : chunk.getTileEntities()) {
                    if (state instanceof InventoryHolder holder) {
                        stripInventory(holder.getInventory(), siteId);
                    }
                }
            }
        }
    }

    /**
     * @param stand display stand
     * @param siteId excavation
     */
    private void stripArmorStand(ArmorStand stand, UUID siteId) {
        EntityEquipment equipment = stand.getEquipment();
        if (equipment == null) {
            return;
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (belongs(equipment.getItem(slot), siteId)) {
                equipment.setItem(slot, null);
            }
        }
    }

    /**
     * @param inventory bag, chest, or similar
     * @param siteId excavation
     */
    private void stripInventory(Inventory inventory, UUID siteId) {
        if (inventory == null) {
            return;
        }
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            if (belongs(stack, siteId)) {
                inventory.setItem(i, null);
                continue;
            }
            ItemStack nested = stripNested(stack, siteId);
            if (nested != stack) {
                inventory.setItem(i, nested);
            }
        }
    }

    /**
     * Empties matching finds packed inside a shulker or similar block-state item.
     *
     * @param stack container item
     * @param siteId excavation
     * @return the same stack, mutated when an inner inventory was rewritten
     */
    private ItemStack stripNested(ItemStack stack, UUID siteId) {
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof BlockStateMeta blockMeta)) {
            return stack;
        }
        BlockState innerState = blockMeta.getBlockState();
        if (!(innerState instanceof InventoryHolder holder)) {
            return stack;
        }
        stripInventory(holder.getInventory(), siteId);
        blockMeta.setBlockState(innerState);
        stack.setItemMeta(blockMeta);
        return stack;
    }

    /**
     * @param stack candidate
     * @param siteId excavation
     * @return whether this stack is tagged to the ruin
     */
    private boolean belongs(ItemStack stack, UUID siteId) {
        if (stack == null || stack.getType().isAir() || siteId == null) {
            return false;
        }
        return siteId.equals(recovered.siteIdOf(stack))
                || siteId.equals(archiveBook.siteIdOf(stack))
                || siteId.equals(reportBook.siteIdOf(stack))
                || siteId.equals(sketch.siteIdOf(stack));
    }

    /**
     * Breaks a standing camp into air. Closed camps whose blocks were never recorded are skipped.
     *
     * @param site excavation
     */
    private void removeCamp(Site site) {
        if (site.getCampBlocks().isEmpty()) {
            return;
        }
        World world = site.getWorldName() == null ? null : plugin.getServer().getWorld(site.getWorldName());
        if (world == null) {
            plugin.getLogger().warning("Could not remove camp blocks: world "
                    + site.getWorldName() + " is not loaded.");
            return;
        }
        for (BlockCell cell : site.getCampBlocks()) {
            world.getBlockAt(cell.x(), cell.y(), cell.z()).setType(Material.AIR, false);
        }
    }
}
