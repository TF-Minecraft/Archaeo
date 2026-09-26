package net.tfminecraft.archaeo.establish;

import net.tfminecraft.archaeo.config.EstablishSettings;
import net.tfminecraft.archaeo.excavation.FindDustService;
import net.tfminecraft.archaeo.excavation.PrismWound;
import net.tfminecraft.archaeo.item.EstablishItem;
import net.tfminecraft.archaeo.item.SiteLabelRefresh;
import net.tfminecraft.archaeo.model.BlockCell;
import net.tfminecraft.archaeo.model.Site;
import net.tfminecraft.archaeo.model.SiteStatus;
import net.tfminecraft.archaeo.site.SiteRepository;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BlockIterator;
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
    private final Map<UUID, UUID> relocating = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> renameForSite = new ConcurrentHashMap<>();
    private final Map<UUID, String> woolChoicePrimary = new ConcurrentHashMap<>();
    private final Map<UUID, String> woolChoiceSecondary = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> woolCycleTick = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> moveAimProxies = new ConcurrentHashMap<>();
    private final Map<UUID, Pulse> lastPulse = new ConcurrentHashMap<>();
    private FindDustService findDust;
    private SiteLabelRefresh labels;

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
     * @param findDust leak loop, started when this camp opens an excavation whose chunk is loaded
     */
    public void setFindDust(FindDustService findDust) {
        this.findDust = findDust;
    }

    /**
     * @param labels rewrite of {@code #name-n} on recovered pieces and sketches
     */
    public void setLabelRefresh(SiteLabelRefresh labels) {
        this.labels = labels;
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
        lastPulse.clear();
        for (UUID playerId : new HashSet<>(moveAimProxies.keySet())) {
            removeMoveAimProxy(playerId);
        }
        for (BossBar bar : bars.values()) {
            bar.removeAll();
        }
        bars.clear();
    }

    /**
     * Shows a client-only camp ghost while the kit is held.
     */
    public void pulse() {
        if (!settings.enabled()) {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                cancelRelocate(player);
                clearPreview(player);
                hideHud(player);
            }
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Site moving = relocatingSite(player);
            if (moving != null) {
                pulsePlacement(player, moving, true);
                updateMoveAimProxy(player);
                continue;
            }
            removeMoveAimProxy(player);
            if (!holdingKit(player)) {
                clearPreview(player);
                hideHud(player);
                continue;
            }
            if (atDirectorCap(player)) {
                clearPreview(player);
                showHud(player, false, directorCapHint());
                continue;
            }
            Site site = nearestConfirmedHidden(player);
            if (site == null) {
                clearPreview(player);
                showHud(player, false, "No confirmed ruin nearby.");
                continue;
            }
            pulsePlacement(player, site, false);
        }
    }

    /**
     * Draws ghost, outline, and HUD for one viewer.
     *
     * @param player viewer
     * @param site ruin or excavation
     * @param moving relocate mode
     */
    private void pulsePlacement(Player player, Site site, boolean moving) {
        CampPlacement placement = assess(player, site, aimBlock(player, site));
        lastPulse.put(player.getUniqueId(), new Pulse(site.getId(), placement));
        applyGhosts(player, site, placement);
        if (placement.allowed()) {
            String action = moving
                    ? "Right-click to move. Left-click or type cancel to abort."
                    : "Right-click to establish.";
            showHud(player, true, action);
        } else {
            showHud(player, false, placement.reason());
        }
    }

    /**
     * Kit right-click plants a new camp. Placement follows the look ray, same as the ghost.
     *
     * @param player kit holder
     * @param clicked block the client reported, or {@code null} for air; planted camp pieces are ignored
     */
    public void tryUseKit(Player player, Block clicked) {
        if (!settings.enabled()) {
            return;
        }
        if (isLockedCampBlock(clicked)) {
            return;
        }
        if (isRelocating(player)) {
            tryFinishMove(player);
            return;
        }
        if (atDirectorCap(player)) {
            player.sendMessage(directorCapHint());
            return;
        }
        Site site = nearestConfirmedHidden(player);
        if (site == null) {
            player.sendMessage("Look at a chunk next to a ruin you have confirmed.");
            return;
        }
        tryPlace(player, site, false);
    }

    /**
     * Confirms a move started from the excavation board, using the same look as the ghost.
     *
     * @param player director
     * @return {@code true} if this player was relocating
     */
    public boolean tryFinishMove(Player player) {
        Site moving = relocatingSite(player);
        if (moving == null) {
            return false;
        }
        tryPlace(player, moving, true);
        return true;
    }

    /**
     * Confirms a move when the director right-clicks the invisible aim proxy.
     * Empty-hand right-click air sends no packet; the kit does, which is why first plant worked.
     *
     * @param player director
     * @param clicked entity under the crosshair
     * @return whether this was this player's move proxy
     */
    public boolean tryFinishMoveOnAimProxy(Player player, Entity clicked) {
        UUID standId = moveAimProxies.get(player.getUniqueId());
        if (standId == null || !standId.equals(clicked.getUniqueId())) {
            return false;
        }
        tryFinishMove(player);
        return true;
    }

    /**
     * Left-click on the aim proxy aborts the move.
     *
     * @param player director
     * @param clicked damaged entity
     * @return whether this was this player's move proxy
     */
    public boolean handleMoveAimProxyAttack(Player player, Entity clicked) {
        UUID standId = moveAimProxies.get(player.getUniqueId());
        if (standId == null || !standId.equals(clicked.getUniqueId())) {
            return false;
        }
        tryCancelMove(player);
        return true;
    }

    /**
     * Aborts relocate mode if this player is moving a camp.
     *
     * @param player director
     * @return whether a move was cancelled
     */
    public boolean tryCancelMove(Player player) {
        if (!isRelocating(player)) {
            return false;
        }
        cancelRelocate(player);
        player.sendMessage("Camp move cancelled. It stays where it is.");
        return true;
    }

    /**
     * Starts relocate mode from the board. The camp stays until a new spot is confirmed.
     *
     * @param player director
     * @param site excavation
     */
    public void beginRelocate(Player player, Site site) {
        relocating.put(player.getUniqueId(), site.getId());
        woolChoicePrimary.remove(player.getUniqueId());
        woolChoiceSecondary.remove(player.getUniqueId());
        player.sendMessage("Moving the camp. Right-click to place the ghost. Left-click or type cancel to abort.");
    }

    /**
     * Starts chat rename from the board.
     *
     * @param player director
     * @param site excavation
     */
    public void beginRename(Player player, Site site) {
        renameForSite.put(player.getUniqueId(), site.getId());
        player.sendMessage("Type the new excavation name in chat, or type cancel.");
    }

    /**
     * Drops a pending rename so another board chat prompt can take the next line.
     *
     * @param player director
     */
    public void abortRename(Player player) {
        renameForSite.remove(player.getUniqueId());
    }

    /**
     * Applies a chat line if this player is renaming.
     *
     * @param player director
     * @param raw chat text
     * @return whether the message was consumed
     */
    public boolean handleRenameChat(Player player, String raw) {
        UUID siteId = renameForSite.remove(player.getUniqueId());
        if (siteId == null) {
            return false;
        }
        if (raw.equalsIgnoreCase("cancel")) {
            player.sendMessage("Rename cancelled.");
            return true;
        }
        Site site = sites.findById(siteId).orElse(null);
        if (site == null || !site.isCampLocked()) {
            player.sendMessage("That excavation is no longer active.");
            return true;
        }
        if (!site.isDirector(player.getUniqueId())) {
            return true;
        }
        String name = raw.replace('§', ' ').trim();
        if (name.isEmpty() || name.length() > 40) {
            player.sendMessage("Use a name of 1–40 characters.");
            renameForSite.put(player.getUniqueId(), siteId);
            return true;
        }
        site.setName(name);
        sites.save(site);
        if (labels != null) {
            labels.retitle(site);
        }
        if (site.getCampSignX() != null) {
            World world = plugin.getServer().getWorld(site.getWorldName());
            if (world != null) {
                CampSigns.write(
                        world.getBlockAt(site.getCampSignX(), site.getCampSignY(), site.getCampSignZ()),
                        site.publicName());
            }
        }
        player.sendMessage("Excavation renamed to " + site.publicName() + ".");
        return true;
    }

    /**
     * Cycles secondary wool ({@code R}) on a left-click while sneak is held during first plant.
     * Relocate mode does not change colour.
     *
     * @param player viewer
     * @return whether wool was (or already was this tick) cycled
     */
    public boolean tryCycleWool(Player player) {
        if (isRelocating(player)) {
            return false;
        }
        if (!sneakHeld(player)) {
            return false;
        }
        if (!holdingKit(player)) {
            return false;
        }
        cycleWool(player);
        return true;
    }

    /**
     * Shift key or sneak pose. Entity attacks often report {@code isSneaking()} as false.
     *
     * @param player viewer
     * @return whether sneak is held for wool / cancel checks
     */
    public boolean sneakHeld(Player player) {
        return player.isSneaking() || player.getPose() == Pose.SNEAKING;
    }

    /**
     * Applies the next secondary colour. Prefer {@link #tryCycleWool(Player)} from clicks.
     *
     * @param player kit holder placing a new camp
     */
    public void cycleWool(Player player) {
        if (isRelocating(player)) {
            return;
        }
        // The server tick, not World#getFullTime: the day clock stands still when doDaylightCycle is off.
        int tick = Bukkit.getCurrentTick();
        Integer previous = woolCycleTick.put(player.getUniqueId(), tick);
        if (previous != null && previous == tick) {
            return;
        }
        Site site = nearestConfirmedHidden(player);
        String current = secondaryWoolName(player, site);
        String next = CampWools.next(current).name();
        woolChoiceSecondary.put(player.getUniqueId(), next);
        if (site != null) {
            pulsePlacement(player, site, false);
        }
    }

    /**
     * Sets one wool role from the excavation board and rewrites those template cells in the world.
     *
     * @param player director
     * @param site excavation
     * @param color chosen DyeColor
     * @param role primary ({@code W}) or secondary ({@code R})
     */
    public void applyCampWool(Player player, Site site, DyeColor color, CampWoolRole role) {
        if (!site.isDirector(player.getUniqueId())) {
            return;
        }
        DyeColor fallback = role == CampWoolRole.PRIMARY ? DyeColor.WHITE : DyeColor.RED;
        Material to = CampWools.woolOf(color.name(), fallback);
        World world = plugin.getServer().getWorld(site.getWorldName());
        if (world != null) {
            recolorWoolCells(world, site, role, to);
        }
        if (role == CampWoolRole.PRIMARY) {
            site.setCampWoolPrimary(color.name());
            woolChoicePrimary.put(player.getUniqueId(), color.name());
        } else {
            site.setCampWoolSecondary(color.name());
            woolChoiceSecondary.put(player.getUniqueId(), color.name());
        }
        sites.save(site);
    }

    /**
     * Overwrites every template cell of {@code role} at the planted origin, whatever block is there.
     *
     * @param world camp world
     * @param site excavation
     * @param role which grid letters to rewrite
     * @param to new wool
     */
    private void recolorWoolCells(World world, Site site, CampWoolRole role, Material to) {
        BlockFace facing = campFacing(site);
        if (facing == null) {
            DyeColor fallback = role == CampWoolRole.PRIMARY ? DyeColor.WHITE : DyeColor.RED;
            String stored = role == CampWoolRole.PRIMARY ? site.getCampWoolPrimary() : site.getCampWoolSecondary();
            Material from = CampWools.woolOf(stored, fallback);
            for (BlockCell cell : site.getCampBlocks()) {
                Block block = world.getBlockAt(cell.x(), cell.y(), cell.z());
                if (block.getType() == from) {
                    block.setType(to, false);
                }
            }
            return;
        }
        List<CampTemplate.Piece> pieces = CampTemplate.basic(
                CampWools.woolOf(site.getCampWoolPrimary(), DyeColor.WHITE),
                CampWools.woolOf(site.getCampWoolSecondary(), DyeColor.RED));
        for (CampTemplate.Piece piece : pieces) {
            if (piece.wool() != role) {
                continue;
            }
            Vector offset = CampTemplate.rotate(piece.dx(), piece.dz(), facing);
            world.getBlockAt(
                    site.getCampX() + offset.getBlockX(),
                    site.getCampY() + piece.dy(),
                    site.getCampZ() + offset.getBlockZ()
            ).setType(to, false);
        }
    }

    /**
     * Stored facing, or inferred from the sign cell of the tent grid ({@code N} at local −2, −2).
     * The dossier stores the three origin coordinates together, so a present X means a full origin.
     *
     * @param site excavation
     * @return cardinal, or {@code null} when the camp origin or its orientation is unknown
     */
    private BlockFace campFacing(Site site) {
        if (site.getCampX() == null) {
            return null;
        }
        if (site.getCampFacing() != null) {
            try {
                BlockFace stored = BlockFace.valueOf(site.getCampFacing());
                if (stored.isCartesian() && stored.getModY() == 0) {
                    return stored;
                }
            } catch (IllegalArgumentException ignored) {
                // fall through to sign inference
            }
        }
        if (site.getCampSignX() == null) {
            return null;
        }
        int dx = site.getCampSignX() - site.getCampX();
        int dz = site.getCampSignZ() - site.getCampZ();
        int dy = site.getCampSignY() - site.getCampY();
        if (dy != 0) {
            return null;
        }
        for (BlockFace facing : new BlockFace[] {BlockFace.SOUTH, BlockFace.WEST, BlockFace.NORTH, BlockFace.EAST}) {
            Vector offset = CampTemplate.rotate(-2, -2, facing);
            if (offset.getBlockX() == dx && offset.getBlockZ() == dz) {
                return facing;
            }
        }
        return null;
    }

    /**
     * Drops relocate and pending rename for every player working this excavation.
     *
     * @param siteId excavation being erased
     */
    public void abortSessionsFor(UUID siteId) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (siteId.equals(relocating.get(player.getUniqueId()))) {
                cancelRelocate(player);
            }
            if (siteId.equals(renameForSite.get(player.getUniqueId()))) {
                renameForSite.remove(player.getUniqueId());
            }
        }
    }

    /**
     * Drops relocate mode; the existing camp is untouched.
     *
     * @param player kit holder
     */
    public void cancelRelocate(Player player) {
        if (relocating.remove(player.getUniqueId()) != null) {
            removeMoveAimProxy(player);
            clearPreview(player);
            if (!holdingKit(player)) {
                hideHud(player);
            }
        }
    }

    /**
     * Drops relocate and pending rename (disconnect / world change).
     *
     * @param player viewer
     */
    public void clearSession(Player player) {
        cancelRelocate(player);
        renameForSite.remove(player.getUniqueId());
        woolCycleTick.remove(player.getUniqueId());
    }

    /**
     * @param player viewer
     * @return whether they are placing a moved camp
     */
    public boolean isRelocating(Player player) {
        return relocating.containsKey(player.getUniqueId());
    }

    /**
     * @param player viewer
     * @return whether they must send the next chat as a new name
     */
    public boolean isRenaming(Player player) {
        return renameForSite.containsKey(player.getUniqueId());
    }

    /**
     * Plants or relocates the camp if the current look matches a valid ghost.
     *
     * @param player kit holder or director moving
     * @param site ruin or excavation
     * @param moving whether this is a director move
     */
    private void tryPlace(Player player, Site site, boolean moving) {
        CampPlacement placement = lastAllowedPulse(player, site);
        if (placement == null) {
            placement = assess(player, site, aimBlock(player, site));
        }
        if (!placement.allowed()) {
            player.sendMessage(placement.reason());
            return;
        }
        clearPreview(player);
        World world = player.getWorld();
        if (moving) {
            for (BlockCell cell : List.copyOf(site.getCampBlocks())) {
                world.getBlockAt(cell.x(), cell.y(), cell.z()).setType(Material.AIR, false);
            }
        }
        for (CampPlacement.Ghost ghost : placement.ghosts()) {
            world.getBlockAt(ghost.x(), ghost.y(), ghost.z()).setBlockData(ghost.data(), false);
        }
        site.getCampBlocks().clear();
        site.setCampSignX(null);
        site.setCampSignY(null);
        site.setCampSignZ(null);
        for (CampPlacement.Ghost ghost : placement.ghosts()) {
            site.getCampBlocks().add(new BlockCell(ghost.x(), ghost.y(), ghost.z()));
            if (ghost.data().getMaterial() == Material.OAK_SIGN) {
                site.setCampSignX(ghost.x());
                site.setCampSignY(ghost.y());
                site.setCampSignZ(ghost.z());
            }
        }
        site.setCampFacing(CampTemplate.facingFromYaw(player.getLocation().getYaw()).name());
        if (!moving) {
            site.setCampWoolPrimary(primaryWoolName(player, site));
            site.setCampWoolSecondary(secondaryWoolName(player, site));
        }
        Block camp = world.getBlockAt(placement.originX(), placement.originY(), placement.originZ());
        Chunk campChunk = camp.getChunk();
        if (moving) {
            site.relocateCamp(campChunk.getX(), campChunk.getZ(), camp.getX(), camp.getY(), camp.getZ());
            relocating.remove(player.getUniqueId());
            removeMoveAimProxy(player);
        } else {
            site.establish(
                    player.getUniqueId(),
                    campChunk.getX(),
                    campChunk.getZ(),
                    camp.getX(),
                    camp.getY(),
                    camp.getZ()
            );
            consumeOne(player);
        }
        if (!moving) {
            noteMissingTerrain(player, world, site);
        }
        sites.save(site);
        if (findDust != null) {
            findDust.syncTimer();
        }
        // Every template has one sign piece, and an allowed placement plants all of them.
        plugin.getServer().getScheduler().runTask(plugin, () -> CampSigns.write(
                world.getBlockAt(site.getCampSignX(), site.getCampSignY(), site.getCampSignZ()),
                site.publicName()));
        player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, SoundCategory.PLAYERS, 0.8f, 1.1f);
        if (moving) {
            player.sendMessage("Camp moved.");
        } else {
            player.sendMessage("You established an archaeological excavation.");
            player.sendMessage("Interact with the camp to see the site record.");
        }
    }

    /**
     * @param player kit holder
     * @return whether they already direct as many open camps as the config allows
     */
    private boolean atDirectorCap(Player player) {
        if (settings.unlimitedExcavations()) {
            return false;
        }
        return sites.countDirectedCamps(player.getUniqueId()) >= settings.maxExcavations();
    }

    /**
     * @return chat and HUD copy when a new camp would exceed {@code establish.max-excavations}
     */
    private String directorCapHint() {
        int cap = settings.maxExcavations();
        if (cap == 1) {
            return "You already direct an excavation. Close it at the camp before establishing another.";
        }
        return "You already direct " + cap
                + " excavations. Close one at its camp before establishing another.";
    }

    /**
     * First plant: finds whose cells are already air, water, or builds are damaged. Does not block the claim.
     *
     * @param player director
     * @param ruinWorld world the camp was just planted in, which is always the ruin's world
     * @param site excavation just established
     */
    private void noteMissingTerrain(Player player, World ruinWorld, Site site) {
        ruinWorld.getChunkAt(site.getChunkX(), site.getChunkZ()).load();
        PrismWound.Prior prior = PrismWound.markMissingTerrain(ruinWorld, site);
        site.catalogSettledFinds(null);
        if (prior.disturbed() <= 0) {
            return;
        }
        String message = prior.disturbed() == 1
                ? "One find was already disturbed before this dig opened"
                : prior.disturbed() + " finds were already disturbed before this dig opened";
        if (prior.lost() > 0) {
            message += ", " + prior.lost() + " beyond recovery";
        }
        player.sendMessage(message + ".");
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
     * @param player kit holder
     * @return excavation being relocated, or {@code null}
     */
    private Site relocatingSite(Player player) {
        UUID siteId = relocating.get(player.getUniqueId());
        if (siteId == null) {
            return null;
        }
        Site site = sites.findById(siteId).orElse(null);
        if (site == null || !site.isCampLocked() || !site.isDirector(player.getUniqueId())) {
            cancelRelocate(player);
            return null;
        }
        return site;
    }

    /**
     * Outside a move the site is always a hidden ruin, so only the player's own choice applies.
     *
     * @param player kit holder
     * @param site site being placed; the camp being moved while relocating
     * @return DyeColor name for {@code W} cells
     */
    private String primaryWoolName(Player player, Site site) {
        if (isRelocating(player)) {
            return site.getCampWoolPrimary();
        }
        String chosen = woolChoicePrimary.get(player.getUniqueId());
        return chosen != null ? chosen : "WHITE";
    }

    /**
     * @param player kit holder
     * @param site site being placed, the camp being moved, or {@code null} when cycling away from any ruin
     * @return DyeColor name for {@code R} cells
     */
    private String secondaryWoolName(Player player, Site site) {
        if (isRelocating(player)) {
            return site.getCampWoolSecondary();
        }
        String chosen = woolChoiceSecondary.get(player.getUniqueId());
        return chosen != null ? chosen : "RED";
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
        List<CampTemplate.Piece> pieces = CampTemplate.basic(
                CampWools.woolOf(primaryWoolName(player, site), DyeColor.WHITE),
                CampWools.woolOf(secondaryWoolName(player, site), DyeColor.RED));
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
            CampPlacement.Issue cell = cellIssue(world.getBlockAt(x, y, z), piece.dy() == 0, site);
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
        return new CampPlacement(true, "Right-click to establish.", ghosts, originX, originY, originZ);
    }

    /**
     * Look ray used by the ghost and by confirm. While moving, skips the existing camp
     * because that camp is drawn as air on the client.
     *
     * @param player viewer
     * @param site ruin or excavation being placed
     * @return first real block on the crosshair, or {@code null}
     */
    private Block aimBlock(Player player, Site site) {
        // While relocating, the only site ever aimed for is the camp being moved.
        if (!relocating.containsKey(player.getUniqueId())) {
            return player.getTargetBlockExact(64);
        }
        BlockIterator iterator = new BlockIterator(player, 64);
        while (iterator.hasNext()) {
            Block block = iterator.next();
            if (site.isCampBlock(block.getX(), block.getY(), block.getZ())) {
                continue;
            }
            if (block.getType().isAir() || block.isLiquid()) {
                continue;
            }
            if (block.isPassable() && !block.getType().isSolid()) {
                continue;
            }
            return block;
        }
        return null;
    }

    /**
     * Ghost from the last preview tick, if it was legal for this site.
     *
     * @param player viewer
     * @param site ruin or excavation
     * @return placement to confirm, or {@code null} to re-aim
     */
    private CampPlacement lastAllowedPulse(Player player, Site site) {
        Pulse pulse = lastPulse.get(player.getUniqueId());
        if (pulse == null || !pulse.siteId().equals(site.getId()) || !pulse.placement().allowed()) {
            return null;
        }
        return pulse.placement();
    }

    /**
     * Ghost drawn on the last preview tick, with the site it was drawn for.
     *
     * @param siteId ruin or excavation the ghost belongs to
     * @param placement assessed ghost
     */
    private record Pulse(UUID siteId, CampPlacement placement) {
    }

    /**
     * Invisible stand on the look vector so empty-hand right-click still reaches the server.
     *
     * @param player director moving a camp
     */
    private void updateMoveAimProxy(Player player) {
        Location loc = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(2.4));
        UUID existingId = moveAimProxies.get(player.getUniqueId());
        Entity existing = existingId == null ? null : plugin.getServer().getEntity(existingId);
        // A world change ends the move and removes the stand, so a live stand shares the viewer's world.
        if (existing instanceof ArmorStand stand && stand.isValid()) {
            stand.teleport(loc);
            return;
        }
        ArmorStand stand = loc.getWorld().spawn(loc, ArmorStand.class, this::configureMoveAimProxy);
        moveAimProxies.put(player.getUniqueId(), stand.getUniqueId());
    }

    /**
     * @param stand click target only; not part of the camp
     */
    private void configureMoveAimProxy(ArmorStand stand) {
        stand.setInvisible(true);
        stand.setSmall(true);
        stand.setGravity(false);
        stand.setBasePlate(false);
        stand.setArms(false);
        stand.setCollidable(false);
        stand.setInvulnerable(true);
        stand.setPersistent(false);
        stand.setSilent(true);
        stand.setCanPickupItems(false);
        stand.setCustomNameVisible(false);
        for (org.bukkit.inventory.EquipmentSlot slot : org.bukkit.inventory.EquipmentSlot.values()) {
            stand.addEquipmentLock(slot, ArmorStand.LockType.ADDING_OR_CHANGING);
            stand.addEquipmentLock(slot, ArmorStand.LockType.REMOVING_OR_CHANGING);
        }
    }

    /**
     * @param player viewer
     */
    private void removeMoveAimProxy(Player player) {
        removeMoveAimProxy(player.getUniqueId());
    }

    /**
     * @param playerId viewer id (online or not)
     */
    private void removeMoveAimProxy(UUID playerId) {
        UUID standId = moveAimProxies.remove(playerId);
        if (standId == null) {
            return;
        }
        Entity entity = plugin.getServer().getEntity(standId);
        if (entity != null) {
            entity.remove();
        }
    }

    /**
     * @param block world block, or {@code null}
     * @return whether this cell is a planted camp piece (board handle, not a place target)
     */
    public boolean isLockedCampBlock(Block block) {
        if (block == null) {
            return false;
        }
        return sites.findLockedCampBlock(
                block.getWorld().getName(),
                block.getX(),
                block.getY(),
                block.getZ()).isPresent();
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
        if (!world.equals(site.getWorldName())) {
            return CampPlacement.Issue.NOT_NEIGHBOR;
        }
        if (cx == site.getChunkX() && cz == site.getChunkZ()) {
            return CampPlacement.Issue.ON_DIG;
        }
        if (occupiedByOther(site, world, cx, cz)) {
            return CampPlacement.Issue.OCCUPIED;
        }
        if (!isNeighborChunk(site.getChunkX(), site.getChunkZ(), cx, cz)) {
            return CampPlacement.Issue.NOT_NEIGHBOR;
        }
        return null;
    }

    /**
     * @param cell template cell in the world
     * @param floor whether this is a ground-level piece that needs solid support
     * @param site camp whose own blocks may be overwritten while moving
     * @return terrain problem, or {@code null} if the cell can be planted
     */
    private CampPlacement.Issue cellIssue(Block cell, boolean floor, Site site) {
        if (site.isCampBlock(cell.getX(), cell.getY(), cell.getZ())) {
            return floor ? supportIssue(cell.getRelative(BlockFace.DOWN)) : null;
        }
        if (!isFree(cell)) {
            return CampPlacement.Issue.BLOCKED;
        }
        if (!floor) {
            return null;
        }
        return supportIssue(cell.getRelative(BlockFace.DOWN));
    }

    /**
     * @param below block under a floor piece
     * @return fluid or unsupported, or {@code null}
     */
    private CampPlacement.Issue supportIssue(Block below) {
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
        if (relocating.containsKey(player.getUniqueId())) {
            BlockData air = Material.AIR.createBlockData();
            for (BlockCell cell : site.getCampBlocks()) {
                desired.put(new BlockPos(worldName, cell.x(), cell.y(), cell.z()), air);
            }
        }
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
     * Callers rule out the site's own dig chunk first, so any ruin found there is another one.
     *
     * @param self site being placed or moved
     * @param world world name
     * @param chunkX chunk X, never the dig chunk of {@code self}
     * @param chunkZ chunk Z
     * @return whether a different site already owns this chunk
     */
    private boolean occupiedByOther(Site self, String world, int chunkX, int chunkZ) {
        return sites.findByChunk(world, chunkX, chunkZ).isPresent()
                || sites.findByEstablishmentChunk(world, chunkX, chunkZ)
                .filter(other -> !other.getId().equals(self.getId()))
                .isPresent();
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
        return Math.abs(chunkX - siteX) + Math.abs(chunkZ - siteZ) == 1;
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
        lastPulse.remove(player.getUniqueId());
        Map<BlockPos, BlockData> previous = previews.remove(player.getUniqueId());
        if (previous == null) {
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
        if (previous == null) {
            return;
        }
        for (BlockPos pos : previous.keySet()) {
            restore(player, from, pos);
        }
    }

    /**
     * Skips cells of another world. A world change cancels a camp move through
     * {@link #clearSession(Player)} before {@link #clearPreviewFromWorld(Player, World)} runs, so
     * {@link #clearPreview(Player)} then sees the destination world. The client has already
     * dropped the old world's ghost blocks, and reading the destination would load its chunks.
     *
     * @param player viewer
     * @param world world to read real blocks from
     * @param pos fake block
     */
    private void restore(Player player, World world, BlockPos pos) {
        if (!pos.world().equals(world.getName())) {
            return;
        }
        Block real = world.getBlockAt(pos.x(), pos.y(), pos.z());
        player.sendBlockChange(real.getLocation(), real.getBlockData());
        if (real.getState() instanceof Sign) {
            plugin.getServer().getScheduler().runTask(plugin, () -> resendSign(player, world, pos));
        }
    }

    /**
     * Sign text is a tile entity; it arrives one tick after the block packet so the
     * client has a sign to write into after the move-ghost air overlay.
     *
     * @param player viewer
     * @param world world of the restored cell
     * @param pos restored cell
     */
    private void resendSign(Player player, World world, BlockPos pos) {
        if (!player.isOnline()) {
            return;
        }
        CampSigns.sendTo(player, world.getBlockAt(pos.x(), pos.y(), pos.z()));
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
        org.bukkit.inventory.ItemStack main = player.getInventory().getItemInMainHand();
        if (item.isEstablish(main)) {
            main.setAmount(main.getAmount() - 1);
            return;
        }
        org.bukkit.inventory.ItemStack off = player.getInventory().getItemInOffHand();
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
        bar.addPlayer(player); // Idempotent on Spigot and Paper: the viewers are a set.
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
