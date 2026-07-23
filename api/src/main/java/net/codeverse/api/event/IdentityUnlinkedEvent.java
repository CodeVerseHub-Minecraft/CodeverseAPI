package net.codeverse.api.event;

import net.codeverse.api.identity.Identity;

import java.time.Instant;
import java.util.Objects;

/** An external account link was removed. */
public record IdentityUnlinkedEvent(
        Identity identity,
        String discordId,
        Instant occurredAt,
        boolean remote
) implements CodeverseEvent {

    public IdentityUnlinkedEvent {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(discordId, "discordId");
    }
}
