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

/**
 * A person's account: email (with a normalised form for uniqueness), password hash, role, status, email
 * confirmation, accepted legal version, failed-login lockout and scheduled erasure. The server alone decides its
 * state.
 */
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

    /** M9b: the account's phone number (E.164), and when a code sent to it was confirmed. */
    @Column(name = "phone_e164", length = 16)
    private String phoneE164;

    @Column(name = "phone_verified_at")
    private Instant phoneVerifiedAt;

    /** The version of the terms and privacy notice this account agreed to, and when (V54, Y15). */
    /** The language the app's own words are shown in (en or km; V56). Never the content's language. */
    @Column(name = "preferred_language", nullable = false, length = 5)
    private String preferredLanguage = com.platform.identity.UiLanguage.ENGLISH;

    @Column(name = "legal_version_accepted", length = 40)
    private String legalVersionAccepted;

    @Column(name = "legal_accepted_at")
    private Instant legalAcceptedAt;

    /** When this account is erased with its family (V51); null unless its owner asked. */
    @Column(name = "erase_after")
    private Instant eraseAfter;
 
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
 
    /** Counts a failed sign-in; returns true when this failure locks the account. */
    public boolean recordFailedLogin(int maxAttempts, Duration lockoutDuration, Instant now) {

        this.failedLoginAttempts++;

        if (failedLoginAttempts >= maxAttempts) {

            this.lockedUntil = now.plus(lockoutDuration);
            return true;

        }

        return false;

    }
 
    public void resetFailedLogins() {

        this.failedLoginAttempts = 0;
        this.lockedUntil = null;

    }
 
    public boolean isLocked(Instant now) {

        return lockedUntil != null && lockedUntil.isAfter(now);

    }
 
    /**
     * Records that the owner proved access to the mailbox. It never changes the account's
     * status by itself: PENDING_VERIFICATION is the new principal's state, which
     * AccountRecoveryService#confirmVerification ends (M4); a password reset never does. The
     * first proof is kept.
     */
    public void markEmailVerified(Instant at) {

        Assert.notNull(at, "at must not be null");
        if (this.emailVerifiedAt == null) {
            this.emailVerifiedAt = at;
        }

    }

    /**
     * Moves the sign-in address to one its owner has just proven (an EMAIL_CHANGE link was
     * opened from it), so the new address counts as confirmed from {@code provenAt}.
     */
    public void changeEmail(String newEmail, Instant provenAt) {

        Assert.hasText(newEmail, "newEmail must not be blank");
        Assert.notNull(provenAt, "provenAt must not be null");
        this.email = newEmail.strip();
        this.normalizedEmail = normalize(newEmail);
        this.emailVerifiedAt = provenAt;

    }

    /** Its owner asked to delete the account; it keeps working until {@code at}. */
    public void scheduleErasure(Instant at) {
        Assert.notNull(at, "at must not be null");
        this.eraseAfter = at;
    }

    public void cancelErasure() {
        this.eraseAfter = null;
    }

    public Instant getEraseAfter() {
        return eraseAfter;
    }

    public void changeLanguage(String language) {
        Assert.isTrue(com.platform.identity.UiLanguage.isSupported(language), "unsupported language: " + language);
        this.preferredLanguage = language;
    }

    public String getPreferredLanguage() {
        return preferredLanguage;
    }

    public void acceptLegal(String version, Instant at) {
        Assert.hasText(version, "version must not be blank");
        Assert.notNull(at, "at must not be null");
        this.legalVersionAccepted = version;
        this.legalAcceptedAt = at;
    }

    public String getLegalVersionAccepted() {
        return legalVersionAccepted;
    }

    public Instant getLegalAcceptedAt() {
        return legalAcceptedAt;
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
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

    /** Ends a suspension. Only from DISABLED, so it can never stand in for approving a pending account. */
    public void reinstate() {
        if (status != AccountStatus.DISABLED) {
            throw new IllegalStateException("Only a suspended account can be reinstated");
        }
        this.status = AccountStatus.ACTIVE;
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
 
    /** M9b: the number a code sent to it proved this account holds. */
    public void verifyPhone(String e164, Instant at) {
        Assert.hasText(e164, "e164 must not be blank");
        this.phoneE164 = e164;
        this.phoneVerifiedAt = at;
    }

    /** M9b: the account no longer has a phone number. */
    public void removePhone() {
        this.phoneE164 = null;
        this.phoneVerifiedAt = null;
    }

    public String getPhoneE164() {
        return phoneE164;
    }

    public Instant getPhoneVerifiedAt() {
        return phoneVerifiedAt;
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
