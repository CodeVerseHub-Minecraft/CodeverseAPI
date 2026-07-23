package net.codeverse.api;

import net.codeverse.api.identity.IdentityService;
import net.codeverse.api.link.LinkService;
import net.codeverse.api.voice.VoiceService;

import java.util.Optional;

/**
 * Entry point to the network's services.
 *
 * Obtained through {@link CodeverseApiProvider}. Services are optional
 * individually because not every server runs every plugin: a minigame backend
 * has identity but no voice, and a lobby has both while hosting neither.
 * Returning an optional forces a consumer to decide what to do when a service
 * is absent, rather than discovering it through a null at an inconvenient time.
 */
public interface CodeverseApi {

    /** Identity resolution. Present wherever the authentication or voice plugin runs. */
    Optional<IdentityService> identity();

    /** Voice restrictions. Present wherever the voice plugin runs. */
    Optional<VoiceService> voice();

    /** External account linking. Present only where the authentication plugin runs. */
    Optional<LinkService> link();

    /** Subscription to network events. Always present. */
    net.codeverse.api.event.EventBus events();

    /**
     * The API version this implementation was built against, as major.minor.
     *
     * Consumers can compare against their own expectation to fail with a clear
     * message rather than a NoSuchMethodError, which is what a version mismatch
     * otherwise looks like from inside a plugin.
     */
    String apiVersion();

    default IdentityService requireIdentity() {
        return identity().orElseThrow(() -> new IllegalStateException(
                "No identity service is registered on this server. The authentication or voice plugin "
                        + "must be installed for identity lookups to work."));
    }

    default VoiceService requireVoice() {
        return voice().orElseThrow(() -> new IllegalStateException(
                "No voice service is registered on this server. The voice plugin must be installed."));
    }

    default LinkService requireLink() {
        return link().orElseThrow(() -> new IllegalStateException(
                "No link service is registered on this server. Account linking is only available where "
                        + "the authentication plugin runs, which is normally the proxy."));
    }
}
