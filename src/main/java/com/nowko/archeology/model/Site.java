package com.nowko.archeology.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One administered ruin or excavation: chunk, strata, hidden finds, and camp metadata.
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
     * @return serial plus name for command and log output
     */
    public String displayLabel() {
        String label = name != null && !name.isBlank() ? name : "Site #" + serial;
        return "#" + serial + " — " + label;
    }
}
