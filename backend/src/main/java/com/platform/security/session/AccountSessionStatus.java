package com.platform.security.session;

/**
 * Mirrors the V9 migration's {@code ck_account_sessions_status} CHECK constraint
 * exactly: {@code status IN ('ACTIVE', 'ROTATED', 'REVOKED', 'EXPIRED')}. Adding a
 * value here without a matching migration change would violate that constraint at
 * the database level the first time it was persisted.
 */
public enum AccountSessionStatus {

    /** Currently valid; can be presented to {@code /api/v1/auth/refresh}. */
    ACTIVE,

    /**
     * Consumed by exactly one successful rotation; {@code replacedBySessionId}
     * points at the session that replaced it. Presenting the refresh token for a
     * ROTATED session again is treated as reuse (see {@link AccountSessionService}).
     */
    ROTATED,

    /** Explicitly ended (logout, reuse-triggered family revocation, or account disablement). */
    REVOKED,

    /**
     * Reserved for a future cleanup/sweep job that marks long-past-expiry rows for
     * observability. Nothing in this battle writes this status - live requests
     * always compare {@code expiresAt} against the current time directly rather
     * than trusting a possibly-stale EXPIRED flag (see {@link AccountSession#isUsable}).
     */
    EXPIRED

}
