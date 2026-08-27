package com.nowko.archeology.tracker;

import com.nowko.archeology.config.TrackerSettings;
import com.nowko.archeology.item.TrackerItem;
import com.nowko.archeology.model.Site;
import com.nowko.archeology.model.SiteStatus;
import com.nowko.archeology.site.SiteRepository;
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
 * Hold-to-scan tracker: 1–3 pips, rings that lean toward an 8-way heading, lock with hysteresis.
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
     * Keeps the current ruin until another is closer by {@code target-switch-margin}.
     *
     * @param player scanner
     * @return locked or newly acquired hit, or {@code null}
     */
    ScanHit lockedTarget(Player player) {
        ScanHit nearest = nearestHidden(player);
        UUID locked = lockedSiteId.get(player.getUniqueId());
        if (locked == null) {
            return nearest;
        }
        ScanHit current = hitIfInRange(player, locked);
        if (current == null) {
            return nearest;
        }
        if (nearest != null
                && !nearest.site().getId().equals(current.site().getId())
                && nearest.distance() + settings.targetSwitchMargin() < current.distance()) {
            return nearest;
        }
        return current;
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
     * Picks the closest {@link SiteStatus#HIDDEN} site in range in the player's world.
     *
     * @param player scanner
     * @return hit, or {@code null} if nothing is in range
     */
    ScanHit nearestHidden(Player player) {
        ScanHit best = null;
        for (Site site : sites.all()) {
            ScanHit hit = hitOrNull(player, site);
            if (hit == null) {
                continue;
            }
            if (best == null || hit.distance() < best.distance()) {
                best = hit;
            }
        }
        return best;
    }

    /**
     * @param player scanner
     * @param site candidate ruin
     * @return hit if hidden, same world, and inside combined range
     */
    private ScanHit hitOrNull(Player player, Site site) {
        if (site.getStatus() != SiteStatus.HIDDEN) {
            return null;
        }
        if (!player.getWorld().getName().equals(site.getWorldName())) {
            return null;
        }
        int range = Math.min(settings.defaultMaxRange(), Math.max(1, site.getDetectionRadius()));
        double distance = horizontalDistance(player.getLocation(), site);
        if (distance > range) {
            return null;
        }
        return new ScanHit(site, distance, range);
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
        double distance = hit.distance();
        int range = hit.range();
        int bands = proximityBands(hit);
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
     * One burst: 1–3 pips and matching rings. Pitch is per band; rings are offset toward an 8-way heading.
     *
     * @param player scanner
     * @param hit locked site
     */
    private void pip(Player player, ScanHit hit) {
        Location feet = player.getLocation().clone().add(0, 0.12, 0);
        double[] heading = snappedHeading(feet, hit.site());
        Location origin = feet.clone().add(
                heading[0] * settings.waveBiasBlocks(),
                0,
                heading[1] * settings.waveBiasBlocks()
        );
        int bands = proximityBands(hit);
        float pitch = bandPitch(bands);
        List<Double> radii = settings.waveRadii();
        int count = Math.min(bands, Math.max(1, radii.size()));
        int step = settings.waveStepTicks();
        boolean dense = bands >= 2;
        for (int index = 0; index < count; index++) {
            double radius = ringRadius(hit, bands, index, radii);
            float note = pitch + index * 0.06f;
            long delay = (long) index * step;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                World world = origin.getWorld();
                if (world == null) {
                    return;
                }
                world.playSound(origin, Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.PLAYERS, 0.5f, note);
                if (settings.pulseParticles()) {
                    spawnRing(origin, radius, dense, heading);
                }
            }, delay);
        }
    }

    /**
     * Far band uses a single ring that grows toward the medium radius as the player walks in.
     *
     * @param hit locked site
     * @param bands 1–3
     * @param index pip index inside the burst
     * @param radii configured sizes
     * @return ring radius in blocks
     */
    private double ringRadius(ScanHit hit, int bands, int index, List<Double> radii) {
        if (bands == 1) {
            double inner = radii.get(0);
            double outer = radii.size() > 1 ? radii.get(1) : inner * 2;
            double medium = mediumBand();
            double farSpan = Math.max(1.0, hit.range() - medium);
            double t = Math.min(1.0, Math.max(0.0, (hit.distance() - medium) / farSpan));
            return outer + (inner - outer) * (t * t);
        }
        return radii.get(Math.min(index, radii.size() - 1));
    }

    /**
     * @param bands 1 far, 2 medium, 3 close
     * @return note-block pitch for that band
     */
    private float bandPitch(int bands) {
        return switch (bands) {
            case 3 -> 1.55f;
            case 2 -> 1.12f;
            default -> 0.72f;
        };
    }

    /**
     * Maps distance onto the three signal strengths: one, two, or three pips.
     *
     * @param hit nearest in-range site
     * @return {@code 1} far, {@code 2} medium, {@code 3} close
     */
    int proximityBands(ScanHit hit) {
        if (hit.distance() <= closeBand()) {
            return 3;
        }
        if (hit.distance() <= mediumBand()) {
            return 2;
        }
        return 1;
    }

    /**
     * @return inner (three-pip) radius
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
        int fade = settings.pulseParticles() ? settings.particleFadeTicks() : 4;
        return Math.max(1, (bands - 1) * settings.waveStepTicks() + fade);
    }

    /**
     * Draws one horizontal circle; points facing the snapped heading spawn a second particle.
     *
     * @param origin biased centre
     * @param radius blocks from origin
     * @param near denser points in medium/close bands
     * @param heading unit 8-way {@code {x, z}}
     */
    private void spawnRing(Location origin, double radius, boolean near, double[] heading) {
        World world = origin.getWorld();
        if (world == null) {
            return;
        }
        Particle particle = settings.waveParticle();
        int points = Math.max(12, (int) Math.round(radius * (near ? 14 : 10)));
        double headingAngle = Math.atan2(heading[1], heading[0]);
        for (int i = 0; i < points; i++) {
            double angle = (2 * Math.PI * i) / points;
            double x = origin.getX() + Math.cos(angle) * radius;
            double z = origin.getZ() + Math.sin(angle) * radius;
            world.spawnParticle(particle, x, origin.getY(), z, 1, 0, 0, 0, 0);
            if (heading[0] == 0 && heading[1] == 0) {
                continue;
            }
            double delta = Math.abs(Math.atan2(Math.sin(angle - headingAngle), Math.cos(angle - headingAngle)));
            if (delta < Math.PI / 5) {
                world.spawnParticle(particle, x, origin.getY() + 0.08, z, 1, 0, 0, 0, 0);
            }
        }
    }

    /**
     * Snaps the vector to the site onto eight compass points so the lean is a hint, not a needle.
     *
     * @param here player location
     * @param site target ruin
     * @return unit {@code {x, z}} on a 45-degree step
     */
    private double[] snappedHeading(Location here, Site site) {
        double dx = site.centerBlockX() + 0.5 - here.getX();
        double dz = site.centerBlockZ() + 0.5 - here.getZ();
        if (Math.abs(dx) < 0.01 && Math.abs(dz) < 0.01) {
            return new double[] {0, 0};
        }
        double angle = Math.atan2(dz, dx);
        double step = Math.PI / 4;
        double snapped = Math.round(angle / step) * step;
        return new double[] {Math.cos(snapped), Math.sin(snapped)};
    }

    /**
     * Sends the prospecting hint when the scanner is close enough to the site.
     * Cooldown is per viewer (scanner, and optionally nearby players).
     *
     * @param scanner holder of the tracker
     * @param hit nearest in-range site
     */
    private void maybeDetectMessage(Player scanner, ScanHit hit) {
        if (hit.distance() > settings.detectMessageRange()) {
            return;
        }
        sendDetectIfReady(scanner);
        int share = settings.detectMessageShareRange();
        if (share <= 0) {
            return;
        }
        Location here = scanner.getLocation();
        for (Player other : scanner.getWorld().getPlayers()) {
            if (other.getUniqueId().equals(scanner.getUniqueId())) {
                continue;
            }
            if (other.getLocation().distanceSquared(here) <= (double) share * share) {
                sendDetectIfReady(other);
            }
        }
    }

    /**
     * @param viewer player who may receive the detect chat
     */
    private void sendDetectIfReady(Player viewer) {
        int last = lastDetectMessageTick.getOrDefault(viewer.getUniqueId(), Integer.MIN_VALUE / 2);
        if (tick - last < settings.detectMessageCooldownTicks()) {
            return;
        }
        lastDetectMessageTick.put(viewer.getUniqueId(), tick);
        viewer.sendMessage("Archaeological signal detected.");
        viewer.sendMessage("Prospect the area to determine the site location.");
    }

    /**
     * Hidden site still inside its detection bubble.
     *
     * @param site ruin
     * @param distance horizontal blocks to chunk center
     * @param range effective radius used for this hit
     */
    record ScanHit(Site site, double distance, int range) {
    }
}
