package com.nowko.archeology.model;

import java.time.Instant;
import java.util.UUID;

/**
 * One reading a player attached to a find. It lives on the excavation archive, not on the object.
 */
public final class FindInterpretation {
    private final String interpretationId;
    private final UUID author;
    private final Instant recordedAt;
    private final InterpretationConfidence confidence;

    /**
     * @param interpretationId key from {@code interpretations.yml}
     * @param author who wrote the reading
     * @param recordedAt when it was filed
     * @param confidence how sure they said they were
     */
    public FindInterpretation(
            String interpretationId,
            UUID author,
            Instant recordedAt,
            InterpretationConfidence confidence
    ) {
        this.interpretationId = interpretationId;
        this.author = author;
        this.recordedAt = recordedAt;
        this.confidence = confidence == null ? InterpretationConfidence.MEDIUM : confidence;
    }

    /**
     * @return key from {@code interpretations.yml}
     */
    public String interpretationId() {
        return interpretationId;
    }

    /**
     * @return who wrote the reading
     */
    public UUID author() {
        return author;
    }

    /**
     * @return when it was filed
     */
    public Instant recordedAt() {
        return recordedAt;
    }

    /**
     * @return how sure they said they were
     */
    public InterpretationConfidence confidence() {
        return confidence;
    }
}
