package net.codeverse.api;

import java.util.Optional;

/**
 * Static access to the network API.
 *
 * Follows the pattern LuckPerms uses, for the same reason: a plugin needing the
 * API often cannot receive it by injection, because the platforms involved here
 * offer three different and incompatible ways of doing that. A single static
 * holder works identically on Paper, Velocity and anything added later.
 *
 * Registration is performed by whichever plugin provides the services. Consumer
 * plugins only ever read.
 */
public final class CodeverseApiProvider {

    private static volatile CodeverseApi instance;

    private CodeverseApiProvider() {
    }

    /**
     * The registered API, or empty when no providing plugin has loaded yet.
     *
     * Consumers should prefer this to {@link #get()} during their own startup,
     * since plugin load order is not guaranteed and an absent API at that point
     * is normal rather than exceptional.
     */
    public static Optional<CodeverseApi> find() {
        return Optional.ofNullable(instance);
    }

    /**
     * The registered API.
     *
     * @throws IllegalStateException when nothing has registered, which means
     *         either no providing plugin is installed or the caller ran before
     *         one finished loading
     */
    public static CodeverseApi get() {
        CodeverseApi current = instance;
        if (current == null) {
            throw new IllegalStateException(
                    "The Codeverse API has not been registered. Ensure a providing plugin is installed, "
                            + "and that this call happens after it has enabled.");
        }
        return current;
    }

    public static boolean isAvailable() {
        return instance != null;
    }

    /**
     * Registers the implementation. Called by providing plugins only.
     *
     * Replacing an existing registration is permitted so that a plugin reload
     * leaves a working API rather than a stale one, but it is worth a provider
     * logging when it happens, since two providers competing is a
     * misconfiguration rather than a supported arrangement.
     */
    public static void register(CodeverseApi api) {
        if (api == null) {
            throw new IllegalArgumentException("api cannot be null");
        }
        instance = api;
    }

    /** Clears the registration. Called by providing plugins on shutdown. */
    public static void unregister(CodeverseApi api) {
        // Compared by identity so a plugin shutting down after another has
        // already taken over does not clear the live registration.
        if (instance == api) {
            instance = null;
        }
    }
}
