package net.tfminecraft.archaeo.tracker;

import net.tfminecraft.archaeo.config.TrackerSettings;
import net.tfminecraft.archaeo.item.TrackerItem;
import net.tfminecraft.archaeo.model.Site;
import net.tfminecraft.archaeo.model.SiteStatus;
import net.tfminecraft.archaeo.site.SiteRepository;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hold-to-scan tracker: arcs fire the way the player looks; pulse strength follows
 * how well that look lines up with a hidden ruin and how far it is.
 */
public class TrackerService {
    private final JavaPlugin plugin;
    private final SiteRepository sites;
    private final TrackerItem item;
    private TrackerSettings settings;
    private final Map<UUID, Integer> lastBeepTick = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastDetectMessageTick = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> lockedSiteId = new ConcurrentHashMap<>();
    private int tick;
    private BukkitTask task;

    /**
     * @param plugin used to schedule the scan loop and delayed rings
     * @param sites ruin dossiers
     * @param item tracker recognition
     * @param settings radii and pip timing
     */
    public TrackerService(JavaPlugin plugin, SiteRepository sites, TrackerItem item, TrackerSettings settings) {
        this.plugin = plugin;
        this.sites = sites;
        this.item = item;
        this.settings = settings;
    }

    /**
     * @param settings latest values after a catalog reload
     */
    public void setSettings(TrackerSettings settings) {
        this.settings = settings;
    }

    /**
     * Starts the repeating hold-to-scan loop.
     */
    public void start() {
        stop();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::pulse, 20L, 2L);
    }

    /**
     * Cancels the scan loop and forgets per-player cooldowns and locks.
     */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        lastBeepTick.clear();
        lastDetectMessageTick.clear();
        lockedSiteId.clear();
    }

    /**
     * Scans online players holding a tracker. Called by the scheduler.
     */
    public void pulse() {
        tick += 2;
        if (!settings.enabled()) {
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            if (!holdingTracker(player)) {
                lastBeepTick.remove(id);
                lockedSiteId.remove(id);
                continue;
            }
            ScanHit hit = lockedTarget(player);
            if (hit == null) {
                lockedSiteId.remove(id);
                continue;
            }
            lockedSiteId.put(id, hit.site().getId());
            int interval = Math.max(beepInterval(hit), burstTicks(hit));
            int last = lastBeepTick.getOrDefault(id, Integer.MIN_VALUE / 2);
            if (tick - last < interval) {
                continue;
            }
            lastBeepTick.put(id, tick);
            pip(player, hit);
            maybeDetectMessage(player, hit);
        }
    }

    /**
     * @param player online player
     * @return {@code true} if main or off hand is a tracker
     */
    private boolean holdingTracker(Player player) {
        return item.isTracker(player.getInventory().getItemInMainHand())
                || item.isTracker(player.getInventory().getItemInOffHand());
    }

    /**
     * Keeps the ruin the player is facing until another is clearly a stronger look.
     *
     * @param player scanner
     * @return locked or newly acquired hit, or {@code null} if nothing is ahead in range
     */
    ScanHit lockedTarget(Player player) {
        ScanHit best = strongestAhead(player);
        UUID locked = lockedSiteId.get(player.getUniqueId());
        if (locked == null) {
            return best;
        }
        ScanHit current = hitIfInRange(player, locked);
        if (current == null) {
            return best;
        }
        // The locked site is hidden, in range and ahead, so the full scan found at least that one.
        if (!best.site().getId().equals(current.site().getId())
                && betterLook(best, current)) {
            return best;
        }
        return current;
    }

    /**
     * @param candidate another in-range ruin ahead
     * @param current locked ruin
     * @return whether the player is aiming at {@code candidate} clearly enough to switch
     */
    private boolean betterLook(ScanHit candidate, ScanHit current) {
        if (candidate.alignment() > current.alignment() + 0.12) {
            return true;
        }
        boolean similarAim = Math.abs(candidate.alignment() - current.alignment()) < 0.08;
        return similarAim && candidate.distance() + settings.targetSwitchMargin() < current.distance();
    }

    /**
     * @param player scanner
     * @param siteId previously locked site
     * @return updated hit if that site is still hidden and in range
     */
    private ScanHit hitIfInRange(Player player, UUID siteId) {
        return sites.findById(siteId)
                .filter(site -> site.getStatus() == SiteStatus.HIDDEN)
                .map(site -> hitOrNull(player, site))
                .orElse(null);
    }

    /**
     * Picks the hidden ruin the player is aiming at most clearly, then the nearer one.
     *
     * @param player scanner
     * @return hit, or {@code null} if nothing is in range and ahead
     */
    ScanHit strongestAhead(Player player) {
        ScanHit best = null;
        for (Site site : sites.all()) {
            ScanHit hit = hitOrNull(player, site);
            if (hit == null) {
                continue;
            }
            if (best == null
                    || hit.alignment() > best.alignment() + 0.02
                    || (Math.abs(hit.alignment() - best.alignment()) <= 0.02 && hit.distance() < best.distance())) {
                best = hit;
            }
        }
        return best;
    }

    /**
     * @param player scanner
     * @param site candidate ruin
     * @return hit if hidden, same world, in range, and either on the chunk or looking toward it
     */
    private ScanHit hitOrNull(Player player, Site site) {
        if (site.getStatus() != SiteStatus.HIDDEN) {
            return null;
        }
        if (!player.getWorld().getName().equals(site.getWorldName())) {
            return null;
        }
        int range = Math.max(1, settings.defaultMaxRange());
        double distance = horizontalDistance(player.getLocation(), site);
        if (distance > range) {
            return null;
        }
        boolean onChunk = standingOnSite(player, site);
        double alignment = onChunk ? 1.0 : lookAlignment(player, site);
        if (alignment <= 0) {
            return null;
        }
        return new ScanHit(site, distance, range, alignment, onChunk);
    }

    /**
     * @param player scanner
     * @param site ruin
     * @return whether the player is already in the registered chunk (look no longer matters);
     *         {@link #hitOrNull} has already matched the world
     */
    private boolean standingOnSite(Player player, Site site) {
        Location here = player.getLocation();
        return here.getChunk().getX() == site.getChunkX()
                && here.getChunk().getZ() == site.getChunkZ();
    }

    /**
     * Horizontal facing from yaw, ignoring pitch so looking up still scans the horizon.
     *
     * @param player scanner
     * @return unit {@code {x, z}}
     */
    private double[] lookHeading(Player player) {
        double yawRad = Math.toRadians(player.getLocation().getYaw());
        return new double[] {-Math.sin(yawRad), Math.cos(yawRad)};
    }

    /**
     * @param player scanner
     * @param site ruin
     * @return {@code 1} looking straight at the chunk, {@code 0} side-on or behind. Not used on-chunk,
     *         so the scanner is always several blocks from the chunk centre
     */
    private double lookAlignment(Player player, Site site) {
        Location here = player.getLocation();
        double dx = site.centerBlockX() + 0.5 - here.getX();
        double dz = site.centerBlockZ() + 0.5 - here.getZ();
        double length = Math.hypot(dx, dz);
        double[] look = lookHeading(player);
        return Math.max(0.0, (look[0] * dx + look[1] * dz) / length);
    }

    /**
     * @param here player position
     * @param site ruin chunk
     * @return horizontal blocks to chunk center (Y is ignored so flight still works)
     */
    private double horizontalDistance(Location here, Site site) {
        double dx = here.getX() - site.centerBlockX();
        double dz = here.getZ() - site.centerBlockZ();
        return Math.hypot(dx, dz);
    }

    /**
     * Burst spacing: quadratic in the far band so a short walk near the edge of range is audible.
     *
     * @param hit locked site
     * @return ticks to wait before the next burst
     */
    int beepInterval(ScanHit hit) {
        int max = Math.max(settings.beepMinTicks(), settings.beepMaxTicks());
        int min = Math.min(settings.beepMinTicks(), settings.beepMaxTicks());
        int mid = (max + min) / 2;
        double close = closeBand();
        double medium = mediumBand();
        double distance = hit.sensedDistance();
        int range = hit.range();
        int bands = proximityBands(hit);
        if (bands == 4) {
            return Math.max(1, min - 2);
        }
        if (bands == 3) {
            return min;
        }
        if (bands == 2) {
            double span = Math.max(1.0, medium - close);
            double t = Math.min(1.0, Math.max(0.0, (distance - close) / span));
            return (int) Math.round(min + t * (mid - min));
        }
        double farSpan = Math.max(1.0, range - medium);
        double t = Math.min(1.0, Math.max(0.0, (distance - medium) / farSpan));
        double curved = t * t;
        return (int) Math.round(mid + curved * (max - mid));
    }

    /**
     * One burst: look-aimed arcs (1–3) or a centred full ring on the ruin chunk (same three radii, stronger).
     *
     * @param player scanner
     * @param hit locked site
     */
    private void pip(Player player, ScanHit hit) {
        Location feet = player.getLocation().clone().add(0, 0.12, 0);
        int bands = proximityBands(hit);
        boolean onChunk = bands == 4;
        double[] heading = lookHeading(player);
        Location origin = onChunk
                ? feet
                : feet.clone().add(
                        heading[0] * settings.waveBiasBlocks(),
                        0,
                        heading[1] * settings.waveBiasBlocks()
                );
        float pitch = bandPitch(bands);
        List<Double> radii = settings.waveRadii();
        int count = ringCount(bands, radii);
        int step = onChunk ? 1 : settings.waveStepTicks();
        boolean dense = bands >= 2;
        float volume = onChunk ? 0.75f : 0.5f;
        for (int index = 0; index < count; index++) {
            double radius = ringRadius(hit, bands, index, radii);
            float note = pitch + index * 0.06f;
            long delay = (long) index * step;
            // The origin is a copy of the scanner's location, which always carries its world.
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                World world = origin.getWorld();
                world.playSound(origin, Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.PLAYERS, volume, note);
                if (settings.pulseParticles()) {
                    spawnRing(origin, radius, dense, heading, onChunk);
                }
            }, delay);
        }
    }

    /**
     * Far band uses a single ring that grows toward the medium radius as the player walks in.
     *
     * @param hit locked site
     * @param bands 1–4
     * @param index pip index inside the burst
     * @param radii configured sizes; the catalogue always loads three
     * @return ring radius in blocks
     */
    private double ringRadius(ScanHit hit, int bands, int index, List<Double> radii) {
        if (bands == 1) {
            double inner = radii.get(0);
            double outer = radii.get(1);
            double medium = mediumBand();
            double farSpan = Math.max(1.0, hit.range() - medium);
            double t = Math.min(1.0, Math.max(0.0, (hit.sensedDistance() - medium) / farSpan));
            return outer + (inner - outer) * (t * t);
        }
        return radii.get(Math.min(index, radii.size() - 1));
    }

    /**
     * @param bands 1 far cone, 2 medium, 3 close cone, 4 on-chunk circle
     * @return note-block pitch for that band
     */
    private float bandPitch(int bands) {
        return switch (bands) {
            case 4 -> 1.85f;
            case 3 -> 1.55f;
            case 2 -> 1.12f;
            default -> 0.72f;
        };
    }

    /**
     * Maps on-chunk presence or sensed distance onto 1–4 signal strengths.
     *
     * @param hit ruin ahead in range, or the chunk underfoot
     * @return {@code 1} far, {@code 2} medium, {@code 3} close cone, {@code 4} on the ruin chunk
     */
    int proximityBands(ScanHit hit) {
        if (hit.onChunk()) {
            return 4;
        }
        double distance = hit.sensedDistance();
        if (distance <= closeBand()) {
            return 3;
        }
        if (distance <= mediumBand()) {
            return 2;
        }
        return 1;
    }

    /**
     * @return inner (three-pip cone) radius, outside the on-chunk circle
     */
    private double closeBand() {
        return Math.min(settings.detectMessageRange(), settings.nearRange());
    }

    /**
     * @return medium (two-pip) radius
     */
    private double mediumBand() {
        return Math.max(settings.detectMessageRange(), settings.nearRange());
    }

    /**
     * Minimum ticks from burst start to the next start: last ring plus fade time.
     * Client particles cannot be despawned; we wait instead.
     *
     * @param hit nearest in-range site
     * @return ticks so bursts do not overlap on screen
     */
    private int burstTicks(ScanHit hit) {
        int bands = proximityBands(hit);
        int rings = ringCount(bands, settings.waveRadii());
        int fade = settings.pulseParticles() ? settings.particleFadeTicks() : 4;
        int step = bands == 4 ? 1 : settings.waveStepTicks();
        return Math.max(1, (rings - 1) * step + fade);
    }

    /**
     * Never more than three rings; the on-chunk band reuses that set with a closed circle.
     *
     * @param bands signal strength 1–4
     * @param radii configured sizes
     * @return how many rings this burst draws
     */
    private int ringCount(int bands, List<Double> radii) {
        int available = Math.max(1, radii.size());
        return Math.min(3, Math.min(Math.max(1, bands), available));
    }

    /**
     * Draws a look-aimed arc, or a full circle when the scanner is on the ruin chunk.
     * Each point is doubled a few centimetres out so the stroke reads slightly thicker.
     *
     * @param origin biased centre, or feet when on-chunk
     * @param radius blocks from origin
     * @param near denser points in medium/close bands
     * @param heading unit look {@code {x, z}}
     * @param fullCircle whether to close the ring (on-chunk band)
     */
    private void spawnRing(Location origin, double radius, boolean near, double[] heading, boolean fullCircle) {
        World world = origin.getWorld();
        Particle particle = settings.waveParticle();
        int points = Math.max(12, (int) Math.round(radius * (fullCircle ? 16 : near ? 14 : 10)));
        // The heading is a unit vector, so an off-chunk pulse always has a direction to clip to.
        boolean clipArc = !fullCircle;
        double headingAngle = Math.atan2(heading[1], heading[0]);
        for (int i = 0; i < points; i++) {
            double angle = (2 * Math.PI * i) / points;
            if (clipArc && !onHeadingArc(angle, headingAngle)) {
                continue;
            }
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double y = origin.getY();
            world.spawnParticle(particle, origin.getX() + cos * radius, y, origin.getZ() + sin * radius, 1, 0, 0, 0, 0);
            double outer = radius + 0.08;
            world.spawnParticle(particle, origin.getX() + cos * outer, y, origin.getZ() + sin * outer, 1, 0, 0, 0, 0);
        }
    }

    /**
     * Open back of the pulse: about 170°, so it reads as a fat octant / near-semicircle.
     *
     * @param angle point on the would-be ring
     * @param headingAngle look yaw on the XZ plane
     * @return whether the point belongs on the visible arc
     */
    private boolean onHeadingArc(double angle, double headingAngle) {
        double delta = Math.abs(Math.atan2(Math.sin(angle - headingAngle), Math.cos(angle - headingAngle)));
        return delta < Math.toRadians(85);
    }

    /**
     * Sends the prospecting hint when the scanner is close and facing the site.
     *
     * @param scanner holder of the tracker
     * @param hit ruin ahead
     */
    private void maybeDetectMessage(Player scanner, ScanHit hit) {
        if (hit.distance() > settings.detectMessageRange() || hit.alignment() < 0.65) {
            return;
        }
        sendDetectIfReady(scanner);
    }

    /**
     * @param scanner holder who may receive the detect chat
     */
    private void sendDetectIfReady(Player scanner) {
        int last = lastDetectMessageTick.getOrDefault(scanner.getUniqueId(), Integer.MIN_VALUE / 2);
        if (tick - last < settings.detectMessageCooldownTicks()) {
            return;
        }
        lastDetectMessageTick.put(scanner.getUniqueId(), tick);
        scanner.sendMessage("Archaeological signal detected.");
        scanner.sendMessage("Prospect the area to determine the site location.");
    }

    /**
     * Hidden ruin still inside its detection bubble and in front of the scanner.
     *
     * @param site ruin
     * @param distance horizontal blocks to chunk center
     * @param range effective radius used for this hit
     * @param alignment {@code 1} looking at the chunk, {@code 0} at the edge of the forward hemisphere
     * @param onChunk whether the scanner is standing in the ruin chunk
     */
    record ScanHit(Site site, double distance, int range, double alignment, boolean onChunk) {
        /**
         * Distance used for pips and tempo: real range when facing the site, stretched toward
         * {@code range} when the look slides off-axis.
         *
         * @return blocks for band and interval math
         */
        double sensedDistance() {
            return distance + (1.0 - alignment) * (range - distance);
        }
    }
}
