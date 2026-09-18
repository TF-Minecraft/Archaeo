package com.nowko.archeology.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * One administered ruin or excavation: chunk, stratum datum, hidden finds, prospecting, and camp metadata.
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
    private final Map<UUID, WorkerRecord> workers = new LinkedHashMap<>();
    private final List<String> factions = new ArrayList<>();
    private final Map<UUID, List<BlockCell>> prospectSamples = new LinkedHashMap<>();
    private final Set<UUID> prospectConfirmed = new LinkedHashSet<>();
    private Integer establishmentChunkX;
    private Integer establishmentChunkZ;
    private Integer campX;
    private Integer campY;
    private Integer campZ;
    private Integer campSignX;
    private Integer campSignY;
    private Integer campSignZ;
    private final List<BlockCell> campBlocks = new ArrayList<>();
    private String campWoolPrimary = "WHITE";
    private String campWoolSecondary = "RED";
    private String campFacing;
    private long jornadaWorldDay = -1L;
    private int jornadaPickLeft;
    private final Map<BlockCell, Integer> fillDamage = new LinkedHashMap<>();

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

    /**
     * @return median ground Y of the ruin chunk when finds were generated (stratum datum)
     */
    public int getSurfaceY() {
        return surfaceY;
    }

    /**
     * @param surfaceY median ground Y used as the stratum datum
     */
    public void setSurfaceY(int surfaceY) {
        this.surfaceY = surfaceY;
    }

    /**
     * @param blockX block X
     * @param blockZ block Z
     * @return whether the column sits in this site's archaeological chunk
     */
    public boolean isInRuinChunk(int blockX, int blockZ) {
        return (blockX >> 4) == chunkX && (blockZ >> 4) == chunkZ;
    }

    /**
     * Horizontal chunk plus a present stratum Y band. Air above the datum is outside.
     *
     * @param blockX block X
     * @param blockY block Y
     * @param blockZ block Z
     * @return whether the cell is inside the excavation prism
     */
    public boolean isInPrism(int blockX, int blockY, int blockZ) {
        if (!isInRuinChunk(blockX, blockZ)) {
            return false;
        }
        for (StratumBand band : strata.values()) {
            if (band.isPresent() && blockY >= band.getMinY() && blockY <= band.getMaxY()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Present stratum band that contains this Y, or {@code null}.
     *
     * @param blockY block Y
     * @return band, if any
     */
    public StratumBand stratumAt(int blockY) {
        for (StratumBand band : strata.values()) {
            if (band.isPresent() && blockY >= band.getMinY() && blockY <= band.getMaxY()) {
                return band;
            }
        }
        return null;
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

    /**
     * @param cell world cell
     * @return the find whose shape includes this cell, if any
     */
    public Optional<BuriedFind> findAt(BlockCell cell) {
        for (BuriedFind find : finds) {
            if (find.getCells().contains(cell)) {
                return Optional.of(find);
            }
        }
        return Optional.empty();
    }

    /** @return players allowed to excavate */
    public List<UUID> getExcavators() {
        return excavators;
    }

    /**
     * @param playerId viewer or worker
     * @return whether this player planted the camp
     */
    public boolean isDirector(UUID playerId) {
        return director != null && director.equals(playerId);
    }

    /**
     * @param playerId viewer
     * @return whether they are the director or a hired excavator, including after the cut closes
     */
    public boolean onStaff(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        return isDirector(playerId) || excavators.contains(playerId);
    }

    /**
     * Director and granted excavators may work on the dig site. Every role opens the cut; only the
     * brush is rationed, so this stays the plain roster question it always was. Visibility modes are
     * unused in v1.
     *
     * @param playerId worker
     * @return whether field work is allowed on this established excavation
     */
    public boolean mayWork(UUID playerId) {
        if (playerId == null || getStatus() != SiteStatus.ESTABLISHED) {
            return false;
        }
        if (isDirector(playerId)) {
            return true;
        }
        return excavators.contains(playerId);
    }

    /**
     * @param playerId worker
     * @return whether this person may brush a find clean and lift it
     */
    public boolean mayRecover(UUID playerId) {
        return mayWork(playerId) && roleOf(playerId).mayRecover();
    }

    /**
     * Study and interpretation continue after the camp is filed: the dossier is the archive,
     * whether it is still a standing camp or a field book. The director and anyone with
     * {@link SiteRole#mayCatalog()} on the roster may write.
     *
     * @param playerId cataloguer
     * @return whether this person may study a recovered piece and file readings
     */
    public boolean mayCatalog(UUID playerId) {
        if (playerId == null || !mayConsult()) {
            return false;
        }
        if (isDirector(playerId)) {
            return true;
        }
        return excavators.contains(playerId) && roleOf(playerId).mayCatalog();
    }

    /**
     * Finds that have left the cut, numbered for the register. Hidden shapes are omitted.
     *
     * @return recovered and lost finds, lowest inventory number first
     */
    public List<BuriedFind> cataloguedFinds() {
        List<BuriedFind> list = new ArrayList<>();
        for (BuriedFind find : finds) {
            if (find.getFindNumber() > 0 || find.hasLeftTheCut()) {
                list.add(find);
            }
        }
        list.sort((a, b) -> Integer.compare(a.getFindNumber(), b.getFindNumber()));
        return list;
    }

    /**
     * @param findId buried-find id
     * @return that row, or empty
     */
    public Optional<BuriedFind> findById(UUID findId) {
        if (findId == null) {
            return Optional.empty();
        }
        for (BuriedFind find : finds) {
            if (findId.equals(find.getId())) {
                return Optional.of(find);
            }
        }
        return Optional.empty();
    }

    /**
     * Gives an inventory number to every piece that has left the cut and does not have one yet.
     * Lost-in-cut rows join the register so the archive is honest.
     *
     * @param actor recoverer or the player who destroyed the piece; {@code null} when unknown
     * @return whether any row changed
     */
    public boolean catalogSettledFinds(UUID actor) {
        boolean changed = false;
        Instant now = Instant.now();
        for (BuriedFind find : finds) {
            if (!find.hasLeftTheCut()) {
                continue;
            }
            if (find.getFindNumber() <= 0) {
                find.setFindNumber(nextFindNumber());
                changed = true;
            }
            if (find.getRecoveredAt() == null) {
                find.setRecoveredAt(now);
                changed = true;
            }
            if (find.getRecoveredBy() == null && actor != null) {
                find.setRecoveredBy(actor);
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Assigns inventory numbers to recovered or lost rows that predate the register, without
     * stamping a new recoverer or time. Used when loading old dossiers.
     *
     * @return whether any number was filled in
     */
    public boolean assignMissingFindNumbers() {
        boolean changed = false;
        for (BuriedFind find : finds) {
            if (find.getFindNumber() > 0 || !find.hasLeftTheCut()) {
                continue;
            }
            find.setFindNumber(nextFindNumber());
            changed = true;
        }
        return changed;
    }

    /**
     * @return next unused inventory sequence on this excavation
     */
    private int nextFindNumber() {
        int max = 0;
        for (BuriedFind find : finds) {
            max = Math.max(max, find.getFindNumber());
        }
        return max + 1;
    }

    /**
     * Adds a worker. The director cannot be duplicated.
     *
     * @param playerId excavator to grant
     * @return {@code true} if the roster changed
     */
    public boolean grantExcavator(UUID playerId) {
        if (playerId == null || excavators.contains(playerId)) {
            return false;
        }
        excavators.add(playerId);
        worker(playerId);
        return true;
    }

    /**
     * Removes a worker. The director stays on the project.
     *
     * <p>Their tally survives the dismissal — the cut they opened is still opened and the finds they
     * broke are still broken — but their role does not: a role is an appointment on a running dig,
     * so someone hired back later starts from the default rather than from a bench they were put on
     * months ago and nobody remembers.
     *
     * @param playerId excavator to revoke
     * @return {@code true} if the roster changed
     */
    public boolean revokeExcavator(UUID playerId) {
        if (playerId == null || isDirector(playerId)) {
            return false;
        }
        if (!excavators.remove(playerId)) {
            return false;
        }
        WorkerRecord record = workers.get(playerId);
        if (record != null) {
            record.setRole(SiteRole.defaultRole());
        }
        return true;
    }

    /**
     * @return every standing and tally on this project, keyed by player (for persistence)
     */
    public Map<UUID, WorkerRecord> getWorkers() {
        return workers;
    }

    /**
     * Opens this person's page in the dossier, creating it on first touch.
     *
     * @param playerId staff member
     * @return their record, never {@code null}
     */
    public WorkerRecord worker(UUID playerId) {
        return workers.computeIfAbsent(playerId, id -> {
            WorkerRecord record = new WorkerRecord();
            record.setRole(isDirector(id) ? SiteRole.DIRECTOR : SiteRole.defaultRole());
            record.setJoinedAt(Instant.now());
            return record;
        });
    }

    /**
     * Read-only lookup that does not create a page, so drawing a board cannot dirty the dossier.
     *
     * @param playerId staff member
     * @return their record, or {@code null} when they have none yet
     */
    public WorkerRecord workerRecord(UUID playerId) {
        return workers.get(playerId);
    }

    /**
     * Page to charge field work to. Only roster members keep a tally, so a trespasser swinging a
     * shovel through the cut does not open a staff page in the dossier.
     *
     * @param playerId whoever acted
     * @return their record, or {@code null} when they are not on the staff
     */
    public WorkerRecord staffLog(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        if (!isDirector(playerId) && !excavators.contains(playerId)) {
            return null;
        }
        return worker(playerId);
    }

    /**
     * The director's role follows the camp rather than the record, so a camp handover cannot leave
     * a director filed as a visitor.
     *
     * @param playerId staff member
     * @return their role, defaulting to {@link SiteRole#defaultRole()} for pre-role dossiers
     */
    public SiteRole roleOf(UUID playerId) {
        if (isDirector(playerId)) {
            return SiteRole.DIRECTOR;
        }
        WorkerRecord record = workers.get(playerId);
        return record == null ? SiteRole.defaultRole() : record.getRole();
    }

    /**
     * Moves a roster member to another role. The director's own standing cannot be edited.
     *
     * @param playerId staff member on the roster
     * @param role role to hand out; {@link SiteRole#DIRECTOR} is not assignable
     * @return {@code true} when the dossier changed
     */
    public boolean assignRole(UUID playerId, SiteRole role) {
        if (playerId == null || role == null || role == SiteRole.DIRECTOR || isDirector(playerId)) {
            return false;
        }
        if (!excavators.contains(playerId)) {
            return false;
        }
        WorkerRecord record = worker(playerId);
        if (record.getRole() == role) {
            return false;
        }
        record.setRole(role);
        return true;
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
     * The camp survives the dig until someone closes it: an exhausted excavation keeps its blocks
     * and its board so the project can still be read on site.
     *
     * @return true once a camp is planted and until the excavation is closed
     */
    public boolean isCampLocked() {
        return getStatus() == SiteStatus.ESTABLISHED || getStatus() == SiteStatus.EXHAUSTED;
    }

    /**
     * @return whether the excavation record may still be opened from the camp or a field book
     */
    public boolean mayConsult() {
        return status == SiteStatus.ESTABLISHED
                || status == SiteStatus.EXHAUSTED
                || status == SiteStatus.CLOSED;
    }

    /**
     * @return placed template cells
     */
    public List<BlockCell> getCampBlocks() {
        return campBlocks;
    }

    /**
     * @param x block X
     * @param y block Y
     * @param z block Z
     * @return whether this cell is part of the locked camp
     */
    public boolean isCampBlock(int x, int y, int z) {
        return campBlocks.contains(new BlockCell(x, y, z));
    }

    /**
     * @return sign X, or {@code null}
     */
    public Integer getCampSignX() {
        return campSignX;
    }

    /**
     * @param campSignX sign X
     */
    public void setCampSignX(Integer campSignX) {
        this.campSignX = campSignX;
    }

    /**
     * @return sign Y, or {@code null}
     */
    public Integer getCampSignY() {
        return campSignY;
    }

    /**
     * @param campSignY sign Y
     */
    public void setCampSignY(Integer campSignY) {
        this.campSignY = campSignY;
    }

    /**
     * @return sign Z, or {@code null}
     */
    public Integer getCampSignZ() {
        return campSignZ;
    }

    /**
     * @param campSignZ sign Z
     */
    public void setCampSignZ(Integer campSignZ) {
        this.campSignZ = campSignZ;
    }

    /**
     * @return primary wool ({@code W} cells), default {@code WHITE}
     */
    public String getCampWoolPrimary() {
        return campWoolPrimary == null || campWoolPrimary.isBlank() ? "WHITE" : campWoolPrimary;
    }

    /**
     * @param campWoolPrimary DyeColor name without {@code _WOOL}
     */
    public void setCampWoolPrimary(String campWoolPrimary) {
        this.campWoolPrimary = campWoolPrimary;
    }

    /**
     * @return secondary wool ({@code R} cells), default {@code RED}
     */
    public String getCampWoolSecondary() {
        return campWoolSecondary == null || campWoolSecondary.isBlank() ? "RED" : campWoolSecondary;
    }

    /**
     * @param campWoolSecondary DyeColor name without {@code _WOOL}
     */
    public void setCampWoolSecondary(String campWoolSecondary) {
        this.campWoolSecondary = campWoolSecondary;
    }

    /**
     * @return cardinal the camp front faces, or {@code null} if unknown (legacy sites)
     */
    public String getCampFacing() {
        return campFacing;
    }

    /**
     * @param campFacing {@code NORTH}, {@code SOUTH}, {@code EAST}, or {@code WEST}
     */
    public void setCampFacing(String campFacing) {
        this.campFacing = campFacing;
    }

    /**
     * @return Minecraft day the pick budget was last filled, or {@code -1}
     */
    public long getJornadaWorldDay() {
        return jornadaWorldDay;
    }

    /**
     * @param jornadaWorldDay {@code world fullTime / 24000}
     */
    public void setJornadaWorldDay(long jornadaWorldDay) {
        this.jornadaWorldDay = jornadaWorldDay;
    }

    /**
     * @return pick actions remaining today
     */
    public int getJornadaPickLeft() {
        return jornadaPickLeft;
    }

    /**
     * @param jornadaPickLeft pick actions remaining today
     */
    public void setJornadaPickLeft(int jornadaPickLeft) {
        this.jornadaPickLeft = jornadaPickLeft;
    }

    /**
     * @return stages already applied to fill cells (only damaged blocks)
     */
    public Map<BlockCell, Integer> getFillDamage() {
        return fillDamage;
    }

    /**
     * Adds alteration stages to a fill cell.
     *
     * @param cell prism cell
     * @param stages hits or stages to add
     * @return total stages after the hit
     */
    public int addFillDamage(BlockCell cell, int stages) {
        int total = fillDamage.getOrDefault(cell, 0) + stages;
        fillDamage.put(cell, total);
        return total;
    }

    /**
     * @param cell block that was removed
     */
    public void clearFillDamage(BlockCell cell) {
        fillDamage.remove(cell);
    }

    /**
     * Updates only camp coordinates after a director move.
     *
     * @param campChunkX new camp chunk X
     * @param campChunkZ new camp chunk Z
     * @param blockX origin X
     * @param blockY origin Y
     * @param blockZ origin Z
     */
    public void relocateCamp(int campChunkX, int campChunkZ, int blockX, int blockY, int blockZ) {
        setEstablishmentChunkX(campChunkX);
        setEstablishmentChunkZ(campChunkZ);
        setCampX(blockX);
        setCampY(blockY);
        setCampZ(blockZ);
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
        worker(directorId).setRole(SiteRole.DIRECTOR);
    }

    /**
     * A find still owes the cut work while it can be lifted; a destroyed one owes nothing.
     *
     * @return whether any find is neither recovered nor lost
     */
    public boolean hasPendingFinds() {
        for (BuriedFind find : finds) {
            if (find.getState() != FindState.RECOVERED && find.getState() != FindState.LOST) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds that have left the cut, recovered or lost. Buried pieces keep this below the generated
     * total, which is how an early camp close still shows that the excavation was unfinished.
     *
     * @return settled find count
     */
    public int settledFindCount() {
        int settled = 0;
        for (BuriedFind find : finds) {
            if (find.getState() == FindState.RECOVERED || find.getState() == FindState.LOST) {
                settled++;
            }
        }
        return settled;
    }

    /**
     * @return 0–100 share of generated finds that have left the cut; {@code 0} when none were generated
     */
    public int completionPercent() {
        int total = finds.size();
        if (total <= 0) {
            return 0;
        }
        return Math.round(100f * settledFindCount() / total);
    }

    /**
     * Same test the camp board uses before asking for an extra confirm: generated finds that have
     * not all left the cut.
     *
     * @return whether closing now would leave work in the ground
     */
    public boolean isUnfinishedCut() {
        return !finds.isEmpty() && completionPercent() < 100;
    }

    /**
     * Wounds already written on hidden finds (vanilla smash, lost cells). Missing terrain that was
     * never recorded still has to be checked in the world.
     *
     * @return whether any find is no longer a pristine hidden shape
     */
    public boolean hasRecordedFindWounds() {
        for (BuriedFind find : finds) {
            if (find.isFieldDamaged() || find.isDisturbedBeforeDig() || find.getState() != FindState.HIDDEN) {
                return true;
            }
        }
        return false;
    }

    /**
     * Drops generated wealth so staff can rebuild a hidden ruin at a new interest. Identity,
     * chunk, name, and author stay. Prospect confirmations are left in place so the caller can
     * refuse the rewrite if anyone already confirmed the site.
     */
    public void clearGeneratedLayout() {
        strata.clear();
        finds.clear();
        hintIds.clear();
        fillDamage.clear();
        prospectSamples.clear();
        recoveredCount = 0;
    }

    /**
     * Closes the project when the cut has nothing left to give. A site without generated finds
     * never closes this way, so a mis-generated ruin does not die on its first pick swing.
     *
     * @return {@code true} when this call turned an active excavation into an exhausted one
     */
    public boolean exhaustIfSettled() {
        if (status != SiteStatus.ESTABLISHED || finds.isEmpty() || hasPendingFinds()) {
            return false;
        }
        status = SiteStatus.EXHAUSTED;
        return true;
    }

    /**
     * Unlocks the camp while the project still has a dossier. Allowed on an active dig or after
     * the cut is exhausted; blocks stay in the world as ordinary pieces.
     *
     * @return {@code true} when this call closed a standing camp
     */
    public boolean closeCamp() {
        if (status != SiteStatus.ESTABLISHED && status != SiteStatus.EXHAUSTED) {
            return false;
        }
        status = SiteStatus.CLOSED;
        return true;
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
        String label = name != null && !name.isBlank() ? name : "Site";
        return "#" + serial + " — " + label;
    }
}
