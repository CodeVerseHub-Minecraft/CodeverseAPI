package net.codeverse.api.voice;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoiceRestrictionTest {

    private static final UUID IDENTITY = UUID.randomUUID();
    private static final Instant ISSUED = Instant.ofEpochSecond(1_700_000_000L);

    private static VoiceRestriction temporary(Duration length) {
        return new VoiceRestriction(IDENTITY, "spam", Optional.empty(), ISSUED,
                Optional.of(ISSUED.plus(length)), true);
    }

    private static VoiceRestriction permanent() {
        return new VoiceRestriction(IDENTITY, "hate speech", Optional.empty(), ISSUED,
                Optional.empty(), true);
    }

    @Test
    void permanentRestrictionsNeverLapse() {
        VoiceRestriction restriction = permanent();
        assertTrue(restriction.isPermanent());
        assertFalse(restriction.hasExpired(Instant.MAX));
        assertTrue(restriction.isEnforceable(Instant.MAX));
        assertEquals(Optional.empty(), restriction.remaining(ISSUED));
    }

    @Test
    void temporaryRestrictionsLapseOnTheClock() {
        VoiceRestriction restriction = temporary(Duration.ofMinutes(30));

        assertTrue(restriction.isEnforceable(ISSUED.plusSeconds(60)));
        assertFalse(restriction.isEnforceable(ISSUED.plus(Duration.ofMinutes(30))));
        assertTrue(restriction.hasExpired(ISSUED.plus(Duration.ofMinutes(31))));
    }

    @Test
    void remainingNeverGoesNegative() {
        VoiceRestriction restriction = temporary(Duration.ofMinutes(10));

        assertEquals(Duration.ofMinutes(4),
                restriction.remaining(ISSUED.plus(Duration.ofMinutes(6))).orElseThrow());
        assertEquals(Duration.ZERO,
                restriction.remaining(ISSUED.plus(Duration.ofHours(5))).orElseThrow());
    }

    @Test
    void liftedRestrictionsStopApplyingWhileRemainingReadable() {
        VoiceRestriction lifted = new VoiceRestriction(IDENTITY, "spam", Optional.empty(), ISSUED,
                Optional.of(ISSUED.plusSeconds(600)), false);

        assertFalse(lifted.isEnforceable(ISSUED.plusSeconds(1)));
        assertEquals("spam", lifted.reason());
    }

    @Test
    void rejectsExpiryThatPrecedesIssue() {
        assertThrows(IllegalArgumentException.class,
                () -> new VoiceRestriction(IDENTITY, "spam", Optional.empty(), ISSUED,
                        Optional.of(ISSUED.minusSeconds(1)), true));
        assertThrows(IllegalArgumentException.class,
                () -> new VoiceRestriction(IDENTITY, "spam", Optional.empty(), ISSUED,
                        Optional.of(ISSUED), true));
    }

    @Test
    void rejectsBlankReasons() {
        assertThrows(IllegalArgumentException.class,
                () -> new VoiceRestriction(IDENTITY, "  ", Optional.empty(), ISSUED,
                        Optional.empty(), true));
    }
}
