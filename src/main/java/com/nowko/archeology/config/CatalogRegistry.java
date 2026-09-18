package com.nowko.archeology.config;

import com.nowko.archeology.item.ItemRef;
import com.nowko.archeology.model.BuriedFind;
import com.nowko.archeology.model.InterestLevel;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
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
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Loads catalog YAML from the plugin jar (first run) and then from the data folder.
 */
public class CatalogRegistry {
    private final JavaPlugin plugin;
    private final Map<InterestLevel, InterestSettings> interests = new EnumMap<>(InterestLevel.class);
    private final Map<String, StratumDefinition> strata = new LinkedHashMap<>();
    private final Map<String, ArtifactTemplate> artifacts = new LinkedHashMap<>();
    private final Map<String, HintTemplate> hints = new LinkedHashMap<>();
    private final Map<String, InterpretationTemplate> interpretations = new LinkedHashMap<>();
    private final Map<String, InterpretationType> interpretationTypes = new LinkedHashMap<>();
    private final Map<FindProfile, List<String>> profileTypeIds = new EnumMap<>(FindProfile.class);
    private final Map<String, FindMaterial> materials = new LinkedHashMap<>();
    private int maxShapeAttempts = 24;
    private boolean useWorldSeed = true;
    private int findMinCover = 2;
    private TrackerSettings tracker = TrackerSettings.defaults();
    private ProspectSettings prospect = ProspectSettings.defaults();
    private EstablishSettings establish = EstablishSettings.defaults();
    private PickSettings pick = PickSettings.defaults();
    private RecoverySettings recovery = RecoverySettings.defaults();
    private SketchSettings sketch = SketchSettings.defaults();
    private MuseumSettings museum = MuseumSettings.defaults();
    private ToolWearSettings toolWear = ToolWearSettings.defaults();
    private AutoRuinSettings autoRuins = AutoRuinSettings.defaults();
    private ItemMaterials items = ItemMaterials.defaults();
    private String staffPermission = "archaeo.admin";
    private Map<String, RarityStyle> rarities = defaultRarities();
    private List<WeightRarityBand> rarityFromWeight = defaultRarityFromWeight();

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
        loadToolWear(config);
        loadRecovery(config);
        loadSketch(config);
        loadMuseum(config);
        loadAutoRuins(config);
        loadStaffPermission(config);
        loadRarities(config);
        loadRarityFromWeight(config);
        loadStrata(yaml("strata.yml"));
        loadArtifacts(yaml("artifacts.yml"));
        loadHints(yaml("hints.yml"));
        loadInterpretations(yaml("interpretations.yml"));
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
     * Resolves the rarity id for a template: explicit {@code rarity:} wins; otherwise weight bands.
     *
     * @param template find template, or {@code null}
     * @return rarity key such as {@code rare}
     */
    public String resolveRarityId(ArtifactTemplate template) {
        if (template == null) {
            return "common";
        }
        return resolveRarityId(template.rarity(), template.weight());
    }

    /**
     * @param override optional {@code artifacts.yml} rarity token
     * @param weight generation weight
     * @return rarity key such as {@code rare}
     */
    public String resolveRarityId(String override, int weight) {
        if (override != null && !override.isBlank()) {
            String key = override.trim().toLowerCase(Locale.ROOT);
            if ("uncommon".equals(key)) {
                return "rare";
            }
            return key;
        }
        int w = Math.max(1, weight);
        for (WeightRarityBand band : rarityFromWeight) {
            if (w <= band.maxWeight()) {
                return band.id();
            }
        }
        return "common";
    }

    /**
     * Coloured lore line for a template (hybrid rarity).
     *
     * @param template find template
     * @return line such as {@code Rarity: RARE}
     */
    public String rarityLoreLine(ArtifactTemplate template) {
        return rarityLoreLineForId(resolveRarityId(template));
    }

    /**
     * Coloured lore line for an explicit rarity key. Blank falls back to {@code common}.
     *
     * @param raw rarity token, may be blank
     * @return line such as {@code Rarity: RARE}
     */
    public String rarityLoreLine(String raw) {
        if (raw == null || raw.isBlank()) {
            return rarityLoreLineForId("common");
        }
        return rarityLoreLineForId(resolveRarityId(raw, 1));
    }

    /**
     * @param id resolved rarity key
     * @return lore line
     */
    private String rarityLoreLineForId(String id) {
        if (id == null || id.isBlank()) {
            return rarities.getOrDefault("common", ArtifactRarity.COMMON.style()).loreLine();
        }
        String key = id.trim().toLowerCase(Locale.ROOT);
        RarityStyle style = rarities.get(key);
        if (style != null) {
            return style.loreLine();
        }
        return ArtifactRarity.fromConfig(key).loreLine();
    }

    /**
     * Lab profile for a catalog material. Unknown ids still get a {@code clean} wipe so the cabinet can run.
     *
     * @param id material key from {@code artifacts.yml} / {@code materials.yml}
     * @return row, never {@code null}
     */
    public FindMaterial materialOf(String id) {
        if (id != null && !id.isBlank()) {
            FindMaterial material = materials.get(id);
            if (material != null) {
                return material;
            }
        }
        String key = id == null || id.isBlank() ? "unknown" : id;
        return new FindMaterial(
                key,
                materialDisplayName(key),
                1.0,
                defaultCleanGlass(key),
                defaultStains(key));
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
     * @return interpretation templates in file order
     */
    public List<InterpretationTemplate> interpretations() {
        return List.copyOf(interpretations.values());
    }

    /**
     * @param id interpretation catalog key
     * @return template or {@code null}
     */
    public InterpretationTemplate interpretation(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return interpretations.get(id);
    }

    /**
     * @return interpretation types in file order (the station asks these one by one)
     */
    public List<InterpretationType> interpretationTypes() {
        return List.copyOf(interpretationTypes.values());
    }

    /**
     * Station questions for one classification path, in the order {@code profiles.<id>.types} lists them.
     * A missing list (legacy {@code interpretations.yml}) falls back to every loaded type.
     *
     * @param profile find path
     * @return types that path may sign
     */
    public List<InterpretationType> interpretationTypes(FindProfile profile) {
        FindProfile key = profile == null ? FindProfile.OBJECT : profile;
        List<String> ids = profileTypeIds.get(key);
        if (ids == null || ids.isEmpty()) {
            return interpretationTypes();
        }
        List<InterpretationType> list = new ArrayList<>();
        for (String id : ids) {
            InterpretationType type = interpretationTypes.get(id);
            if (type != null) {
                list.add(type);
            }
        }
        return list.isEmpty() ? interpretationTypes() : List.copyOf(list);
    }

    /**
     * Which classification path a catalog row uses.
     *
     * @param artifactId template key, or {@code null}
     * @return {@link FindProfile#OBJECT} when the row is missing
     */
    public FindProfile profileOf(String artifactId) {
        ArtifactTemplate template = artifact(artifactId);
        return template == null ? FindProfile.OBJECT : template.profile();
    }

    /**
     * @param id type key
     * @return type, or {@code null}
     */
    public InterpretationType interpretationType(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return interpretationTypes.get(id);
    }

    /**
     * First station question this find has not signed yet, for its template profile.
     *
     * @param find archive row
     * @return type still open, or {@code null} when every type on that path has an answer
     */
    public InterpretationType nextOpenType(BuriedFind find) {
        if (find == null) {
            return null;
        }
        for (InterpretationType type : interpretationTypes(profileOf(find.getArtifactId()))) {
            if (!find.hasType(type.id())) {
                return type;
            }
        }
        return null;
    }

    /**
     * Three (or fewer) phrases for one station question. The draw is stable for this find and type.
     * If any phrase lists this artifact in {@code suggested-for}, one of those is always among the three.
     *
     * @param typeId question key
     * @param findId archive row id
     * @param artifactId catalog artifact key, or {@code null}
     * @param tags artifact and hint tags
     * @return offers in draw order, never more than three
     */
    public List<InterpretationTemplate> stationOffers(
            String typeId,
            UUID findId,
            String artifactId,
            Set<String> tags
    ) {
        InterpretationType type = interpretationType(typeId);
        if (type == null || type.options() == null || type.options().isEmpty()) {
            return List.of();
        }
        FindProfile profile = profileOf(artifactId);
        List<InterpretationTemplate> pool = new ArrayList<>();
        for (InterpretationTemplate option : type.options()) {
            if (option.appliesTo(profile)) {
                pool.add(option);
            }
        }
        int want = Math.min(3, pool.size());
        if (want == pool.size()) {
            return List.copyOf(pool);
        }
        long seed = 0L;
        if (findId != null) {
            seed = findId.getMostSignificantBits() ^ findId.getLeastSignificantBits();
        }
        seed ^= (long) typeId.hashCode() * 0x9E3779B97F4A7C15L;
        Random rng = new Random(seed);
        Set<String> weightTags = tags == null ? Set.of() : tags;
        List<InterpretationTemplate> fitting = new ArrayList<>();
        for (InterpretationTemplate option : pool) {
            if (option.suggestedFor(artifactId)) {
                fitting.add(option);
            }
        }
        List<InterpretationTemplate> offers = new ArrayList<>(want);
        if (!fitting.isEmpty()) {
            InterpretationTemplate floor = takeWeighted(fitting, rng, weightTags);
            pool.remove(floor);
            offers.add(floor);
        }
        while (offers.size() < want && !pool.isEmpty()) {
            offers.add(takeWeighted(pool, rng, weightTags));
        }
        if (offers.size() > 1) {
            int slot = rng.nextInt(offers.size());
            InterpretationTemplate first = offers.remove(0);
            offers.add(slot, first);
        }
        return List.copyOf(offers);
    }

    /**
     * @param pool remaining phrases; the chosen row is removed
     * @param rng seeded draw
     * @param tags tag weights
     * @return one phrase
     */
    private static InterpretationTemplate takeWeighted(
            List<InterpretationTemplate> pool,
            Random rng,
            Set<String> tags
    ) {
        int total = 0;
        int[] weights = new int[pool.size()];
        for (int i = 0; i < pool.size(); i++) {
            weights[i] = pool.get(i).suggestedBy(tags) ? 3 : 1;
            total += weights[i];
        }
        int roll = rng.nextInt(Math.max(1, total));
        int index = 0;
        for (int i = 0; i < pool.size(); i++) {
            roll -= weights[i];
            if (roll < 0) {
                index = i;
                break;
            }
        }
        return pool.remove(index);
    }

    /**
     * @return max retries when growing a connected find shape
     */
    public int maxShapeAttempts() {
        return maxShapeAttempts;
    }

    /**
     * Ground a generated find must have over its head. Stratum bands are measured from the
     * chunk's median surface, so this is what keeps a find off a slope or a shore where the
     * band itself pokes out into the open.
     *
     * @return fill blocks required above every cell of a generated find
     */
    public int findMinCover() {
        return findMinCover;
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
     * @return field-sketch pencil wear, cabinet block, and lab wipe
     */
    public SketchSettings sketch() {
        return sketch;
    }

    /**
     * @return world supports that open a find plaque on sneak-use
     */
    public MuseumSettings museum() {
        return museum;
    }

    /**
     * @return trial auto-spawn knobs from {@code auto-ruins}
     */
    public AutoRuinSettings autoRuins() {
        return autoRuins;
    }

    /**
     * @return durability the pick and the brush spend while working the cut
     */
    public ToolWearSettings toolWear() {
        return toolWear;
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
        findMinCover = Math.max(1, section.getInt("find-min-cover", 2));
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
                parseItemId(fallback.sketchPaper(), itemsFile, config,
                        "sketch.paper", "items.sketch-paper"),
                parseItemId(fallback.sketchPencil(), itemsFile, config,
                        "sketch.pencil", "items.sketch-pencil"),
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
     * Reads establishment-kit rules, camp block, preview blocks, staff cap, and director camp cap.
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
                        "establish.ruin-outline-block"),
                EstablishSettings.clampMaxStaff(section.getInt("max-staff", fallback.maxStaff())),
                EstablishSettings.clampMaxExcavations(
                        section.getInt("max-excavations", fallback.maxExcavations()))
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
                Math.max(0, firstInt(excavation, pickSection, fallback.jornadaActions(),
                        "workday-actions", "workday-allowed-actions", "jornada-actions")),
                firstBool(excavation, pickSection, fallback.visualCues(), "visual-cues"),
                firstBool(excavation, pickSection, fallback.findDust(), "find-particles", "find-dust"),
                Math.max(1, firstInt(excavation, pickSection, fallback.findDustIntervalTicks(),
                        "find-particles-interval-ticks", "find-dust-interval-ticks")),
                Math.max(1, firstInt(excavation, pickSection, fallback.findDustCount(),
                        "find-particles-count", "find-dust-count")),
                loadConservation(excavation, fallback.conservation()),
                loadLimits(excavation, fallback.limits()),
                firstBool(excavation, pickSection, fallback.neighborTraces(), "neighbor-traces"),
                loadCues(excavation, fallback.cues()),
                loadProfiles(excavation, pickSection, fallback)
        );
    }

    /**
     * Reads {@code excavation.cues}: pick/shovel fill lists and match scales for {@code cue-ticks}.
     *
     * @param excavation {@code excavation:} or {@code null}
     * @param fallback packaged affinity
     * @return merged cue settings
     */
    private CueSettings loadCues(ConfigurationSection excavation, CueSettings fallback) {
        ConfigurationSection root = excavation == null
                ? null
                : excavation.getConfigurationSection("cues");
        if (root == null) {
            return fallback;
        }
        Set<Material> pick = materialsFromList(
                firstStringList(root, "hard-blocks", "pick-faster", "hard-faster"),
                "excavation.cues.hard-blocks");
        Set<Material> shovel = materialsFromList(
                firstStringList(root, "soft-blocks", "shovel-faster", "soft-faster"),
                "excavation.cues.soft-blocks");
        double matched = firstDouble(root, fallback.matchedFactor(),
                "on-match", "matched-factor", "faster-when-matched");
        double mismatched = firstDouble(root, fallback.mismatchedFactor(),
                "on-mismatch", "mismatched-factor", "slower-when-mismatched");
        if (matched <= 0) {
            matched = fallback.matchedFactor();
        }
        if (mismatched <= 0) {
            mismatched = fallback.mismatchedFactor();
        }
        return new CueSettings(
                CueSettings.copyOf(pick),
                CueSettings.copyOf(shovel),
                matched,
                mismatched
        );
    }

    /**
     * Parses material names and {@code #namespace:tag} / {@code #tag} block tags into a set.
     * An empty list means the caller should fall back to vanilla mineable tags at runtime.
     *
     * @param raw YAML string list
     * @param path config path for warnings
     * @return materials, possibly empty
     */
    private Set<Material> materialsFromList(List<String> raw, String path) {
        if (raw == null || raw.isEmpty()) {
            return Set.of();
        }
        EnumSet<Material> out = EnumSet.noneOf(Material.class);
        for (String token : raw) {
            if (token == null || token.isBlank()) {
                continue;
            }
            String trimmed = token.trim();
            if (trimmed.startsWith("#")) {
                addTagMaterials(out, trimmed.substring(1).trim(), path + ":" + trimmed);
                continue;
            }
            Material material = Material.matchMaterial(trimmed);
            if (material == null || !material.isBlock()) {
                plugin.getLogger().log(Level.WARNING, "Unknown block at " + path + ": " + trimmed);
                continue;
            }
            out.add(material);
        }
        return out;
    }

    /**
     * @param out destination
     * @param tagToken {@code mineable/pickaxe} or {@code minecraft:mineable/pickaxe}
     * @param path warning path
     */
    private void addTagMaterials(Set<Material> out, String tagToken, String path) {
        if (tagToken == null || tagToken.isBlank()) {
            return;
        }
        String key = tagToken.contains(":")
                ? tagToken.toLowerCase(Locale.ROOT)
                : "minecraft:" + tagToken.toLowerCase(Locale.ROOT);
        NamespacedKey namespaced = NamespacedKey.fromString(key);
        if (namespaced == null) {
            plugin.getLogger().log(Level.WARNING, "Bad block tag at " + path + ": " + tagToken);
            return;
        }
        Tag<Material> tag = Bukkit.getTag(Tag.REGISTRY_BLOCKS, namespaced, Material.class);
        if (tag == null) {
            plugin.getLogger().log(Level.WARNING, "Unknown block tag at " + path + ": " + tagToken);
            return;
        }
        out.addAll(tag.getValues());
    }

    /**
     * Reads {@code excavation.tool-wear}: what a cut and a brushed cube cost the tool in hand.
     * Kept out of {@link PickSettings} because the brush answers to it too.
     *
     * @param config root plugin config
     */
    private void loadToolWear(FileConfiguration config) {
        ToolWearSettings fallback = ToolWearSettings.defaults();
        ConfigurationSection excavation = config.getConfigurationSection("excavation");
        ConfigurationSection root = excavation == null
                ? null
                : excavation.getConfigurationSection("tool-wear");
        if (root == null) {
            toolWear = fallback;
            return;
        }
        toolWear = new ToolWearSettings(
                Math.max(0, root.getInt("pick", fallback.pick())),
                Math.max(0, root.getInt("brush", fallback.brush())),
                root.getBoolean("unbreaking", fallback.unbreaking())
        );
    }

    /**
     * Reads {@code excavation.limits}: how long the camp board shows the prism, how thin the
     * edges are, and from how far the client still draws them.
     *
     * @param excavation {@code excavation:} or {@code null}
     * @param fallback packaged outline settings
     * @return merged settings; missing keys keep the packaged value
     */
    private LimitsSettings loadLimits(ConfigurationSection excavation, LimitsSettings fallback) {
        ConfigurationSection root = excavation == null
                ? null
                : excavation.getConfigurationSection("limits");
        if (root == null) {
            return fallback;
        }
        return new LimitsSettings(
                Math.max(1, root.getInt("seconds", fallback.seconds())),
                Math.min(1.0, Math.max(0.01, root.getDouble("thickness", fallback.thickness()))),
                Math.max(16, root.getInt("view-distance", fallback.viewDistance()))
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
                    inherit.cueTicks(),
                    inherit.digClass(),
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
        int cueTicks = readTempo(section);
        int chimeTicks = Math.max(0, sectionInt(section, inherit.chimeTicks(),
                "legacy-chime-ticks"));
        DigClass digClass = DigClass.parse(section.getString("dig-class"));
        if (digClass == null) {
            digClass = inherit.digClass();
        }
        return new ExcavationTool(
                id,
                materials,
                cueTicks,
                digClass,
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
     * Reads {@code tempo:} — {@code vanilla} / omit → {@code 0}; a number → Archaeo metronome ticks.
     * Legacy {@code cue-ticks} is still accepted. Omitting tempo never inherits a packaged metronome.
     *
     * @param section tool profile
     * @return cue ticks; {@code 0} means vanilla mining tempo
     */
    private static int readTempo(ConfigurationSection section) {
        if (section == null) {
            return 0;
        }
        if (section.contains("tempo")) {
            Object raw = section.get("tempo");
            if (raw instanceof Number number) {
                return Math.max(0, number.intValue());
            }
            if (raw != null) {
                String token = raw.toString().trim();
                if (token.isEmpty()
                        || token.equalsIgnoreCase("vanilla")
                        || token.equalsIgnoreCase("default")
                        || token.equalsIgnoreCase("auto")) {
                    return 0;
                }
                try {
                    return Math.max(0, Integer.parseInt(token));
                } catch (NumberFormatException ignored) {
                    return 0;
                }
            }
        }
        Integer legacy = sectionIntOrNull(
                section,
                "cue-ticks",
                "chime-ticks",
                "beat-ticks",
                "strike-interval-ticks");
        return legacy == null ? 0 : Math.max(0, legacy);
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
     * First defined string list among {@code keys} on {@code section}.
     *
     * @param section YAML map, or {@code null}
     * @param keys preference order
     * @return list, or empty when none is present
     */
    private static List<String> firstStringList(ConfigurationSection section, String... keys) {
        if (section == null || keys == null) {
            return List.of();
        }
        for (String key : keys) {
            if (section.contains(key)) {
                return section.getStringList(key);
            }
        }
        return List.of();
    }

    /**
     * First defined double among {@code keys} on {@code section}.
     *
     * @param section YAML map, or {@code null}
     * @param fallback when none is present
     * @param keys preference order
     * @return value
     */
    private static double firstDouble(ConfigurationSection section, double fallback, String... keys) {
        if (section == null || keys == null) {
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
     * Reads pencil wear, the cabinet block, and the lab wipe window from {@code sketch:}.
     *
     * @param config root plugin config
     */
    private void loadSketch(FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("sketch");
        SketchSettings fallback = SketchSettings.defaults();
        if (section == null) {
            sketch = fallback;
            return;
        }
        sketch = new SketchSettings(
                Math.max(0, section.getInt("pencil-uses", fallback.pencilUses())),
                ItemRef.parseOr(plugin, section.getString("cabinet"), fallback.cabinet()),
                loadLab(section.getConfigurationSection("lab"), fallback.lab()));
    }

    /**
     * Reads plaque supports from {@code museum.displays}. Missing key keeps packaged vanilla furniture.
     *
     * @param config root plugin config
     */
    private void loadMuseum(FileConfiguration config) {
        MuseumSettings fallback = MuseumSettings.defaults();
        ConfigurationSection section = config.getConfigurationSection("museum");
        if (section == null || !section.contains("displays")) {
            museum = fallback;
            return;
        }
        List<ItemRef> displays = ItemRef.parseYamlList(plugin, section.getList("displays"));
        museum = new MuseumSettings(List.copyOf(displays));
    }

    /**
     * Reads trial auto-spawn density and fitness gates from {@code auto-ruins}.
     *
     * @param config root plugin config
     */
    private void loadAutoRuins(FileConfiguration config) {
        AutoRuinSettings fallback = AutoRuinSettings.defaults();
        ConfigurationSection section = config.getConfigurationSection("auto-ruins");
        if (section == null) {
            autoRuins = fallback;
            return;
        }
        List<String> worlds = section.getStringList("worlds");
        Map<InterestLevel, Integer> weights = new EnumMap<>(InterestLevel.class);
        ConfigurationSection weightSection = section.getConfigurationSection("interest-weights");
        for (InterestLevel level : InterestLevel.values()) {
            int packaged = fallback.interestWeights().getOrDefault(level, 0);
            int value = weightSection == null
                    ? packaged
                    : weightSection.getInt(level.yamlKey(), packaged);
            weights.put(level, Math.max(0, value));
        }
        Set<String> excludedBiomes = section.contains("excluded-biomes")
                ? AutoRuinSettings.normalizeBiomeList(section.getStringList("excluded-biomes"), Set.of())
                : fallback.excludedBiomes();
        autoRuins = new AutoRuinSettings(
                section.getBoolean("enabled", fallback.enabled()),
                List.copyOf(worlds),
                Math.max(0.0, Math.min(1.0, readUnitInterval(section, "chance-per-chunk", fallback.chancePerChunk()))),
                Math.max(0, section.getInt("min-chunk-distance", fallback.minChunkDistance())),
                Math.max(0, section.getInt("max-sites-per-world", fallback.maxSitesPerWorld())),
                Math.max(0, section.getInt("exclude-spawn-chunks", fallback.excludeSpawnChunks())),
                Math.max(0, section.getInt("max-relief-blocks", fallback.maxReliefBlocks())),
                Math.max(0.0, Math.min(1.0, readUnitInterval(section, "min-soil-fraction", fallback.minSoilFraction()))),
                excludedBiomes,
                Map.copyOf(weights),
                Math.max(1, section.getInt("max-pending", fallback.maxPending())),
                Math.max(1, section.getInt("max-unload-purge-per-tick", fallback.maxUnloadPurgePerTick())),
                Math.max(1, section.getInt("max-evaluations-per-tick", fallback.maxEvaluationsPerTick())),
                section.getBoolean("notify-staff", fallback.notifyStaff()));
    }

    /**
     * Reads a 0–1 style double, accepting {@code 0.1} or locale-style {@code 0,1} strings.
     *
     * @param section config block
     * @param key YAML key
     * @param fallback packaged default
     * @return parsed value, or {@code fallback} when missing/invalid
     */
    private static double readUnitInterval(ConfigurationSection section, String key, double fallback) {
        if (!section.contains(key)) {
            return fallback;
        }
        if (section.isDouble(key) || section.isInt(key) || section.isLong(key)) {
            return section.getDouble(key);
        }
        String raw = section.getString(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(raw.trim().replace(',', '.'));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    /**
     * Reads the wipe field, rack tools, and stain catalogue from {@code sketch.lab}.
     *
     * @param section {@code sketch.lab}, or {@code null}
     * @param fallback packaged lab
     * @return merged settings
     */
    private LabSettings loadLab(ConfigurationSection section, LabSettings fallback) {
        if (section == null) {
            return fallback;
        }
        int dirty = Math.max(1, Math.min(LabSettings.FIELD_SLOTS, section.getInt("dirty-count", fallback.dirtyCount())));
        ConfigurationSection toolsRoot = section.getConfigurationSection("tools");
        List<LabTool> tools = new ArrayList<>();
        if (toolsRoot != null) {
            for (String id : toolsRoot.getKeys(false)) {
                ConfigurationSection tool = toolsRoot.getConfigurationSection(id);
                if (tool == null) {
                    continue;
                }
                tools.add(new LabTool(
                        id,
                        ConfigEnums.material(
                                plugin,
                                tool.getString("item"),
                                Material.STICK,
                                "sketch.lab.tools." + id + ".item"),
                        tool.getString("display-name", id),
                        tool.getString("description", ""),
                        ConfigEnums.sound(
                                plugin,
                                tool.getString("sound"),
                                defaultToolSound(id),
                                "sketch.lab.tools." + id + ".sound")));
            }
        }
        if (tools.isEmpty()) {
            tools = fallback.tools();
        }
        ConfigurationSection stainsRoot = section.getConfigurationSection("stains");
        List<LabStain> stains = new ArrayList<>();
        if (stainsRoot != null) {
            for (String id : stainsRoot.getKeys(false)) {
                ConfigurationSection stain = stainsRoot.getConfigurationSection(id);
                if (stain == null) {
                    continue;
                }
                stains.add(new LabStain(
                        id,
                        stain.getString("display-name", id),
                        ConfigEnums.material(
                                plugin,
                                stain.getString("glass"),
                                Material.BROWN_STAINED_GLASS_PANE,
                                "sketch.lab.stains." + id + ".glass"),
                        stain.getString("tool", "brush")));
            }
        }
        if (stains.isEmpty()) {
            stains = fallback.stains();
        }
        return new LabSettings(dirty, List.copyOf(tools), List.copyOf(stains));
    }

    /**
     * @param id rack tool key
     * @return packaged wipe sound
     */
    private static org.bukkit.Sound defaultToolSound(String id) {
        return switch (id == null ? "" : id.toLowerCase(java.util.Locale.ROOT)) {
            case "water" -> org.bukkit.Sound.ITEM_BUCKET_EMPTY;
            case "air" -> org.bukkit.Sound.ITEM_BRUSH_BRUSHING_SAND;
            case "brush" -> org.bukkit.Sound.ITEM_BRUSH_BRUSHING_GENERIC;
            default -> org.bukkit.Sound.BLOCK_WOOL_HIT;
        };
    }

    /**
     * @param id material key
     * @return packaged clean pane
     */
    private static Material defaultCleanGlass(String id) {
        return switch (id == null ? "" : id.toLowerCase(java.util.Locale.ROOT)) {
            case "metal" -> Material.GRAY_STAINED_GLASS_PANE;
            case "organic" -> Material.LIME_STAINED_GLASS_PANE;
            case "stone" -> Material.LIGHT_GRAY_STAINED_GLASS_PANE;
            default -> Material.WHITE_STAINED_GLASS_PANE;
        };
    }

    /**
     * @param id material key
     * @return packaged stain ids for that material
     */
    private static List<String> defaultStains(String id) {
        return switch (id == null ? "" : id.toLowerCase(java.util.Locale.ROOT)) {
            case "ceramic" -> List.of("limescale", "soil");
            case "stone" -> List.of("limescale");
            case "metal" -> List.of("rust");
            case "organic" -> List.of("mud");
            default -> List.of("soil");
        };
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
     * Reads {@code rarity:} display labels and colours. Missing keys keep the packaged tier style.
     * Extra keys become valid {@code artifacts.yml} rarity tokens for lore only.
     *
     * @param config root plugin config
     */
    private void loadRarities(FileConfiguration config) {
        Map<String, RarityStyle> loaded = defaultRarities();
        ConfigurationSection root = config.getConfigurationSection("rarity");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                if (id == null || id.isBlank()) {
                    continue;
                }
                String key = id.trim().toLowerCase(Locale.ROOT);
                ConfigurationSection section = root.getConfigurationSection(id);
                RarityStyle fallback = loaded.getOrDefault(key, new RarityStyle(key, key.toUpperCase(Locale.ROOT), ChatColor.WHITE));
                if (section == null) {
                    String colorName = root.getString(id);
                    ChatColor color = parseChatColor(colorName, fallback.color());
                    loaded.put(key, new RarityStyle(key, fallback.label(), color));
                    continue;
                }
                String label = section.getString("label", fallback.label());
                if (label == null || label.isBlank()) {
                    label = fallback.label();
                }
                ChatColor color = parseChatColor(section.getString("color"), fallback.color());
                loaded.put(key, new RarityStyle(key, label, color));
            }
        }
        rarities = Map.copyOf(loaded);
    }

    /**
     * Reads {@code rarity-from-weight:}: max weight per tier when an artifact omits {@code rarity:}.
     * Lower max-weight bands are checked first (scarcer finds first).
     *
     * @param config root plugin config
     */
    private void loadRarityFromWeight(FileConfiguration config) {
        ConfigurationSection root = config.getConfigurationSection("rarity-from-weight");
        if (root == null || root.getKeys(false).isEmpty()) {
            rarityFromWeight = defaultRarityFromWeight();
            return;
        }
        List<WeightRarityBand> bands = new ArrayList<>();
        for (String id : root.getKeys(false)) {
            if (id == null || id.isBlank()) {
                continue;
            }
            String key = id.trim().toLowerCase(Locale.ROOT);
            int max = root.getInt(id, -1);
            if (max < 1) {
                plugin.getLogger().log(Level.WARNING, "Ignoring rarity-from-weight." + id + " (max weight must be >= 1)");
                continue;
            }
            bands.add(new WeightRarityBand(key, max));
        }
        if (bands.isEmpty()) {
            rarityFromWeight = defaultRarityFromWeight();
            return;
        }
        bands.sort(Comparator.comparingInt(WeightRarityBand::maxWeight));
        rarityFromWeight = List.copyOf(bands);
    }

    /**
     * @return packaged weight bands (lower weight → rarer tier)
     */
    private static List<WeightRarityBand> defaultRarityFromWeight() {
        return List.of(
                new WeightRarityBand("legendary", 4),
                new WeightRarityBand("epic", 7),
                new WeightRarityBand("rare", 15),
                new WeightRarityBand("common", 9999)
        );
    }

    /**
     * @return packaged styles for the four built-in tiers
     */
    private static Map<String, RarityStyle> defaultRarities() {
        Map<String, RarityStyle> map = new LinkedHashMap<>();
        for (ArtifactRarity rarity : ArtifactRarity.values()) {
            map.put(rarity.id(), rarity.style());
        }
        return map;
    }

    /**
     * @param raw Bukkit {@link ChatColor} name
     * @param fallback when missing or unknown
     * @return colour
     */
    private static ChatColor parseChatColor(String raw, ChatColor fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return ChatColor.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
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
        int fallbackDetectionRadius = Math.max(1, config.getInt(
                "tracker.max-range",
                TrackerSettings.defaults().defaultMaxRange()));
        for (InterestLevel level : InterestLevel.values()) {
            ConfigurationSection section = root.getConfigurationSection(level.yamlKey());
            if (section == null) {
                throw new IllegalStateException("Missing interest-levels." + level.yamlKey());
            }
            int detectionRadius = section.contains("detection-radius")
                    ? Math.max(1, section.getInt("detection-radius"))
                    : fallbackDetectionRadius;
            interests.put(level, new InterestSettings(
                    level,
                    section.getString("display-name", level.yamlKey()),
                    section.getInt("base-wealth"),
                    section.getInt("variation"),
                    detectionRadius,
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
                    section.getString("rarity"),
                    section.getBoolean("relic", false),
                    Math.max(1, section.getInt("weight", 1)),
                    new LinkedHashSet<>(section.getStringList("strata")),
                    new LinkedHashSet<>(section.getStringList("tags")),
                    FindProfile.fromConfig(section.getString("profile")),
                    section.getString("item", "STONE"),
                    section.getString("study-notes", "")
            ));
        }
    }

    /**
     * Reads station questions from {@code interpretations.yml}. {@code profiles} lists which
     * types each find path asks. A legacy flat {@code interpretations:} map is loaded as a
     * single {@code function} type so old data folders still start.
     *
     * @param yaml parsed interpretations file
     */
    private void loadInterpretations(YamlConfiguration yaml) {
        interpretations.clear();
        interpretationTypes.clear();
        profileTypeIds.clear();
        ConfigurationSection typesRoot = yaml.getConfigurationSection("types");
        if (typesRoot != null) {
            for (String typeId : typesRoot.getKeys(false)) {
                ConfigurationSection typeSection = typesRoot.getConfigurationSection(typeId);
                if (typeSection == null) {
                    continue;
                }
                List<InterpretationTemplate> options = new ArrayList<>();
                ConfigurationSection optionsRoot = typeSection.getConfigurationSection("options");
                if (optionsRoot != null) {
                    for (String optionId : optionsRoot.getKeys(false)) {
                        ConfigurationSection optionSection = optionsRoot.getConfigurationSection(optionId);
                        if (optionSection == null) {
                            continue;
                        }
                        InterpretationTemplate option = new InterpretationTemplate(
                                optionId,
                                typeId,
                                optionSection.getString("display-name", optionId),
                                new LinkedHashSet<>(optionSection.getStringList("suggested-by")),
                                new LinkedHashSet<>(optionSection.getStringList("suggested-for")),
                                readOptionProfiles(optionSection)
                        );
                        options.add(option);
                        interpretations.put(optionId, option);
                    }
                }
                if (options.isEmpty()) {
                    continue;
                }
                interpretationTypes.put(typeId, new InterpretationType(
                        typeId,
                        typeSection.getString("display-name", typeId),
                        typeSection.getString("question", typeId),
                        List.copyOf(options)
                ));
            }
            loadInterpretationProfiles(yaml);
            return;
        }
        ConfigurationSection root = yaml.getConfigurationSection("interpretations");
        if (root == null) {
            return;
        }
        List<InterpretationTemplate> options = new ArrayList<>();
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            InterpretationTemplate option = new InterpretationTemplate(
                    id,
                    "function",
                    section.getString("display-name", id),
                    new LinkedHashSet<>(section.getStringList("suggested-by")),
                    new LinkedHashSet<>(section.getStringList("suggested-for")),
                    Set.of()
            );
            options.add(option);
            interpretations.put(id, option);
        }
        if (!options.isEmpty()) {
            interpretationTypes.put("function", new InterpretationType(
                    "function",
                    "Function",
                    "What was it for?",
                    List.copyOf(options)
            ));
        }
    }

    /**
     * Reads which station questions each find path asks. Missing lists keep the legacy
     * “every loaded type” behaviour so old data folders still start.
     *
     * @param yaml parsed interpretations file
     */
    private void loadInterpretationProfiles(YamlConfiguration yaml) {
        ConfigurationSection root = yaml.getConfigurationSection("profiles");
        if (root == null) {
            return;
        }
        for (FindProfile profile : FindProfile.values()) {
            ConfigurationSection section = root.getConfigurationSection(profile.id());
            if (section == null) {
                continue;
            }
            List<String> ids = section.getStringList("types");
            if (!ids.isEmpty()) {
                profileTypeIds.put(profile, List.copyOf(ids));
            }
        }
    }

    /**
     * @param optionSection one phrase
     * @return paths that may draw it; empty means every path
     */
    private static Set<FindProfile> readOptionProfiles(ConfigurationSection optionSection) {
        Set<FindProfile> profiles = new LinkedHashSet<>();
        for (String token : optionSection.getStringList("profiles")) {
            FindProfile.parseListed(token).ifPresent(profiles::add);
        }
        return profiles;
    }

    /**
     * Reads field-trace labels, clean glass, and which stains may appear from {@code materials.yml}.
     * Stain colour and matching tool live on {@code sketch.lab.stains}.
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
            List<String> stains = section.getStringList("stains");
            if (stains.isEmpty()) {
                stains = defaultStains(id);
            } else {
                stains = List.copyOf(stains);
            }
            materials.put(id, new FindMaterial(
                    id,
                    section.getString("display-name", id),
                    Math.max(0.05, Math.min(1.0, section.getDouble("survival", 1.0))),
                    ConfigEnums.material(
                            plugin,
                            section.getString("clean-glass"),
                            defaultCleanGlass(id),
                            "materials." + id + ".clean-glass"),
                    stains));
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
