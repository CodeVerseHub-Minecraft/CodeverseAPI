package net.codeverse.api.voice;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoiceAccessTest {

    @Test
    void onlyAllowedPermitsSpeech() {
        assertTrue(VoiceAccess.ALLOWED.allowed());
        for (VoiceAccess access : VoiceAccess.values()) {
            if (access != VoiceAccess.ALLOWED) {
                assertFalse(access.allowed(), access + " must not permit speech");
            }
        }
    }

    @Test
    void unknownIdentityIsARefusal() {
        // The fail closed rule, stated as a test so that a future change making
        // it permissive has to delete an assertion rather than slip through.
        assertFalse(VoiceAccess.UNKNOWN_IDENTITY.allowed());
    }
}
