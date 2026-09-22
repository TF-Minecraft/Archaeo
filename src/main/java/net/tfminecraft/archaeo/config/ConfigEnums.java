package net.tfminecraft.archaeo.config;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
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
     * @param raw YAML token such as {@code ENCHANTED_HIT}
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

    /**
     * @param plugin logger owner
     * @param raw YAML token such as {@code ITEM_BUCKET_EMPTY} or {@code item.bucket.empty}
     * @param fallback used when {@code raw} is missing or invalid
     * @param path config path for the warning
     * @return sound, or {@code fallback}
     */
    public static Sound sound(JavaPlugin plugin, String raw, Sound fallback, String path) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String token = raw.trim();
        String key = token;
        if (!token.contains(":") && !token.contains(".")) {
            key = "minecraft:" + token.toLowerCase(Locale.ROOT).replace('_', '.');
        } else if (!token.contains(":")) {
            key = "minecraft:" + token.toLowerCase(Locale.ROOT);
        } else {
            key = token.toLowerCase(Locale.ROOT);
        }
        NamespacedKey namespaced = NamespacedKey.fromString(key);
        Sound sound = namespaced == null ? null : Registry.SOUNDS.get(namespaced);
        if (sound != null) {
            return sound;
        }
        plugin.getLogger().log(Level.WARNING, "Unknown sound at " + path + ": " + raw + " — using " + fallbackKey(fallback));
        return fallback;
    }

    /**
     * @param sound resolved sound
     * @return log token
     */
    private static String fallbackKey(Sound sound) {
        if (sound == null) {
            return "none";
        }
        NamespacedKey key = sound.getKey();
        return key == null ? sound.toString() : key.toString();
    }
}
