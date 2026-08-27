package com.nowko.archeology.model;

/**
 * Player-facing lifecycle of a site.
 */
public enum SiteStatus {
    /** Registered but no camp yet. */
    HIDDEN,
    /** Camp planted; excavation in progress. */
    ESTABLISHED,
    /** Finds recovered; site remains as a record. */
    EXHAUSTED
}
