package com.nowko.archeology.establish;

import com.nowko.archeology.model.Site;
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
final class CampNames {
    private CampNames() {
    }

    /**
     * Director first, then granted excavators, without duplicates.
     *
     * @param site excavation
     * @return ordered roster
     */
    static List<UUID> roster(Site site) {
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        if (site.getDirector() != null) {
            ids.add(site.getDirector());
        }
        ids.addAll(site.getExcavators());
        return new ArrayList<>(ids);
    }

    /**
     * @param viewer player opening the board
     * @param id stored uuid
     * @return last known name, or a short uuid
     */
    static String of(Player viewer, UUID id) {
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
        OfflinePlayer offline = Bukkit.getOfflinePlayer(id);
        String name = offline.getName();
        if (name != null && !name.isBlank()) {
            return name;
        }
        return id.toString().substring(0, 8);
    }

    /**
     * Matches an online player or someone who has already joined this server.
     *
     * @param raw chat name
     * @return known player, or {@code null}
     */
    static OfflinePlayer known(String raw) {
        if (raw == null || raw.isBlank()) {
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
