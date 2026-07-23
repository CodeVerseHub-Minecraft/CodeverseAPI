package net.codeverse.api.identity;

import java.util.Locale;
import java.util.Optional;

/**
 * How much the network trusts a connection's claimed identity.
 *
 * This enum is the reason the API exists. Before it, the authentication plugin
 * held tiers as an enum while the voice plugin compared configured strings, and
 * nothing stopped the two from drifting: adding a tier on one side silently
 * changed behaviour on the other. Both now depend on this declaration, so a new
 * tier is a compile time event rather than a runtime surprise.
 *
 * Ordered from least to most trusted. Consumers should treat the ordinal as
 * meaningful for comparison but must not persist it, since inserting a tier
 * would shift every value stored that way. Persist {@link #name()} instead.
 */
public enum TrustTier {

    /**
     * Offline account whose username was not verified against Mojang. Could be
     * anyone claiming any unregistered name.
     */
    CRACKED,

    /**
     * Offline account whose owner proved control of a Discord identity in the
     * community. Not a paid Minecraft account, but tied to something that can
     * be held accountable.
     */
    DISCORD_LINKED,

    /**
     * Bedrock player authenticated by Microsoft through the Bedrock login flow.
     * A cryptographically verified origin.
     */
    BEDROCK,

    /**
     * Paid Java account verified against Mojang's session servers. The username
     * is cryptographically proven to belong to this player.
     */
    PREMIUM;

    /**
     * Whether this tier may hold network permissions beyond the baseline.
     *
     * The founding rule of the network is expressed here rather than in any
     * plugin's configuration, so that a mistyped group name cannot grant
     * elevated access to an account whose identity was never proven.
     */
    public boolean eligibleForElevatedPermissions() {
        return this != CRACKED;
    }

    /** Whether the account's origin was cryptographically proven. */
    public boolean isVerifiedOrigin() {
        return this == PREMIUM || this == BEDROCK;
    }

    /** Whether accounts in this tier authenticate with a stored password. */
    public boolean requiresPassword() {
        return this == CRACKED || this == DISCORD_LINKED;
    }

    /** Whether this tier is at least as trusted as another. */
    public boolean isAtLeast(TrustTier other) {
        return other != null && ordinal() >= other.ordinal();
    }

    /**
     * Parses a stored tier name.
     *
     * Returns empty rather than throwing on an unrecognised value, because a
     * consumer reading a tier written by a newer release must be able to treat
     * it as untrusted rather than crash. Failing closed on an unknown tier is
     * the correct behaviour; failing loudly is not.
     */
    public static Optional<TrustTier> parse(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(name.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
        }
    }
}
