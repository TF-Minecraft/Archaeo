package net.tfminecraft.archaeo.excavation;

import net.tfminecraft.archaeo.config.ArtifactTemplate;
import net.tfminecraft.archaeo.config.CatalogRegistry;
import net.tfminecraft.archaeo.config.RecoverySettings;
import net.tfminecraft.archaeo.events.FindRecoveredEvent;
import net.tfminecraft.archaeo.item.BrushItem;
import net.tfminecraft.archaeo.item.RecoveredFindItem;
import net.tfminecraft.archaeo.item.ToolWear;
import net.tfminecraft.archaeo.model.BlockCell;
import net.tfminecraft.archaeo.model.BuriedFind;
import net.tfminecraft.archaeo.model.FindState;
import net.tfminecraft.archaeo.model.Site;
import net.tfminecraft.archaeo.site.SiteClosure;
import net.tfminecraft.archaeo.site.SiteRepository;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Brushes matrix off a fully exposed find. The last required cube drops one item and lifts the shape.
 */
public class RecoverService {
    private static final long WARN_MS = 3000L;

    private final JavaPlugin plugin;
    private final SiteRepository sites;
    private final CatalogRegistry catalogs;
    private final BrushItem brush;
    private final RecoveredFindItem recoveredItem;
    private RecoverySettings settings;
    private final Map<UUID, Channel> channels = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastWarn = new ConcurrentHashMap<>();
    /** Wall clock in milliseconds for the warning cooldown; tests replace it to step past the cooldown. */
    LongSupplier clock = System::currentTimeMillis;

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
        for (Channel channel : List.copyOf(channels.values())) {
            stash(channel);
            hideBar(channel);
            channel.task.cancel();
        }
        channels.clear();
    }

    /**
     * Parks HUD and remaining ticks, then stops the look watcher.
     *
     * @param player holder
     */
    public void cancel(Player player) {
        Channel channel = channels.get(player.getUniqueId());
        stash(channel);
        teardown(player);
    }

    /**
     * Keeps a look watcher so an unfinished cube can show its bar again when aimed with the brush.
     *
     * @param player holder
     */
    public void watch(Player player) {
        if (!settings.enabled()) {
            return;
        }
        if (!brush.isBrush(player.getInventory().getItemInMainHand())) {
            return;
        }
        if (channels.containsKey(player.getUniqueId())) {
            return;
        }
        Channel channel = new Channel(createBar(player), Math.max(1, settings.channelTicks()));
        channel.task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> pulse(player, channel), 1L, 1L);
        channels.put(player.getUniqueId(), channel);
    }

    /**
     * Starts or resumes dusting on a fully exposed find cell. Remaining ticks live on the find cube
     * so looking away does not reset the bar.
     *
     * @param player holder
     * @param block clicked cell
     */
    public void begin(Player player, Block block) {
        if (!settings.enabled()) {
            return;
        }
        if (!brush.isBrush(player.getInventory().getItemInMainHand())) {
            return;
        }
        Site site = sites.findEstablishedPrism(
                block.getWorld().getName(),
                block.getX(),
                block.getY(),
                block.getZ()).orElse(null);
        if (site == null) {
            return;
        }
        if (deniedRecovery(player, site)) {
            return;
        }
        BuriedFind find = site.findAt(new BlockCell(block.getX(), block.getY(), block.getZ())).orElse(null);
        if (find == null || find.getState() == FindState.LOST || find.getState() == FindState.RECOVERED) {
            return;
        }
        if (find.getState() != FindState.DISCOVERED) {
            warn(player, "The shape is not fully free.");
            playPuff(block);
            return;
        }
        if (!PrismFill.isTerrainFill(block.getType())) {
            return;
        }
        BlockCell cell = new BlockCell(block.getX(), block.getY(), block.getZ());
        if (find.isCleaned(cell)) {
            warn(player, "That cube is already clean.");
            return;
        }
        // Recovery is on and a brush is held, so watch always leaves a channel.
        watch(player);
        Channel channel = channels.get(player.getUniqueId());
        if (channel.focused && channel.sameCell(block) && channel.findId.equals(find.getId())
                && find.brushRemaining(cell) != null) {
            channel.startGrace = 3;
            return;
        }
        stash(channel);
        attach(channel, site, find, block);
        channel.startGrace = 3;
        find.setBrushRemaining(cell, channel.remaining);
        showHud(channel, player);
        playBrush(block);
    }

    /**
     * Hides the bar when the player looks away; restores the stored remaining ticks when they
     * aim at that cube again with the brush.
     *
     * @param player holder
     * @param channel look watcher
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
        Block target = player.getTargetBlockExact(6);
        Site site = null;
        BuriedFind find = null;
        if (target != null) {
            site = sites.findEstablishedPrism(
                    target.getWorld().getName(),
                    target.getX(),
                    target.getY(),
                    target.getZ()).orElse(null);
            if (site != null) {
                if (!site.mayRecover(player.getUniqueId())) {
                    cancel(player);
                    deniedRecovery(player, site);
                    return;
                }
                find = site.findAt(new BlockCell(target.getX(), target.getY(), target.getZ())).orElse(null);
            }
        }
        // A find is only looked up for a real target, so a missing target leaves it null.
        if (!isDustable(target, find)) {
            stash(channel);
            hideHud(channel);
            channel.clearFocus();
            return;
        }
        BlockCell cell = new BlockCell(target.getX(), target.getY(), target.getZ());
        if (!channel.focused || !channel.sameCell(target) || !channel.findId.equals(find.getId())) {
            stash(channel);
            attach(channel, site, find, target);
        }
        Integer saved = find.brushRemaining(cell);
        if (saved == null) {
            hideHud(channel);
            return;
        }
        channel.remaining = Math.min(channel.duration, saved);
        showHud(channel, player);
        if (!isUsingBrush(player)) {
            if (channel.startGrace > 0) {
                channel.startGrace--;
            } else {
                return;
            }
        } else {
            channel.startGrace = 0;
        }
        int tick = Bukkit.getCurrentTick();
        for (Channel peer : channels.values()) {
            // One cube of one site holds a single find, so the site id is enough to match it.
            if (peer.lastBrushTick == tick && peer.sameCell(target) && site.getId().equals(peer.siteId)) {
                return;
            }
        }
        channel.lastBrushTick = tick;
        channel.remaining--;
        find.setBrushRemaining(cell, channel.remaining);
        updateBar(channel);
        if (channel.remaining % 5 == 0) {
            playBrush(target);
        }
        if (channel.remaining > 0) {
            return;
        }
        find.setBrushRemaining(cell, 0);
        teardown(player);
        finishCell(player, target, site, find, cell);
    }

    /**
     * Marks this cube clean; lifts the piece when the tedium cap is met.
     *
     * @param player holder allowed to recover here
     * @param block cleaned cell, still fill
     * @param site excavation
     * @param find shape
     * @param cell cleaned coordinates
     */
    private void finishCell(Player player, Block block, Site site, BuriedFind find, BlockCell cell) {
        // The pulse has just checked that this is uncleaned fill of a discovered find and that
        // the player may recover here, so the staff log exists.
        find.markCleaned(cell);
        site.staffLog(player.getUniqueId()).noteCellBrushed();
        ToolWear.spend(
                player,
                player.getInventory().getItemInMainHand(),
                catalogs.toolWear().brush(),
                catalogs.toolWear().unbreaking());
        playCleaned(block);
        if (readyToLift(block.getWorld(), find)) {
            liftFind(player, site, find, block);
        }
        sites.save(site);
        SiteClosure.settle(sites, site);
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
        // The cube just cleaned is still fill, so there is at least one.
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
        // A find whose condition reaches zero is already LOST, so a discovered one has some left.
        find.setState(FindState.RECOVERED);
        int conservation = find.getConservation();
        String grade = catalogs.pick().conservation().gradeLabel(conservation);
        boolean fieldDamaged = find.isFieldDamaged();
        site.catalogSettledFinds(player.getUniqueId());
        site.setRecoveredCount(site.getRecoveredCount() + 1);
        site.staffLog(player.getUniqueId()).noteFindRecovered();
        ArtifactTemplate template = catalogs.artifact(find.getArtifactId());
        if (template == null) {
            player.sendMessage("Recovered a find, but its template is missing from the catalog.");
            return;
        }
        ItemStack stack = recoveredItem.create(
                template, site, find, player.getUniqueId(), grade, fieldDamaged, catalogs);
        Location dropAt = origin.getLocation().add(0.5, 0.35, 0.5);
        Item dropped = world.dropItem(dropAt, stack);
        dropped.setVelocity(new Vector(0, 0.12, 0));
        world.playSound(dropAt, Sound.ITEM_BRUSH_BRUSHING_GENERIC, SoundCategory.BLOCKS, 1f, 1.35f);
        world.spawnParticle(Particle.CLOUD, dropAt, 12, 0.25, 0.2, 0.25, 0.02);
        Bukkit.getPluginManager().callEvent(new FindRecoveredEvent(player, stack.clone()));
        StringBuilder quality = new StringBuilder(" · ").append(conservation).append('%');
        if (!grade.isBlank()) {
            quality.append(" · ").append(grade.toLowerCase(Locale.ROOT));
        }
        if (find.isDisturbedBeforeDig()) {
            quality.append(" · disturbed before the dig");
        }
        if (fieldDamaged) {
            quality.append(" · hurt while digging");
        }
        player.sendMessage("Recovered: " + find.publicNumber(site) + " · "
                + template.displayName() + quality);
    }

    /**
     * Clears the matrix still holding the piece. Broken the vanilla way, with no tool argument:
     * the matrix is being lifted properly, so it leaves its spoil and whatever sat on top of it
     * comes down with it.
     *
     * @param block remaining find fill
     */
    private static void clearFill(Block block) {
        BlockData data = block.getBlockData();
        Location at = block.getLocation().add(0.5, 0.5, 0.5);
        block.breakNaturally();
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
     * Creates a boss bar for this hold when {@code recovery.progress-bar} is on.
     *
     * @param player viewer
     * @return shown bar, or {@code null} when disabled
     */
    private BossBar createBar(Player player) {
        if (!settings.progressBar()) {
            return null;
        }
        BossBar bar = Bukkit.createBossBar("Brushing", BarColor.YELLOW, BarStyle.SEGMENTED_10);
        bar.setProgress(0);
        bar.setVisible(false);
        bar.addPlayer(player);
        return bar;
    }

    /**
     * @param channel open brush
     */
    private static void updateBar(Channel channel) {
        if (channel.bar == null) {
            return;
        }
        double elapsed = channel.duration - channel.remaining;
        channel.bar.setProgress(Math.max(0, Math.min(1, elapsed / channel.duration)));
    }

    /**
     * @param channel finished or aborted brush
     */
    private static void hideBar(Channel channel) {
        if (channel.bar == null) {
            return;
        }
        channel.bar.removeAll();
        channel.bar.setVisible(false);
    }

    /**
     * @param player holder
     * @param message English line
     */
    private void warn(Player player, String message) {
        long now = clock.getAsLong();
        Long previous = lastWarn.get(player.getUniqueId());
        if (previous != null && now - previous < WARN_MS) {
            return;
        }
        lastWarn.put(player.getUniqueId(), now);
        player.sendMessage(message);
    }

    /**
     * Lifting a piece is the archaeologist's job, so a worker who may still swing a pick can be
     * turned away here. The two refusals read differently on purpose: one is "you do not work
     * here", the other is "this is not your part of the work".
     *
     * @param player holder
     * @param site excavation under the brush
     * @return whether the brush must stop
     */
    private boolean deniedRecovery(Player player, Site site) {
        if (site.mayRecover(player.getUniqueId())) {
            return false;
        }
        warn(player, site.mayWork(player.getUniqueId())
                ? "Your role does not lift pieces on this excavation."
                : "You are not authorised to work on this excavation.");
        return true;
    }

    /**
     * Marks shared cube progress dirty so a later look with the brush can restore the bar.
     * Each active pulse already updates the cube; a paused channel must not overwrite newer work.
     *
     * @param channel watcher, or {@code null}
     */
    private void stash(Channel channel) {
        if (channel == null || !channel.focused) {
            return;
        }
        Site site = sites.findById(channel.siteId).orElse(null);
        if (site == null) {
            return;
        }
        BuriedFind find = findOn(site, channel.findId);
        if (find == null) {
            return;
        }
        sites.touch(site);
    }

    /**
     * Loads stored remaining ticks for this cube onto the watcher.
     *
     * @param channel watcher
     * @param site excavation
     * @param find shape
     * @param block aimed cell
     */
    private void attach(Channel channel, Site site, BuriedFind find, Block block) {
        channel.focused = true;
        channel.siteId = site.getId();
        channel.findId = find.getId();
        channel.x = block.getX();
        channel.y = block.getY();
        channel.z = block.getZ();
        channel.duration = Math.max(1, settings.channelTicks());
        Integer saved = find.brushRemaining(new BlockCell(block.getX(), block.getY(), block.getZ()));
        channel.remaining = saved == null ? channel.duration : Math.min(channel.duration, saved);
        channel.startGrace = 0;
        channel.lastBrushTick = -1;
    }

    /**
     * @param block aimed cube, or {@code null}
     * @param find shape at that cube; {@code null} whenever {@code block} is
     * @return whether the brush can still work this fill cell
     */
    private static boolean isDustable(Block block, BuriedFind find) {
        if (find == null) {
            return false;
        }
        if (find.getState() != FindState.DISCOVERED) {
            return false;
        }
        BlockCell cell = new BlockCell(block.getX(), block.getY(), block.getZ());
        if (find.isCleaned(cell)) {
            return false;
        }
        return PrismFill.isTerrainFill(block.getType());
    }

    /**
     * @param site excavation
     * @param findId shape id
     * @return the find, or {@code null}
     */
    private static BuriedFind findOn(Site site, UUID findId) {
        for (BuriedFind candidate : site.getFinds()) {
            if (candidate.getId().equals(findId)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * @param channel watcher
     * @param player viewer
     */
    private static void showHud(Channel channel, Player player) {
        // The bar was created for this player and is only emptied when the channel ends.
        if (channel.bar == null) {
            return;
        }
        channel.bar.setVisible(true);
        updateBar(channel);
    }

    /**
     * @param channel watcher
     */
    private static void hideHud(Channel channel) {
        if (channel.bar == null) {
            return;
        }
        channel.bar.setVisible(false);
    }

    /**
     * Stops a brush channel aimed at this excavation so a staff wipe does not leave a HUD bar.
     *
     * @param siteId id of the excavation being erased
     */
    public void abortForSite(UUID siteId) {
        for (Map.Entry<UUID, Channel> entry : List.copyOf(channels.entrySet())) {
            Channel channel = entry.getValue();
            if (!siteId.equals(channel.siteId)) {
                continue;
            }
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player != null) {
                teardown(player);
                continue;
            }
            hideBar(channel);
            channel.task.cancel();
            channels.remove(entry.getKey());
        }
    }

    /**
     * @param player holder
     */
    private void teardown(Player player) {
        Channel channel = channels.remove(player.getUniqueId());
        if (channel == null) {
            return;
        }
        hideBar(channel);
        channel.task.cancel();
    }

    /**
     * Spigot does not fire {@code PlayerInteractEvent} again while a brush is held
     * ({@code SPIGOT-7501}). {@link Player#getItemInUse()} is true for those few ticks.
     *
     * @param player holder whose main hand is already known to be a brush
     * @return whether the main-hand brush is currently being used
     */
    private boolean isUsingBrush(Player player) {
        ItemStack using = player.getItemInUse();
        if (using != null) {
            return brush.isBrush(using);
        }
        return player.getItemInUseTicks() > 0;
    }

    /**
     * One player's brush look-watcher. Remaining ticks are stored on the find cube, not only here.
     */
    private static final class Channel {
        private boolean focused;
        private UUID siteId;
        private UUID findId;
        private int x;
        private int y;
        private int z;
        private int duration;
        private final BossBar bar;
        private int remaining;
        /** Coordinates same-cell watchers so several brushes still consume only one tick of work. */
        private int lastBrushTick = -1;
        /** First pulses after a click may run before the server marks the brush as in-use. */
        private int startGrace;
        private BukkitTask task;

        /**
         * @param bar progress shown to the player, or {@code null}
         * @param duration ticks until a fresh cube is clean
         */
        private Channel(BossBar bar, int duration) {
            this.bar = bar;
            this.duration = duration;
            this.remaining = duration;
        }

        /**
         * Forgets the aimed cube without dropping stored remaining ticks.
         */
        private void clearFocus() {
            focused = false;
            siteId = null;
            findId = null;
        }

        /**
         * @param block world cell
         * @return whether this watcher is still on that cube
         */
        private boolean sameCell(Block block) {
            return block.getX() == x && block.getY() == y && block.getZ() == z;
        }
    }
}
