package com.nowko.archeology.config;

import com.nowko.archeology.model.InterestLevel;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Loads catalog YAML from the plugin jar (first run) and then from the data folder.
 */
public class CatalogRegistry {
    private final JavaPlugin plugin;
    private final Map<InterestLevel, InterestSettings> interests = new EnumMap<>(InterestLevel.class);
    private final Map<String, StratumDefinition> strata = new LinkedHashMap<>();
    private final Map<String, ArtifactTemplate> artifacts = new LinkedHashMap<>();
    private final Map<String, HintTemplate> hints = new LinkedHashMap<>();
    private int maxShapeAttempts = 24;
    private boolean growVertically = true;
    private boolean useWorldSeed = true;
    private TrackerSettings tracker = TrackerSettings.defaults();
    private ItemMaterials items = ItemMaterials.defaults();
    private String staffPermission = "archaeo.admin";

    /**
     * @param plugin owner used for data folder and {@link JavaPlugin#saveResource}
     */
    public CatalogRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Ensures default catalog files exist on disk (first run only), then reads them into memory.
     */
    public void load() {
        copyDefaultIfAbsent("config.yml");
        copyDefaultIfAbsent("strata.yml");
        copyDefaultIfAbsent("artifacts.yml");
        copyDefaultIfAbsent("hints.yml");
        copyDefaultIfAbsent("interpretations.yml");
        copyDefaultIfAbsent("materials.yml");

        plugin.reloadConfig();
        loadInterests(plugin.getConfig());
        loadGeneration(plugin.getConfig());
        loadTracker(plugin.getConfig());
        loadItems(plugin.getConfig());
        loadStaffPermission(plugin.getConfig());
        loadStrata(yaml("strata.yml"));
        loadArtifacts(yaml("artifacts.yml"));
        loadHints(yaml("hints.yml"));
    }

    /**
     * @param level interest key from config
     * @return generation budget and detection settings for that level
     */
    public InterestSettings interest(InterestLevel level) {
        return interests.get(level);
    }

    /**
     * @return stratum definitions sorted from most recent (I) to deepest
     */
    public List<StratumDefinition> strataInOrder() {
        List<StratumDefinition> list = new ArrayList<>(strata.values());
        list.sort((a, b) -> Integer.compare(a.order(), b.order()));
        return list;
    }

    /**
     * @param id stratum id such as {@code I}
     * @return definition or {@code null}
     */
    public StratumDefinition stratum(String id) {
        return strata.get(id);
    }

    /**
     * @return immutable map of artifact templates by id
     */
    public Map<String, ArtifactTemplate> artifacts() {
        return Collections.unmodifiableMap(artifacts);
    }

    /**
     * @param id artifact template id
     * @return template or {@code null}
     */
    public ArtifactTemplate artifact(String id) {
        return artifacts.get(id);
    }

    /**
     * @return all hint templates
     */
    public List<HintTemplate> hints() {
        return List.copyOf(hints.values());
    }

    /**
     * @return max retries when growing a connected find shape
     */
    public int maxShapeAttempts() {
        return maxShapeAttempts;
    }

    /**
     * @return whether find shapes may grow up and down inside a stratum band
     */
    public boolean growVertically() {
        return growVertically;
    }

    /**
     * @return tracker radii, pip timing, and item copy
     */
    public TrackerSettings tracker() {
        return tracker;
    }

    /**
     * @return materials for tracker and excavation tools
     */
    public ItemMaterials items() {
        return items;
    }

    /**
     * @return Bukkit permission node required for {@code /archaeo} staff commands
     */
    public String staffPermission() {
        return staffPermission;
    }

    /**
     * @return whether site generation should derive RNG from the world seed and chunk
     */
    public boolean useWorldSeed() {
        return useWorldSeed;
    }

    /**
     * Reads shape-generation flags from {@code config.yml}.
     *
     * @param config root plugin config
     */
    private void loadGeneration(org.bukkit.configuration.file.FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("generation");
        if (section == null) {
            return;
        }
        maxShapeAttempts = section.getInt("max-shape-attempts", 24);
        growVertically = section.getBoolean("grow-vertically", true);
        useWorldSeed = section.getBoolean("use-world-seed", true);
    }

    /**
     * Reads tracker item and scan settings from {@code config.yml}.
     *
     * @param config root plugin config
     */
    private void loadTracker(org.bukkit.configuration.file.FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("tracker");
        if (section == null) {
            return;
        }
        List<String> lore = section.getStringList("item-lore");
        if (lore.isEmpty()) {
            lore = List.of(
                    "Walk. Faster pulses mean closer.",
                    "Rings lean toward a heading; they are not a compass.",
                    "More pips when you are near. No coordinates."
            );
        }
        List<Double> radii = section.getDoubleList("wave-radii");
        if (radii.size() < 3) {
            radii = List.of(1.2, 2.6, 4.2);
        } else {
            radii = List.copyOf(radii.subList(0, 3));
        }
        tracker = new TrackerSettings(
                section.getBoolean("enabled", true),
                Math.max(1, section.getInt("default-max-range", 256)),
                Math.max(1, section.getInt("near-range", 48)),
                Math.max(1, section.getInt("detect-message-range", 16)),
                Math.max(0, section.getInt("detect-message-share-range", 0)),
                section.getBoolean("pulse-particles", true),
                Math.max(1, section.getInt("beep-max-ticks", 70)),
                Math.max(1, section.getInt("beep-min-ticks", 5)),
                Math.max(1, section.getInt("detect-message-cooldown-ticks", 200)),
                radii,
                Math.max(1, section.getInt("wave-step-ticks", 3)),
                Math.max(1, section.getInt("particle-fade-ticks", 10)),
                ConfigEnums.particle(plugin, section.getString("wave-particle"), Particle.END_ROD, "tracker.wave-particle"),
                Math.max(0.0, section.getDouble("wave-bias-blocks", 1.2)),
                Math.max(0.0, section.getDouble("target-switch-margin", 16)),
                section.getString("item-name", "Archaeological tracker"),
                List.copyOf(lore)
        );
    }

    /**
     * Reads Bukkit materials for plugin tools.
     *
     * @param config root plugin config
     */
    private void loadItems(org.bukkit.configuration.file.FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("items");
        ItemMaterials fallback = ItemMaterials.defaults();
        if (section == null) {
            items = fallback;
            return;
        }
        items = new ItemMaterials(
                ConfigEnums.material(plugin, section.getString("tracker"), fallback.tracker(), "items.tracker"),
                ConfigEnums.material(plugin, section.getString("pick"), fallback.pick(), "items.pick"),
                ConfigEnums.material(plugin, section.getString("shovel"), fallback.shovel(), "items.shovel"),
                ConfigEnums.material(plugin, section.getString("hammer"), fallback.hammer(), "items.hammer"),
                ConfigEnums.material(plugin, section.getString("brush"), fallback.brush(), "items.brush")
        );
    }

    /**
     * Reads the LuckPerms / Bukkit node for staff commands.
     *
     * @param config root plugin config
     */
    private void loadStaffPermission(org.bukkit.configuration.file.FileConfiguration config) {
        String node = config.getString("permissions.staff", "archaeo.admin");
        staffPermission = node == null || node.isBlank() ? "archaeo.admin" : node.trim();
    }

    /**
     * Reads interest budgets used when creating a site.
     *
     * @param config root plugin config
     * @throws IllegalStateException if an interest-levels block is missing
     */
    private void loadInterests(org.bukkit.configuration.file.FileConfiguration config) {
        interests.clear();
        ConfigurationSection root = config.getConfigurationSection("interest-levels");
        if (root == null) {
            throw new IllegalStateException("Missing interest-levels in config.yml");
        }
        for (InterestLevel level : InterestLevel.values()) {
            ConfigurationSection section = root.getConfigurationSection(level.yamlKey());
            if (section == null) {
                throw new IllegalStateException("Missing interest-levels." + level.yamlKey());
            }
            interests.put(level, new InterestSettings(
                    level,
                    section.getString("display-name", level.yamlKey()),
                    section.getInt("base-wealth"),
                    section.getInt("variation"),
                    section.getInt("detection-radius"),
                    section.getInt("min-finds"),
                    section.getInt("max-finds"),
                    section.getInt("min-relics"),
                    section.getInt("max-relics"),
                    section.getInt("hint-count", 2),
                    section.getDouble("stratum-iv-chance"),
                    section.getDouble("disturbed-chance")
            ));
        }
    }

    /**
     * Reads stratum depth bands from {@code strata.yml}.
     *
     * @param yaml parsed strata file
     */
    private void loadStrata(YamlConfiguration yaml) {
        strata.clear();
        ConfigurationSection root = yaml.getConfigurationSection("strata");
        if (root == null) {
            throw new IllegalStateException("Missing strata in strata.yml");
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            strata.put(id, new StratumDefinition(
                    id,
                    section.getInt("order"),
                    section.getString("display-name", id),
                    section.getString("antiquity", ""),
                    section.getInt("depth-min"),
                    section.getInt("depth-max"),
                    section.getBoolean("always-present", true)
            ));
        }
    }

    /**
     * Reads find templates (size, tags, relic flag) from {@code artifacts.yml}.
     *
     * @param yaml parsed artifacts file
     */
    private void loadArtifacts(YamlConfiguration yaml) {
        artifacts.clear();
        ConfigurationSection root = yaml.getConfigurationSection("artifacts");
        if (root == null) {
            throw new IllegalStateException("Missing artifacts in artifacts.yml");
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            artifacts.put(id, new ArtifactTemplate(
                    id,
                    section.getString("display-name", id),
                    section.getInt("size-min", 1),
                    section.getInt("size-max", 1),
                    section.getString("material", "stone"),
                    section.getString("rarity", "common"),
                    section.getBoolean("relic", false),
                    Math.max(1, section.getInt("weight", 1)),
                    new LinkedHashSet<>(section.getStringList("strata")),
                    new LinkedHashSet<>(section.getStringList("tags")),
                    section.getString("item", "STONE")
            ));
        }
    }

    /**
     * Reads site hint texts and filter rules from {@code hints.yml}.
     *
     * @param yaml parsed hints file
     */
    private void loadHints(YamlConfiguration yaml) {
        hints.clear();
        ConfigurationSection root = yaml.getConfigurationSection("hints");
        if (root == null) {
            throw new IllegalStateException("Missing hints in hints.yml");
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            hints.put(id, new HintTemplate(
                    id,
                    section.getString("text", id),
                    Math.max(1, section.getInt("weight", 1)),
                    new LinkedHashSet<>(section.getStringList("tags")),
                    new LinkedHashSet<>(section.getStringList("require-tags-any")),
                    new LinkedHashSet<>(section.getStringList("require-tags-all")),
                    new LinkedHashSet<>(section.getStringList("require-strata-all")),
                    section.getString("require-missing-stratum"),
                    section.contains("min-wealth") ? section.getInt("min-wealth") : null,
                    section.contains("max-wealth") ? section.getInt("max-wealth") : null,
                    section.contains("min-strata") ? section.getInt("min-strata") : null,
                    section.contains("require-disturbed") ? section.getBoolean("require-disturbed") : null
            ));
        }
    }

    /**
     * @param name YAML file in the plugin data folder
     * @return parsed configuration (empty if the file is missing)
     */
    private YamlConfiguration yaml(String name) {
        return YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), name));
    }

    /**
     * Copies a packaged default YAML into the plugin data folder when that file
     * does not exist yet.
     * <p>
     * This is not a save of in-memory config. Existing files on disk are left
     * untouched, so operator edits survive restarts. {@code saveResource(..., false)}
     * also refuses to replace an existing file.
     *
     * @param name file name inside {@code src/main/resources}
     */
    private void copyDefaultIfAbsent(String name) {
        File folder = plugin.getDataFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create " + folder.getPath());
        }
        File target = new File(folder, name);
        if (target.exists()) {
            return;
        }
        plugin.saveResource(name, false);
    }
}
