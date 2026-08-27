package com.nowko.archeology.model;

/**
 * Outcome of one prospecting sample. Confirmation is required before a camp can be planted.
 */
public enum ProspectResult {
    /** No registered ruin, or too few samples. */
    INSUFFICIENT,
    /** Traces of activity; keep sampling. */
    WEAK,
    /** Almost enough samples to confirm. */
    POSSIBLE,
    /** Enough distinct points; excavation kit may be used later. */
    CONFIRMED
}
