package com.nowko.archeology.establish;

import com.nowko.archeology.config.EstablishSettings;
import com.nowko.archeology.item.EstablishItem;
import com.nowko.archeology.model.Site;
import com.nowko.archeology.model.SiteStatus;
import com.nowko.archeology.site.SiteRepository;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lets a player who confirmed prospecting claim the ruin by planting a camp in a neighbor chunk.
 */
public class EstablishService {
    private final JavaPlugin plugin;
    private final SiteRepository sites;
    private final EstablishItem item;
    private EstablishSettings settings;
    private BukkitTask task;
    private final Map<UUID, Map<BlockPos, BlockData>> previews = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();

    /**
     * @param plugin scheduler owner
     * @param sites ruin lookup
     * @param item kit recognition
     * @param settings camp block and client-only preview materials
     */
    public EstablishService(
            JavaPlugin plugin,
            SiteRepository sites,
            EstablishItem item,
            EstablishSettings settings
    ) {
        this.plugin = plugin;
        this.sites = sites;
        this.item = item;
        this.settings = settings;
    }

    /**
     * @param settings values after {@code /archaeo reload}
     */
    public void setSettings(EstablishSettings settings) {
        this.settings = settings;
    }

    /**
     * Starts the hold-to-preview loop.
     */
    public void start() {
        stop();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::pulse, 20L, 5L);
    }

    /**
     * Cancels the preview loop and restores client blocks.
     */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (UUID playerId : new HashSet<>(previews.keySet())) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                clearPreview(player);
            }
        }
        previews.clear();
        for (UUID playerId : new HashSet<>(bars.keySet())) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                hideHud(player);
            } else {
                BossBar bar = bars.remove(playerId);
                if (bar != null) {
                    bar.removeAll();
                }
            }
        }
    }

    /**
     * Shows a client-only camp ghost while the kit is held.
     */
    public void pulse() {
        if (!settings.enabled()) {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                clearPreview(player);
                hideHud(player);
            }
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!holdingKit(player)) {
                clearPreview(player);
                hideHud(player);
                continue;
            }
            Site site = nearestConfirmedHidden(player);
            if (site == null) {
                clearPreview(player);
                showHud(player, false, "No confirmed ruin nearby.");
                continue;
            }
            Block target = player.getTargetBlockExact(64);
            CampPlacement placement = assess(player, site, target);
            applyGhosts(player, site, placement);
            showHud(player, placement.allowed(), placement.reason());
        }
    }

    /**
     * Claims the ruin if the click is a valid camp plant.
     *
     * @param player kit holder
     * @param clicked ground block
     */
    public void tryEstablish(Player player, Block clicked) {
        if (!settings.enabled()) {
            return;
        }
        Site site = nearestConfirmedHidden(player);
        if (site == null) {
            player.sendMessage("Look at a chunk next to a ruin you have confirmed.");
            return;
        }
        CampPlacement placement = assess(player, site, clicked);
        if (!placement.allowed()) {
            player.sendMessage(placement.reason());
            return;
        }
        clearPreview(player);
        World world = clicked.getWorld();
        for (CampPlacement.Ghost ghost : placement.ghosts()) {
            world.getBlockAt(ghost.x(), ghost.y(), ghost.z()).setBlockData(ghost.data(), false);
        }
        Block camp = world.getBlockAt(placement.originX(), placement.originY(), placement.originZ());
        Chunk campChunk = camp.getChunk();
        site.establish(
                player.getUniqueId(),
                campChunk.getX(),
                campChunk.getZ(),
                camp.getX(),
                camp.getY(),
                camp.getZ()
        );
        sites.save(site);
        consumeOne(player);
        player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, SoundCategory.PLAYERS, 0.8f, 1.1f);
        player.sendMessage("You established an archaeological excavation.");
        player.sendMessage(site.displayLabel());
        player.sendMessage("Director: " + player.getName());
    }

    /**
     * @param player online player
     * @return {@code true} if main or off hand is the establishment kit
     */
    private boolean holdingKit(Player player) {
        return item.isEstablish(player.getInventory().getItemInMainHand())
                || item.isEstablish(player.getInventory().getItemInOffHand());
    }

    /**
     * Nearest hidden ruin this player has prospect-confirmed, within two chunks.
     *
     * @param player kit holder
     * @return site, or {@code null}
     */
    private Site nearestConfirmedHidden(Player player) {
        Location here = player.getLocation();
        Site best = null;
        double bestDist = Double.MAX_VALUE;
        for (Site site : sites.all()) {
            if (site.getStatus() != SiteStatus.HIDDEN) {
                continue;
            }
            if (!site.isProspectConfirmed(player.getUniqueId())) {
                continue;
            }
            if (!here.getWorld().getName().equals(site.getWorldName())) {
                continue;
            }
            int pcx = here.getChunk().getX();
            int pcz = here.getChunk().getZ();
            if (Math.max(Math.abs(pcx - site.getChunkX()), Math.abs(pcz - site.getChunkZ())) > 2) {
                continue;
            }
            double dist = Math.hypot(here.getX() - site.centerBlockX(), here.getZ() - site.centerBlockZ());
            if (dist < bestDist) {
                bestDist = dist;
                best = site;
            }
        }
        return best;
    }

    /**
     * Builds a ghost at {@code target} and scores chunk plus terrain.
     *
     * @param player kit holder (yaw)
     * @param site confirmed ruin
     * @param target looked-at or clicked ground, or {@code null}
     * @return whether confirm is allowed, a reason, and ghost cells
     */
    private CampPlacement assess(Player player, Site site, Block target) {
        if (target == null) {
            return new CampPlacement(false, CampPlacement.Issue.LOOK_MISS.message(), List.of(), 0, 0, 0);
        }
        CampPlacement.Issue chunkIssue = chunkIssue(site, target);
        boolean chunkOk = chunkIssue == null;
        List<CampTemplate.Piece> pieces = CampTemplate.basic();
        BlockFace facing = CampTemplate.facingFromYaw(player.getLocation().getYaw());
        int[] bounds = CampTemplate.offsetBounds(pieces, facing);
        Chunk chunk = target.getChunk();
        int minX = chunk.getX() << 4;
        int minZ = chunk.getZ() << 4;
        int originX = CampTemplate.clampOrigin(target.getX(), bounds[0], bounds[1], minX, minX + 15);
        int originZ = CampTemplate.clampOrigin(target.getZ(), bounds[2], bounds[3], minZ, minZ + 15);
        int originY = target.getY() + 1;
        World world = target.getWorld();
        List<CampPlacement.Ghost> ghosts = new ArrayList<>();
        CampPlacement.Issue terrainIssue = null;
        BlockData invalid = settings.invalidBlock().createBlockData();
        for (CampTemplate.Piece piece : pieces) {
            Vector offset = CampTemplate.rotate(piece.dx(), piece.dz(), facing);
            int x = originX + offset.getBlockX();
            int y = originY + piece.dy();
            int z = originZ + offset.getBlockZ();
            CampPlacement.Issue cell = cellIssue(world.getBlockAt(x, y, z), piece.dy() == 0);
            boolean fitting = chunkOk && cell == null;
            if (chunkOk && cell != null && terrainIssue == null) {
                terrainIssue = cell;
            }
            BlockData shown = fitting ? CampTemplate.dataFor(piece.material(), facing) : invalid;
            ghosts.add(new CampPlacement.Ghost(x, y, z, shown, fitting));
        }
        if (!chunkOk) {
            return new CampPlacement(false, chunkIssue.message(), ghosts, originX, originY, originZ);
        }
        if (terrainIssue != null) {
            return new CampPlacement(false, terrainIssue.message(), ghosts, originX, originY, originZ);
        }
        return new CampPlacement(true, "Right-click the ground to establish.", ghosts, originX, originY, originZ);
    }

    /**
     * @param site confirmed ruin
     * @param target aimed block
     * @return chunk-level problem, or {@code null} if this is a free neighbor
     */
    private CampPlacement.Issue chunkIssue(Site site, Block target) {
        Chunk chunk = target.getChunk();
        String world = chunk.getWorld().getName();
        int cx = chunk.getX();
        int cz = chunk.getZ();
        if (world.equals(site.getWorldName()) && cx == site.getChunkX() && cz == site.getChunkZ()) {
            return CampPlacement.Issue.ON_DIG;
        }
        if (sites.chunkOccupied(world, cx, cz)) {
            return CampPlacement.Issue.OCCUPIED;
        }
        if (!isValidCampChunk(site, world, cx, cz)) {
            return CampPlacement.Issue.NOT_NEIGHBOR;
        }
        return null;
    }

    /**
     * @param cell template cell in the world
     * @param floor whether this is a ground-level piece that needs solid support
     * @return terrain problem, or {@code null} if the cell can be planted
     */
    private CampPlacement.Issue cellIssue(Block cell, boolean floor) {
        if (!isFree(cell)) {
            return CampPlacement.Issue.BLOCKED;
        }
        if (!floor) {
            return null;
        }
        Block below = cell.getRelative(BlockFace.DOWN);
        if (below.isLiquid()) {
            return CampPlacement.Issue.FLUID;
        }
        if (!below.getType().isSolid()) {
            return CampPlacement.Issue.UNSUPPORTED;
        }
        return null;
    }

    /**
     * @param block world block
     * @return whether a camp piece may occupy this cell
     */
    private boolean isFree(Block block) {
        return block.getType().isAir() || (block.isPassable() && !block.isLiquid());
    }

    /**
     * Sends the ghost, the dig-chunk frame, and restores cells that left the set.
     *
     * @param player viewer
     * @param site confirmed ruin (frame)
     * @param placement current ghost
     */
    private void applyGhosts(Player player, Site site, CampPlacement placement) {
        Map<BlockPos, BlockData> desired = new HashMap<>();
        paintRuinOutline(player.getWorld(), site, desired);
        String worldName = player.getWorld().getName();
        for (CampPlacement.Ghost ghost : placement.ghosts()) {
            desired.put(new BlockPos(worldName, ghost.x(), ghost.y(), ghost.z()), ghost.data());
        }
        applyPreview(player, desired);
    }

    /**
     * Client-only perimeter of the archaeological chunk so the dig is readable at a glance.
     *
     * @param world viewer world
     * @param site confirmed ruin
     * @param desired map to fill
     */
    private void paintRuinOutline(World world, Site site, Map<BlockPos, BlockData> desired) {
        if (!world.getName().equals(site.getWorldName())) {
            return;
        }
        if (!world.isChunkLoaded(site.getChunkX(), site.getChunkZ())) {
            return;
        }
        BlockData data = settings.ruinOutlineBlock().createBlockData();
        int minX = site.getChunkX() << 4;
        int minZ = site.getChunkZ() << 4;
        int maxX = minX + 15;
        int maxZ = minZ + 15;
        for (int x = minX; x <= maxX; x++) {
            addSurface(world, x, minZ, data, desired);
            addSurface(world, x, maxZ, data, desired);
        }
        for (int z = minZ + 1; z <= maxZ - 1; z++) {
            addSurface(world, minX, z, data, desired);
            addSurface(world, maxX, z, data, desired);
        }
    }

    /**
     * @param world world
     * @param x block X
     * @param z block Z
     * @param data client-only block
     * @param desired map to fill
     */
    private void addSurface(World world, int x, int z, BlockData data, Map<BlockPos, BlockData> desired) {
        Block top = world.getHighestBlockAt(x, z);
        desired.put(new BlockPos(world.getName(), top.getX(), top.getY(), top.getZ()), data);
    }

    /**
     * @param site hidden ruin
     * @param world world name
     * @param chunkX candidate camp chunk X
     * @param chunkZ candidate camp chunk Z
     * @return whether the chunk shares a side with the dig and is not already occupied
     */
    boolean isValidCampChunk(Site site, String world, int chunkX, int chunkZ) {
        if (!world.equals(site.getWorldName())) {
            return false;
        }
        if (!isNeighborChunk(site.getChunkX(), site.getChunkZ(), chunkX, chunkZ)) {
            return false;
        }
        return !sites.chunkOccupied(world, chunkX, chunkZ);
    }

    /**
     * Four side-adjacent chunks around the dig; corners and the dig itself are excluded.
     *
     * @param siteX ruin chunk X
     * @param siteZ ruin chunk Z
     * @param chunkX other chunk X
     * @param chunkZ other chunk Z
     * @return whether they share a full side
     */
    static boolean isNeighborChunk(int siteX, int siteZ, int chunkX, int chunkZ) {
        int dx = Math.abs(chunkX - siteX);
        int dz = Math.abs(chunkZ - siteZ);
        return (dx == 1 && dz == 0) || (dx == 0 && dz == 1);
    }

    /**
     * Diffs against the last preview and sends only new or changed client blocks.
     *
     * @param player viewer
     * @param desired locations to show
     */
    private void applyPreview(Player player, Map<BlockPos, BlockData> desired) {
        Map<BlockPos, BlockData> previous = previews.getOrDefault(player.getUniqueId(), Map.of());
        World world = player.getWorld();
        for (BlockPos pos : previous.keySet()) {
            if (desired.containsKey(pos)) {
                continue;
            }
            restore(player, world, pos);
        }
        for (Map.Entry<BlockPos, BlockData> entry : desired.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockData data = entry.getValue();
            if (data.equals(previous.get(pos))) {
                continue;
            }
            player.sendBlockChange(pos.toLocation(world), data);
        }
        previews.put(player.getUniqueId(), desired);
    }

    /**
     * Restores real blocks for this player's preview.
     *
     * @param player viewer
     */
    public void clearPreview(Player player) {
        Map<BlockPos, BlockData> previous = previews.remove(player.getUniqueId());
        if (previous == null || previous.isEmpty()) {
            return;
        }
        World world = player.getWorld();
        for (BlockPos pos : previous.keySet()) {
            restore(player, world, pos);
        }
    }

    /**
     * Restores fake blocks in the world the player just left.
     *
     * @param player viewer
     * @param from previous world
     */
    public void clearPreviewFromWorld(Player player, World from) {
        Map<BlockPos, BlockData> previous = previews.remove(player.getUniqueId());
        if (previous == null || previous.isEmpty()) {
            return;
        }
        for (BlockPos pos : previous.keySet()) {
            restore(player, from, pos);
        }
    }

    /**
     * @param player viewer
     * @param world player's world
     * @param pos fake block
     */
    private void restore(Player player, World world, BlockPos pos) {
        if (!pos.world().equals(world.getName())) {
            return;
        }
        Block real = world.getBlockAt(pos.x(), pos.y(), pos.z());
        player.sendBlockChange(real.getLocation(), real.getBlockData());
    }

    /**
     * World-block key for client-only previews.
     *
     * @param world world name
     * @param x block X
     * @param y block Y
     * @param z block Z
     */
    private record BlockPos(String world, int x, int y, int z) {
        /**
         * @param world loaded world with this name
         * @return Bukkit location
         */
        Location toLocation(World world) {
            return new Location(world, x, y, z);
        }
    }

    /**
     * Removes one kit from the main hand, or the off hand if that is what they used.
     *
     * @param player kit holder
     */
    private void consumeOne(Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        if (item.isEstablish(main)) {
            main.setAmount(main.getAmount() - 1);
            return;
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        if (item.isEstablish(off)) {
            off.setAmount(off.getAmount() - 1);
        }
    }

    /**
     * Player-specific boss bar so the reason does not steal the action bar from other plugins.
     *
     * @param player viewer
     * @param allowed whether the current aim would confirm
     * @param reason English line
     */
    private void showHud(Player player, boolean allowed, String reason) {
        BossBar bar = bars.computeIfAbsent(
                player.getUniqueId(),
                id -> plugin.getServer().createBossBar("", BarColor.WHITE, BarStyle.SOLID));
        if (!bar.getPlayers().contains(player)) {
            bar.addPlayer(player);
        }
        bar.setVisible(true);
        bar.setProgress(1.0);
        bar.setColor(allowed ? BarColor.GREEN : BarColor.RED);
        bar.setTitle((allowed ? ChatColor.WHITE : ChatColor.RED) + reason);
    }

    /**
     * @param player viewer
     */
    public void hideHud(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar == null) {
            return;
        }
        bar.removePlayer(player);
        bar.removeAll();
    }
}
