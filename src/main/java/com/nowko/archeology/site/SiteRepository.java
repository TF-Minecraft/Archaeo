package com.nowko.archeology.site;

import com.nowko.archeology.model.BlockCell;
import com.nowko.archeology.model.BuriedFind;
import com.nowko.archeology.model.FindState;
import com.nowko.archeology.model.InterestLevel;
import com.nowko.archeology.model.Site;
import com.nowko.archeology.model.SiteStatus;
import com.nowko.archeology.model.SiteType;
import com.nowko.archeology.model.StratumBand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Loads and writes site dossiers under {@code plugins/Archaeo/sites/}.
 */
public class SiteRepository {
    private final JavaPlugin plugin;
    private final File sitesFolder;
    private final File indexFile;
    private final Map<UUID, Site> byId = new ConcurrentHashMap<>();
    private int nextSerial = 1;

    /**
     * @param plugin used for the data folder and logging
     */
    public SiteRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        this.sitesFolder = new File(plugin.getDataFolder(), "sites");
        this.indexFile = new File(plugin.getDataFolder(), "sites-index.yml");
    }

    /**
     * Reads every {@code .yml} in the sites folder into memory and updates the next serial.
     */
    public void loadAll() {
        if (!sitesFolder.exists() && !sitesFolder.mkdirs()) {
            plugin.getLogger().warning("Could not create " + sitesFolder.getPath());
        }
        byId.clear();
        nextSerial = 1;
        File[] files = sitesFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            try {
                Site site = read(YamlConfiguration.loadConfiguration(file));
                byId.put(site.getId(), site);
                nextSerial = Math.max(nextSerial, site.getSerial() + 1);
            } catch (Exception exception) {
                plugin.getLogger().log(Level.WARNING, "Could not read " + file.getName(), exception);
            }
        }
    }

    /**
     * @return the next unused serial, then increments the counter
     */
    public int nextSerial() {
        int serial = nextSerial;
        nextSerial++;
        return serial;
    }

    /**
     * @param world world name
     * @param chunkX chunk X
     * @param chunkZ chunk Z
     * @return site occupying that chunk, if any
     */
    public Optional<Site> findByChunk(String world, int chunkX, int chunkZ) {
        return byId.values().stream()
                .filter(site -> site.getWorldName().equals(world)
                        && site.getChunkX() == chunkX
                        && site.getChunkZ() == chunkZ)
                .findFirst();
    }

    /**
     * @param world world name
     * @param chunkX chunk X
     * @param chunkZ chunk Z
     * @return site whose camp occupies that chunk, if any
     */
    public Optional<Site> findByEstablishmentChunk(String world, int chunkX, int chunkZ) {
        return byId.values().stream()
                .filter(site -> site.hasEstablishment()
                        && site.getWorldName().equals(world)
                        && site.getEstablishmentChunkX() == chunkX
                        && site.getEstablishmentChunkZ() == chunkZ)
                .findFirst();
    }

    /**
     * @param world world name
     * @param chunkX chunk X
     * @param chunkZ chunk Z
     * @return whether a ruin or a camp already uses that chunk
     */
    public boolean chunkOccupied(String world, int chunkX, int chunkZ) {
        return findByChunk(world, chunkX, chunkZ).isPresent()
                || findByEstablishmentChunk(world, chunkX, chunkZ).isPresent();
    }

    /**
     * Active excavation whose camp template occupies this block.
     *
     * @param world world name
     * @param x block X
     * @param y block Y
     * @param z block Z
     * @return locked camp site, if any
     */
    public Optional<Site> findLockedCampBlock(String world, int x, int y, int z) {
        return byId.values().stream()
                .filter(Site::isCampLocked)
                .filter(site -> site.getWorldName().equals(world))
                .filter(site -> site.isCampBlock(x, y, z))
                .findFirst();
    }

    /**
     * Established excavation whose stratum prism contains this block.
     *
     * @param world world name
     * @param x block X
     * @param y block Y
     * @param z block Z
     * @return site if the cell sits in a present stratum band
     */
    public Optional<Site> findEstablishedPrism(String world, int x, int y, int z) {
        return findByChunk(world, x >> 4, z >> 4)
                .filter(site -> site.getStatus() == SiteStatus.ESTABLISHED)
                .filter(site -> site.isInPrism(x, y, z));
    }

    /**
     * @param serial human-facing site number
     * @return site with that serial, if loaded
     */
    public Optional<Site> findBySerial(int serial) {
        return byId.values().stream()
                .filter(site -> site.getSerial() == serial)
                .findFirst();
    }

    /**
     * @param name site display name, compared case-insensitively
     * @return all sites whose name equals {@code name}
     */
    public List<Site> findByName(String name) {
        if (name == null || name.isBlank()) {
            return List.of();
        }
        String needle = name.trim();
        return byId.values().stream()
                .filter(site -> site.getName() != null && site.getName().equalsIgnoreCase(needle))
                .toList();
    }

    /**
     * @param prefix start of a site name (case-insensitive)
     * @return matching display names, unique, for tab completion
     */
    public List<String> namesStartingWith(String prefix) {
        String needle = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return byId.values().stream()
                .map(Site::getName)
                .filter(name -> name != null && !name.isBlank())
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(needle))
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * @param id site UUID
     * @return site if loaded
     */
    public Optional<Site> findById(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    /**
     * @return snapshot of all loaded sites
     */
    public Collection<Site> all() {
        return List.copyOf(byId.values());
    }

    /**
     * Writes {@code site} to {@code sites/<uuid>.yml} and refreshes {@code sites-index.yml}.
     *
     * @param site dossier to persist
     * @throws IllegalStateException if the folder cannot be created or the file cannot be written
     */
    public void save(Site site) {
        byId.put(site.getId(), site);
        if (!sitesFolder.exists() && !sitesFolder.mkdirs()) {
            throw new IllegalStateException("Could not create " + sitesFolder.getPath());
        }
        YamlConfiguration yaml = write(site);
        File file = new File(sitesFolder, site.getId() + ".yml");
        try {
            yaml.save(file);
            saveIndex();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save " + file.getName(), exception);
        }
    }

    /**
     * Writes serial counter and known ids so reloads can keep numbering stable.
     */
    private void saveIndex() {
        YamlConfiguration index = new YamlConfiguration();
        index.set("next-serial", nextSerial);
        List<String> ids = byId.keySet().stream().map(UUID::toString).toList();
        index.set("ids", ids);
        try {
            index.save(indexFile);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not save sites-index.yml", exception);
        }
    }

    /**
     * @param site in-memory dossier
     * @return YAML ready to save
     */
    private YamlConfiguration write(Site site) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("id", site.getId().toString());
        yaml.set("serial", site.getSerial());
        yaml.set("type", site.getType().name());
        yaml.set("status", site.getStatus().name());
        yaml.set("interest", site.getInterest().yamlKey());
        yaml.set("name", site.getName());
        yaml.set("world", site.getWorldName());
        yaml.set("chunk-x", site.getChunkX());
        yaml.set("chunk-z", site.getChunkZ());
        yaml.set("surface-y", site.getSurfaceY());
        yaml.set("detection-radius", site.getDetectionRadius());
        yaml.set("created-by", site.getCreatedBy() == null ? null : site.getCreatedBy().toString());
        yaml.set("created-at", site.getCreatedAt().toString());
        yaml.set("director", site.getDirector() == null ? null : site.getDirector().toString());
        yaml.set("visibility", site.getVisibility());
        yaml.set("recovered-count", site.getRecoveredCount());
        yaml.set("hint-ids", site.getHintIds());
        yaml.set("excavators", site.getExcavators().stream().map(UUID::toString).toList());
        yaml.set("factions", site.getFactions());
        if (site.hasEstablishment()) {
            yaml.set("establishment.chunk-x", site.getEstablishmentChunkX());
            yaml.set("establishment.chunk-z", site.getEstablishmentChunkZ());
            yaml.set("establishment.camp-x", site.getCampX());
            yaml.set("establishment.camp-y", site.getCampY());
            yaml.set("establishment.camp-z", site.getCampZ());
            if (site.getCampSignX() != null) {
                yaml.set("establishment.sign-x", site.getCampSignX());
                yaml.set("establishment.sign-y", site.getCampSignY());
                yaml.set("establishment.sign-z", site.getCampSignZ());
            }
            yaml.set(
                    "establishment.blocks",
                    site.getCampBlocks().stream()
                            .map(cell -> cell.x() + "," + cell.y() + "," + cell.z())
                            .toList());
            yaml.set("establishment.wool-primary", site.getCampWoolPrimary());
            yaml.set("establishment.wool-secondary", site.getCampWoolSecondary());
            if (site.getCampFacing() != null) {
                yaml.set("establishment.facing", site.getCampFacing());
            }
        }
        yaml.set("prospect.confirmed", site.getProspectConfirmed().stream().map(UUID::toString).toList());
        for (Map.Entry<UUID, List<BlockCell>> entry : site.allProspectSamples().entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            List<String> cells = entry.getValue().stream()
                    .map(cell -> cell.x() + "," + cell.y() + "," + cell.z())
                    .toList();
            yaml.set("prospect.samples." + entry.getKey(), cells);
        }

        for (StratumBand band : site.getStrata().values()) {
            String path = "strata." + band.getId();
            yaml.set(path + ".present", band.isPresent());
            yaml.set(path + ".disturbed", band.isDisturbed());
            yaml.set(path + ".min-y", band.getMinY());
            yaml.set(path + ".max-y", band.getMaxY());
        }

        List<Map<String, Object>> finds = new ArrayList<>();
        for (BuriedFind find : site.getFinds()) {
            Map<String, Object> node = new java.util.LinkedHashMap<>();
            node.put("id", find.getId().toString());
            node.put("artifact-id", find.getArtifactId());
            node.put("stratum", find.getStratumId());
            node.put("state", find.getState().name());
            node.put("damaged", find.isDamaged());
            node.put("conservation", find.getConservation());
            List<String> cells = find.getCells().stream()
                    .map(cell -> cell.x() + "," + cell.y() + "," + cell.z())
                    .toList();
            node.put("cells", cells);
            List<String> cleaned = find.getCleanedCells().stream()
                    .map(cell -> cell.x() + "," + cell.y() + "," + cell.z())
                    .toList();
            node.put("cleaned-cells", cleaned);
            node.put("grazed-cells", find.getGrazedCells().stream()
                    .map(cell -> cell.x() + "," + cell.y() + "," + cell.z())
                    .toList());
            node.put("direct-hit-cells", find.getDirectHitCells().stream()
                    .map(cell -> cell.x() + "," + cell.y() + "," + cell.z())
                    .toList());
            finds.add(node);
        }
        yaml.set("finds", finds);
        yaml.set("jornada.world-day", site.getJornadaWorldDay());
        yaml.set("jornada.pick-left", site.getJornadaPickLeft());
        List<String> damage = new ArrayList<>();
        for (Map.Entry<BlockCell, Integer> entry : site.getFillDamage().entrySet()) {
            BlockCell cell = entry.getKey();
            damage.add(cell.x() + "," + cell.y() + "," + cell.z() + ":" + entry.getValue());
        }
        yaml.set("fill-damage", damage);
        return yaml;
    }

    /**
     * @param yaml file contents
     * @return reconstructed site
     */
    private Site read(YamlConfiguration yaml) {
        Site site = new Site();
        site.setId(UUID.fromString(yaml.getString("id")));
        site.setSerial(yaml.getInt("serial"));
        site.setType(SiteType.valueOf(yaml.getString("type", "MANAGED_RUIN")));
        site.setStatus(SiteStatus.valueOf(yaml.getString("status", "HIDDEN")));
        site.setInterest(InterestLevel.fromInput(yaml.getString("interest")));
        site.setName(yaml.getString("name"));
        site.setWorldName(yaml.getString("world"));
        site.setChunkX(yaml.getInt("chunk-x"));
        site.setChunkZ(yaml.getInt("chunk-z"));
        site.setSurfaceY(yaml.getInt("surface-y"));
        site.setDetectionRadius(yaml.getInt("detection-radius"));
        if (yaml.getString("created-by") != null) {
            site.setCreatedBy(UUID.fromString(yaml.getString("created-by")));
        }
        if (yaml.getString("created-at") != null) {
            site.setCreatedAt(Instant.parse(yaml.getString("created-at")));
        }
        if (yaml.getString("director") != null) {
            site.setDirector(UUID.fromString(yaml.getString("director")));
        }
        site.setVisibility(yaml.getString("visibility", "private"));
        site.setRecoveredCount(yaml.getInt("recovered-count"));
        site.getHintIds().addAll(yaml.getStringList("hint-ids"));
        for (String raw : yaml.getStringList("excavators")) {
            site.getExcavators().add(UUID.fromString(raw));
        }
        site.getFactions().addAll(yaml.getStringList("factions"));
        if (yaml.contains("establishment.chunk-x")) {
            site.setEstablishmentChunkX(yaml.getInt("establishment.chunk-x"));
            site.setEstablishmentChunkZ(yaml.getInt("establishment.chunk-z"));
            if (yaml.contains("establishment.camp-x")) {
                site.setCampX(yaml.getInt("establishment.camp-x"));
                site.setCampY(yaml.getInt("establishment.camp-y"));
                site.setCampZ(yaml.getInt("establishment.camp-z"));
            }
            if (yaml.contains("establishment.sign-x")) {
                site.setCampSignX(yaml.getInt("establishment.sign-x"));
                site.setCampSignY(yaml.getInt("establishment.sign-y"));
                site.setCampSignZ(yaml.getInt("establishment.sign-z"));
            }
            for (String cell : yaml.getStringList("establishment.blocks")) {
                String[] parts = cell.split(",");
                if (parts.length < 3) {
                    continue;
                }
                site.getCampBlocks().add(new BlockCell(
                        Integer.parseInt(parts[0]),
                        Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2])
                ));
            }
            if (yaml.getString("establishment.wool-primary") != null) {
                site.setCampWoolPrimary(yaml.getString("establishment.wool-primary"));
            }
            if (yaml.getString("establishment.wool-secondary") != null) {
                site.setCampWoolSecondary(yaml.getString("establishment.wool-secondary"));
            } else if (yaml.getString("establishment.wool") != null) {
                site.setCampWoolSecondary(yaml.getString("establishment.wool"));
            }
            if (yaml.getString("establishment.facing") != null) {
                site.setCampFacing(yaml.getString("establishment.facing"));
            }
        }

        ConfigurationSection strata = yaml.getConfigurationSection("strata");
        if (strata != null) {
            for (String id : strata.getKeys(false)) {
                ConfigurationSection section = strata.getConfigurationSection(id);
                if (section == null) {
                    continue;
                }
                StratumBand band = new StratumBand();
                band.setId(id);
                band.setPresent(section.getBoolean("present"));
                band.setDisturbed(section.getBoolean("disturbed"));
                band.setMinY(section.getInt("min-y"));
                band.setMaxY(section.getInt("max-y"));
                site.getStrata().put(id, band);
            }
        }

        List<?> rawFinds = yaml.getList("finds", List.of());
        for (Object raw : rawFinds) {
            if (!(raw instanceof Map<?, ?> map)) {
                continue;
            }
            BuriedFind find = new BuriedFind();
            find.setId(UUID.fromString(String.valueOf(map.get("id"))));
            find.setArtifactId(String.valueOf(map.get("artifact-id")));
            find.setStratumId(String.valueOf(map.get("stratum")));
            find.setState(FindState.valueOf(stringOr(map.get("state"), "HIDDEN")));
            find.setDamaged(Boolean.parseBoolean(stringOr(map.get("damaged"), "false")));
            addCells(map.get("cells"), find.getCells());
            addCells(map.get("cleaned-cells"), find.getCleanedCells());
            addCells(map.get("grazed-cells"), find.getGrazedCells());
            addCells(map.get("direct-hit-cells"), find.getDirectHitCells());
            if (find.getGrazedCells().isEmpty() && find.getDirectHitCells().isEmpty()) {
                find.setConservation(parseConservation(map.get("conservation")));
            } else {
                find.refreshConservation();
            }
            site.getFinds().add(find);
        }

        site.setJornadaWorldDay(yaml.getLong("jornada.world-day", -1L));
        site.setJornadaPickLeft(yaml.getInt("jornada.pick-left"));
        for (String raw : yaml.getStringList("fill-damage")) {
            int split = raw.lastIndexOf(':');
            if (split < 0) {
                continue;
            }
            String[] parts = raw.substring(0, split).split(",");
            if (parts.length < 3) {
                continue;
            }
            site.getFillDamage().put(
                    new BlockCell(
                            Integer.parseInt(parts[0]),
                            Integer.parseInt(parts[1]),
                            Integer.parseInt(parts[2])),
                    Integer.parseInt(raw.substring(split + 1)));
        }

        for (String raw : yaml.getStringList("prospect.confirmed")) {
            site.confirmProspect(UUID.fromString(raw));
        }
        ConfigurationSection samples = yaml.getConfigurationSection("prospect.samples");
        if (samples != null) {
            for (String key : samples.getKeys(false)) {
                UUID playerId = UUID.fromString(key);
                for (String cell : samples.getStringList(key)) {
                    String[] parts = cell.split(",");
                    if (parts.length < 3) {
                        continue;
                    }
                    site.addProspectSample(playerId, new BlockCell(
                            Integer.parseInt(parts[0]),
                            Integer.parseInt(parts[1]),
                            Integer.parseInt(parts[2])
                    ));
                }
            }
        }
        return site;
    }

    /**
     * @param raw YAML list of {@code x,y,z} strings
     * @param into destination
     */
    private static void addCells(Object raw, Collection<BlockCell> into) {
        if (!(raw instanceof List<?> list)) {
            return;
        }
        for (Object cell : list) {
            String[] parts = String.valueOf(cell).split(",");
            if (parts.length < 3) {
                continue;
            }
            into.add(new BlockCell(
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim())
            ));
        }
    }

    /**
     * @param value YAML number or missing
     * @return conservation 0–100, default 100
     */
    private static int parseConservation(Object value) {
        if (value == null) {
            return 100;
        }
        try {
            return Math.max(0, Math.min(100, Integer.parseInt(String.valueOf(value))));
        } catch (NumberFormatException ignored) {
            return 100;
        }
    }

    /**
     * @param value map value that may be {@code null}
     * @param fallback used when {@code value} is {@code null}
     * @return string form of {@code value}, or {@code fallback}
     */
    private static String stringOr(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }
}
