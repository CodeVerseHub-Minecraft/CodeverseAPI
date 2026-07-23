package net.codeverse.api.identity;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A person on the network.
 *
 * The distinction this type exists to enforce: {@link #internalId()} is who
 * someone is, {@link #minecraftId()} is merely where their packets go. Every
 * plugin that stores data about a player should key it on the internal id.
 * Keying on the Minecraft uuid means a person with a linked Java and Bedrock
 * account appears as two people, and any restriction placed on one is shed by
 * connecting with the other.
 *
 * The accessors are deliberately named so that choosing wrongly reads wrongly.
 *
 * @param internalId    canonical network id, stable across every linked account
 * @param minecraftId   uuid of the account this instance describes
 * @param username      in game name including any prefix
 * @param tier          trust tier governing permission eligibility
 * @param registeredAt  when a password was first set, empty when never
 * @param lastLoginAt   most recent successful login, empty when never
 * @param totpEnrolled  whether a second factor is active
 * @param discordId     linked Discord snowflake, empty when unlinked
 */
public record Identity(
        UUID internalId,
        UUID minecraftId,
        String username,
        TrustTier tier,
        Optional<Instant> registeredAt,
        Optional<Instant> lastLoginAt,
        boolean totpEnrolled,
        Optional<String> discordId
) {
    public Identity {
        Objects.requireNonNull(internalId, "internalId");
        Objects.requireNonNull(minecraftId, "minecraftId");
        Objects.requireNonNull(tier, "tier");
        Objects.requireNonNull(registeredAt, "registeredAt");
        Objects.requireNonNull(lastLoginAt, "lastLoginAt");
        Objects.requireNonNull(discordId, "discordId");
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username cannot be blank");
        }
    }

    public boolean isRegistered() {
        return registeredAt.isPresent();
    }

    public boolean isVerifiedOrigin() {
        return tier.isVerifiedOrigin();
    }

    public boolean hasDiscordLink() {
        return discordId.isPresent();
    }

    /** Whether this identity may hold permissions beyond the baseline. */
    public boolean eligibleForElevatedPermissions() {
        return tier.eligibleForElevatedPermissions();
    }

    /** Builder for implementations, keeping the record's argument order out of consumer code. */
    public static Builder builder(UUID internalId, UUID minecraftId, String username, TrustTier tier) {
        return new Builder(internalId, minecraftId, username, tier);
    }

    public static final class Builder {
        private final UUID internalId;
        private final UUID minecraftId;
        private final String username;
        private final TrustTier tier;
        private Instant registeredAt;
        private Instant lastLoginAt;
        private boolean totpEnrolled;
        private String discordId;

        private Builder(UUID internalId, UUID minecraftId, String username, TrustTier tier) {
            this.internalId = internalId;
            this.minecraftId = minecraftId;
            this.username = username;
            this.tier = tier;
        }

        public Builder registeredAt(Instant value) {
            this.registeredAt = value;
            return this;
        }

        /** Accepts epoch millis, treating 0 as never, which is how storage encodes it. */
        public Builder registeredAtMillis(long millis) {
            this.registeredAt = millis > 0L ? Instant.ofEpochMilli(millis) : null;
            return this;
        }

        public Builder lastLoginAt(Instant value) {
            this.lastLoginAt = value;
            return this;
        }

        public Builder lastLoginAtMillis(long millis) {
            this.lastLoginAt = millis > 0L ? Instant.ofEpochMilli(millis) : null;
            return this;
        }

        public Builder totpEnrolled(boolean value) {
            this.totpEnrolled = value;
            return this;
        }

        public Builder discordId(String value) {
            this.discordId = value == null || value.isBlank() ? null : value;
            return this;
        }

        public Identity build() {
            return new Identity(internalId, minecraftId, username, tier,
                    Optional.ofNullable(registeredAt),
                    Optional.ofNullable(lastLoginAt),
                    totpEnrolled,
                    Optional.ofNullable(discordId));
        }
    }
}
