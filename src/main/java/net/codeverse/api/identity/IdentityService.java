package net.codeverse.api.identity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Resolves who someone is.
 *
 * Implementations differ by where they run and consumers do not need to know
 * which they have. On the proxy the authentication plugin answers from memory;
 * on a backend the implementation reads the shared database. Both satisfy this
 * contract, which is what keeps the cross process problem out of consumer code.
 *
 * <h2>Threading</h2>
 * Every method returning a {@link CompletableFuture} may perform network or
 * disk work and must never be joined on a server thread. The methods prefixed
 * with cached are the only ones safe to call from a tick: they read an in
 * memory view and return empty rather than blocking when nothing is cached.
 *
 * Futures complete on an implementation owned executor. Consumers that need to
 * touch the game after a result should hand back to their platform scheduler
 * explicitly rather than assuming the completing thread is safe.
 *
 * <h2>Failure</h2>
 * A lookup that cannot be answered completes exceptionally rather than
 * completing with an empty optional. Empty means the identity does not exist;
 * an exception means the question could not be answered. Conflating the two is
 * how a storage outage silently becomes an authorisation decision.
 */
public interface IdentityService {

    /** The identity behind a connecting account. */
    CompletableFuture<Optional<Identity>> byMinecraftId(UUID minecraftId);

    /** The identity behind an in game name, including any prefix. */
    CompletableFuture<Optional<Identity>> byUsername(String username);

    /** Any one account belonging to an internal id, for display purposes. */
    CompletableFuture<Optional<Identity>> byInternalId(UUID internalId);

    /** The identity linked to a Discord snowflake, empty when unlinked. */
    CompletableFuture<Optional<Identity>> byDiscordId(String discordId);

    /**
     * Every account linked to one person, including the one asked about.
     * A person with only one account yields a single element.
     */
    CompletableFuture<List<Identity>> linkedAccounts(UUID internalId);

    /**
     * The cached identity for a connecting account, without touching storage.
     *
     * Safe to call from a server thread. Returns empty when nothing is cached,
     * which callers should render as a waiting state rather than as an absent
     * account. Placeholders and scoreboards are the intended consumers.
     */
    Optional<Identity> cachedByMinecraftId(UUID minecraftId);

    /** Cached tier only, for the common case of a permission style check. */
    default Optional<TrustTier> cachedTier(UUID minecraftId) {
        return cachedByMinecraftId(minecraftId).map(Identity::tier);
    }

    /**
     * Whether an account is at least as trusted as the given tier.
     *
     * Completes with false when the identity is unknown, so an unresolvable
     * account is never treated as meeting a trust requirement.
     */
    default CompletableFuture<Boolean> isAtLeast(UUID minecraftId, TrustTier minimum) {
        return byMinecraftId(minecraftId)
                .thenApply(identity -> identity.map(value -> value.tier().isAtLeast(minimum)).orElse(false));
    }

    /** Warms the cache for accounts expected to be looked up shortly. */
    CompletableFuture<Void> preload(Collection<UUID> minecraftIds);

    /** Drops any cached view of an account, forcing the next lookup to read storage. */
    void invalidate(UUID minecraftId);

    /**
     * Whether this implementation can resolve network identities at all.
     *
     * False means it is degraded to treating each Minecraft account as its own
     * person, which happens when the authentication plugin's storage is
     * unreachable. Consumers that rely on restrictions following a person
     * across linked accounts should surface this rather than proceed quietly.
     */
    boolean isLinkageAvailable();
}
