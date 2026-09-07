package com.nowko.archeology.config;

import com.nowko.archeology.item.ItemRef;
import com.nowko.archeology.model.InterestLevel;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
    private final Map<String, FindMaterial> materials = new LinkedHashMap<>();
    private int maxShapeAttempts = 24;
    private boolean useWorldSeed = true;
    private TrackerSettings tracker = TrackerSettings.defaults();
    private ProspectSettings prospect = ProspectSettings.defaults();
    private EstablishSettings establish = EstablishSettings.defaults();
    private PickSettings pick = PickSettings.defaults();
    private RecoverySettings recovery = RecoverySettings.defaults();
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
        copyDefaultIfAbsent("interest.yml");
        copyDefaultIfAbsent("strata.yml");
        copyDefaultIfAbsent("artifacts.yml");
        copyDefaultIfAbsent("hints.yml");
        copyDefaultIfAbsent("interpretations.yml");
        copyDefaultIfAbsent("materials.yml");

        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();
        loadItems(yaml("items.yml"), config);
        loadInterests(yaml("interest.yml"), config);
        loadGeneration(yaml("interest.yml"), config);
        loadTracker(config);
        loadProspect(config);
        loadEstablish(config);
        loadPick(config);
        loadRecovery(config);
        loadStaffPermission(config);
        loadStrata(yaml("strata.yml"));
        loadArtifacts(yaml("artifacts.yml"));
        loadHints(yaml("hints.yml"));
        loadMaterials(yaml("materials.yml"));
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
     * @param id material key from {@code artifacts.yml} / {@code materials.yml}
     * @return English label for field traces
     */
    public String materialDisplayName(String id) {
        if (id == null || id.isBlank()) {
            return "unknown";
        }
        FindMaterial material = materials.get(id);
        if (material != null) {
            return material.displayName();
        }
        return Character.toUpperCase(id.charAt(0)) + id.substring(1);
    }

    /**
     * How kindly the ground treats this material. Unknown materials survive fully.
     *
     * @param id material key from {@code artifacts.yml}
     * @return multiplier applied to the buried-condition roll
     */
    public double materialSurvival(String id) {
        if (id == null || id.isBlank()) {
            return 1.0;
        }
        FindMaterial material = materials.get(id);
        return material == null ? 1.0 : material.survival();
    }

    /**
     * @return all hint templates
     */
    public List<HintTemplate> hints() {
        return List.copyOf(hints.values());
    }

    /**
     * @param id hint template key
     * @return template or {@code null}
     */
    public HintTemplate hint(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return hints.get(id);
    }

    /**
     * @return max retries when growing a connected find shape
     */
    public int maxShapeAttempts() {
        return maxShapeAttempts;
    }

    /**
     * @return tracker radii, pip timing, and item copy
     */
    public TrackerSettings tracker() {
        return tracker;
    }

    /**
     * @return prospecting kit sample rules and copy
     */
    public ProspectSettings prospect() {
        return prospect;
    }

    /**
     * @return establishment-kit rules and copy
     */
    public EstablishSettings establish() {
        return establish;
    }

    /**
     * @return excavation flags and named tool profiles
     */
    public PickSettings pick() {
        return pick;
    }

    /**
     * @return field-brush recovery rules and copy
     */
    public RecoverySettings recovery() {
        return recovery;
    }

    /**
     * @return materials for tracker, kits, brush, and excavation profiles
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
     * Reads shape-generation flags from {@code interest.yml}, falling back to {@code config.yml}.
     *
     * @param interest parsed interest file
     * @param config root plugin config
     */
    private void loadGeneration(YamlConfiguration interest, FileConfiguration config) {
        ConfigurationSection section = sectionOr(interest, config, "generation");
        if (section == null) {
            return;
        }
        maxShapeAttempts = section.getInt("max-shape-attempts", 24);
        useWorldSeed = section.getBoolean("use-world-seed", true);
    }

    /**
     * Reads tracker scan radii and pip timing from {@code config.yml}.
     *
     * @param config root plugin config
     */
    private void loadTracker(org.bukkit.configuration.file.FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("tracker");
        if (section == null) {
            return;
        }
        TrackerSettings fallback = TrackerSettings.defaults();
        List<Double> radii = section.getDoubleList("wave-radii");
        if (radii.size() < 3) {
            radii = fallback.waveRadii();
        } else {
            radii = List.copyOf(radii.subList(0, 3));
        }
        tracker = new TrackerSettings(
                section.getBoolean("enabled", fallback.enabled()),
                Math.max(1, sectionInt(section, fallback.defaultMaxRange(), "max-range", "default-max-range")),
                Math.max(1, sectionInt(section, fallback.nearRange(), "medium-range", "near-range")),
                Math.max(1, sectionInt(section, fallback.detectMessageRange(), "close-range", "detect-message-range")),
                section.getBoolean("pulse-particles", fallback.pulseParticles()),
                Math.max(1, sectionInt(section, fallback.beepMaxTicks(), "beep-max-ticks")),
                Math.max(1, sectionInt(section, fallback.beepMinTicks(), "beep-min-ticks")),
                Math.max(1, sectionInt(section, fallback.detectMessageCooldownTicks(), "detect-message-cooldown-ticks")),
                radii,
                Math.max(1, sectionInt(section, fallback.waveStepTicks(), "wave-step-ticks")),
                Math.max(1, sectionInt(section, fallback.particleFadeTicks(), "particle-fade-ticks")),
                ConfigEnums.particle(
                        plugin,
                        section.getString("wave-particle"),
                        fallback.waveParticle(),
                        "tracker.wave-particle"),
                Math.max(0.0, sectionDouble(section, fallback.waveBiasBlocks(), "wave-bias-blocks")),
                Math.max(0.0, sectionDouble(section, fallback.targetSwitchMargin(), "target-switch-margin"))
        );
    }

    /**
     * Reads item ids from each feature section in {@code config.yml}.
     * Older {@code items.yml} or {@code items:} maps are still accepted if those keys are missing.
     *
     * @param itemsFile optional leftover {@code items.yml}
     * @param config root plugin config
     */
    private void loadItems(YamlConfiguration itemsFile, FileConfiguration config) {
        ItemMaterials fallback = ItemMaterials.defaults();
        items = new ItemMaterials(
                parseItemId(fallback.tracker(), itemsFile, config,
                        "tracker.item", "discovery.tracker", "items.tracker"),
                parseItemId(fallback.prospect(), itemsFile, config,
                        "prospect.item", "discovery.prospect", "items.prospect"),
                parseItemId(fallback.establish(), itemsFile, config,
                        "establish.item", "discovery.establish", "items.establish"),
                parseItemId(fallback.brush(), itemsFile, config,
                        "excavation.brush.item", "recovery.item", "recovery.brush", "items.brush"),
                loadExcavationItemLists(itemsFile, config, fallback)
        );
    }

    /**
     * @param fallback packaged id
     * @param itemsFile leftover items file
     * @param config root plugin config
     * @param paths dotted paths tried on {@code config} then {@code itemsFile}
     * @return parsed ref
     */
    private ItemRef parseItemId(
            ItemRef fallback,
            YamlConfiguration itemsFile,
            FileConfiguration config,
            String... paths
    ) {
        String raw = firstPath(config, paths);
        if (raw == null) {
            raw = firstPath(itemsFile, paths);
        }
        return ItemRef.parseOr(plugin, raw, fallback);
    }

    /**
     * @param root YAML root
     * @param paths dotted keys
     * @return first non-blank string that is not a section
     */
    private static String firstPath(Configuration root, String... paths) {
        if (root == null) {
            return null;
        }
        for (String path : paths) {
            if (!root.contains(path) || root.isConfigurationSection(path)) {
                continue;
            }
            String value = root.getString(path);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /**
     * Reads per-profile stack lists from {@code excavation.tools.<id>.items}, then leftover files.
     *
     * @param itemsFile leftover {@code items.yml}
     * @param config root plugin config
     * @param fallback packaged lists
     * @return profile id to whitelist
     */
    private Map<String, List<ItemRef>> loadExcavationItemLists(
            YamlConfiguration itemsFile,
            FileConfiguration config,
            ItemMaterials fallback
    ) {
        Map<String, List<ItemRef>> profiles = new LinkedHashMap<>();
        ConfigurationSection tools = config.getConfigurationSection("excavation.tools");
        ConfigurationSection itemsExcavation = itemsFile == null
                ? null
                : itemsFile.getConfigurationSection("excavation");
        LinkedHashSet<String> ids = new LinkedHashSet<>(fallback.excavationProfiles().keySet());
        if (tools != null) {
            ids.addAll(tools.getKeys(false));
        }
        if (itemsExcavation != null) {
            for (String key : itemsExcavation.getKeys(false)) {
                if (!"pick".equals(key) && !"give".equals(key) && !"brush".equals(key)) {
                    ids.add(key);
                }
            }
        }
        for (String id : ids) {
            List<ItemRef> materials = List.of();
            ConfigurationSection tool = tools == null ? null : tools.getConfigurationSection(id);
            if (tool != null) {
                materials = toolItems(tool);
            }
            if (materials.isEmpty() && itemsExcavation != null) {
                materials = readItemList(itemsExcavation, id);
            }
            if (materials.isEmpty()) {
                materials = fallback.profileMaterials(id);
            }
            if (!materials.isEmpty()) {
                profiles.put(id, materials);
            }
        }
        return Collections.unmodifiableMap(profiles);
    }

    /**
     * @param tool one {@code excavation.tools} profile
     * @return {@code items} or legacy {@code materials}
     */
    private List<ItemRef> toolItems(ConfigurationSection tool) {
        List<String> raw = tool.getStringList("items");
        if (raw.isEmpty()) {
            raw = tool.getStringList("materials");
        }
        return ItemRef.parseList(plugin, raw);
    }

    /**
     * @param parent excavation or tool parent
     * @param id profile key
     * @return parsed list, or empty
     */
    private List<ItemRef> readItemList(ConfigurationSection parent, String id) {
        List<String> raw = parent.getStringList(id);
        if (!raw.isEmpty()) {
            return ItemRef.parseList(plugin, raw);
        }
        ConfigurationSection nested = parent.getConfigurationSection(id);
        if (nested == null) {
            return List.of();
        }
        return ItemRef.parseList(plugin, nested.getStringList("materials"));
    }

    /**
     * Reads prospecting-kit sample rules.
     *
     * @param config root plugin config
     */
    private void loadProspect(org.bukkit.configuration.file.FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("prospect");
        ProspectSettings fallback = ProspectSettings.defaults();
        if (section == null) {
            prospect = fallback;
            return;
        }
        prospect = new ProspectSettings(
                section.getBoolean("enabled", true),
                Math.max(1, section.getInt("points-required", 4)),
                Math.max(1, section.getInt("use-ticks", 40)),
                Math.max(1, section.getInt("min-sample-distance", 3))
        );
    }

    /**
     * Reads establishment-kit rules, camp block, and preview blocks.
     *
     * @param config root plugin config
     */
    private void loadEstablish(org.bukkit.configuration.file.FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("establish");
        EstablishSettings fallback = EstablishSettings.defaults();
        if (section == null) {
            establish = fallback;
            return;
        }
        establish = new EstablishSettings(
                section.getBoolean("enabled", true),
                section.getBoolean("protect-dig-site", fallback.protectDigSite()),
                ConfigEnums.material(
                        plugin,
                        section.getString("invalid-block"),
                        fallback.invalidBlock(),
                        "establish.invalid-block"),
                ConfigEnums.material(
                        plugin,
                        section.getString("ruin-outline-block"),
                        fallback.ruinOutlineBlock(),
                        "establish.ruin-outline-block")
        );
    }

    /**
     * Reads shared excavation flags and named tool profiles.
     * Cadence lives on {@code excavation.tools.<id>}. Shared keys may still sit on {@code pick:}.
     *
     * @param config root plugin config
     */
    private void loadPick(FileConfiguration config) {
        PickSettings fallback = PickSettings.defaults();
        ConfigurationSection excavation = config.getConfigurationSection("excavation");
        ConfigurationSection pickSection = config.getConfigurationSection("pick");
        pick = new PickSettings(
                firstBool(excavation, pickSection, fallback.enabled(), "enabled"),
                Math.max(1, firstInt(excavation, pickSection, fallback.jornadaActions(),
                        "workday-actions", "workday-allowed-actions", "jornada-actions")),
                firstBool(excavation, pickSection, fallback.visualCues(), "visual-cues"),
                firstBool(excavation, pickSection, fallback.findDust(), "find-particles", "find-dust"),
                Math.max(1, firstInt(excavation, pickSection, fallback.findDustIntervalTicks(),
                        "find-particles-interval-ticks", "find-dust-interval-ticks")),
                Math.max(1, firstInt(excavation, pickSection, fallback.findDustCount(),
                        "find-particles-count", "find-dust-count")),
                loadConservation(excavation, fallback.conservation()),
                firstBool(excavation, pickSection, fallback.neighborTraces(), "neighbor-traces"),
                loadProfiles(excavation, pickSection, fallback)
        );
    }

    /**
     * Reads {@code excavation.conservation}: how much of a piece the ground already took,
     * and the bands used to describe the final number.
     *
     * @param excavation {@code excavation:} or {@code null}
     * @param fallback packaged conservation rules
     * @return merged settings; missing keys keep the packaged value
     */
    private ConservationSettings loadConservation(ConfigurationSection excavation, ConservationSettings fallback) {
        ConfigurationSection root = excavation == null
                ? null
                : excavation.getConfigurationSection("conservation");
        if (root == null) {
            return fallback;
        }
        ConfigurationSection buried = root.getConfigurationSection("buried");
        int min = fallback.buriedMin();
        int max = fallback.buriedMax();
        double bias = fallback.bias();
        int depthPenalty = fallback.depthPenalty();
        int disturbedPenalty = fallback.disturbedPenalty();
        if (buried != null) {
            min = clampPercent(buried.getInt("min", fallback.buriedMin()));
            max = clampPercent(buried.getInt("max", fallback.buriedMax()));
            bias = Math.max(0.1, buried.getDouble("bias", fallback.bias()));
            depthPenalty = Math.max(0, buried.getInt("depth-penalty", fallback.depthPenalty()));
            disturbedPenalty = Math.max(0, buried.getInt("disturbed-penalty", fallback.disturbedPenalty()));
        }
        if (min > max) {
            int swap = min;
            min = max;
            max = swap;
        }
        List<ConservationGrade> grades = loadGrades(root, fallback.grades());
        return new ConservationSettings(min, max, bias, depthPenalty, disturbedPenalty, grades);
    }

    /**
     * @param root {@code excavation.conservation}
     * @param fallback packaged bands
     * @return bands sorted from the best condition down
     */
    private List<ConservationGrade> loadGrades(ConfigurationSection root, List<ConservationGrade> fallback) {
        List<?> raw = root.getList("grades");
        if (raw == null || raw.isEmpty()) {
            return fallback;
        }
        List<ConservationGrade> grades = new ArrayList<>();
        for (Object entry : raw) {
            if (!(entry instanceof Map<?, ?> map)) {
                continue;
            }
            Object id = map.get("id");
            if (id == null) {
                continue;
            }
            String key = String.valueOf(id);
            int minPercent = clampPercent(intOrDefault(map.get("min-percent"), 0));
            String label = map.get("label") == null ? key : String.valueOf(map.get("label"));
            grades.add(new ConservationGrade(key, minPercent, label));
        }
        if (grades.isEmpty()) {
            return fallback;
        }
        grades.sort(Comparator.comparingInt(ConservationGrade::minPercent).reversed());
        return List.copyOf(grades);
    }

    /**
     * @param raw YAML scalar
     * @param fallback when the value is missing or not a number
     * @return parsed integer
     */
    private static int intOrDefault(Object raw, int fallback) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(raw).trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    /**
     * @param value raw percentage
     * @return value clamped to 0–100
     */
    private static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }

    /**
     * Builds named tools from {@code excavation.tools} (whitelist, lifts, window; tempo is vanilla).
     *
     * @param excavation {@code excavation:} or {@code null}
     * @param pickSection {@code pick:} or {@code null}
     * @param fallback packaged profiles
     * @return named tools; legacy {@code pick.tools} becomes one {@code hand} profile
     */
    private List<ExcavationTool> loadProfiles(
            ConfigurationSection excavation,
            ConfigurationSection pickSection,
            PickSettings fallback
    ) {
        ConfigurationSection tools = excavation == null ? null : excavation.getConfigurationSection("tools");
        LinkedHashSet<String> ids = new LinkedHashSet<>(items.excavationProfiles().keySet());
        if (tools != null) {
            ids.addAll(tools.getKeys(false));
        }
        if (!ids.isEmpty()) {
            List<ExcavationTool> profiles = new ArrayList<>();
            for (String id : ids) {
                ConfigurationSection tool = tools == null ? null : tools.getConfigurationSection(id);
                List<ItemRef> materials = items.profileMaterials(id);
                if (materials.isEmpty() && tool != null) {
                    materials = toolItems(tool);
                }
                if (materials.isEmpty()) {
                    continue;
                }
                profiles.add(readTool(id, tool, materials, ExcavationTool.packaged(id)));
            }
            if (!profiles.isEmpty()) {
                return List.copyOf(profiles);
            }
        }
        if (pickSection != null && !pickSection.getStringList("tools").isEmpty()) {
            List<ItemRef> materials = ItemRef.parseAll(plugin, pickSection.getStringList("tools"));
            ExcavationTool inherit = ExcavationTool.hand();
            return List.of(readTool("hand", pickSection, materials, inherit));
        }
        return fallback.profiles();
    }

    /**
     * @param id profile key
     * @param section tool (or legacy pick) keys; {@code null} uses {@code inherit} lifts and window
     * @param materials already parsed stacks
     * @param inherit defaults when a key is omitted
     * @return one profile
     */
    private static ExcavationTool readTool(
            String id,
            ConfigurationSection section,
            List<ItemRef> materials,
            ExcavationTool inherit
    ) {
        if (section == null) {
            return new ExcavationTool(
                    id,
                    materials,
                    inherit.chimeTicks(),
                    inherit.miningSpeed(),
                    inherit.miningSpeedMultiplier(),
                    inherit.cellsOnTime(),
                    inherit.cellsOnLate(),
                    inherit.breakShape(),
                    inherit.jornadaCost(),
                    inherit.readyWindowTicks()
            );
        }
        int cellsOnTime = Math.max(1, sectionInt(section, inherit.cellsOnTime(), "lift-on-ready", "blocks-on-time", "cells-on-time"));
        String shape = sectionString(section, "break-shape", "lift-shape", "late-extras", "late-shape", "extra-shape");
        int chimeTicks = Math.max(0, sectionInt(section, inherit.chimeTicks(),
                "chime-ticks", "beat-ticks", "strike-interval-ticks"));
        return new ExcavationTool(
                id,
                materials,
                chimeTicks,
                sectionFloat(section, inherit.miningSpeed(), "mining-speed", "default-mining-speed"),
                sectionFloat(section, inherit.miningSpeedMultiplier(), "mining-speed-multiplier", "break-speed-multiplier"),
                cellsOnTime,
                Math.max(cellsOnTime, sectionInt(section, inherit.cellsOnLate(), "lift-if-late", "blocks-on-late", "cells-on-late")),
                shape != null ? BreakShape.parse(shape) : inherit.breakShape(),
                Math.max(1, sectionInt(section, inherit.jornadaCost(), "workday-cost", "jornada-cost")),
                Math.max(1, sectionInt(section, inherit.readyWindowTicks(), "release-window-ticks", "ready-ticks", "ready-window-ticks"))
        );
    }

    /**
     * @param first preferred section
     * @param second fallback section
     * @param fallback when no key is present
     * @param keys YAML keys in preference order
     * @return integer value
     */
    private static int firstInt(ConfigurationSection first, ConfigurationSection second, int fallback, String... keys) {
        Integer value = sectionIntOrNull(first, keys);
        if (value != null) {
            return value;
        }
        value = sectionIntOrNull(second, keys);
        return value != null ? value : fallback;
    }

    /**
     * @param first preferred section
     * @param second fallback section
     * @param fallback when no key is present
     * @param keys YAML keys in preference order
     * @return boolean value
     */
    private static boolean firstBool(ConfigurationSection first, ConfigurationSection second, boolean fallback, String... keys) {
        for (String key : keys) {
            if (first != null && first.contains(key)) {
                return first.getBoolean(key);
            }
        }
        for (String key : keys) {
            if (second != null && second.contains(key)) {
                return second.getBoolean(key);
            }
        }
        return fallback;
    }

    /**
     * @param section tool or feature block
     * @param fallback when no key is present
     * @param keys YAML keys in preference order
     * @return integer value
     */
    private static int sectionInt(ConfigurationSection section, int fallback, String... keys) {
        Integer value = sectionIntOrNull(section, keys);
        return value != null ? value : fallback;
    }

    /**
     * @param section YAML block
     * @param fallback when no key is present ({@code null} means leave the live item)
     * @param keys YAML keys in preference order
     * @return first present float, or {@code fallback}
     */
    private static Float sectionFloat(ConfigurationSection section, Float fallback, String... keys) {
        if (section == null) {
            return fallback;
        }
        for (String key : keys) {
            if (!section.contains(key) || section.isConfigurationSection(key)) {
                continue;
            }
            String raw = section.getString(key);
            if (raw == null || raw.isBlank()) {
                return null;
            }
            return (float) section.getDouble(key);
        }
        return fallback;
    }

    /**
     * @param section YAML block
     * @param keys keys in preference order
     * @return first present int, or {@code null}
     */
    private static Integer sectionIntOrNull(ConfigurationSection section, String... keys) {
        if (section == null) {
            return null;
        }
        for (String key : keys) {
            if (section.contains(key)) {
                return section.getInt(key);
            }
        }
        return null;
    }

    /**
     * @param section tool block
     * @param fallback when no key is present
     * @param keys YAML keys in preference order
     * @return double value
     */
    private static double sectionDouble(ConfigurationSection section, double fallback, String... keys) {
        if (section == null) {
            return fallback;
        }
        for (String key : keys) {
            if (section.contains(key)) {
                return section.getDouble(key);
            }
        }
        return fallback;
    }

    /**
     * @param section tool block
     * @param keys YAML keys in preference order
     * @return first present string, or {@code null}
     */
    private static String sectionString(ConfigurationSection section, String... keys) {
        if (section == null) {
            return null;
        }
        for (String key : keys) {
            if (section.contains(key) && !section.isConfigurationSection(key)) {
                return section.getString(key);
            }
        }
        return null;
    }

    /**
     * Reads field-brush recovery from {@code excavation.brush}, or leftover {@code recovery:}.
     *
     * @param config root plugin config
     */
    private void loadRecovery(FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("excavation.brush");
        if (section == null) {
            section = config.getConfigurationSection("recovery");
        }
        RecoverySettings fallback = RecoverySettings.defaults();
        if (section == null) {
            recovery = fallback;
            return;
        }
        recovery = new RecoverySettings(
                section.getBoolean("enabled", true),
                Math.max(1, sectionInt(section, fallback.channelTicks(), "hold-ticks", "channel-ticks")),
                Math.max(1, section.getInt("max-cells-to-clean", fallback.maxCellsToClean())),
                section.getBoolean("progress-bar", fallback.progressBar())
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
     * Reads interest budgets from {@code interest.yml}, falling back to {@code config.yml}.
     *
     * @param interest parsed interest file
     * @param config root plugin config
     * @throws IllegalStateException if an interest-levels block is missing
     */
    private void loadInterests(YamlConfiguration interest, FileConfiguration config) {
        interests.clear();
        ConfigurationSection root = sectionOr(interest, config, "interest-levels");
        if (root == null) {
            throw new IllegalStateException("Missing interest-levels in interest.yml");
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
     * Reads field-trace labels from {@code materials.yml}. Lab steps are ignored until the lab exists.
     *
     * @param yaml parsed materials file
     */
    private void loadMaterials(YamlConfiguration yaml) {
        materials.clear();
        ConfigurationSection root = yaml.getConfigurationSection("materials");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            double survival = Math.max(0.05, Math.min(1.0, section.getDouble("survival", 1.0)));
            materials.put(id, new FindMaterial(id, section.getString("display-name", id), survival));
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
     * @param preferred first file
     * @param fallback second file
     * @param path section path
     * @return first non-null section
     */
    private static ConfigurationSection sectionOr(
            YamlConfiguration preferred,
            FileConfiguration fallback,
            String path
    ) {
        if (preferred != null) {
            ConfigurationSection section = preferred.getConfigurationSection(path);
            if (section != null) {
                return section;
            }
        }
        return fallback == null ? null : fallback.getConfigurationSection(path);
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
