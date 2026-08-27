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
            List<String> cells = find.getCells().stream()
                    .map(cell -> cell.x() + "," + cell.y() + "," + cell.z())
                    .toList();
            node.put("cells", cells);
            finds.add(node);
        }
        yaml.set("finds", finds);
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
            Object cells = map.get("cells");
            if (cells instanceof List<?> list) {
                for (Object cell : list) {
                    String[] parts = String.valueOf(cell).split(",");
                    find.getCells().add(new BlockCell(
                            Integer.parseInt(parts[0]),
                            Integer.parseInt(parts[1]),
                            Integer.parseInt(parts[2])
                    ));
                }
            }
            site.getFinds().add(find);
        }
        return site;
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
