package com.nowko.archeology.item;

import com.nowko.archeology.config.ArtifactTemplate;
import com.nowko.archeology.config.CatalogRegistry;
import com.nowko.archeology.model.BuriedFind;
import com.nowko.archeology.model.Site;
import com.nowko.archeology.site.SiteRepository;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.UUID;

/**
 * Writes an anvil rename onto the excavation dossier so camp fiches and later lore refreshes
 * keep the new name instead of snapping back to the catalog.
 */
public final class RecoveredFindListener implements Listener {
    private static final int ANVIL_RESULT_SLOT = 2;

    private final JavaPlugin plugin;
    private final SiteRepository sites;
    private final CatalogRegistry catalogs;
    private final RecoveredFindItem recovered;

    /**
     * @param plugin used to refresh the stack on the next tick
     * @param sites dossiers that store {@code given-name}
     * @param catalogs catalog title used when the anvil name matches the template
     * @param recovered recovered-find tags
     */
    public RecoveredFindListener(
            JavaPlugin plugin,
            SiteRepository sites,
            CatalogRegistry catalogs,
            RecoveredFindItem recovered
    ) {
        this.plugin = plugin;
        this.sites = sites;
        this.catalogs = catalogs;
        this.recovered = recovered;
    }

    /**
     * Keeps recovered-find PDC on the anvil output so a rename cannot strip the archive tag.
     *
     * @param event anvil preview
     */
    @EventHandler(ignoreCancelled = true)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory anvil = event.getInventory();
        ItemStack left = anvil.getItem(0);
        ItemStack result = event.getResult();
        if (!recovered.isRecovered(left) || result == null || result.getType().isAir()) {
            return;
        }
        if (recovered.isRecovered(result)) {
            return;
        }
        ItemStack stamped = left.clone();
        stamped.setAmount(result.getAmount());
        ItemMeta meta = stamped.getItemMeta();
        String typed = BuriedFind.sanitizeGivenName(strip(anvil.getRenameText()));
        if (meta != null && typed != null) {
            meta.setDisplayName(ChatColor.WHITE + typed);
            stamped.setItemMeta(meta);
        }
        event.setResult(stamped);
    }

    /**
     * Records the taken name on the find row after the player takes the anvil result.
     *
     * @param event click in any inventory
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnvilTake(InventoryClickEvent event) {
        if (event.getRawSlot() != ANVIL_RESULT_SLOT) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory() instanceof AnvilInventory anvil)) {
            return;
        }
        ItemStack result = event.getCurrentItem();
        if (!recovered.isRecovered(result)) {
            return;
        }
        String typed = anvil.getRenameText();
        if (typed == null || typed.isBlank()) {
            typed = recovered.labelOf(result);
        }
        applyGivenName(player, result, typed);
    }

    /**
     * @param player who took the result
     * @param stack renamed recovered piece
     * @param typed anvil line or current display name
     */
    private void applyGivenName(Player player, ItemStack stack, String typed) {
        UUID findId = recovered.findIdOf(stack);
        UUID siteId = recovered.siteIdOf(stack);
        if (findId == null || siteId == null) {
            return;
        }
        Site site = sites.findById(siteId).orElse(null);
        BuriedFind find = site == null ? null : site.findById(findId).orElse(null);
        if (find == null) {
            return;
        }
        ArtifactTemplate template = catalogs.artifact(find.getArtifactId());
        String catalog = template == null ? null : template.displayName();
        String name = BuriedFind.sanitizeGivenName(strip(typed));
        if (name != null && catalog != null && name.equalsIgnoreCase(catalog)) {
            name = null;
        }
        if (Objects.equals(find.getGivenName(), name)) {
            return;
        }
        find.setGivenName(name);
        sites.save(site);
        String grade = catalogs.pick().conservation().gradeLabel(find.getConservation());
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                recovered.refreshCarried(player, site, find, template, grade, catalogs);
            }
        });
    }

    /**
     * @param raw anvil or item name
     * @return colour-stripped text, or {@code null}
     */
    private static String strip(String raw) {
        if (raw == null) {
            return null;
        }
        return ChatColor.stripColor(raw);
    }
}
