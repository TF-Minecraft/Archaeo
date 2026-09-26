package net.tfminecraft.archaeo.sketch;

import net.tfminecraft.archaeo.config.ArtifactTemplate;
import net.tfminecraft.archaeo.config.CatalogRegistry;
import net.tfminecraft.archaeo.config.FindMaterial;
import net.tfminecraft.archaeo.config.LabSettings;
import net.tfminecraft.archaeo.config.LabStain;
import net.tfminecraft.archaeo.config.LabTool;
import net.tfminecraft.archaeo.config.SketchSettings;
import net.tfminecraft.archaeo.item.RecoveredFindItem;
import net.tfminecraft.archaeo.model.BuriedFind;
import net.tfminecraft.archaeo.model.FindState;
import net.tfminecraft.archaeo.model.Site;
import net.tfminecraft.archaeo.site.SiteRepository;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * First lab step at the cabinet: a glass field in the material colour, with stains
 * from the lab catalogue. Each stain names its own wipe tool.
 */
final class CabinetLab {
    private final JavaPlugin plugin;
    private final SiteRepository sites;
    private final CatalogRegistry catalogs;
    private final RecoveredFindItem recovered;
    private final NamespacedKey kindKey;
    private final NamespacedKey toolKey;
    private final NamespacedKey stainKey;
    private final Map<UUID, CabinetLabBoard> open = new ConcurrentHashMap<>();
    private SketchSettings settings;

    /**
     * @param plugin scheduler, PDC, and sounds
     * @param sites excavation archive
     * @param catalogs materials and conservation grades
     * @param recovered recovered-find tags
     * @param settings rack tools and dirt budget
     */
    CabinetLab(
            JavaPlugin plugin,
            SiteRepository sites,
            CatalogRegistry catalogs,
            RecoveredFindItem recovered,
            SketchSettings settings
    ) {
        this.plugin = plugin;
        this.sites = sites;
        this.catalogs = catalogs;
        this.recovered = recovered;
        this.settings = settings;
        this.kindKey = new NamespacedKey(plugin, "lab_kind");
        this.toolKey = new NamespacedKey(plugin, "lab_tool");
        this.stainKey = new NamespacedKey(plugin, "lab_stain");
    }

    /**
     * @param settings rack tools after reload
     */
    void setSettings(SketchSettings settings) {
        this.settings = settings;
    }

    /**
     * Opens the wipe window for the archived piece the player holds, unless it is already clean.
     *
     * @param player clicker
     * @param block cabinet, or {@code null}
     * @param site excavation that owns the piece
     * @param find archive row of the piece in hand
     * @return whether the lab window opened (or is already open)
     */
    boolean tryStart(Player player, Block block, Site site, BuriedFind find) {
        if (find.getState() != FindState.RECOVERED || find.isLabCleaned() || find.isCatalogued()) {
            return false;
        }
        if (open.containsKey(player.getUniqueId())) {
            return true;
        }
        FindMaterial material = materialOf(find);
        Location cabinet = block == null ? player.getLocation() : block.getLocation().add(0.5, 1.05, 0.5);
        CabinetLabBoard board = new CabinetLabBoard(
                site.getId(),
                find.getId(),
                material,
                lab(),
                cabinet,
                kindKey,
                toolKey,
                stainKey);
        open.put(player.getUniqueId(), board);
        if (!board.open(player)) {
            // Another plugin cancelled the open; no close will follow to unregister the board.
            open.remove(player.getUniqueId(), board);
            return true;
        }
        player.sendMessage(ChatColor.GOLD + "Pick a tool and wipe the dirt. "
                + ChatColor.WHITE + material.firstStepGerund() + ".");
        return true;
    }

    /**
     * Closes an unfinished wipe.
     *
     * @param player worker
     */
    void cancel(Player player) {
        // A board stays registered only while its window is open: every close runs handleClose.
        if (open.containsKey(player.getUniqueId())) {
            player.closeInventory();
        }
    }

    /**
     * Closes every lab window.
     */
    void stop() {
        for (UUID id : Set.copyOf(open.keySet())) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) {
                cancel(player);
            } else {
                open.remove(id);
            }
        }
    }

    /**
     * Click in the lab window: pick a rack tool, or wipe a dirty pane with the cursor tool.
     *
     * @param player worker
     * @param board open window
     * @param slot clicked top slot
     * @param cursor item on the cursor
     * @return cursor after the click
     */
    ItemStack handleClick(Player player, CabinetLabBoard board, int slot, ItemStack cursor) {
        if (board.finished()) {
            return cursor;
        }
        if (board.isToolSlot(slot)) {
            ItemStack picked = board.copyTool(slot);
            if (!board.isTool(cursor)) {
                playTool(player, board.tool(board.toolId(picked)));
                return picked;
            }
            if (sameTool(board, cursor, picked)) {
                return empty();
            }
            playTool(player, board.tool(board.toolId(picked)));
            return picked;
        }
        if (slot >= LabSettings.FIELD_SLOTS) {
            return cursor;
        }
        if (!board.isTool(cursor)) {
            if (!board.isDirty(slot)) {
                return cursor;
            }
            player.sendMessage(ChatColor.GRAY + "Pick a tool from the rack, then wipe the dirt.");
            return cursor;
        }
        tryWipe(player, board, slot, cursor);
        return cursor;
    }

    /**
     * Drops fake lab stacks when the window closes.
     *
     * @param player worker
     * @param board window that closed
     */
    void handleClose(Player player, CabinetLabBoard board) {
        open.remove(player.getUniqueId(), board);
        stripLabItems(player);
        plugin.getServer().getScheduler().runTask(plugin, () -> stripLabItems(player));
    }

    /**
     * @param player worker
     * @param board open window
     * @param slot field index
     * @param cursor tool on the cursor
     */
    private void tryWipe(Player player, CabinetLabBoard board, int slot, ItemStack cursor) {
        if (!board.isDirty(slot)) {
            return;
        }
        String toolId = board.toolId(cursor);
        LabStain stain = board.stainOf(slot);
        if (!stain.allowsTool(toolId)) {
            if (!board.warnedWrongTool()) {
                board.markWarnedWrongTool();
                String label = stain.label().toLowerCase(java.util.Locale.ROOT);
                player.sendMessage(ChatColor.GOLD + "That tool is not for " + label + ".");
            }
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.PLAYERS, 0.4f, 0.7f);
            return;
        }
        board.wipe(slot);
        cue(player, board, board.tool(toolId));
        if (board.dirtyLeft() > 0) {
            return;
        }
        finish(player, board);
    }

    /**
     * @param player worker
     * @param board completed window
     */
    private void finish(Player player, CabinetLabBoard board) {
        board.markFinished();
        Site site = sites.findById(board.siteId()).orElse(null);
        BuriedFind find = site == null ? null : site.findById(board.findId()).orElse(null);
        if (find != null) {
            find.setLabCleaned(true);
            sites.save(site);
            ArtifactTemplate template = catalogs.artifact(find.getArtifactId());
            String grade = catalogs.pick().conservation().gradeLabel(find.getConservation());
            recovered.refreshCarried(player, site, find, template, grade, catalogs);
        }
        player.sendMessage(ChatColor.GOLD + board.material().firstStepDone() + ChatColor.WHITE + " (1/3)");
        player.sendMessage(ChatColor.GRAY + "Draw the piece and register the drawing at the cabinet. (2/3)");
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.closeInventory();
            }
        });
    }

    /**
     * @param player worker
     * @param board open window
     * @param tool rack tool that wiped the pane
     */
    private void cue(Player player, CabinetLabBoard board, LabTool tool) {
        playTool(player, tool);
        Location at = board.cabinet();
        if ("water".equals(tool.id())) {
            player.getWorld().spawnParticle(Particle.SPLASH, at, 8, 0.2, 0.1, 0.2, 0.01);
            return;
        }
        player.getWorld().spawnParticle(Particle.CLOUD, at, 5, 0.15, 0.08, 0.15, 0.01);
    }

    /**
     * @param player listener
     * @param tool rack tool; config always resolves a sound
     */
    private static void playTool(Player player, LabTool tool) {
        player.playSound(player.getLocation(), tool.sound(), SoundCategory.PLAYERS, 0.4f, 1.2f);
    }

    /**
     * Removes lab panes and rack copies from cursor, off-hand, and bag.
     *
     * @param player worker
     */
    private void stripLabItems(Player player) {
        if (!player.isOnline()) {
            return;
        }
        if (isLabStack(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
        }
        // Player contents include armour and the off-hand, so this reaches every slot.
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isLabStack(contents[i])) {
                inventory.setItem(i, null);
            }
        }
    }

    /**
     * @param stack candidate
     * @return whether this stack was spawned by a lab window
     */
    boolean isLabItem(ItemStack stack) {
        return isLabStack(stack);
    }

    /**
     * @param stack candidate
     * @return whether this stack was spawned by a lab window
     */
    private boolean isLabStack(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        return stack.getItemMeta().getPersistentDataContainer().has(kindKey, org.bukkit.persistence.PersistentDataType.STRING);
    }

    /**
     * @param find archive row
     * @return material row
     */
    private FindMaterial materialOf(BuriedFind find) {
        ArtifactTemplate template = catalogs.artifact(find.getArtifactId());
        return catalogs.materialOf(template == null ? null : template.material());
    }

    /**
     * @return current lab settings
     */
    private LabSettings lab() {
        return settings.lab();
    }

    /**
     * @param board open window
     * @param a cursor
     * @param b rack copy
     * @return whether both are the same rack tool
     */
    private static boolean sameTool(CabinetLabBoard board, ItemStack a, ItemStack b) {
        return board.toolId(a).equals(board.toolId(b));
    }

    /**
     * @return empty cursor
     */
    private static ItemStack empty() {
        return new ItemStack(Material.AIR);
    }
}
