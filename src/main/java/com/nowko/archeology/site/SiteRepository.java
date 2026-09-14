package com.nowko.archeology.site;

import com.nowko.archeology.model.BlockCell;
import com.nowko.archeology.model.BuriedFind;
import com.nowko.archeology.model.FindInterpretation;
import com.nowko.archeology.model.FindState;
import com.nowko.archeology.model.InterestLevel;
import com.nowko.archeology.model.Site;
import com.nowko.archeology.model.SiteRole;
import com.nowko.archeology.model.SiteStatus;
import com.nowko.archeology.model.SiteType;
import com.nowko.archeology.model.StratumBand;
import com.nowko.archeology.model.WorkerRecord;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Loads and writes site dossiers under {@code plugins/Archaeo/sites/}.
 *
 * <p>World-coupled mutations call {@link #commit(Site)} in the same tick. Plugin counters
 * call {@link #touch(Site)} and land on disk within one second, on disable, or before reload.
 * Files are replaced atomically so a crash cannot leave a truncated YAML.
 */
public class SiteRepository {
    /** Dossier format written by this build; older files without the key are treated as 1. */
    public static final int SCHEMA = 1;
    /** Dirty sites are flushed at least this often (~1 s). */
    private static final long FLUSH_PERIOD_TICKS = 20L;

    private final JavaPlugin plugin;
    private final File sitesFolder;
    private final File indexFile;
    private final Map<UUID, Site> byId = new ConcurrentHashMap<>();
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();
    private int nextSerial = 1;
    /** Last {@link #nextSerial} known to be on disk in {@code sites-index.yml}. */
    private int persistedSerial = 1;
    private BukkitTask flushTask;

    /**
     * @param plugin used for the data folder and logging
     */
    public SiteRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        this.sitesFolder = new File(plugin.getDataFolder(), "sites");
        this.indexFile = new File(plugin.getDataFolder(), "sites-index.yml");
    }

    /**
     * Reads every {@code .yml} in the sites folder into a new map, then swaps it in so a failed
     * listing cannot wipe the live session. Temp files and {@code .trash/} are ignored.
     */
    public void loadAll() {
        if (!sitesFolder.exists() && !sitesFolder.mkdirs()) {
            plugin.getLogger().warning("Could not create " + sitesFolder.getPath());
            if (!byId.isEmpty()) {
                return;
            }
        }
        File[] files = sitesFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            plugin.getLogger().warning("Could not list " + sitesFolder.getPath());
            if (!byId.isEmpty()) {
                return;
            }
            files = new File[0];
        }
        Map<UUID, Site> loaded = new ConcurrentHashMap<>();
        int serial = 1;
        for (File file : files) {
            try {
                Site site = read(YamlConfiguration.loadConfiguration(file));
                loaded.put(site.getId(), site);
                serial = Math.max(serial, site.getSerial() + 1);
            } catch (Exception exception) {
                plugin.getLogger().log(Level.WARNING, "Could not read " + file.getName(), exception);
            }
        }
        serial = Math.max(serial, readIndexSerial());
        byId.clear();
        byId.putAll(loaded);
        dirty.clear();
        nextSerial = serial;
        persistedSerial = serial;
    }

    /**
     * Starts the dirty-flush loop so {@link #touch(Site)} hits disk within about one second.
     */
    public void start() {
        if (flushTask != null) {
            return;
        }
        flushTask = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::flushDirty, FLUSH_PERIOD_TICKS, FLUSH_PERIOD_TICKS);
    }

    /**
     * Stops the flush loop and writes every dirty dossier before the JVM unloads the plugin.
     */
    public void stop() {
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
        flushDirty();
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
        return findPrism(world, x, y, z)
                .filter(site -> site.getStatus() == SiteStatus.ESTABLISHED);
    }

    /**
     * Ruin or live excavation whose prism contains this block (not yet exhausted or closed).
     * Unclaimed hidden ruins still occupy a prism so vanilla mining can smash finds.
     *
     * @param world world name
     * @param x block X
     * @param y block Y
     * @param z block Z
     * @return site if the cell sits in a present stratum band
     */
    public Optional<Site> findPrism(String world, int x, int y, int z) {
        return findByChunk(world, x >> 4, z >> 4)
                .filter(site -> site.getStatus() == SiteStatus.HIDDEN
                        || site.getStatus() == SiteStatus.ESTABLISHED)
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
     * Counts camps this player still directs. Exhausted sites keep the camp, so they occupy a
     * slot until someone closes them. Hidden ruins and closed dossiers do not count.
     *
     * @param playerId director to count
     * @return number of locked camps they run
     */
    public int countDirectedCamps(UUID playerId) {
        return findDirectedCamps(playerId).size();
    }

    /**
     * Locked camps this player still directs (established or exhausted). Ordered by serial so
     * staff lists and teleports stay stable.
     *
     * @param playerId director to match
     * @return directed camps, never {@code null}
     */
    public List<Site> findDirectedCamps(UUID playerId) {
        if (playerId == null) {
            return List.of();
        }
        return byId.values().stream()
                .filter(site -> site.isCampLocked() && site.isDirector(playerId))
                .sorted(Comparator.comparingInt(Site::getSerial))
                .toList();
    }

    /**
     * Same as {@link #commit(Site)}. Kept so existing field code stays a one-line persist.
     *
     * @param site dossier to persist
     */
    public void save(Site site) {
        commit(site);
    }

    /**
     * Drops a site from memory and moves its YAML under {@code sites/.trash/} so a bad auto-spawn
     * (empty find list) does not leave a hollow ruin on the map.
     *
     * @param site dossier to remove
     */
    public void delete(Site site) {
        if (site == null || site.getId() == null) {
            return;
        }
        UUID id = site.getId();
        byId.remove(id);
        dirty.remove(id);
        File file = new File(sitesFolder, id + ".yml");
        if (!file.exists()) {
            return;
        }
        File trash = new File(sitesFolder, ".trash");
        if (!trash.exists() && !trash.mkdirs()) {
            plugin.getLogger().warning("Could not create " + trash.getPath());
            if (!file.delete()) {
                plugin.getLogger().warning("Could not delete " + file.getName());
            }
            return;
        }
        File destination = new File(trash, id + ".yml");
        try {
            Files.move(file.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not trash " + file.getName(), exception);
            if (!file.delete()) {
                plugin.getLogger().warning("Could not delete " + file.getName());
            }
        }
    }

    /**
     * Writes {@code site} this tick. Use when the world or the finds register already changed.
     *
     * @param site dossier to persist
     * @throws IllegalArgumentException if {@code site} or its id is missing
     * @throws IllegalStateException if the folder cannot be created or the file cannot be written
     */
    public void commit(Site site) {
        if (site == null || site.getId() == null) {
            throw new IllegalArgumentException("Site with id is required");
        }
        byId.put(site.getId(), site);
        try {
            writeAtomic(site);
            dirty.remove(site.getId());
        } catch (RuntimeException exception) {
            dirty.add(site.getId());
            throw exception;
        }
    }

    /**
     * Marks {@code site} dirty. The flusher, disable, or the next {@link #commit(Site)} writes it.
     * Use for plugin counters (jornada, brush remaining, find dust) that must survive a restart
     * but need not match a block change in this tick.
     *
     * @param site dossier whose RAM copy changed
     */
    public void touch(Site site) {
        if (site == null || site.getId() == null) {
            return;
        }
        byId.put(site.getId(), site);
        dirty.add(site.getId());
    }

    /**
     * Commits every dirty dossier. Safe to call from the scheduler, disable, or reload.
     */
    public void flushDirty() {
        for (UUID id : List.copyOf(dirty)) {
            Site site = byId.get(id);
            if (site == null) {
                dirty.remove(id);
                continue;
            }
            try {
                commit(site);
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not flush site " + id, exception);
            }
        }
    }

    /**
     * Serializes {@code site} to a temp file, then replaces {@code sites/<uuid>.yml}.
     *
     * @param site dossier already in {@link #byId}
     */
    private void writeAtomic(Site site) {
        if (!sitesFolder.exists() && !sitesFolder.mkdirs()) {
            throw new IllegalStateException("Could not create " + sitesFolder.getPath());
        }
        YamlConfiguration yaml = write(site);
        File file = new File(sitesFolder, site.getId() + ".yml");
        replaceAtomically(yaml, file);
        persistIndexIfNeeded();
    }

    /**
     * Writes {@code sites-index.yml} only when the next serial has moved since the last disk copy.
     */
    private void persistIndexIfNeeded() {
        if (nextSerial == persistedSerial) {
            return;
        }
        YamlConfiguration index = new YamlConfiguration();
        index.set("schema", SCHEMA);
        index.set("next-serial", nextSerial);
        try {
            replaceAtomically(index, indexFile);
            persistedSerial = nextSerial;
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not save sites-index.yml", exception);
        }
    }

    /**
     * @return {@code next-serial} from the index, or {@code 1} when the file is missing or corrupt
     */
    private int readIndexSerial() {
        if (!indexFile.exists()) {
            return 1;
        }
        try {
            YamlConfiguration index = YamlConfiguration.loadConfiguration(indexFile);
            return Math.max(1, index.getInt("next-serial", 1));
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not read sites-index.yml", exception);
            return 1;
        }
    }

    /**
     * Writes {@code yaml} to {@code target} via a sibling {@code .tmp} so a crash leaves the
     * previous file intact.
     *
     * @param yaml contents
     * @param target destination file
     */
    private void replaceAtomically(YamlConfiguration yaml, File target) {
        File directory = target.getParentFile() == null ? sitesFolder : target.getParentFile();
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Could not create " + directory.getPath());
        }
        File tmp = new File(directory, target.getName() + ".tmp");
        try {
            yaml.save(tmp);
            try {
                Files.move(
                        tmp.toPath(),
                        target.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            if (tmp.exists() && !tmp.delete()) {
                plugin.getLogger().warning("Could not delete leftover " + tmp.getName());
            }
            throw new IllegalStateException("Could not save " + target.getName(), exception);
        }
    }

    /**
     * @param site in-memory dossier
     * @return YAML ready to save
     */
    private YamlConfiguration write(Site site) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema", SCHEMA);
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
        writeWorkers(yaml, site);
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
            if (find.getGivenName() != null && !find.getGivenName().isBlank()) {
                node.put("given-name", find.getGivenName());
            }
            node.put("stratum", find.getStratumId());
            node.put("state", find.getState().name());
            node.put("buried-conservation", find.getBuriedConservation());
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
            node.put("prior-cells", find.getPriorCells().stream()
                    .map(cell -> cell.x() + "," + cell.y() + "," + cell.z())
                    .toList());
            List<String> brush = new ArrayList<>();
            for (Map.Entry<BlockCell, Integer> entry : find.getBrushRemaining().entrySet()) {
                BlockCell cell = entry.getKey();
                brush.add(cell.x() + "," + cell.y() + "," + cell.z() + ":" + entry.getValue());
            }
            node.put("brush-remaining", brush);
            if (find.getFindNumber() > 0) {
                node.put("find-number", find.getFindNumber());
            }
            if (find.getRecoveredBy() != null) {
                node.put("recovered-by", find.getRecoveredBy().toString());
            }
            if (find.getRecoveredAt() != null) {
                node.put("recovered-at", find.getRecoveredAt().toString());
            }
            node.put("studied", find.isStudied());
            node.put("lab-cleaned", find.isLabCleaned());
            node.put("field-sketch", find.hasFieldSketch());
            if (find.getStudyNotes() != null && !find.getStudyNotes().isBlank()) {
                node.put("study-notes", find.getStudyNotes());
            }
            if (!find.getInterpretations().isEmpty()) {
                List<Map<String, Object>> readings = new ArrayList<>();
                for (FindInterpretation reading : find.getInterpretations()) {
                    Map<String, Object> row = new java.util.LinkedHashMap<>();
                    if (reading.typeId() != null && !reading.typeId().isBlank()) {
                        row.put("type", reading.typeId());
                    }
                    row.put("id", reading.interpretationId());
                    if (reading.author() != null) {
                        row.put("author", reading.author().toString());
                    }
                    if (reading.recordedAt() != null) {
                        row.put("at", reading.recordedAt().toString());
                    }
                    readings.add(row);
                }
                node.put("interpretations", readings);
            }
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
     * Writes the staff pages: role plus the tally each person has run up on this project.
     *
     * @param yaml dossier being written
     * @param site in-memory dossier
     */
    private static void writeWorkers(YamlConfiguration yaml, Site site) {
        for (Map.Entry<UUID, WorkerRecord> entry : site.getWorkers().entrySet()) {
            WorkerRecord record = entry.getValue();
            String path = "workers." + entry.getKey();
            yaml.set(path + ".role", record.getRole().yamlKey());
            yaml.set(path + ".blocks-removed", record.getBlocksRemoved());
            yaml.set(path + ".cells-brushed", record.getCellsBrushed());
            yaml.set(path + ".finds-recovered", record.getFindsRecovered());
            yaml.set(path + ".finds-damaged", record.getFindsDamaged());
            yaml.set(path + ".finds-lost", record.getFindsLost());
            yaml.set(path + ".joined-at", record.getJoinedAt() == null ? null : record.getJoinedAt().toString());
            yaml.set(
                    path + ".last-active-at",
                    record.getLastActiveAt() == null ? null : record.getLastActiveAt().toString());
        }
    }

    /**
     * Reads the staff pages. A dossier written before roles existed has no block here, so everyone
     * on it keeps the default role and an empty tally.
     *
     * @param yaml file contents
     * @param site site being rebuilt
     */
    private static void readWorkers(YamlConfiguration yaml, Site site) {
        ConfigurationSection workers = yaml.getConfigurationSection("workers");
        if (workers == null) {
            return;
        }
        for (String key : workers.getKeys(false)) {
            ConfigurationSection section = workers.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            UUID playerId;
            try {
                playerId = UUID.fromString(key);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            WorkerRecord record = site.worker(playerId);
            record.setRole(SiteRole.fromYaml(section.getString("role")));
            record.setBlocksRemoved(section.getInt("blocks-removed"));
            record.setCellsBrushed(section.getInt("cells-brushed"));
            record.setFindsRecovered(section.getInt("finds-recovered"));
            record.setFindsDamaged(section.getInt("finds-damaged"));
            record.setFindsLost(section.getInt("finds-lost"));
            record.setJoinedAt(parseInstant(section.getString("joined-at")));
            record.setLastActiveAt(parseInstant(section.getString("last-active-at")));
        }
    }

    /**
     * @param raw ISO-8601 text, or {@code null}
     * @return parsed moment, or {@code null} when the line is missing or corrupt
     */
    private static Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /**
     * @param yaml file contents
     * @return reconstructed site
     */
    private Site read(YamlConfiguration yaml) {
        int schema = yaml.getInt("schema", 1);
        if (schema > SCHEMA) {
            plugin.getLogger().warning(
                    "Site " + yaml.getString("id") + " uses schema " + schema
                            + " (this build writes " + SCHEMA + ")");
        }
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
        readWorkers(yaml, site);
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
            if (map.get("given-name") != null) {
                find.setGivenName(BuriedFind.sanitizeGivenName(String.valueOf(map.get("given-name"))));
            }
            find.setStratumId(String.valueOf(map.get("stratum")));
            find.setState(FindState.valueOf(stringOr(map.get("state"), "HIDDEN")));
            addCells(map.get("cells"), find.getCells());
            addCells(map.get("cleaned-cells"), find.getCleanedCells());
            addCells(map.get("grazed-cells"), find.getGrazedCells());
            addCells(map.get("direct-hit-cells"), find.getDirectHitCells());
            addCells(map.get("prior-cells"), find.getPriorCells());
            addRemaining(map.get("brush-remaining"), find.getBrushRemaining());
            if (map.get("find-number") != null) {
                find.setFindNumber(parsePositive(map.get("find-number")));
            }
            if (map.get("recovered-by") != null) {
                try {
                    find.setRecoveredBy(UUID.fromString(String.valueOf(map.get("recovered-by"))));
                } catch (IllegalArgumentException ignored) {
                    // old or corrupt dossier line
                }
            }
            if (map.get("recovered-at") != null) {
                try {
                    find.setRecoveredAt(Instant.parse(String.valueOf(map.get("recovered-at"))));
                } catch (RuntimeException ignored) {
                    // old or corrupt dossier line
                }
            }
            find.setStudied(parseBoolean(map.get("studied")));
            find.setLabCleaned(parseBoolean(map.get("lab-cleaned")));
            find.setFieldSketch(parseBoolean(map.get("field-sketch")));
            if (map.get("study-notes") != null) {
                find.setStudyNotes(String.valueOf(map.get("study-notes")));
            }
            addInterpretations(map.get("interpretations"), find);
            if (map.get("buried-conservation") != null) {
                find.setBuriedConservation(parseConservation(map.get("buried-conservation")));
            } else if (find.getGrazedCells().isEmpty()
                    && find.getDirectHitCells().isEmpty()
                    && find.getPriorCells().isEmpty()) {
                // Pre-conservation-roll file: whatever it stored was the untouched value.
                find.setBuriedConservation(parseConservation(map.get("conservation")));
            } else {
                find.refreshConservation();
            }
            site.getFinds().add(find);
        }
        site.assignMissingFindNumbers();

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
     * @param raw YAML list of {@code x,y,z:ticks} strings
     * @param into cell → remaining brush ticks
     */
    private static void addRemaining(Object raw, Map<BlockCell, Integer> into) {
        if (!(raw instanceof List<?> list)) {
            return;
        }
        for (Object entry : list) {
            String text = String.valueOf(entry);
            int split = text.lastIndexOf(':');
            if (split < 0) {
                continue;
            }
            String[] parts = text.substring(0, split).split(",");
            if (parts.length < 3) {
                continue;
            }
            try {
                int ticks = Integer.parseInt(text.substring(split + 1).trim());
                if (ticks <= 0) {
                    continue;
                }
                into.put(
                        new BlockCell(
                                Integer.parseInt(parts[0].trim()),
                                Integer.parseInt(parts[1].trim()),
                                Integer.parseInt(parts[2].trim())),
                        ticks);
            } catch (NumberFormatException ignored) {
                // skip a corrupt dossier line
            }
        }
    }

    /**
     * @param raw YAML list of interpretation maps
     * @param find row to fill
     */
    private static void addInterpretations(Object raw, BuriedFind find) {
        if (!(raw instanceof List<?> list)) {
            return;
        }
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> map)) {
                continue;
            }
            Object id = map.get("id");
            if (id == null) {
                continue;
            }
            UUID author = null;
            if (map.get("author") != null) {
                try {
                    author = UUID.fromString(String.valueOf(map.get("author")));
                } catch (IllegalArgumentException ignored) {
                    // skip a corrupt author
                }
            }
            Instant at = null;
            if (map.get("at") != null) {
                try {
                    at = Instant.parse(String.valueOf(map.get("at")));
                } catch (RuntimeException ignored) {
                    // skip a corrupt timestamp
                }
            }
            Object type = map.get("type");
            find.getInterpretations().add(new FindInterpretation(
                    type == null ? null : String.valueOf(type),
                    String.valueOf(id),
                    author,
                    at));
        }
    }

    /**
     * @param value YAML number or missing
     * @return integer ≥ 0, or {@code 0} when missing or unparsable
     */
    private static int parsePositive(Object value) {
        if (value == null) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(String.valueOf(value)));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    /**
     * @param value YAML boolean or missing
     * @return {@code true} only when the value is a true boolean or the string {@code true}
     */
    private static boolean parseBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
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
