package com.platform.security.session;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.util.Assert;

import com.platform.common.entity.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * A server-side, revocable refresh-token session backing the short-lived access
 * token issued by {@link com.platform.security.jwt.JwtService}. Maps to the
 * account_sessions table created by the V9 migration - see that migration for the
 * authoritative column/constraint definitions this mapping must stay consistent
 * with.
 *
 * Only a SHA-256 hash of the raw refresh token is ever persisted here (see
 * {@link AccountSessionService#issueSession}) - the raw token exists only in
 * memory on the request that issues/rotates it and in that one response body. It
 * is never logged and never reconstructable from this entity.
 *
 * Rotation forms a linked chain via {@link #replacedBySessionId}: each refresh
 * call that succeeds creates a brand-new ACTIVE row and marks this one ROTATED,
 * pointing at the new row. That chain is the "session family" {@link
 * AccountSessionService} revokes in full the moment a rotated/revoked token is
 * presented again - a strong signal of token theft/replay.
 *
 * State transitions for {@link #status} (ACTIVE -&gt; ROTATED, ACTIVE/ROTATED -&gt;
 * REVOKED) are performed as conditional bulk updates in {@link
 * AccountSessionRepository} (guarded by {@code WHERE status = ...}), not through
 * mutator methods on this class. That keeps two concurrent refresh attempts
 * against the same row racing safely at the database - exactly one UPDATE
 * affects a row - rather than needing to recover from a Hibernate
 * optimistic-lock failure mid-transaction. This class is deliberately a plain
 * read/construction model for that reason.
 */
@Entity
@Table(name = "account_sessions")
public class AccountSession extends AuditableEntity {

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    /** The login or registration this session descends from; reuse detection revokes the whole family (V37). */
    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    // V9 declares this CHAR(64) (a SHA-256 hex digest is always exactly 64 chars).
    // Without an explicit CHAR mapping Hibernate expects VARCHAR(64), and
    // ddl-auto=validate refuses to start the application - the same mismatch V33
    // fixed for idempotency_records.request_fingerprint. The migration is the
    // authoritative definition (see class javadoc), so the mapping follows it.
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "refresh_token_hash", nullable = false, updatable = false, length = 64)
    private String refreshTokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountSessionStatus status;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "rotated_at")
    private Instant rotatedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by_session_id")
    private UUID replacedBySessionId;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    /** M10a: set when this sign-in may act only for this child (a child's device); null for an ordinary sign-in. */
    @Column(name = "child_id", updatable = false)
    private UUID childId;

    protected AccountSession() {
        // JPA
    }

    private AccountSession(UUID accountId, UUID familyId, String refreshTokenHash, Instant issuedAt, Instant expiresAt,
                            String userAgent, String ipAddress) {

        this.accountId = accountId;
        this.familyId = familyId;
        this.refreshTokenHash = refreshTokenHash;
        this.status = AccountSessionStatus.ACTIVE;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.userAgent = truncate(userAgent, 500);
        this.ipAddress = truncate(ipAddress, 64);

    }

    /**
     * Starts a brand-new ACTIVE session in a new family - a genuine login or
     * registration. {@code refreshTokenHash} must already be the SHA-256 hex digest
     * of the raw token; this method never sees or stores the raw value.
     */
    public static AccountSession issue(UUID accountId, String refreshTokenHash, Instant issuedAt, Instant expiresAt,
                                        String userAgent, String ipAddress) {

        return successor(accountId, UUID.randomUUID(), refreshTokenHash, issuedAt, expiresAt, userAgent, ipAddress);

    }

    /** M10a: a new sign-in in a new family that may act only for {@code childId} (a child's device). */
    public static AccountSession issueForChild(UUID accountId, UUID childId, String refreshTokenHash, Instant issuedAt,
                                               Instant expiresAt, String userAgent, String ipAddress) {

        Assert.notNull(childId, "childId must not be null");
        AccountSession session = successor(accountId, UUID.randomUUID(), refreshTokenHash, issuedAt, expiresAt, userAgent, ipAddress);
        session.childId = childId;
        return session;

    }

    /** The next session of the same family, keeping its child scope: a rotation never widens what a sign-in may do. */
    public static AccountSession successor(AccountSession presented, String refreshTokenHash, Instant issuedAt,
                                            Instant expiresAt, String userAgent, String ipAddress) {

        AccountSession next = successor(presented.getAccountId(), presented.getFamilyId(), refreshTokenHash, issuedAt, expiresAt,
                userAgent, ipAddress);
        next.childId = presented.childId;
        return next;

    }

    /** The next ACTIVE session of an existing family - the new side of a refresh rotation. */
    public static AccountSession successor(UUID accountId, UUID familyId, String refreshTokenHash, Instant issuedAt,
                                            Instant expiresAt, String userAgent, String ipAddress) {

        Assert.notNull(familyId, "familyId must not be null");
        Assert.notNull(accountId, "accountId must not be null");
        Assert.hasText(refreshTokenHash, "refreshTokenHash must not be blank");
        Assert.notNull(issuedAt, "issuedAt must not be null");
        Assert.notNull(expiresAt, "expiresAt must not be null");
        Assert.isTrue(expiresAt.isAfter(issuedAt), "expiresAt must be after issuedAt");

        return new AccountSession(accountId, familyId, refreshTokenHash, issuedAt, expiresAt, userAgent, ipAddress);

    }

    /**
     * True only while this session can still be used to refresh. Both the status
     * and the absolute expiry are checked live against {@code now} rather than
     * trusting a possibly-stale EXPIRED status (no cleanup job writes that status
     * yet - see {@link AccountSessionStatus#EXPIRED}).
     */
    public boolean isUsable(Instant now) {

        return status == AccountSessionStatus.ACTIVE && expiresAt.isAfter(now);

    }

    private static String truncate(String value, int maxLength) {

        if (value == null) {
            return null;
        }

        return value.length() <= maxLength ? value : value.substring(0, maxLength);

    }

    public UUID getAccountId() {
        return accountId;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public UUID getChildId() {
        return childId;
    }

    /** Whether a request's user agent matches this session's, as stored (truncated to the column). */
    public boolean sameUserAgent(String requestUserAgent) {
        return java.util.Objects.equals(userAgent, truncate(requestUserAgent, 500));
    }

    public String getRefreshTokenHash() {
        return refreshTokenHash;
    }

    public AccountSessionStatus getStatus() {
        return status;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRotatedAt() {
        return rotatedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public UUID getReplacedBySessionId() {
        return replacedBySessionId;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getIpAddress() {
        return ipAddress;
    }

}
