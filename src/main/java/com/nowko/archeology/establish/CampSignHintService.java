package com.nowko.archeology.establish;

import com.nowko.archeology.config.EstablishSettings;
import com.nowko.archeology.model.Site;
import com.nowko.archeology.site.SiteRepository;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Rotatable;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * Dusts the camp sign for roster members standing nearby, so the board is not another oak sign.
 * Strangers do not see the motes; the cue is only for people who already belong to the project.
 */
final class CampSignHintService {
    private static final Particle.DustOptions DUST = new Particle.DustOptions(Color.fromRGB(186, 160, 110), 0.7f);
    private static final int COUNT = 2;

    private final JavaPlugin plugin;
    private final SiteRepository sites;
    private EstablishSettings settings;
    private BukkitTask task;

    /**
     * @param plugin scheduler owner
     * @param sites camp-locked excavations
     * @param settings radius and cadence
     */
    CampSignHintService(JavaPlugin plugin, SiteRepository sites, EstablishSettings settings) {
        this.plugin = plugin;
        this.sites = sites;
        this.settings = settings;
    }

    /**
     * @param settings values after {@code /archaeo reload}
     */
    void setSettings(EstablishSettings settings) {
        this.settings = settings;
        start();
    }

    /**
     * Starts the mote loop, or stops it when the radius is zero.
     */
    void start() {
        stop();
        if (settings.signHintRadius() <= 0) {
            return;
        }
        long interval = settings.signHintIntervalTicks();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::pulse, interval, interval);
    }

    /**
     * Cancels the mote loop.
     */
    void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /**
     * One pass: each nearby roster member gets a few parchment motes on the sign face.
     */
    private void pulse() {
        int radius = settings.signHintRadius();
        if (radius <= 0) {
            return;
        }
        double rangeSq = (double) radius * radius;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            World world = player.getWorld();
            Location eyes = player.getLocation();
            for (Site site : sites.all()) {
                if (!site.isCampLocked() || !site.onStaff(player.getUniqueId())) {
                    continue;
                }
                if (site.getCampSignX() == null || site.getCampSignY() == null || site.getCampSignZ() == null) {
                    continue;
                }
                if (site.getWorldName() == null || !site.getWorldName().equals(world.getName())) {
                    continue;
                }
                Block block = world.getBlockAt(site.getCampSignX(), site.getCampSignY(), site.getCampSignZ());
                if (!Tag.ALL_SIGNS.isTagged(block.getType())) {
                    continue;
                }
                Location at = hintAt(block);
                if (eyes.distanceSquared(at) > rangeSq) {
                    continue;
                }
                player.spawnParticle(Particle.DUST, at, COUNT, 0.12, 0.18, 0.12, 0, DUST);
            }
        }
    }

    /**
     * Sits the motes just in front of the written face, so they read as the board and not the post.
     *
     * @param block camp sign
     * @return spawn origin
     */
    private static Location hintAt(Block block) {
        Location at = block.getLocation().add(0.5, 0.85, 0.5);
        BlockData data = block.getBlockData();
        Vector forward = null;
        if (data instanceof Directional directional) {
            forward = directional.getFacing().getDirection();
        } else if (data instanceof Rotatable rotatable) {
            forward = rotatable.getRotation().getDirection();
        }
        if (forward != null && forward.lengthSquared() > 0) {
            at.add(forward.normalize().multiply(0.32));
        }
        return at;
    }
}
