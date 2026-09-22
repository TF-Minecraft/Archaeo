package net.tfminecraft.archaeo.establish;

import net.tfminecraft.archaeo.model.Site;
import net.tfminecraft.archaeo.site.SiteRepository;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Shared camp close used by the board and by {@code /archaeo ruin close}. Unlocks the camp and
 * issues the field book; never prints the director's signed report.
 */
public final class CampClosure {
    private final JavaPlugin plugin;
    private final SiteRepository sites;
    private final CampArchiveBook archiveBook;

    /**
     * @param plugin server access for director notices and world drops
     * @param sites dossier store written after a successful close
     */
    public CampClosure(JavaPlugin plugin, SiteRepository sites) {
        this.plugin = plugin;
        this.sites = sites;
        this.archiveBook = new CampArchiveBook(plugin);
    }

    /**
     * @return field-book factory shared with click and craft handlers
     */
    public CampArchiveBook archiveBook() {
        return archiveBook;
    }

    /**
     * Unlocks a standing camp and hands the closer a field book that still opens this record.
     * Console drops the book on the camp table. The director is told if they are not the closer.
     *
     * @param closer director or staff who confirmed the close
     * @param site locked camp
     * @return whether the camp is now closed
     */
    public boolean close(CommandSender closer, Site site) {
        if (closer == null || site == null || !site.isCampLocked()) {
            return false;
        }
        if (!site.closeCamp()) {
            closer.sendMessage("This excavation cannot be closed.");
            return false;
        }
        sites.save(site);
        deliverFieldBook(closer, site);
        int percent = site.completionPercent();
        if (site.isUnfinishedCut()) {
            closer.sendMessage("Closed " + site.publicName() + " at " + percent
                    + "% complete. The record is in the field book.");
        } else {
            closer.sendMessage("Closed " + site.publicName() + ". The record is in the field book.");
        }
        notifyDirector(closer, site);
        return true;
    }

    /**
     * @param closer who receives the book, or console (drop at camp)
     * @param site just closed
     */
    private void deliverFieldBook(CommandSender closer, Site site) {
        String author = closer instanceof Player player && player.getName() != null
                ? player.getName()
                : "Staff";
        ItemStack book = archiveBook.create(author, site);
        if (closer instanceof Player player) {
            giveStack(player, book);
            return;
        }
        World world = plugin.getServer().getWorld(site.getWorldName());
        if (world == null || site.getCampX() == null || site.getCampY() == null || site.getCampZ() == null) {
            closer.sendMessage("No field book could be given from console.");
            return;
        }
        Location drop = new Location(
                world,
                site.getCampX() + 0.5,
                site.getCampY() + 1.0,
                site.getCampZ() + 0.5);
        world.dropItemNaturally(drop, book);
        closer.sendMessage("Field book dropped at the camp.");
    }

    /**
     * @param closer who closed
     * @param site closed excavation
     */
    private void notifyDirector(CommandSender closer, Site site) {
        UUID directorId = site.getDirector();
        if (directorId == null) {
            return;
        }
        if (closer instanceof Player player && directorId.equals(player.getUniqueId())) {
            return;
        }
        Player director = plugin.getServer().getPlayer(directorId);
        if (director != null) {
            director.sendMessage("The camp at " + site.publicName()
                    + " was closed. The record is in a field book.");
        }
    }

    /**
     * @param player recipient
     * @param stack field book
     */
    private static void giveStack(Player player, ItemStack stack) {
        var overflow = player.getInventory().addItem(stack);
        for (ItemStack extra : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), extra);
        }
    }
}
