package net.codeverse.api.link;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkCodeTest {

    private static final UUID IDENTITY = UUID.randomUUID();
    private static final Instant ISSUED = Instant.ofEpochSecond(1_700_000_000L);

    private static LinkCode code(Duration lifetime) {
        return new LinkCode("ABC123", IDENTITY, ISSUED, ISSUED.plus(lifetime));
    }

    @Test
    void redeemableUntilTheMomentItExpires() {
        LinkCode linkCode = code(Duration.ofMinutes(5));

        assertTrue(linkCode.isRedeemable(ISSUED));
        assertTrue(linkCode.isRedeemable(ISSUED.plus(Duration.ofMinutes(4))));
        assertFalse(linkCode.isRedeemable(ISSUED.plus(Duration.ofMinutes(5))));
        assertTrue(linkCode.hasExpired(ISSUED.plus(Duration.ofMinutes(6))));
    }

    @Test
    void rejectsCodesThatExpireBeforeTheyAreIssued() {
        assertThrows(IllegalArgumentException.class,
                () -> new LinkCode("ABC123", IDENTITY, ISSUED, ISSUED.minusSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new LinkCode("ABC123", IDENTITY, ISSUED, ISSUED));
    }

    @Test
    void rejectsBlankCodes() {
        assertThrows(IllegalArgumentException.class,
                () -> new LinkCode("  ", IDENTITY, ISSUED, ISSUED.plusSeconds(60)));
    }
}
