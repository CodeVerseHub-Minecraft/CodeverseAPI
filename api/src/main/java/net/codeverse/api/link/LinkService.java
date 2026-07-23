package net.codeverse.api.link;

import net.codeverse.api.identity.Identity;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Links an in game identity to an external one, currently Discord.
 *
 * Linking is what makes the DISCORD_LINKED trust tier meaningful: an offline
 * account that is not a paid Minecraft account, but is tied to a community
 * identity that can be held accountable. Without a link that tier has no
 * members and the middle of the trust ladder is empty.
 */
public interface LinkService {

    /**
     * Issues a code for the player to present through Discord.
     *
     * Any code previously issued to this identity is invalidated, so a player
     * who generates a second code cannot be confused about which is live, and
     * an abandoned code cannot be redeemed later by whoever saw it.
     */
    CompletableFuture<LinkCode> issueCode(UUID internalId, Duration lifetime);

    /**
     * Redeems a code, creating the link.
     *
     * Completes with the identity as it stands after linking, so the returned
     * value carries the new Discord id and any tier promotion that resulted.
     * Returning the pre link state would force every caller to perform a second
     * lookup to see what their own call did, and a caller that forgot would
     * silently act on stale data.
     *
     * Completes with empty when the code is unknown, expired or already used.
     * The three are deliberately indistinguishable to the caller: telling an
     * unauthenticated party which codes exist turns the code space into
     * something worth guessing at.
     */
    CompletableFuture<Optional<Identity>> redeem(String code, String discordId);

    /** Removes a link. Returns whether one existed. */
    CompletableFuture<Boolean> unlink(UUID internalId);

    /** Removes a link by its Discord side, for use when someone leaves the community. */
    CompletableFuture<Boolean> unlinkByDiscordId(String discordId);

    /** The Discord snowflake linked to an identity, empty when unlinked. */
    CompletableFuture<Optional<String>> discordIdOf(UUID internalId);

    /** Discards codes past their lifetime. Implementations may also do this on a timer. */
    CompletableFuture<Integer> purgeExpiredCodes();
}
