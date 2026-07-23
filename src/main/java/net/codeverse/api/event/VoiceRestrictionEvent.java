package net.codeverse.api.event;

import net.codeverse.api.voice.VoiceRestriction;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A voice restriction was applied, lifted or expired.
 *
 * Carries the internal id separately from the restriction so that a lift can be
 * reported without inventing a restriction record for something that no longer
 * exists.
 */
public record VoiceRestrictionEvent(
        UUID internalId,
        Type type,
        VoiceRestriction restriction,
        Instant occurredAt,
        boolean remote
) implements CodeverseEvent {

    public enum Type {
        APPLIED,
        LIFTED,
        EXPIRED
    }

    public VoiceRestrictionEvent {
        Objects.requireNonNull(internalId, "internalId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (type == Type.APPLIED && restriction == null) {
            throw new IllegalArgumentException("an applied restriction must carry the restriction");
        }
    }
}
