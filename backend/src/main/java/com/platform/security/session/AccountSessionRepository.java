package com.platform.security.session;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountSessionRepository extends JpaRepository<AccountSession, UUID> {

    Optional<AccountSession> findByRefreshTokenHash(String refreshTokenHash);

    /** The session, if any, that {@code childSessionId} replaced - i.e. its parent in the rotation chain. */
    Optional<AccountSession> findByReplacedBySessionId(UUID childSessionId);

    /**
     * Atomically rotates one session: ACTIVE -&gt; ROTATED, pointing at
     * {@code newSessionId}. Written as a single conditional UPDATE (guarded by
     * {@code WHERE status = ACTIVE}) rather than a load-mutate-save on the entity
     * so two concurrent refresh calls presenting the same token race safely at the
     * database - exactly one statement affects a row. The caller (see {@link
     * AccountSessionService#refresh}) treats a return value of 0 as "lost the
     * race" and responds identically to a genuine reuse attempt.
     *
     * <p>{@code clearAutomatically}/{@code flushAutomatically} keep the
     * persistence context consistent with this bulk write, since the entity this
     * targets may already be loaded (from {@link #findByRefreshTokenHash}) in the
     * same transaction.
     *
     * @return the number of rows updated: 1 if this call won the race, 0 otherwise.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AccountSession s
               set s.status = com.platform.security.session.AccountSessionStatus.ROTATED,
                   s.rotatedAt = :now,
                   s.replacedBySessionId = :newSessionId,
                   s.version = s.version + 1
             where s.id = :id
               and s.status = com.platform.security.session.AccountSessionStatus.ACTIVE
            """)
    int rotateIfActive(@Param("id") UUID id, @Param("newSessionId") UUID newSessionId, @Param("now") Instant now);

    /**
     * Atomically revokes one session from ACTIVE or ROTATED -&gt; REVOKED.
     * Idempotent by construction: calling this on an already-REVOKED or EXPIRED
     * session simply affects 0 rows rather than erroring, which is what both
     * {@link AccountSessionService#logout} (safe to call twice) and {@link
     * AccountSessionService#refresh}'s family-revocation walk (which does not
     * track what it has already revoked) rely on.
     *
     * <p>Always clears {@code replacedBySessionId}, even when revoking a session
     * that was ROTATED (and therefore had it set). The V9 migration's {@code
     * ck_account_sessions_rotated_consistency} CHECK constraint requires {@code
     * replaced_by_session_id IS NOT NULL} if, and only if, {@code status =
     * 'ROTATED'} - leaving it set while moving a row to REVOKED would violate
     * that constraint and fail the update outright. This does mean a revoked
     * family's rotation-chain pointers are not reconstructable from this table
     * after revocation; nothing in this battle relies on reading them back once
     * revoked.
     *
     * @return the number of rows updated: 1 if this call revoked it, 0 if it was already REVOKED/EXPIRED.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AccountSession s
               set s.status = com.platform.security.session.AccountSessionStatus.REVOKED,
                   s.revokedAt = :now,
                   s.replacedBySessionId = null,
                   s.version = s.version + 1
             where s.id = :id
               and s.status in (com.platform.security.session.AccountSessionStatus.ACTIVE,
                                 com.platform.security.session.AccountSessionStatus.ROTATED)
            """)
    int revokeIfRevocable(@Param("id") UUID id, @Param("now") Instant now);

}
