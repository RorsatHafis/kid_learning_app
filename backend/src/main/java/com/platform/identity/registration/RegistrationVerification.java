package com.platform.identity.registration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Owner requirement 2026-11-10: proof that someone controls an address, before any account exists (V87). The emailed
 * link and the registration grant it yields are each stored only as a SHA-256 hash, each expires, and each works once.
 */
@Entity
@Table(name = "registration_verifications")
public class RegistrationVerification {

    @Id
    private UUID id;

    @Column(name = "normalized_email", nullable = false, updatable = false)
    private String normalizedEmail;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
    @Column(name = "link_token_hash", nullable = false, updatable = false, length = 64)
    private String linkTokenHash;

    @Column(name = "link_expires_at", nullable = false, updatable = false)
    private Instant linkExpiresAt;

    @Column(name = "link_used_at")
    private Instant linkUsedAt;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
    @Column(name = "grant_hash", length = 64)
    private String grantHash;

    @Column(name = "grant_expires_at")
    private Instant grantExpiresAt;

    @Column(name = "grant_used_at")
    private Instant grantUsedAt;

    @Column(name = "requested_ip", updatable = false)
    private String requestedIp;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RegistrationVerification() {
    }

    public static RegistrationVerification issue(String normalizedEmail, String linkTokenHash, Instant now,
                                                 Instant linkExpiresAt, String requestedIp) {
        RegistrationVerification v = new RegistrationVerification();
        v.id = UUID.randomUUID();
        v.normalizedEmail = normalizedEmail;
        v.linkTokenHash = linkTokenHash;
        v.linkExpiresAt = linkExpiresAt;
        v.requestedIp = requestedIp == null ? null : requestedIp.substring(0, Math.min(64, requestedIp.length()));
        v.createdAt = now;
        return v;
    }

    /** The link works once, until it expires. */
    public boolean linkUsable(Instant now) {
        return linkUsedAt == null && now.isBefore(linkExpiresAt);
    }

    /** Opening the link uses it and yields the grant the registration form carries. */
    public void useLink(Instant now, String grantHash, Instant grantExpiresAt) {
        this.linkUsedAt = now;
        this.grantHash = grantHash;
        this.grantExpiresAt = grantExpiresAt;
    }

    /** The grant works once, until it expires. */
    public boolean grantUsable(Instant now) {
        return grantHash != null && grantUsedAt == null && now.isBefore(grantExpiresAt);
    }

    public void useGrant(Instant now) {
        this.grantUsedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getNormalizedEmail() {
        return normalizedEmail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getGrantExpiresAt() {
        return grantExpiresAt;
    }
}
