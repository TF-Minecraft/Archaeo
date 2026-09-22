package net.tfminecraft.archaeo.model;

import java.time.Instant;
import java.util.UUID;

/**
 * One signed answer to a station question. It lives on the excavation archive, not on the object.
 */
public final class FindInterpretation {
    private final String typeId;
    private final String interpretationId;
    private final UUID author;
    private final Instant recordedAt;

    /**
     * @param typeId question key from {@code interpretations.yml}
     * @param interpretationId phrase key from that type's pool
     * @param author who signed the reading
     * @param recordedAt when it was filed
     */
    public FindInterpretation(
            String typeId,
            String interpretationId,
            UUID author,
            Instant recordedAt
    ) {
        this.typeId = typeId;
        this.interpretationId = interpretationId;
        this.author = author;
        this.recordedAt = recordedAt;
    }

    /**
     * @return question key, or {@code null} on a legacy row
     */
    public String typeId() {
        return typeId;
    }

    /**
     * @return phrase key from {@code interpretations.yml}
     */
    public String interpretationId() {
        return interpretationId;
    }

    /**
     * @return who signed the reading
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
}
