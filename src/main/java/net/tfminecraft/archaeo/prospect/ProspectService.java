package net.tfminecraft.archaeo.prospect;

import net.tfminecraft.archaeo.config.CatalogRegistry;
import net.tfminecraft.archaeo.config.InterestSettings;
import net.tfminecraft.archaeo.config.ProspectSettings;
import net.tfminecraft.archaeo.item.ProspectItem;
import net.tfminecraft.archaeo.model.BlockCell;
import net.tfminecraft.archaeo.model.ProspectResult;
import net.tfminecraft.archaeo.model.Site;
import net.tfminecraft.archaeo.model.SiteStatus;
import net.tfminecraft.archaeo.site.SiteRepository;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Samples ground in a hidden ruin chunk. Confirmation is per player and does not plant a camp.
 */
public class ProspectService {
    private final JavaPlugin plugin;
    private final CatalogRegistry catalogs;
    private final SiteRepository sites;
    private final ProspectItem item;
    private ProspectSettings settings;
    private final Map<UUID, BukkitTask> channeling = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldownUntilMs = new ConcurrentHashMap<>();

    /**
     * @param plugin scheduler owner
     * @param catalogs interest, hints, and prospect settings
     * @param sites ruin lookup
     * @param item kit recognition
     * @param settings sample counts and channel time
     */
    public ProspectService(
            JavaPlugin plugin,
            CatalogRegistry catalogs,
            SiteRepository sites,
            ProspectItem item,
            ProspectSettings settings
    ) {
        this.plugin = plugin;
        this.catalogs = catalogs;
        this.sites = sites;
        this.item = item;
        this.settings = settings;
    }

    /**
     * @param settings values after {@code /archaeo reload}
     */
    public void setSettings(ProspectSettings settings) {
        this.settings = settings;
    }

    /**
     * Cancels in-progress samples (disable / quit).
     */
    public void stop() {
        for (UUID playerId : channeling.keySet()) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                clearActionBar(player);
            }
        }
        for (BukkitTask task : channeling.values()) {
            task.cancel();
        }
        channeling.clear();
        cooldownUntilMs.clear();
    }

    /**
     * @param player scanner
     */
    public void cancel(Player player) {
        BukkitTask task = channeling.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
        clearActionBar(player);
    }

    /**
     * Starts a sample on {@code block}, or prints a recap if this player already confirmed.
     *
     * @param player scanner
     * @param block clicked ground
     */
    public void begin(Player player, Block block) {
        if (!settings.enabled()) {
            return;
        }
        if (!isSampleGround(block)) {
            refuseWrongGround(player);
            return;
        }
        if (onCooldown(player)) {
            return;
        }
        if (channeling.containsKey(player.getUniqueId())) {
            return;
        }
        Location click = block.getLocation();
        Site site = sites.findByChunk(click.getWorld().getName(), click.getChunk().getX(), click.getChunk().getZ())
                .orElse(null);
        if (site == null || site.getStatus() != SiteStatus.HIDDEN) {
            armCooldown(player, 800);
            player.sendMessage("Soil sample analysed. No archaeological traces here.");
            return;
        }
        if (site.isProspectConfirmed(player.getUniqueId())) {
            armCooldown(player, 1500);
            sendConfirmed(player);
            return;
        }
        BlockCell cell = new BlockCell(block.getX(), block.getY(), block.getZ());
        if (site.hasProspectSample(player.getUniqueId(), cell)) {
            player.sendMessage("You already sampled this spot. Try another point.");
            return;
        }
        if (tooClose(site, player.getUniqueId(), cell)) {
            player.sendMessage("Sample farther apart (" + settings.minSampleDistance() + " blocks).");
            return;
        }
        startChannel(player, block, site, cell);
    }

    /**
     * Shovel-dug ground: dirt, grass, sand, gravel, clay, mud, snow, and the rest of
     * {@link Tag#MINEABLE_SHOVEL}. Stations, wool, stone, and furniture are not samples.
     *
     * @param block clicked block, or {@code null}
     * @return whether the kit may take a soil sample here
     */
    public boolean isSampleGround(Block block) {
        return block != null && Tag.MINEABLE_SHOVEL.isTagged(block.getType());
    }

    /**
     * Tells the player that this block is not soil. Cooldown stops chat spam.
     *
     * @param player scanner
     */
    public void refuseWrongGround(Player player) {
        if (player == null || onCooldown(player)) {
            return;
        }
        armCooldown(player, 800);
        player.sendMessage("That is not soil. Sample dirt, sand, gravel, or clay.");
    }

    /**
     * @param site hidden ruin
     * @param playerId sampler
     * @param cell new point
     * @return whether another sample is closer than {@code min-sample-distance}
     */
    private boolean tooClose(Site site, UUID playerId, BlockCell cell) {
        int min = settings.minSampleDistance();
        for (BlockCell existing : site.prospectSamples(playerId)) {
            double dx = existing.x() - cell.x();
            double dy = existing.y() - cell.y();
            double dz = existing.z() - cell.z();
            if (Math.sqrt(dx * dx + dy * dy + dz * dz) < min) {
                return true;
            }
        }
        return false;
    }

    /**
     * Holds the player on the block for {@code use-ticks}, then records the sample.
     *
     * @param player scanner
     * @param block clicked block
     * @param site hidden ruin
     * @param cell sample coordinates
     */
    private void startChannel(Player player, Block block, Site site, BlockCell cell) {
        Location start = player.getLocation().clone();
        int total = Math.max(1, settings.useTicks());
        player.playSound(player.getLocation(), Sound.ITEM_HOE_TILL, SoundCategory.PLAYERS, 0.5f, 0.9f);
        showSampleProgress(player, 0, total);
        final int[] elapsed = {0};
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || !item.isProspect(player.getInventory().getItemInMainHand())) {
                cancel(player);
                return;
            }
            if (player.getLocation().distanceSquared(start) > 4.0) {
                player.sendMessage("Sampling interrupted.");
                cancel(player);
                return;
            }
            elapsed[0]++;
            showSampleProgress(player, elapsed[0], total);
            if (elapsed[0] % 10 == 0) {
                player.swingMainHand();
                player.getWorld().spawnParticle(
                        org.bukkit.Particle.BLOCK,
                        block.getLocation().add(0.5, 1.05, 0.5),
                        6,
                        0.2,
                        0.05,
                        0.2,
                        0.0,
                        block.getBlockData()
                );
            }
            if (elapsed[0] < total) {
                return;
            }
            cancel(player);
            finishSample(player, site, cell);
        }, 1L, 1L);
        channeling.put(player.getUniqueId(), task);
    }

    /**
     * Stores the point, maps progress to a result, and persists the site.
     *
     * @param player scanner
     * @param site hidden ruin
     * @param cell sample coordinates
     */
    private void finishSample(Player player, Site site, BlockCell cell) {
        if (!site.addProspectSample(player.getUniqueId(), cell)) {
            player.sendMessage("You already sampled this spot. Try another point.");
            return;
        }
        damageKit(player);
        int count = site.prospectSamples(player.getUniqueId()).size();
        int need = Math.max(1, settings.pointsRequired());
        ProspectResult result = resultFor(count, need);
        if (result == ProspectResult.CONFIRMED) {
            site.confirmProspect(player.getUniqueId());
        }
        sites.save(site);
        sendResult(player, site, result, count, need);
    }

    /**
     * @param count unique samples this player has on the site
     * @param need samples required to confirm
     * @return staged result; confirmation cannot be skipped
     */
    private ProspectResult resultFor(int count, int need) {
        if (count >= need) {
            return ProspectResult.CONFIRMED;
        }
        if (count >= Math.max(1, need - 1)) {
            return ProspectResult.POSSIBLE;
        }
        if (count >= 2) {
            return ProspectResult.WEAK;
        }
        return ProspectResult.INSUFFICIENT;
    }

    /**
     * @param player scanner
     * @param site dossier
     * @param result stage reached
     * @param count samples so far
     * @param need samples required
     */
    private void sendResult(Player player, Site site, ProspectResult result, int count, int need) {
        player.playSound(player.getLocation(), Sound.BLOCK_GRAVEL_HIT, SoundCategory.PLAYERS, 0.6f, 1.1f);
        switch (result) {
            case INSUFFICIENT -> player.sendMessage(
                    "Soil sample analysed. Not enough traces yet. (" + count + "/" + need + ")");
            case WEAK -> player.sendMessage(
                    "Weak traces of human activity. Keep sampling other points. (" + count + "/" + need + ")");
            case POSSIBLE -> player.sendMessage(
                    "Possible archaeological site. One more distinct point should confirm it. ("
                            + count + "/" + need + ")");
            case CONFIRMED -> {
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.7f, 1.2f);
                sendConfirmed(player);
            }
        }
        if (result != ProspectResult.CONFIRMED && result != ProspectResult.INSUFFICIENT) {
            player.sendMessage(ChatColor.DARK_GRAY + readingFlavor(site, player, count));
        }
    }

    /**
     * Wealth jitter for flavour only; never below the staff base wealth.
     *
     * @param site ruin
     * @param player scanner
     * @param sampleIndex which sample this is
     * @return short English line
     */
    private String readingFlavor(Site site, Player player, int sampleIndex) {
        if (site.getInterest() == null) {
            return "The reading is inconclusive.";
        }
        InterestSettings interest = catalogs.interest(site.getInterest());
        if (interest == null) {
            return "The reading is inconclusive.";
        }
        int extra = 0;
        if (interest.variation() > 0) {
            int hash = Objects.hash(site.getId(), player.getUniqueId(), sampleIndex);
            extra = Math.floorMod(hash, interest.variation() + 1);
        }
        int reading = interest.baseWealth() + extra;
        if (reading >= 8) {
            return "The soil feels unusually rich in remains.";
        }
        if (reading >= 4) {
            return "The soil holds a fair amount of cultural material.";
        }
        return "The soil holds only modest remains.";
    }

    /**
     * One confirmation line. Interest and field notes live on the camp board after planting.
     *
     * @param player scanner
     */
    private void sendConfirmed(Player player) {
        player.sendMessage("Archaeological site confirmed. A camp can be established.");
    }

    /**
     * Applies one durability point to the kit in the main hand.
     *
     * @param player scanner
     */
    private void damageKit(Player player) {
        ItemStack stack = player.getInventory().getItemInMainHand();
        if (!item.isProspect(stack)) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return;
        }
        short max = stack.getType().getMaxDurability();
        if (max <= 0) {
            return;
        }
        damageable.setDamage(damageable.getDamage() + 1);
        stack.setItemMeta(meta);
        if (damageable.getDamage() >= max) {
            player.getInventory().setItemInMainHand(null);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 1.0f, 1.0f);
        }
    }

    /**
     * Updates the action bar to match {@code use-ticks} remaining.
     *
     * @param player scanner
     * @param elapsed ticks already spent
     * @param total channel length from config
     */
    private void showSampleProgress(Player player, int elapsed, int total) {
        int width = 10;
        int clamped = Math.min(total, Math.max(0, elapsed));
        int filled = (int) Math.round((clamped / (double) total) * width);
        filled = Math.min(width, Math.max(0, filled));
        String bar = ChatColor.GREEN + "█".repeat(filled)
                + ChatColor.DARK_GRAY + "█".repeat(width - filled);
        double remain = Math.max(0, total - clamped) / 20.0;
        String line = ChatColor.WHITE + "Sampling " + bar
                + ChatColor.GRAY + String.format(" %.1fs", remain);
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(line));
    }

    /**
     * Clears the sampling action bar so it does not linger after cancel or finish.
     *
     * @param player scanner
     */
    private void clearActionBar(Player player) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(""));
    }

    /**
     * @param player scanner
     * @return whether the kit is still cooling down
     */
    private boolean onCooldown(Player player) {
        Long until = cooldownUntilMs.get(player.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    /**
     * @param player scanner
     * @param millis cooldown length
     */
    private void armCooldown(Player player, long millis) {
        cooldownUntilMs.put(player.getUniqueId(), System.currentTimeMillis() + millis);
    }
}
