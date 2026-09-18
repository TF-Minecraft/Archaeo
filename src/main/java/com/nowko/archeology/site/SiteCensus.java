package com.nowko.archeology.site;

import com.nowko.archeology.model.SiteStatus;

/**
 * Snapshot of how many dossiers sit in each lifecycle bucket. Used by staff census, not by players.
 *
 * @param total every loaded site
 * @param hidden registered ruins with no camp yet
 * @param established live excavations
 * @param exhausted finished cuts whose camp still stands
 * @param closed camps taken down; the dossier remains
 */
public record SiteCensus(int total, int hidden, int established, int exhausted, int closed) {

    /**
     * Sites that have been claimed at least once. Closed dossiers still count: they were excavations.
     *
     * @return established + exhausted + closed
     */
    public int excavations() {
        return established + exhausted + closed;
    }

    /**
     * @param status lifecycle bucket
     * @return count for that status
     */
    public int of(SiteStatus status) {
        return switch (status) {
            case HIDDEN -> hidden;
            case ESTABLISHED -> established;
            case EXHAUSTED -> exhausted;
            case CLOSED -> closed;
        };
    }
}
