package net.codeverse.api.voice;

/**
 * Why someone may or may not speak.
 *
 * Returned instead of a boolean so a consumer can tell the person which of
 * several reasons applies. Saying "you are muted" to someone actually blocked
 * by trust tier sends them to staff with the wrong question, and staff then
 * spend their time correcting the diagnosis rather than the problem.
 */
public enum VoiceAccess {

    ALLOWED,

    /** An active restriction applies. */
    RESTRICTED,

    /** Trust tier is not eligible, typically an unverified account. */
    UNTRUSTED,

    /** Lacks the permission required to use voice at all. */
    NO_PERMISSION,

    /**
     * Identity could not be resolved, so no decision could be made safely.
     * Treated as denied, matching the fail closed stance the authentication
     * layer takes: a capability granted to an unknown identity cannot be taken
     * back from the right person afterwards.
     */
    UNKNOWN_IDENTITY;

    public boolean allowed() {
        return this == ALLOWED;
    }
}
