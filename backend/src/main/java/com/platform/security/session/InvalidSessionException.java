package com.platform.security.session;

/**
 * Thrown for every refresh/logout failure mode the caller should see identically
 * as "your session is no longer valid, sign in again" - an unknown token, a
 * time-expired token, an already-rotated token (reuse), and an already-revoked
 * token all surface this same exception with this same message on purpose (see
 * {@link AccountSessionService#refresh}). A failed refresh must never give a
 * client - or an attacker replaying a stolen token - any signal that
 * distinguishes "wrong token" from "valid-but-stale token" from "we just revoked
 * your whole session family because we detected reuse".
 */
public final class InvalidSessionException extends RuntimeException {

    public InvalidSessionException() {
        super("Session is invalid or expired; please sign in again");
    }

}
