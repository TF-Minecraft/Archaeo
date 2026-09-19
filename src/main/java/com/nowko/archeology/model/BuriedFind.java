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
 * One hidden artifact instance: template id, stratum, and connected block cells.
 */
public class BuriedFind {
    private UUID id;
    private String artifactId;
    /** Bukkit material chosen from the template pool when this instance was generated. */
    private String item;
    /** Player name from an anvil; catalog display name is used when this is blank. */
    private String givenName;
    private String stratumId;
    private FindState state = FindState.HIDDEN;
    private int buriedConservation = 100;
    private int conservation = 100;
    /** Public inventory number once the piece has left the cut; {@code 0} means not yet filed. */
    private int findNumber;
    private UUID recoveredBy;
    private Instant recoveredAt;
    private boolean studied;
    /** Whether the first lab wipe has been finished at the cabinet. */
    private boolean labCleaned;
    /** Whether a signed field sketch has been filed at the cabinet. */
    private boolean fieldSketch;
    private String studyNotes;
    private final List<FindInterpretation> interpretations = new ArrayList<>();
    private final List<BlockCell> cells = new ArrayList<>();
    private final Set<BlockCell> cleanedCells = new LinkedHashSet<>();
    private final Set<BlockCell> grazedCells = new LinkedHashSet<>();
    private final Set<BlockCell> directHitCells = new LinkedHashSet<>();
    private final Set<BlockCell> priorCells = new LinkedHashSet<>();
    /** Ticks still needed to finish brushing a cube; absent means this cube has not been started. */
    private final Map<BlockCell, Integer> brushRemaining = new LinkedHashMap<>();

    /** @return unique id of this find instance */
    public UUID getId() {
        return id;
    }

    /** @param id unique id of this find instance */
    public void setId(UUID id) {
        this.id = id;
    }

    /** @return catalog artifact template id */
    public String getArtifactId() {
        return artifactId;
    }

    /** @param artifactId catalog artifact template id */
    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    /**
     * Material rolled from the template's {@code item} pool. Blank on old dossiers until the piece is lifted.
     *
     * @return Bukkit material name, or {@code null} if none has been chosen yet
     */
    public String getItem() {
        return item;
    }

    /**
     * @param item Bukkit material name stored for this instance
     */
    public void setItem(String item) {
        if (item == null || item.isBlank()) {
            this.item = null;
            return;
        }
        this.item = item;
    }

    /**
     * @return anvil name stored on the dossier, or {@code null} when the catalog name still applies
     */
    public String getGivenName() {
        return givenName;
    }

    /**
     * @param givenName anvil name, or {@code null}/blank to revert to the catalog
     */
    public void setGivenName(String givenName) {
        if (givenName == null || givenName.isBlank()) {
            this.givenName = null;
            return;
        }
        this.givenName = givenName;
    }

    /**
     * Strips colours and caps length so an anvil line can live in YAML and on boards.
     *
     * @param raw rename text from the anvil, or {@code null}
     * @return stored name, or {@code null} when empty
     */
    public static String sanitizeGivenName(String raw) {
        if (raw == null) {
            return null;
        }
        String name = raw.replace('§', ' ').trim();
        if (name.isEmpty()) {
            return null;
        }
        if (name.length() > 40) {
            name = name.substring(0, 40).trim();
        }
        return name.isEmpty() ? null : name;
    }

    /**
     * Name on the piece, camp fiche, and report. An anvil rename wins; otherwise the catalog.
     *
     * @param catalogName {@link com.nowko.archeology.config.ArtifactTemplate#displayName()}, or {@code null}
     * @return player-facing title
     */
    public String shownName(String catalogName) {
        if (givenName != null && !givenName.isBlank()) {
            return givenName;
        }
        if (catalogName != null && !catalogName.isBlank()) {
            return catalogName;
        }
        return artifactId == null || artifactId.isBlank() ? "recovered find" : artifactId;
    }

    /** @return stratum id that contains this find */
    public String getStratumId() {
        return stratumId;
    }

    /** @param stratumId stratum id that contains this find */
    public void setStratumId(String stratumId) {
        this.stratumId = stratumId;
    }

    /** @return excavation reveal progress */
    public FindState getState() {
        return state;
    }

    /** @param state excavation reveal progress */
    public void setState(FindState state) {
        this.state = state;
    }

    /**
     * Excavation wounds are not the same as centuries underground: this reports only the dig.
     *
     * @return whether any cell of this find was grazed or struck while digging
     */
    public boolean isFieldDamaged() {
        return !grazedCells.isEmpty() || !directHitCells.isEmpty();
    }

    /**
     * Cells that were already gone when the camp was planted: a vanilla tunnel, a lava flow, or a
     * build put there while the ruin sat unclaimed. They cost the same as a graze, but they are
     * nobody's fault in the field, so the piece must not be reported as hurt while digging.
     *
     * @return coordinates lost before the excavation opened
     */
    public Set<BlockCell> getPriorCells() {
        return priorCells;
    }

    /**
     * @return whether the ground was already broken over this find before the first work day
     */
    public boolean isDisturbedBeforeDig() {
        return !priorCells.isEmpty();
    }

    /**
     * Spends a cell that the world had already taken before the claim. No-op if already counted.
     *
     * @param cell find cell missing at claim time
     * @return whether conservation changed
     */
    public boolean woundBeforeDig(BlockCell cell) {
        return applyWound(cell, priorCells);
    }

    /**
     * Condition the piece already had in the ground, rolled once when the site is generated.
     * Field work can only subtract from it, so a flawless dig does not create a flawless piece.
     *
     * @return buried condition ceiling, 0–100
     */
    public int getBuriedConservation() {
        return buriedConservation;
    }

    /**
     * @param buriedConservation buried condition ceiling, 0–100
     */
    public void setBuriedConservation(int buriedConservation) {
        this.buriedConservation = Math.max(0, Math.min(100, buriedConservation));
        refreshConservation();
    }

    /**
     * @return remaining quality, 0–100
     */
    public int getConservation() {
        return conservation;
    }

    /**
     * @param conservation remaining quality, 0–100
     */
    public void setConservation(int conservation) {
        this.conservation = Math.max(0, Math.min(100, conservation));
    }

    /**
     * Public inventory number assigned when the piece leaves the cut. Zero means it is still buried.
     *
     * @return find number, or {@code 0}
     */
    public int getFindNumber() {
        return findNumber;
    }

    /**
     * @param findNumber public inventory number, or {@code 0} while still buried
     */
    public void setFindNumber(int findNumber) {
        this.findNumber = Math.max(0, findNumber);
    }

    /**
     * Sequence of this piece on its excavation ({@code #Site in plains-1}).
     * The player-facing site name is the prefix so two camps can both have a {@code #1}
     * without looking like a hidden global list. Staff serial stays off this label.
     *
     * @param site excavation that owns this find
     * @return {@code #name-n}, or {@code null} when this find has no number yet
     */
    public String publicNumber(Site site) {
        if (findNumber <= 0) {
            return null;
        }
        String name = site == null ? "Site" : site.publicName();
        return "#" + name + "-" + findNumber;
    }

    /**
     * @return who lifted the piece, or who last wounded it into loss; {@code null} if unknown
     */
    public UUID getRecoveredBy() {
        return recoveredBy;
    }

    /**
     * @param recoveredBy recoverer or responsible player, or {@code null}
     */
    public void setRecoveredBy(UUID recoveredBy) {
        this.recoveredBy = recoveredBy;
    }

    /**
     * @return when the piece left the cut, or {@code null} if it is still buried
     */
    public Instant getRecoveredAt() {
        return recoveredAt;
    }

    /**
     * @param recoveredAt when the piece left the cut
     */
    public void setRecoveredAt(Instant recoveredAt) {
        this.recoveredAt = recoveredAt;
    }

    /**
     * @return whether study has revealed rarity, tags, and notes on this row
     */
    public boolean isStudied() {
        return studied;
    }

    /**
     * @param studied whether the piece has been examined at camp
     */
    public void setStudied(boolean studied) {
        this.studied = studied;
    }

    /**
     * @return whether the first lab step has been finished at the cabinet
     */
    public boolean isLabCleaned() {
        return labCleaned;
    }

    /**
     * @param labCleaned whether the piece has been wiped at the cabinet
     */
    public void setLabCleaned(boolean labCleaned) {
        this.labCleaned = labCleaned;
    }

    /**
     * @return whether a signed field sketch has been filed for this piece
     */
    public boolean hasFieldSketch() {
        return fieldSketch;
    }

    /**
     * @param fieldSketch whether a signed field sketch is on file
     */
    public void setFieldSketch(boolean fieldSketch) {
        this.fieldSketch = fieldSketch;
    }

    /**
     * Snapshot of the catalog note copied at study time, so later YAML edits do not rewrite the archive.
     *
     * @return study notes, or {@code null} before study
     */
    public String getStudyNotes() {
        return studyNotes;
    }

    /**
     * @param studyNotes catalog note copied at study time
     */
    public void setStudyNotes(String studyNotes) {
        this.studyNotes = studyNotes;
    }

    /**
     * @return readings filed on this row, in the order they were written
     */
    public List<FindInterpretation> getInterpretations() {
        return interpretations;
    }

    /**
     * A find may carry at most one reading per station question.
     *
     * @param reading new reading
     * @return {@code true} if the list changed
     */
    public boolean addInterpretation(FindInterpretation reading) {
        if (reading == null || reading.interpretationId() == null || reading.interpretationId().isBlank()) {
            return false;
        }
        if (reading.typeId() != null && !reading.typeId().isBlank() && hasType(reading.typeId())) {
            return false;
        }
        if (interpretations.size() >= 3) {
            return false;
        }
        for (FindInterpretation existing : interpretations) {
            if (reading.interpretationId().equals(existing.interpretationId())) {
                return false;
            }
        }
        interpretations.add(reading);
        return true;
    }

    /**
     * @param typeId station question key
     * @return whether that question already has a signed answer
     */
    public boolean hasType(String typeId) {
        if (typeId == null || typeId.isBlank()) {
            return false;
        }
        for (FindInterpretation reading : interpretations) {
            if (typeId.equals(reading.typeId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param interpretationId catalog key to drop
     * @return {@code true} if a reading was removed
     */
    public boolean removeInterpretation(String interpretationId) {
        if (interpretationId == null) {
            return false;
        }
        return interpretations.removeIf(reading -> interpretationId.equals(reading.interpretationId()));
    }

    /**
     * @param interpretationId catalog key
     * @return whether that reading is already on this row
     */
    public boolean hasInterpretation(String interpretationId) {
        if (interpretationId == null) {
            return false;
        }
        for (FindInterpretation reading : interpretations) {
            if (interpretationId.equals(reading.interpretationId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return whether this find has left the cut (lifted or destroyed)
     */
    public boolean hasLeftTheCut() {
        return state == FindState.RECOVERED || state == FindState.LOST;
    }

    /**
     * @return short register status for lore and boards: field catalog, cleaned, sketched, studied, catalogued
     */
    public String catalogStatusLabel() {
        if (state == FindState.LOST) {
            return "Lost in the cut";
        }
        if (!interpretations.isEmpty()) {
            return "Catalogued";
        }
        if (studied) {
            return "Studied";
        }
        if (fieldSketch) {
            return "Sketched";
        }
        if (labCleaned) {
            return "Cleaned";
        }
        return "Field catalog";
    }

    /**
     * @return whether at least one reading has been filed
     */
    public boolean isCatalogued() {
        return !interpretations.isEmpty();
    }

    /** @return connected cells that make up the hidden shape */
    public List<BlockCell> getCells() {
        return cells;
    }

    /**
     * Cells whose matrix has been brushed off. The block stays until the piece is lifted.
     *
     * @return cleaned coordinates
     */
    public Set<BlockCell> getCleanedCells() {
        return cleanedCells;
    }

    /**
     * @param cell a shape cell
     * @return whether this cube no longer sheds recovery dust
     */
    public boolean isCleaned(BlockCell cell) {
        return cleanedCells.contains(cell);
    }

    /**
     * @param cell a still-present fill cell that was just brushed
     */
    public void markCleaned(BlockCell cell) {
        cleanedCells.add(cell);
        brushRemaining.remove(cell);
    }

    /**
     * In-progress brush bar for this cube. Looking away hides the HUD; looking back with the brush
     * restores the same remaining ticks.
     *
     * @return cell → ticks still needed
     */
    public Map<BlockCell, Integer> getBrushRemaining() {
        return brushRemaining;
    }

    /**
     * @param cell a shape cell
     * @return ticks still needed, or {@code null} if dusting has not started
     */
    public Integer brushRemaining(BlockCell cell) {
        return brushRemaining.get(cell);
    }

    /**
     * @param cell cube being dusted
     * @param remaining ticks until this cube is clean; {@code 0} or less clears the entry
     */
    public void setBrushRemaining(BlockCell cell, int remaining) {
        if (cell == null || remaining <= 0) {
            brushRemaining.remove(cell);
            return;
        }
        brushRemaining.put(cell, remaining);
    }

    /**
     * Cells spent by collapsing from above (late pick or vanilla). At most once per cell.
     *
     * @return coordinates already grazed
     */
    public Set<BlockCell> getGrazedCells() {
        return grazedCells;
    }

    /**
     * Cells struck by lifting the find cube itself. At most once per cell.
     *
     * @return coordinates already hit directly
     */
    public Set<BlockCell> getDirectHitCells() {
        return directHitCells;
    }

    /**
     * Spends this cell from above: {@code 100 / n} of the piece. No-op if already grazed or not in the shape.
     *
     * @param cell find cell that was removed from above
     * @return whether conservation changed
     */
    public boolean woundFromAbove(BlockCell cell) {
        return applyWound(cell, grazedCells);
    }

    /**
     * Direct hit on this find cube: {@code 200 / n} of the piece. No-op if already hit or not in the shape.
     *
     * @param cell find cell that was the aimed cube
     * @return whether conservation changed
     */
    public boolean woundDirect(BlockCell cell) {
        return applyWound(cell, directHitCells);
    }

    /**
     * @param cell candidate
     * @param bucket graze or direct set
     * @return whether this was a new wound
     */
    private boolean applyWound(BlockCell cell, Set<BlockCell> bucket) {
        if (cell == null || !cells.contains(cell) || bucket.contains(cell)) {
            return false;
        }
        bucket.add(cell);
        refreshConservation();
        return true;
    }

    /**
     * Recomputes {@code 0–100} from the buried condition minus cell wounds:
     * graze or a cell lost before the dig {@code 100/n}, direct hit {@code 200/n}, clamped.
     */
    public void refreshConservation() {
        int n = Math.max(1, cells.size());
        double share = 100.0 / n;
        double remaining = buriedConservation;
        for (BlockCell cell : cells) {
            if (grazedCells.contains(cell) || priorCells.contains(cell)) {
                remaining -= share;
            }
            if (directHitCells.contains(cell)) {
                remaining -= 2 * share;
            }
        }
        conservation = (int) Math.round(Math.max(0.0, Math.min(100.0, remaining)));
        if (conservation <= 0 && state != FindState.RECOVERED && state != FindState.LOST) {
            state = FindState.LOST;
        }
    }
}
