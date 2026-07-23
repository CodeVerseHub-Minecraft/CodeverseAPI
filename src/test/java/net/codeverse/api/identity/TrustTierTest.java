package net.codeverse.api.identity;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TrustTier is the contract both plugins compile against, so its behaviour is
 * pinned here rather than left to each consumer's interpretation. Drift between
 * two independent readings of the same concept is the problem this API exists
 * to remove.
 */
class TrustTierTest {

    @Test
    void onlyCrackedIsBarredFromElevatedPermissions() {
        assertFalse(TrustTier.CRACKED.eligibleForElevatedPermissions());
        assertTrue(TrustTier.DISCORD_LINKED.eligibleForElevatedPermissions());
        assertTrue(TrustTier.BEDROCK.eligibleForElevatedPermissions());
        assertTrue(TrustTier.PREMIUM.eligibleForElevatedPermissions());
    }

    @Test
    void verifiedOriginMeansCryptographicallyProven() {
        assertTrue(TrustTier.PREMIUM.isVerifiedOrigin());
        assertTrue(TrustTier.BEDROCK.isVerifiedOrigin());
        // A Discord link proves a community identity, not the Minecraft account.
        assertFalse(TrustTier.DISCORD_LINKED.isVerifiedOrigin());
        assertFalse(TrustTier.CRACKED.isVerifiedOrigin());
    }

    @Test
    void onlyOfflineTiersHoldAPassword() {
        assertTrue(TrustTier.CRACKED.requiresPassword());
        assertTrue(TrustTier.DISCORD_LINKED.requiresPassword());
        assertFalse(TrustTier.BEDROCK.requiresPassword());
        assertFalse(TrustTier.PREMIUM.requiresPassword());
    }

    @Test
    void orderingRunsFromLeastToMostTrusted() {
        assertTrue(TrustTier.PREMIUM.isAtLeast(TrustTier.CRACKED));
        assertTrue(TrustTier.BEDROCK.isAtLeast(TrustTier.DISCORD_LINKED));
        assertTrue(TrustTier.CRACKED.isAtLeast(TrustTier.CRACKED));
        assertFalse(TrustTier.CRACKED.isAtLeast(TrustTier.PREMIUM));
        assertFalse(TrustTier.DISCORD_LINKED.isAtLeast(TrustTier.BEDROCK));
    }

    @Test
    void isAtLeastTreatsNullAsUnsatisfiable() {
        assertFalse(TrustTier.PREMIUM.isAtLeast(null));
    }

    @Test
    void parsingIsForgivingAboutCaseAndPadding() {
        assertEquals(Optional.of(TrustTier.PREMIUM), TrustTier.parse("premium"));
        assertEquals(Optional.of(TrustTier.BEDROCK), TrustTier.parse("  Bedrock "));
        assertEquals(Optional.of(TrustTier.DISCORD_LINKED), TrustTier.parse("DISCORD_LINKED"));
    }

    @Test
    void unknownTierParsesToEmptyRatherThanThrowing() {
        // A consumer reading a tier written by a newer release has to be able
        // to fail closed. Throwing would take the server down instead.
        assertEquals(Optional.empty(), TrustTier.parse("SOME_FUTURE_TIER"));
        assertEquals(Optional.empty(), TrustTier.parse(null));
        assertEquals(Optional.empty(), TrustTier.parse("  "));
    }

    @Test
    void everyTierIsAccountedForByThePredicates() {
        // Guards against a tier being added without deciding what it means,
        // which would otherwise surface as a permission bug rather than here.
        for (TrustTier tier : TrustTier.values()) {
            boolean elevated = tier.eligibleForElevatedPermissions();
            boolean verified = tier.isVerifiedOrigin();
            boolean password = tier.requiresPassword();
            assertTrue(elevated || tier == TrustTier.CRACKED,
                    tier + " must either allow elevation or be CRACKED");
            assertFalse(verified && password,
                    tier + " cannot both be a verified origin and hold a password");
        }
    }
}
