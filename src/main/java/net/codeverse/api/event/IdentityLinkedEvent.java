package net.codeverse.api.event;

import net.codeverse.api.identity.Identity;

import java.time.Instant;
import java.util.Objects;

/**
 * An external account was linked to a network identity.
 *
 * Published when a link code is redeemed. The identity carried is the state
 * after linking, so a listener does not need a second lookup to see the new
 * tier.
 */
public record IdentityLinkedEvent(
        Identity identity,
        String discordId,
        Instant occurredAt,
        boolean remote
) implements CodeverseEvent {

    public IdentityLinkedEvent {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (discordId == null || discordId.isBlank()) {
            throw new IllegalArgumentException("discordId cannot be blank");
        }
    }
}
