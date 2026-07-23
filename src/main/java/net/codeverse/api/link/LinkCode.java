package net.codeverse.api.link;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A short lived one time code proving control of an in game account.
 *
 * Exists because a Discord bot cannot be trusted to assert which Minecraft
 * account belongs to which Discord user. If linking were a direct write, one
 * leaked bot token would be account takeover for every player on the network.
 *
 * The flow proves both sides. The player generates a code in game, which proves
 * they control the account. They present it through Discord, which proves they
 * control the Discord identity. Only the pairing of the two creates a link.
 *
 * Codes are single use, short lived, and worthless once redeemed or expired.
 *
 * @param code        the value the player is shown, unambiguous by construction
 * @param internalId  identity that generated it
 * @param issuedAt    when it was created
 * @param expiresAt   when it stops being redeemable
 */
public record LinkCode(String code, UUID internalId, Instant issuedAt, Instant expiresAt) {

    public LinkCode {
        Objects.requireNonNull(internalId, "internalId");
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code cannot be blank");
        }
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("expiresAt must be after issuedAt");
        }
    }

    public boolean hasExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean isRedeemable(Instant now) {
        return !hasExpired(now);
    }
}
