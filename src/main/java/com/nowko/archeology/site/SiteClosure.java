package com.nowko.archeology.site;

import com.nowko.archeology.model.Site;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Turns an excavation into a finished project. Every path that can settle the last find
 * (lifting it, smashing it with the pick, or losing it to vanilla damage) ends here so the
 * closing rule and its message live in one place.
 */
public final class SiteClosure {
    private SiteClosure() {
    }

    /**
     * Closes the excavation when no find can be recovered any more, persists it, and tells
     * whoever is standing on the dig chunk or its neighbours, plus staff who happen to be online.
     * Field work stops on its own: {@code mayWork} and the established-prism lookups only answer
     * for an active site.
     *
     * @param sites dossier store, written only when the status actually changed
     * @param site excavation that just settled a find, or {@code null}
     * @return whether this call closed the excavation
     */
    public static boolean settle(SiteRepository sites, Site site) {
        if (site == null || !site.exhaustIfSettled()) {
            return false;
        }
        sites.save(site);
        announce(site);
        return true;
    }

    /**
     * @param site excavation that just ran out of recoverable finds
     */
    private static void announce(Site site) {
        String message = "The excavation at " + site.publicName()
                + " is exhausted: nothing remains to recover.";
        Set<UUID> told = new HashSet<>();
        World world = site.getWorldName() == null ? null : Bukkit.getWorld(site.getWorldName());
        if (world != null) {
            int cx = site.getChunkX();
            int cz = site.getChunkZ();
            for (Player player : world.getPlayers()) {
                if (player.getLocation().getWorld() != world) {
                    continue;
                }
                int px = player.getLocation().getBlockX() >> 4;
                int pz = player.getLocation().getBlockZ() >> 4;
                if (Math.abs(px - cx) <= 1 && Math.abs(pz - cz) <= 1) {
                    player.sendMessage(message);
                    told.add(player.getUniqueId());
                }
            }
        }
        tell(site.getDirector(), message, told);
        for (UUID excavator : site.getExcavators()) {
            tell(excavator, message, told);
        }
    }

    /**
     * @param playerId staff member, or {@code null}
     * @param message English line
     * @param told people who already received it in the field
     */
    private static void tell(UUID playerId, String message, Set<UUID> told) {
        if (playerId == null || !told.add(playerId)) {
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            player.sendMessage(message);
        }
    }
}
