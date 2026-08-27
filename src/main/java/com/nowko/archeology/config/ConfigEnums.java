package com.nowko.archeology.config;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.logging.Level;

/**
 * Parses Bukkit enum names from YAML with a fallback when the token is unknown.
 */
public final class ConfigEnums {
    private ConfigEnums() {
    }

    /**
     * @param plugin logger owner
     * @param raw YAML token such as {@code IRON_PICKAXE}
     * @param fallback used when {@code raw} is missing or invalid
     * @param path config path for the warning
     * @return material, never air
     */
    public static Material material(JavaPlugin plugin, String raw, Material fallback, String path) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        Material material = Material.matchMaterial(raw.trim());
        if (material == null || material.isAir()) {
            plugin.getLogger().log(Level.WARNING, "Unknown material at " + path + ": " + raw + " — using " + fallback.name());
            return fallback;
        }
        return material;
    }

    /**
     * @param plugin logger owner
     * @param raw YAML token such as {@code END_ROD}
     * @param fallback used when {@code raw} is missing or invalid
     * @param path config path for the warning
     * @return particle type
     */
    public static Particle particle(JavaPlugin plugin, String raw, Particle fallback, String path) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Particle.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().log(Level.WARNING, "Unknown particle at " + path + ": " + raw + " — using " + fallback.name());
            return fallback;
        }
    }
}
