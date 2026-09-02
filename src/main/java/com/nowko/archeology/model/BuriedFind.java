package com.nowko.archeology.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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
    private boolean damaged;
    private int conservation = 100;
    private final List<BlockCell> cells = new ArrayList<>();
    private final Set<BlockCell> cleanedCells = new LinkedHashSet<>();

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

    /** @return whether recovery will yield a damaged item */
    public boolean isDamaged() {
        return damaged;
    }

    /** @param damaged whether recovery will yield a damaged item */
    public void setDamaged(boolean damaged) {
        this.damaged = damaged;
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
    }
}
