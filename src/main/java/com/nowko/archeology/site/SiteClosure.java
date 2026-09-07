package com.nowko.archeology.site;

import com.nowko.archeology.model.Site;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

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
     * Closes the excavation when no find can be recovered any more, persists it, and tells the
     * staff that happens to be online. Field work stops on its own: {@code mayWork} and the
     * established-prism lookups only answer for an active site.
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
     * @param site excavation that just closed
     */
    private static void announce(Site site) {
        String message = "Excavation " + site.displayLabel()
                + " is exhausted: the cut holds nothing else to recover.";
        tell(site.getDirector(), message);
        for (UUID excavator : site.getExcavators()) {
            if (!excavator.equals(site.getDirector())) {
                tell(excavator, message);
            }
        }
    }

    /**
     * @param playerId staff member, or {@code null}
     * @param message English line
     */
    private static void tell(UUID playerId, String message) {
        if (playerId == null) {
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            player.sendMessage(message);
        }
    }
}
