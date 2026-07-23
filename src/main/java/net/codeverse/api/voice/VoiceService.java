package net.codeverse.api.voice;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Reads and applies voice chat restrictions.
 *
 * Available on every server running the voice plugin, including those that do
 * not host voice chat themselves. A lobby can therefore answer "is this person
 * restricted and for how long" without any audio being present, which is what
 * lets a scoreboard show the state of something happening elsewhere.
 *
 * Threading and failure semantics match {@link net.codeverse.api.identity.IdentityService}:
 * futures may do I/O and must not be joined on a server thread, the cached
 * variants are the only ones safe from a tick, and a lookup that cannot be
 * answered completes exceptionally rather than pretending nothing was found.
 */
public interface VoiceService {

    /** The active restriction on an identity, empty when unrestricted. */
    CompletableFuture<Optional<VoiceRestriction>> activeRestriction(UUID internalId);

    /**
     * The cached restriction, without touching storage.
     *
     * Safe from a server thread. Returns empty both when unrestricted and when
     * nothing is cached; consumers needing to tell those apart should use the
     * asynchronous form.
     */
    Optional<VoiceRestriction> cachedRestriction(UUID internalId);

    /**
     * Applies a restriction.
     *
     * @param duration how long it lasts, or empty for permanent
     */
    CompletableFuture<VoiceRestriction> restrict(UUID internalId,
                                                 String reason,
                                                 UUID issuedBy,
                                                 Optional<Duration> duration);

    /** Lifts every active restriction on an identity. Completes with whether any existed. */
    CompletableFuture<Boolean> lift(UUID internalId, UUID liftedBy);

    /** Past restrictions, newest first, including lifted and expired ones. */
    CompletableFuture<List<VoiceRestriction>> history(UUID internalId, int limit);

    /**
     * Whether a connecting account may speak, and why not when it may not.
     *
     * Evaluates trust tier, permission and restriction together, in the order
     * that makes an unresolved identity a refusal rather than an oversight.
     */
    CompletableFuture<VoiceAccess> evaluate(UUID minecraftId);

    /** The cached decision, safe from a server thread. */
    Optional<VoiceAccess> cachedEvaluate(UUID minecraftId);

    /** Whether this server hosts voice chat, as opposed to only reporting on it. */
    boolean isEnforcing();
}
