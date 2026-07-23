package net.codeverse.api.event;

import net.codeverse.api.identity.Identity;
import net.codeverse.api.identity.TrustTier;

import java.time.Instant;
import java.util.Objects;

/**
 * Someone's trust tier changed.
 *
 * The case worth handling is a promotion into DISCORD_LINKED, which turns an
 * account that could hold no permissions into one that can. Any plugin caching
 * permission decisions should treat this as an invalidation.
 */
public record TrustTierChangedEvent(
        Identity identity,
        TrustTier previous,
        TrustTier current,
        Instant occurredAt,
        boolean remote
) implements CodeverseEvent {

    public TrustTierChangedEvent {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    /** Whether the change grants access that was previously refused. */
    public boolean isPromotion() {
        return current.ordinal() > previous.ordinal();
    }
}
