package com.nowko.archeology.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One administered ruin or excavation: chunk, strata, hidden finds, prospecting, and camp metadata.
 */
public class Site {
    private UUID id;
    private int serial;
    private SiteType type = SiteType.MANAGED_RUIN;
    private SiteStatus status = SiteStatus.HIDDEN;
    private InterestLevel interest;
    private String name;
    private String worldName;
    private int chunkX;
    private int chunkZ;
    private int surfaceY;
    private int detectionRadius;
    private UUID createdBy;
    private Instant createdAt;
    private UUID director;
    private String visibility = "private";
    private int recoveredCount;
    private final List<String> hintIds = new ArrayList<>();
    private final Map<String, StratumBand> strata = new LinkedHashMap<>();
    private final List<BuriedFind> finds = new ArrayList<>();
    private final List<UUID> excavators = new ArrayList<>();
    private final List<String> factions = new ArrayList<>();
    private final Map<UUID, List<BlockCell>> prospectSamples = new LinkedHashMap<>();
    private final Set<UUID> prospectConfirmed = new LinkedHashSet<>();
    private Integer establishmentChunkX;
    private Integer establishmentChunkZ;
    private Integer campX;
    private Integer campY;
    private Integer campZ;

    /** @return persistent site UUID */
    public UUID getId() {
        return id;
    }

    /** @param id persistent site UUID */
    public void setId(UUID id) {
        this.id = id;
    }

    /** @return human-facing sequential number */
    public int getSerial() {
        return serial;
    }

    /** @param serial human-facing sequential number */
    public void setSerial(int serial) {
        this.serial = serial;
    }

    /** @return whether this is a staff-registered ruin or a player excavation */
    public SiteType getType() {
        return type;
    }

    /** @param type ruin vs excavation */
    public void setType(SiteType type) {
        this.type = type;
    }

    /** @return lifecycle: hidden, camp established, or exhausted */
    public SiteStatus getStatus() {
        return status;
    }

    /** @param status lifecycle state */
    public void setStatus(SiteStatus status) {
        this.status = status;
    }

    /** @return generation wealth / detection budget */
    public InterestLevel getInterest() {
        return interest;
    }

    /** @param interest generation wealth / detection budget */
    public void setInterest(InterestLevel interest) {
        this.interest = interest;
    }

    /** @return optional display name */
    public String getName() {
        return name;
    }

    /** @param name optional display name */
    public void setName(String name) {
        this.name = name;
    }

    /** @return Bukkit world name */
    public String getWorldName() {
        return worldName;
    }

    /** @param worldName Bukkit world name */
    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }

    /** @return chunk X of the site bounds */
    public int getChunkX() {
        return chunkX;
    }

    /** @param chunkX chunk X of the site bounds */
    public void setChunkX(int chunkX) {
        this.chunkX = chunkX;
    }

    /** @return chunk Z of the site bounds */
    public int getChunkZ() {
        return chunkZ;
    }

    /** @param chunkZ chunk Z of the site bounds */
    public void setChunkZ(int chunkZ) {
        this.chunkZ = chunkZ;
    }

    /** @return surface Y used as the stratum depth datum */
    public int getSurfaceY() {
        return surfaceY;
    }

    /** @param surfaceY surface Y used as the stratum depth datum */
    public void setSurfaceY(int surfaceY) {
        this.surfaceY = surfaceY;
    }

    /** @return tracker detection radius in blocks */
    public int getDetectionRadius() {
        return detectionRadius;
    }

    /** @param detectionRadius tracker detection radius in blocks */
    public void setDetectionRadius(int detectionRadius) {
        this.detectionRadius = detectionRadius;
    }

    /** @return staff player who registered the site, or {@code null} */
    public UUID getCreatedBy() {
        return createdBy;
    }

    /** @param createdBy staff player who registered the site, or {@code null} */
    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    /** @return creation timestamp */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /** @param createdAt creation timestamp */
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    /** @return excavation director, or {@code null} until a camp is planted */
    public UUID getDirector() {
        return director;
    }

    /** @param director excavation director, or {@code null} */
    public void setDirector(UUID director) {
        this.director = director;
    }

    /** @return access mode such as {@code private}, invite, or public */
    public String getVisibility() {
        return visibility;
    }

    /** @param visibility access mode such as {@code private} */
    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }

    /** @return how many finds have been recovered */
    public int getRecoveredCount() {
        return recoveredCount;
    }

    /** @param recoveredCount how many finds have been recovered */
    public void setRecoveredCount(int recoveredCount) {
        this.recoveredCount = recoveredCount;
    }

    /** @return hint template ids shown after prospecting */
    public List<String> getHintIds() {
        return hintIds;
    }

    /** @return present/absent Y bands by stratum id */
    public Map<String, StratumBand> getStrata() {
        return strata;
    }

    /** @return buried finds (hidden shapes) */
    public List<BuriedFind> getFinds() {
        return finds;
    }

    /** @return players allowed to excavate */
    public List<UUID> getExcavators() {
        return excavators;
    }

    /** @return faction ids granted access */
    public List<String> getFactions() {
        return factions;
    }

    /**
     * @param playerId sampler
     * @return unique ground points this player has sampled; empty if none
     */
    public List<BlockCell> prospectSamples(UUID playerId) {
        List<BlockCell> list = prospectSamples.get(playerId);
        return list == null ? List.of() : list;
    }

    /**
     * @return all per-player sample lists (for persistence)
     */
    public Map<UUID, List<BlockCell>> allProspectSamples() {
        return prospectSamples;
    }

    /**
     * @param playerId sampler
     * @param cell ground point
     * @return whether that player already sampled this cell
     */
    public boolean hasProspectSample(UUID playerId, BlockCell cell) {
        return prospectSamples(playerId).contains(cell);
    }

    /**
     * @param playerId sampler
     * @param cell ground point
     * @return {@code true} if the point was new
     */
    public boolean addProspectSample(UUID playerId, BlockCell cell) {
        List<BlockCell> list = prospectSamples.computeIfAbsent(playerId, id -> new ArrayList<>());
        if (list.contains(cell)) {
            return false;
        }
        list.add(cell);
        return true;
    }

    /**
     * @return players who have confirmed this hidden ruin
     */
    public Set<UUID> getProspectConfirmed() {
        return prospectConfirmed;
    }

    /**
     * @param playerId sampler
     * @return whether that player has finished prospecting
     */
    public boolean isProspectConfirmed(UUID playerId) {
        return prospectConfirmed.contains(playerId);
    }

    /**
     * @param playerId player who reached the sample quota
     */
    public void confirmProspect(UUID playerId) {
        prospectConfirmed.add(playerId);
    }

    /**
     * @return camp chunk X, or {@code null} until established
     */
    public Integer getEstablishmentChunkX() {
        return establishmentChunkX;
    }

    /**
     * @param establishmentChunkX camp chunk X
     */
    public void setEstablishmentChunkX(Integer establishmentChunkX) {
        this.establishmentChunkX = establishmentChunkX;
    }

    /**
     * @return camp chunk Z, or {@code null} until established
     */
    public Integer getEstablishmentChunkZ() {
        return establishmentChunkZ;
    }

    /**
     * @param establishmentChunkZ camp chunk Z
     */
    public void setEstablishmentChunkZ(Integer establishmentChunkZ) {
        this.establishmentChunkZ = establishmentChunkZ;
    }

    /**
     * @return camp block X, or {@code null}
     */
    public Integer getCampX() {
        return campX;
    }

    /**
     * @param campX camp block X
     */
    public void setCampX(Integer campX) {
        this.campX = campX;
    }

    /**
     * @return camp block Y, or {@code null}
     */
    public Integer getCampY() {
        return campY;
    }

    /**
     * @param campY camp block Y
     */
    public void setCampY(Integer campY) {
        this.campY = campY;
    }

    /**
     * @return camp block Z, or {@code null}
     */
    public Integer getCampZ() {
        return campZ;
    }

    /**
     * @param campZ camp block Z
     */
    public void setCampZ(Integer campZ) {
        this.campZ = campZ;
    }

    /**
     * @return whether a camp chunk has been recorded
     */
    public boolean hasEstablishment() {
        return establishmentChunkX != null && establishmentChunkZ != null;
    }

    /**
     * Records the camp, director, and claimed status. Does not place blocks.
     *
     * @param directorId player who confirmed the kit
     * @param campChunkX neighbor chunk X
     * @param campChunkZ neighbor chunk Z
     * @param blockX camp block X
     * @param blockY camp block Y
     * @param blockZ camp block Z
     */
    public void establish(UUID directorId, int campChunkX, int campChunkZ, int blockX, int blockY, int blockZ) {
        setStatus(SiteStatus.ESTABLISHED);
        setType(SiteType.EXCAVATION);
        setDirector(directorId);
        setEstablishmentChunkX(campChunkX);
        setEstablishmentChunkZ(campChunkZ);
        setCampX(blockX);
        setCampY(blockY);
        setCampZ(blockZ);
        if (!getExcavators().contains(directorId)) {
            getExcavators().add(directorId);
        }
    }

    /**
     * @return block X of the chunk center used for tracker distance
     */
    public int centerBlockX() {
        return (chunkX << 4) + 8;
    }

    /**
     * @return block Z of the chunk center used for tracker distance
     */
    public int centerBlockZ() {
        return (chunkZ << 4) + 8;
    }

    /**
     * @return serial plus name for command and log output
     */
    public String displayLabel() {
        String label = name != null && !name.isBlank() ? name : "Site #" + serial;
        return "#" + serial + " — " + label;
    }
}
