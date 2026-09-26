package net.tfminecraft.archaeo.establish;

import net.tfminecraft.archaeo.model.Site;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * Resolves excavation staff names without asking Mojang for unknown accounts.
 */
public final class CampNames {
    private CampNames() {
    }

    /**
     * Director first, then granted excavators, without duplicates.
     *
     * @param site excavation
     * @return ordered roster
     */
    public static List<UUID> roster(Site site) {
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        if (site.getDirector() != null) {
            ids.add(site.getDirector());
        }
        ids.addAll(site.getExcavators());
        return new ArrayList<>(ids);
    }

    /**
     * @param viewer player opening the board, or {@code null} when resolving a name off a fiche
     * @param id stored uuid
     * @return last known name, or a short uuid
     */
    public static String of(Player viewer, UUID id) {
        if (id == null) {
            return "—";
        }
        if (viewer != null && id.equals(viewer.getUniqueId())) {
            return viewer.getName();
        }
        Player online = Bukkit.getPlayer(id);
        if (online != null) {
            return online.getName();
        }
        // Paper and Spigot answer a real name or null here, never a blank one.
        String name = Bukkit.getOfflinePlayer(id).getName();
        if (name != null) {
            return name;
        }
        return id.toString().substring(0, 8);
    }

    /**
     * Matches an online player or someone who has already joined this server.
     *
     * @param raw chat or command name, never {@code null}
     * @return known player, or {@code null}
     */
    public static OfflinePlayer known(String raw) {
        if (raw.isBlank()) {
            return null;
        }
        String name = raw.trim();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().equalsIgnoreCase(name)) {
                return player;
            }
        }
        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            if (player.getName() != null && player.getName().equalsIgnoreCase(name)) {
                return player;
            }
        }
        return null;
    }
}
