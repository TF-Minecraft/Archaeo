package com.nowko.archeology.model;

import java.util.ArrayList;
import java.util.List;
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
    private final List<BlockCell> cells = new ArrayList<>();

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

    /** @return connected cells that make up the hidden shape */
    public List<BlockCell> getCells() {
        return cells;
    }
}
