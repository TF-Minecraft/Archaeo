package com.nowko.archeology.model;

/**
 * Player-facing lifecycle of a site.
 */
public enum SiteStatus {
    /** Registered but no camp yet. */
    HIDDEN,
    /** Camp planted; excavation in progress. */
    ESTABLISHED,
    /** Finds recovered; the camp still stands as the on-site archive. */
    EXHAUSTED,
    /** Camp taken down; the dossier remains for a field book. */
    CLOSED
}
