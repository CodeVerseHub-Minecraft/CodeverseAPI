package net.codeverse.api.voice;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A restriction on someone's ability to speak in voice chat.
 *
 * Keyed by internal id rather than Minecraft uuid, so it follows the person
 * across every account they have linked. Restricting the account instead would
 * mean a network that accepts both Java and Bedrock hands every restricted
 * player an obvious way out.
 *
 * @param internalId  identity the restriction applies to
 * @param reason      staff supplied reason, shown to the person
 * @param issuedBy    internal id of the issuing staff member, empty for console
 * @param issuedAt    when it was created
 * @param expiresAt   when it lapses, empty when permanent
 * @param active      false once lifted, retained for audit rather than deleted
 */
public record VoiceRestriction(
        UUID internalId,
        String reason,
        Optional<UUID> issuedBy,
        Instant issuedAt,
        Optional<Instant> expiresAt,
        boolean active
) {
    public VoiceRestriction {
        Objects.requireNonNull(internalId, "internalId");
        Objects.requireNonNull(issuedBy, "issuedBy");
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason cannot be blank");
        }
        if (expiresAt.isPresent() && !expiresAt.get().isAfter(issuedAt)) {
            throw new IllegalArgumentException("expiresAt must be after issuedAt");
        }
    }

    public boolean isPermanent() {
        return expiresAt.isEmpty();
    }

    public boolean hasExpired(Instant now) {
        return expiresAt.isPresent() && !now.isBefore(expiresAt.get());
    }

    /**
     * Whether this currently prevents speaking.
     *
     * Expiry is evaluated against the clock rather than trusting a scheduled
     * sweep, so a restriction lapses on time even if the sweep is late or the
     * server was offline when it should have run.
     */
    public boolean isEnforceable(Instant now) {
        return active && !hasExpired(now);
    }

    public Optional<Duration> remaining(Instant now) {
        if (isPermanent()) {
            return Optional.empty();
        }
        Instant end = expiresAt.orElseThrow();
        return Optional.of(now.isAfter(end) ? Duration.ZERO : Duration.between(now, end));
    }
}
