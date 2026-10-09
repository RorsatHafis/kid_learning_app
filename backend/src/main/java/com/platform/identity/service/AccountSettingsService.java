package com.platform.identity.service;

import com.platform.audit.dto.RecordAuditEvent;
import com.platform.audit.entity.AuditActorType;
import com.platform.audit.service.AuditService;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.identity.dto.LoginResult;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.AccountStatus;
import com.platform.identity.exception.AccountLockedException;
import com.platform.identity.exception.CurrentPasswordMismatchException;
import com.platform.identity.exception.WeakPasswordException;
import com.platform.identity.repository.AccountRepository;
import com.platform.security.SecurityProperties;
import com.platform.security.jwt.JwtService;
import com.platform.security.session.AccountSessionService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A signed-in account managing itself: changing its password and ending its sessions.
 *
 * <p>Changing the password requires the current one and is subject to the same lockout
 * as login (a wrong current password counts as a failed attempt), so a stolen access
 * token can't be used to guess or replace the password. On success every existing
 * session is revoked - a password change is how a parent cuts off a device they no
 * longer trust - and a fresh session is issued so the caller stays signed in.
 */
@Service
public class AccountSettingsService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final SecurityProperties securityProperties;
    private final JwtService jwtService;
    private final AccountSessionService accountSessionService;
    private final AuditService auditService;
    private final AccountRecoveryService recovery;
    private final com.platform.identity.legal.LegalDocuments legal;

    public AccountSettingsService(AccountRepository accountRepository, PasswordEncoder passwordEncoder,
                                  PasswordPolicy passwordPolicy, SecurityProperties securityProperties,
                                  JwtService jwtService, AccountSessionService accountSessionService,
                                  AuditService auditService, AccountRecoveryService recovery,
                                  com.platform.identity.legal.LegalDocuments legal) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.securityProperties = securityProperties;
        this.jwtService = jwtService;
        this.accountSessionService = accountSessionService;
        this.auditService = auditService;
        this.recovery = recovery;
        this.legal = legal;
    }

    @Transactional(readOnly = true)
    public Account get(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
    }

    @Transactional(noRollbackFor = {CurrentPasswordMismatchException.class, AccountLockedException.class})
    public LoginResult changePassword(UUID accountId, String currentPassword, String newPassword,
                                      String userAgent, String ipAddress) {
        Account account = get(accountId);
        Instant now = Instant.now();
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccessDeniedException("Account is not active");
        }
        if (account.isLocked(now)) {
            throw new AccountLockedException(account.getLockedUntil());
        }
        if (!passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
            account.recordFailedLogin(
                    securityProperties.lockout().maxAttempts(), securityProperties.lockout().duration(), now);
            throw new CurrentPasswordMismatchException();
        }
        passwordPolicy.validate(newPassword);
        if (passwordEncoder.matches(newPassword, account.getPasswordHash())) {
            throw new WeakPasswordException("The new password must be different from the current one");
        }

        account.changePasswordHash(passwordEncoder.encode(newPassword));
        account.resetFailedLogins();
        int revoked = accountSessionService.revokeAllSessions(accountId);

        AccountSessionService.IssuedSession session =
                accountSessionService.issueSession(account.getId(), userAgent, ipAddress);
        JwtService.IssuedToken issued = jwtService.issueAccessToken(account.getId(), account.getPlatformRole(),
                session.familyId());
        audit(account, "ACCOUNT_PASSWORD_CHANGED", Map.of("sessionsEnded", revoked));

        return new LoginResult(account.getId(), account.getPlatformRole(), issued.token(), issued.expiresAt(),
                session.rawToken(), session.expiresAt());
    }

    /**
     * Asks to move the sign-in address. Needs the current password, under the same lockout
     * as login, so a stolen access token can't redirect the account's emails; the move
     * happens only when the link sent to the new address is opened.
     */
    @Transactional(noRollbackFor = {CurrentPasswordMismatchException.class, AccountLockedException.class})
    public void requestEmailChange(UUID accountId, String currentPassword, String newEmail, String ipAddress) {
        Account account = accountRepository.findForUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        Instant now = Instant.now();
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccessDeniedException("Account is not active");
        }
        if (account.isLocked(now)) {
            throw new AccountLockedException(account.getLockedUntil());
        }
        if (!passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
            account.recordFailedLogin(
                    securityProperties.lockout().maxAttempts(), securityProperties.lockout().duration(), now);
            throw new CurrentPasswordMismatchException();
        }
        account.resetFailedLogins();
        recovery.sendEmailChange(account, newEmail, ipAddress);
    }

    /** Y15: records agreement to the current terms and privacy notice; audited. */
    @Transactional
    public Account acceptLegal(UUID accountId, String version) {
        if (!legal.configured() || !legal.version().equals(version)) {
            throw new IllegalStateException("That isn't the current version of the terms and privacy notice");
        }
        Account account = accountRepository.findForUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        account.acceptLegal(version, Instant.now());
        audit(account, "LEGAL_TERMS_ACCEPTED", Map.of("version", version));
        return account;
    }

    /** Signs the account out on every device. Returns how many sessions were ended. */
    @Transactional
    public int signOutEverywhere(UUID accountId) {
        Account account = get(accountId);
        int revoked = accountSessionService.revokeAllSessions(accountId);
        audit(account, "ACCOUNT_SIGNED_OUT_EVERYWHERE", Map.of("sessionsEnded", revoked));
        return revoked;
    }

    private void audit(Account account, String action, Map<String, Object> metadata) {
        auditService.record(new RecordAuditEvent(null, account.getId(),
                AuditActorType.valueOf(account.getPlatformRole().name()), action, "ACCOUNT", account.getId(),
                metadata));
    }

}
