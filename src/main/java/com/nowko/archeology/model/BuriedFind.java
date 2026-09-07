package com.nowko.archeology.model;

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
    private String stratumId;
    private FindState state = FindState.HIDDEN;
    private int buriedConservation = 100;
    private int conservation = 100;
    private final List<BlockCell> cells = new ArrayList<>();
    private final Set<BlockCell> cleanedCells = new LinkedHashSet<>();
    private final Set<BlockCell> grazedCells = new LinkedHashSet<>();
    private final Set<BlockCell> directHitCells = new LinkedHashSet<>();
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
     * graze {@code 100/n}, direct {@code 200/n}, clamped.
     */
    public void refreshConservation() {
        int n = Math.max(1, cells.size());
        double share = 100.0 / n;
        double remaining = buriedConservation;
        for (BlockCell cell : cells) {
            if (grazedCells.contains(cell)) {
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
