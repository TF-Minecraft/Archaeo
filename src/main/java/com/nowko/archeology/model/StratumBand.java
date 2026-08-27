package com.nowko.archeology.model;

/**
 * Whether a catalog stratum exists on this site, and the Y range it occupies.
 */
public class StratumBand {
    private String id;
    private boolean present;
    private boolean disturbed;
    private int minY;
    private int maxY;

    /** @return catalog stratum id such as {@code I} */
    public String getId() {
        return id;
    }

    /** @param id catalog stratum id */
    public void setId(String id) {
        this.id = id;
    }

    /** @return whether this layer exists on the site */
    public boolean isPresent() {
        return present;
    }

    /** @param present whether this layer exists on the site */
    public void setPresent(boolean present) {
        this.present = present;
    }

    /** @return whether the layer is mixed (generation flag) */
    public boolean isDisturbed() {
        return disturbed;
    }

    /** @param disturbed whether the layer is mixed */
    public void setDisturbed(boolean disturbed) {
        this.disturbed = disturbed;
    }

    /** @return inclusive lower Y of the band */
    public int getMinY() {
        return minY;
    }

    /** @param minY inclusive lower Y of the band */
    public void setMinY(int minY) {
        this.minY = minY;
    }

    /** @return inclusive upper Y of the band */
    public int getMaxY() {
        return maxY;
    }

    /** @param maxY inclusive upper Y of the band */
    public void setMaxY(int maxY) {
        this.maxY = maxY;
    }
}
