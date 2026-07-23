package net.codeverse.jdbc;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.codeverse.api.identity.Identity;
import net.codeverse.api.identity.IdentityService;
import net.codeverse.api.identity.TrustTier;

import javax.sql.DataSource;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Identity resolution backed directly by the shared database.
 *
 * This is what makes the API's central claim true rather than aspirational. The
 * authentication plugin runs on the proxy, so a backend asking it a question
 * would need a network protocol between two processes. Instead the backend
 * reads the same rows the authentication plugin writes, behind the same
 * interface, and consumer code cannot tell which implementation it received.
 *
 * The trade is deliberate. This implementation is read only and slightly behind
 * whatever the proxy holds in memory, which is acceptable because the data it
 * serves changes rarely. In exchange it keeps working when the authentication
 * plugin is restarting, which a request and response protocol would not.
 *
 * <h2>Threading</h2>
 * Every query runs on the supplied executor, never on the caller's thread. The
 * cached accessor reads memory only and is safe from a server tick.
 */
public final class JdbcIdentityService implements IdentityService {

    private final DataSource dataSource;
    private final Executor executor;
    private final String accountsTable;
    private final Cache<UUID, Optional<Identity>> byMinecraftId;
    private final Cache<String, Optional<Identity>> byUsername;
    private volatile boolean linkageAvailable;

    public JdbcIdentityService(DataSource dataSource,
                               Executor executor,
                               String accountsTable,
                               Duration cacheFor) {
        this.dataSource = dataSource;
        this.executor = executor;
        this.accountsTable = accountsTable;
        Duration ttl = cacheFor == null || cacheFor.isZero() ? Duration.ofMinutes(5) : cacheFor;
        this.byMinecraftId = Caffeine.newBuilder().maximumSize(20_000).expireAfterWrite(ttl).build();
        this.byUsername = Caffeine.newBuilder().maximumSize(20_000).expireAfterWrite(ttl).build();
        this.linkageAvailable = true;
    }

    /**
     * Confirms the accounts table is readable and reports the outcome.
     *
     * Called once at startup so a missing table is a loud message then, rather
     * than every consumer independently discovering it mid incident.
     */
    public boolean probe() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT internal_id, minecraft_id, username, tier FROM " + accountsTable + " LIMIT 1")) {
            statement.executeQuery().close();
            linkageAvailable = true;
        } catch (SQLException unavailable) {
            linkageAvailable = false;
        }
        return linkageAvailable;
    }

    @Override
    public boolean isLinkageAvailable() {
        return linkageAvailable;
    }

    @Override
    public CompletableFuture<Optional<Identity>> byMinecraftId(UUID minecraftId) {
        Optional<Identity> cached = byMinecraftId.getIfPresent(minecraftId);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return CompletableFuture.supplyAsync(() -> {
            Optional<Identity> found = querySingle(
                    "WHERE minecraft_id = ?", statement -> statement.setBytes(1, toBytes(minecraftId)));
            byMinecraftId.put(minecraftId, found);
            found.ifPresent(identity -> byUsername.put(key(identity.username()), found));
            return found;
        }, executor);
    }

    @Override
    public CompletableFuture<Optional<Identity>> byUsername(String username) {
        if (username == null || username.isBlank()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        String key = key(username);
        Optional<Identity> cached = byUsername.getIfPresent(key);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return CompletableFuture.supplyAsync(() -> {
            Optional<Identity> found = querySingle(
                    "WHERE username_lower = ?", statement -> statement.setString(1, key));
            byUsername.put(key, found);
            found.ifPresent(identity -> byMinecraftId.put(identity.minecraftId(), found));
            return found;
        }, executor);
    }

    @Override
    public CompletableFuture<Optional<Identity>> byInternalId(UUID internalId) {
        return CompletableFuture.supplyAsync(() -> querySingle(
                "WHERE internal_id = ? ORDER BY last_login_at DESC LIMIT 1",
                statement -> statement.setBytes(1, toBytes(internalId))), executor);
    }

    @Override
    public CompletableFuture<Optional<Identity>> byDiscordId(String discordId) {
        if (discordId == null || discordId.isBlank()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return CompletableFuture.supplyAsync(() -> querySingle(
                "WHERE discord_id = ? ORDER BY last_login_at DESC LIMIT 1",
                statement -> statement.setString(1, discordId.trim())), executor);
    }

    @Override
    public CompletableFuture<List<Identity>> linkedAccounts(UUID internalId) {
        return CompletableFuture.supplyAsync(() -> {
            List<Identity> found = new ArrayList<>();
            String sql = selectClause() + " WHERE internal_id = ? ORDER BY last_login_at DESC";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setBytes(1, toBytes(internalId));
                try (ResultSet results = statement.executeQuery()) {
                    while (results.next()) {
                        found.add(read(results));
                    }
                }
            } catch (SQLException failure) {
                // Completing exceptionally rather than with an empty list. An
                // empty list means this person has no accounts, which is a very
                // different claim from being unable to find out.
                throw new IdentityLookupException("Could not read linked accounts for " + internalId, failure);
            }
            return found;
        }, executor);
    }

    @Override
    public Optional<Identity> cachedByMinecraftId(UUID minecraftId) {
        Optional<Identity> cached = byMinecraftId.getIfPresent(minecraftId);
        return cached == null ? Optional.empty() : cached;
    }

    @Override
    public CompletableFuture<Void> preload(Collection<UUID> minecraftIds) {
        if (minecraftIds == null || minecraftIds.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> {
            // One statement for the whole batch. Warming a full server one
            // query at a time is how a harmless cache warm becomes a stall.
            List<UUID> pending = minecraftIds.stream()
                    .filter(id -> byMinecraftId.getIfPresent(id) == null)
                    .toList();
            if (pending.isEmpty()) {
                return;
            }
            String placeholders = String.join(",", java.util.Collections.nCopies(pending.size(), "?"));
            String sql = selectClause() + " WHERE minecraft_id IN (" + placeholders + ")";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int i = 0; i < pending.size(); i++) {
                    statement.setBytes(i + 1, toBytes(pending.get(i)));
                }
                try (ResultSet results = statement.executeQuery()) {
                    while (results.next()) {
                        Identity identity = read(results);
                        Optional<Identity> wrapped = Optional.of(identity);
                        byMinecraftId.put(identity.minecraftId(), wrapped);
                        byUsername.put(key(identity.username()), wrapped);
                    }
                }
            } catch (SQLException failure) {
                throw new IdentityLookupException("Could not preload identities", failure);
            }
        }, executor);
    }

    @Override
    public void invalidate(UUID minecraftId) {
        Optional<Identity> cached = byMinecraftId.getIfPresent(minecraftId);
        if (cached != null) {
            cached.ifPresent(identity -> byUsername.invalidate(key(identity.username())));
        }
        byMinecraftId.invalidate(minecraftId);
    }

    /** Drops every cached identity, for use when another server reports a bulk change. */
    public void invalidateAll() {
        byMinecraftId.invalidateAll();
        byUsername.invalidateAll();
    }

    private Optional<Identity> querySingle(String whereClause, StatementBinder binder) {
        String sql = selectClause() + " " + whereClause;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? Optional.of(read(results)) : Optional.empty();
            }
        } catch (SQLException failure) {
            throw new IdentityLookupException("Identity lookup failed", failure);
        }
    }

    private String selectClause() {
        return "SELECT internal_id, minecraft_id, username, tier, password_hash, totp_secret, "
                + "registered_at, last_login_at, discord_id FROM " + accountsTable;
    }

    private static Identity read(ResultSet results) throws SQLException {
        String storedTier = results.getString("tier");
        // An unrecognised tier is treated as the least trusted rather than
        // rejected. A backend running an older release than the proxy must fail
        // closed on a tier it has never heard of, not refuse to serve the player.
        TrustTier tier = TrustTier.parse(storedTier).orElse(TrustTier.CRACKED);

        return Identity.builder(
                        fromBytes(results.getBytes("internal_id")),
                        fromBytes(results.getBytes("minecraft_id")),
                        results.getString("username"),
                        tier)
                .registeredAtMillis(results.getLong("registered_at"))
                .lastLoginAtMillis(results.getLong("last_login_at"))
                .totpEnrolled(results.getString("totp_secret") != null)
                .discordId(results.getString("discord_id"))
                .build();
    }

    private static String key(String username) {
        return username.toLowerCase(Locale.ROOT);
    }

    public static byte[] toBytes(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }

    public static UUID fromBytes(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    @FunctionalInterface
    private interface StatementBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    /**
     * Signals that a lookup could not be answered.
     *
     * Distinct from an empty result on purpose. Consumers deciding access must
     * be able to tell "this person does not exist" from "I could not find out",
     * because the safe response to the second is refusal while the safe
     * response to the first may not be.
     */
    public static final class IdentityLookupException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public IdentityLookupException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
