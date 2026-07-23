package net.codeverse.api.identity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdentityTest {

    private static final UUID INTERNAL = UUID.randomUUID();
    private static final UUID MINECRAFT = UUID.randomUUID();

    @Test
    void builderProducesAMinimalIdentity() {
        Identity identity = Identity.builder(INTERNAL, MINECRAFT, "~Steve", TrustTier.CRACKED).build();

        assertEquals(INTERNAL, identity.internalId());
        assertEquals(MINECRAFT, identity.minecraftId());
        assertFalse(identity.isRegistered());
        assertFalse(identity.hasDiscordLink());
        assertFalse(identity.totpEnrolled());
    }

    @Test
    void zeroTimestampsMeanNeverRatherThanTheEpoch() {
        // Storage encodes "never" as 0, and an identity claiming someone
        // registered in 1970 would be worse than one admitting it does not know.
        Identity identity = Identity.builder(INTERNAL, MINECRAFT, "Steve", TrustTier.PREMIUM)
                .registeredAtMillis(0L)
                .lastLoginAtMillis(0L)
                .build();

        assertEquals(Optional.empty(), identity.registeredAt());
        assertEquals(Optional.empty(), identity.lastLoginAt());
        assertFalse(identity.isRegistered());
    }

    @Test
    void positiveTimestampsAreCarriedThrough() {
        Identity identity = Identity.builder(INTERNAL, MINECRAFT, "Steve", TrustTier.PREMIUM)
                .registeredAtMillis(1_700_000_000_000L)
                .build();

        assertTrue(identity.isRegistered());
        assertEquals(Instant.ofEpochMilli(1_700_000_000_000L), identity.registeredAt().orElseThrow());
    }

    @Test
    void blankDiscordIdIsTreatedAsAbsent() {
        assertFalse(Identity.builder(INTERNAL, MINECRAFT, "Steve", TrustTier.PREMIUM)
                .discordId("   ").build().hasDiscordLink());
        assertFalse(Identity.builder(INTERNAL, MINECRAFT, "Steve", TrustTier.PREMIUM)
                .discordId(null).build().hasDiscordLink());
        assertTrue(Identity.builder(INTERNAL, MINECRAFT, "Steve", TrustTier.PREMIUM)
                .discordId("123456789").build().hasDiscordLink());
    }

    @Test
    void permissionEligibilityDelegatesToTheTier() {
        assertFalse(Identity.builder(INTERNAL, MINECRAFT, "~Steve", TrustTier.CRACKED)
                .build().eligibleForElevatedPermissions());
        assertTrue(Identity.builder(INTERNAL, MINECRAFT, "Steve", TrustTier.PREMIUM)
                .build().eligibleForElevatedPermissions());
    }

    @Test
    void rejectsIncoherentIdentities() {
        assertThrows(NullPointerException.class,
                () -> new Identity(null, MINECRAFT, "Steve", TrustTier.PREMIUM,
                        Optional.empty(), Optional.empty(), false, Optional.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> new Identity(INTERNAL, MINECRAFT, "  ", TrustTier.PREMIUM,
                        Optional.empty(), Optional.empty(), false, Optional.empty()));
        assertThrows(NullPointerException.class,
                () -> new Identity(INTERNAL, MINECRAFT, "Steve", null,
                        Optional.empty(), Optional.empty(), false, Optional.empty()));
    }
}
