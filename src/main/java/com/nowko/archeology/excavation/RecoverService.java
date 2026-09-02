package com.nowko.archeology.excavation;

import com.nowko.archeology.config.ArtifactTemplate;
import com.nowko.archeology.config.CatalogRegistry;
import com.nowko.archeology.config.RecoverySettings;
import com.nowko.archeology.item.BrushItem;
import com.nowko.archeology.item.RecoveredFindItem;
import com.nowko.archeology.model.BlockCell;
import com.nowko.archeology.model.BuriedFind;
import com.nowko.archeology.model.FindState;
import com.nowko.archeology.model.Site;
import com.nowko.archeology.site.SiteRepository;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Brushes matrix off a fully exposed find. The last required cube drops one item and lifts the shape.
 */
public class RecoverService {
    private static final long WARN_MS = 3000L;
    private static final double MOVE_CANCEL = 2.0;

    private final JavaPlugin plugin;
    private final SiteRepository sites;
    private final CatalogRegistry catalogs;
    private final BrushItem brush;
    private final RecoveredFindItem recoveredItem;
    private RecoverySettings settings;
    private final Map<UUID, Channel> channels = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastWarn = new ConcurrentHashMap<>();

    /**
     * @param plugin scheduler
     * @param sites established excavations
     * @param catalogs artifacts, conservation threshold, and recovery settings
     * @param brush material matcher
     * @param recoveredItem dropped piece factory
     * @param settings channel length and tedium cap
     */
    public RecoverService(
            JavaPlugin plugin,
            SiteRepository sites,
            CatalogRegistry catalogs,
            BrushItem brush,
            RecoveredFindItem recoveredItem,
            RecoverySettings settings
    ) {
        this.plugin = plugin;
        this.sites = sites;
        this.catalogs = catalogs;
        this.brush = brush;
        this.recoveredItem = recoveredItem;
        this.settings = settings;
    }

    /**
     * @param settings after reload
     */
    public void setSettings(RecoverySettings settings) {
        this.settings = settings;
    }

    /**
     * Cancels open brush channels.
     */
    public void stop() {
        for (Channel channel : channels.values()) {
            if (channel.task != null) {
                channel.task.cancel();
            }
        }
        channels.clear();
    }

    /**
     * @param player holder
     */
    public void cancel(Player player) {
        Channel channel = channels.remove(player.getUniqueId());
        if (channel != null && channel.task != null) {
            channel.task.cancel();
        }
    }

    /**
     * Starts or ignores a right-click on prism fill with the field brush.
     *
     * @param player holder
     * @param block clicked cell
     * @return whether this click was a recovery action (vanilla brush must not run)
     */
    public boolean begin(Player player, Block block) {
        if (!settings.enabled()) {
            return false;
        }
        if (!brush.isBrush(player.getInventory().getItemInMainHand())) {
            return false;
        }
        Site site = sites.findEstablishedPrism(
                block.getWorld().getName(),
                block.getX(),
                block.getY(),
                block.getZ()).orElse(null);
        if (site == null) {
            return false;
        }
        BuriedFind find = site.findAt(new BlockCell(block.getX(), block.getY(), block.getZ())).orElse(null);
        if (find == null || find.getState() == FindState.LOST || find.getState() == FindState.RECOVERED) {
            return false;
        }
        if (find.getState() != FindState.DISCOVERED) {
            warn(player, "The shape is not fully free.");
            playPuff(block);
            return true;
        }
        if (!PrismFill.isTerrainFill(block.getType())) {
            return false;
        }
        BlockCell cell = new BlockCell(block.getX(), block.getY(), block.getZ());
        if (find.isCleaned(cell)) {
            warn(player, "That cube is already clean.");
            return true;
        }
        Channel existing = channels.get(player.getUniqueId());
        if (existing != null && existing.sameCell(block) && existing.findId.equals(find.getId())) {
            return true;
        }
        cancel(player);
        Channel channel = new Channel(
                site.getId(),
                find.getId(),
                block.getX(),
                block.getY(),
                block.getZ(),
                player.getLocation().clone());
        channels.put(player.getUniqueId(), channel);
        int ticks = Math.max(1, settings.channelTicks());
        channel.remaining = ticks;
        channel.task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> pulse(player, channel), 1L, 1L);
        playBrush(block);
        return true;
    }

    /**
     * @param player holder
     * @param channel open brush
     */
    private void pulse(Player player, Channel channel) {
        if (!player.isOnline()) {
            cancel(player);
            return;
        }
        if (!brush.isBrush(player.getInventory().getItemInMainHand())) {
            cancel(player);
            return;
        }
        if (player.getLocation().distanceSquared(channel.origin) > MOVE_CANCEL * MOVE_CANCEL) {
            cancel(player);
            return;
        }
        Block target = player.getTargetBlockExact(6);
        if (target == null || !channel.sameCell(target)) {
            cancel(player);
            return;
        }
        channel.remaining--;
        Block block = player.getWorld().getBlockAt(channel.x, channel.y, channel.z);
        if (channel.remaining % 5 == 0) {
            playBrush(block);
            player.spigot().sendMessage(
                    ChatMessageType.ACTION_BAR,
                    new TextComponent("Brushing the matrix."));
        }
        if (channel.remaining > 0) {
            return;
        }
        cancel(player);
        finishCell(player, block, channel);
    }

    /**
     * Marks this cube clean; lifts the piece when the tedium cap is met.
     *
     * @param player holder
     * @param block cleaned cell
     * @param channel finished channel
     */
    private void finishCell(Player player, Block block, Channel channel) {
        Site site = sites.findById(channel.siteId).orElse(null);
        if (site == null) {
            return;
        }
        BuriedFind find = null;
        for (BuriedFind candidate : site.getFinds()) {
            if (candidate.getId().equals(channel.findId)) {
                find = candidate;
                break;
            }
        }
        if (find == null || find.getState() != FindState.DISCOVERED) {
            return;
        }
        BlockCell cell = new BlockCell(block.getX(), block.getY(), block.getZ());
        if (!find.getCells().contains(cell) || find.isCleaned(cell)) {
            return;
        }
        if (!PrismFill.isTerrainFill(block.getType())) {
            return;
        }
        find.markCleaned(cell);
        playCleaned(block);
        if (readyToLift(block.getWorld(), find)) {
            liftFind(player, site, find, block);
        }
        sites.save(site);
    }

    /**
     * @param world ruin world
     * @param find shape still in the cut
     * @return whether enough remaining fill cubes have been brushed
     */
    private boolean readyToLift(World world, BuriedFind find) {
        int fill = 0;
        int cleanedFill = 0;
        for (BlockCell cell : find.getCells()) {
            Block block = world.getBlockAt(cell.x(), cell.y(), cell.z());
            if (!PrismFill.isTerrainFill(block.getType())) {
                continue;
            }
            fill++;
            if (find.isCleaned(cell)) {
                cleanedFill++;
            }
        }
        if (fill == 0) {
            return false;
        }
        int needed = Math.min(fill, Math.max(1, settings.maxCellsToClean()));
        return cleanedFill >= needed;
    }

    /**
     * Turns remaining fill to air, drops one item when conservation remains, and archives the find.
     *
     * @param player recoverer
     * @param site excavation
     * @param find shape to lift
     * @param origin last brushed cube
     */
    private void liftFind(Player player, Site site, BuriedFind find, Block origin) {
        World world = origin.getWorld();
        for (BlockCell cell : find.getCells()) {
            Block block = world.getBlockAt(cell.x(), cell.y(), cell.z());
            if (PrismFill.isTerrainFill(block.getType())) {
                clearFill(block);
            }
        }
        find.setState(FindState.RECOVERED);
        int conservation = find.getConservation();
        int damagedBelow = catalogs.pick().damagedBelowPercent();
        boolean damaged = find.isDamaged() || conservation < damagedBelow;
        if (conservation <= 0) {
            player.sendMessage("Those remains were destroyed. Nothing could be recovered.");
            world.playSound(origin.getLocation(), Sound.ENTITY_ITEM_BREAK, SoundCategory.BLOCKS, 0.8f, 0.7f);
            return;
        }
        site.setRecoveredCount(site.getRecoveredCount() + 1);
        ArtifactTemplate template = catalogs.artifact(find.getArtifactId());
        if (template == null) {
            player.sendMessage("Recovered a find, but its template is missing from the catalog.");
            return;
        }
        ItemStack stack = recoveredItem.create(template, site, find, player.getUniqueId(), damaged);
        Location dropAt = origin.getLocation().add(0.5, 0.35, 0.5);
        Item dropped = world.dropItem(dropAt, stack);
        dropped.setVelocity(new Vector(0, 0.12, 0));
        world.playSound(dropAt, Sound.ITEM_BRUSH_BRUSHING_GENERIC, SoundCategory.BLOCKS, 1f, 1.35f);
        world.spawnParticle(Particle.CLOUD, dropAt, 12, 0.25, 0.2, 0.25, 0.02);
        String quality = damaged ? " · damaged · " + conservation + "%" : " · " + conservation + "%";
        player.sendMessage("Recovered: " + template.displayName() + quality);
    }

    /**
     * @param block remaining find fill
     */
    private static void clearFill(Block block) {
        BlockData data = block.getBlockData();
        Location at = block.getLocation().add(0.5, 0.5, 0.5);
        block.setType(Material.AIR, false);
        block.getWorld().spawnParticle(Particle.BLOCK, at, 16, 0.2, 0.2, 0.2, 0.04, data);
    }

    /**
     * @param block cell being brushed
     */
    private static void playBrush(Block block) {
        block.getWorld().playSound(
                block.getLocation(),
                Sound.ITEM_BRUSH_BRUSHING_GENERIC,
                SoundCategory.BLOCKS,
                0.45f,
                1.1f);
        Location at = block.getLocation().add(0.5, 0.7, 0.5);
        block.getWorld().spawnParticle(Particle.CLOUD, at, 4, 0.15, 0.08, 0.15, 0.01);
    }

    /**
     * @param block cube whose leak should stop
     */
    private static void playCleaned(Block block) {
        block.getWorld().playSound(
                block.getLocation(),
                Sound.ITEM_BRUSH_BRUSHING_GENERIC,
                SoundCategory.BLOCKS,
                0.8f,
                1.5f);
        Location at = block.getLocation().add(0.5, 0.6, 0.5);
        block.getWorld().spawnParticle(Particle.WAX_OFF, at, 8, 0.2, 0.2, 0.2, 0.02);
    }

    /**
     * @param block partial find the player clicked too early
     */
    private static void playPuff(Block block) {
        Location at = block.getLocation().add(0.5, 0.6, 0.5);
        block.getWorld().spawnParticle(Particle.CLOUD, at, 3, 0.12, 0.08, 0.12, 0.01);
    }

    /**
     * @param player holder
     * @param message English line
     */
    private void warn(Player player, String message) {
        long now = System.currentTimeMillis();
        Long previous = lastWarn.get(player.getUniqueId());
        if (previous != null && now - previous < WARN_MS) {
            return;
        }
        lastWarn.put(player.getUniqueId(), now);
        player.sendMessage(message);
    }

    /**
     * One player's brush hold on a find cell.
     */
    private static final class Channel {
        private final UUID siteId;
        private final UUID findId;
        private final int x;
        private final int y;
        private final int z;
        private final Location origin;
        private int remaining;
        private BukkitTask task;

        /**
         * @param siteId excavation
         * @param findId shape
         * @param x block X
         * @param y block Y
         * @param z block Z
         * @param origin player location at start
         */
        private Channel(UUID siteId, UUID findId, int x, int y, int z, Location origin) {
            this.siteId = siteId;
            this.findId = findId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.origin = origin;
        }

        /**
         * @param block world cell
         * @return whether this channel is still on that cube
         */
        private boolean sameCell(Block block) {
            return block.getX() == x && block.getY() == y && block.getZ() == z;
        }
    }
}
