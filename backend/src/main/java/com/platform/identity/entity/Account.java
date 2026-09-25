package com.platform.identity.entity;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import org.springframework.util.Assert;

import com.platform.common.entity.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(name = "accounts")
public class Account extends AuditableEntity {

    @NotBlank
    @Email
    @Column(name = "email", nullable = false, length = 320)
    private String email;
 
    @NotBlank
    @Column(name = "normalized_email", nullable = false, length = 320)
    private String normalizedEmail;
 
    @NotBlank
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;
 
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountStatus status;
 
    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;
 
    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;
 
    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform_role", nullable = false, updatable = false, length = 20)
    private PlatformRole platformRole;
 
    protected Account() {
        // JPA
    }
 
    private Account(String email, String normalizedEmail, String passwordHash, PlatformRole platformRole) {

        this.email = email;
        this.normalizedEmail = normalizedEmail;
        this.passwordHash = passwordHash;
        this.status = AccountStatus.ACTIVE;
        this.failedLoginAttempts = 0;
        this.platformRole = platformRole;

    }
 
    /**
     * Registers a new account. {@code passwordHash} must already be encoded by the
     * caller - this method never sees or handles a raw password. {@code platformRole}
     * is fixed for the account's lifetime (updatable = false) - a role change is a
     * deliberate administrative action on a new/different record's worth of
     * consequence (re-scoping every guard that keys off it), not a routine mutation,
     * so this schema deliberately offers no setter for it.
     */
    public static Account register(String email, String passwordHash, PlatformRole platformRole) {

        Assert.hasText(email, "email must not be blank");
        Assert.hasText(passwordHash, "passwordHash must not be blank");
        Assert.notNull(platformRole, "platformRole must not be null");

        return new Account(email, normalize(email), passwordHash, platformRole);

    }

    public static String normalize(String email) {

        return email.strip().toLowerCase(Locale.ROOT);

    }
 
    public void recordFailedLogin(int maxAttempts, Duration lockoutDuration, Instant now) {

        this.failedLoginAttempts++;

        if (failedLoginAttempts >= maxAttempts) {

            this.lockedUntil = now.plus(lockoutDuration);

        }

    }
 
    public void resetFailedLogins() {

        this.failedLoginAttempts = 0;
        this.lockedUntil = null;

    }
 
    public boolean isLocked(Instant now) {

        return lockedUntil != null && lockedUntil.isAfter(now);

    }
 
    public void markEmailVerified(Instant at) {

        this.emailVerifiedAt = at;

        if (status == AccountStatus.PENDING_VERIFICATION) {

            this.status = AccountStatus.ACTIVE;

        }

    }
 
    public void markPendingApproval() {
        if (status == AccountStatus.ACTIVE) status = AccountStatus.PENDING_VERIFICATION;
    }

    public void approve() {
        if (status != AccountStatus.PENDING_VERIFICATION) {
            throw new IllegalStateException("Only pending accounts can be approved");
        }
        this.status = AccountStatus.ACTIVE;
    }

    public void disable() {

        this.status = AccountStatus.DISABLED;

    }
 
    public void changePasswordHash(String newPasswordHash) {

        Assert.hasText(newPasswordHash, "newPasswordHash must not be blank");
        this.passwordHash = newPasswordHash;
        
    }
 
    public String getEmail() {
        return email;
    }
 
    public String getNormalizedEmail() {
        return normalizedEmail;
    }
 
    public String getPasswordHash() {
        return passwordHash;
    }
 
    public AccountStatus getStatus() {
        return status;
    }
 
    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }
 
    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }
 
    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public PlatformRole getPlatformRole() {
        return platformRole;
    }
    
}
