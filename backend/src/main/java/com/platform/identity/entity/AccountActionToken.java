package com.platform.identity.entity;

import com.platform.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.util.Assert;

import java.time.Instant;
import java.util.UUID;

/**
 * A single-use, expiring proof of mailbox access (V38): the link in a verification or
 * password-reset email. Only the SHA-256 digest of the raw token is stored. A token is
 * usable until it expires, is consumed, or is invalidated by a newer token for the same
 * purpose - whichever comes first.
 */
@Entity
@Table(name = "account_action_tokens")
public class AccountActionToken extends AuditableEntity {

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, updatable = false, length = 30)
    private AccountActionPurpose purpose;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "invalidated_at")
    private Instant invalidatedAt;

    @Column(name = "requested_ip", length = 64, updatable = false)
    private String requestedIp;

    /** The address an EMAIL_CHANGE link proves; null for every other purpose (V50). */
    @Column(name = "new_email", length = 320, updatable = false)
    private String newEmail;

    protected AccountActionToken() {
        // JPA
    }

    public static AccountActionToken issue(UUID accountId, AccountActionPurpose purpose, String tokenHash,
                                           Instant issuedAt, Instant expiresAt, String requestedIp) {
        Assert.notNull(accountId, "accountId must not be null");
        Assert.notNull(purpose, "purpose must not be null");
        Assert.hasText(tokenHash, "tokenHash must not be blank");
        Assert.isTrue(expiresAt.isAfter(issuedAt), "expiresAt must be after issuedAt");
        AccountActionToken token = new AccountActionToken();
        token.accountId = accountId;
        token.purpose = purpose;
        token.tokenHash = tokenHash;
        token.issuedAt = issuedAt;
        token.expiresAt = expiresAt;
        token.requestedIp = requestedIp == null || requestedIp.length() <= 64 ? requestedIp : requestedIp.substring(0, 64);
        return token;
    }

    /** An EMAIL_CHANGE link: proves the owner reads {@code newEmail}. */
    public static AccountActionToken issueEmailChange(UUID accountId, String newEmail, String tokenHash,
                                                      Instant issuedAt, Instant expiresAt, String requestedIp) {
        Assert.hasText(newEmail, "newEmail must not be blank");
        AccountActionToken token = issue(accountId, AccountActionPurpose.EMAIL_CHANGE, tokenHash, issuedAt, expiresAt,
                requestedIp);
        token.newEmail = newEmail;
        return token;
    }

    public boolean isUsable(Instant now) {
        return consumedAt == null && invalidatedAt == null && expiresAt.isAfter(now);
    }

    public void consume(Instant now) {
        if (!isUsable(now)) {
            throw new IllegalStateException("Token " + getId() + " is no longer usable");
        }
        this.consumedAt = now;
    }

    public void invalidate(Instant now) {
        if (consumedAt == null && invalidatedAt == null) {
            this.invalidatedAt = now;
        }
    }

    public UUID getAccountId() {
        return accountId;
    }

    public AccountActionPurpose getPurpose() {
        return purpose;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public String getNewEmail() {
        return newEmail;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

}
