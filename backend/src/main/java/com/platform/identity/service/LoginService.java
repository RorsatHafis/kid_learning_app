package com.platform.identity.service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.platform.audit.dto.RecordAuditEvent;
import com.platform.audit.entity.AuditActorType;
import com.platform.audit.service.AuditService;
import com.platform.identity.dto.LoginCommand;
import com.platform.identity.dto.LoginResult;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.AccountStatus;
import com.platform.identity.exception.AccountLockedException;
import com.platform.identity.exception.InvalidCredentialsException;
import com.platform.identity.repository.AccountRepository;
import com.platform.security.SecurityProperties;
import com.platform.security.jwt.JwtService;
import com.platform.security.session.AccountSessionService;

/**
 * Signs a person in: checks the password (spending the same time when the email is unknown), applies the
 * failed-attempt lockout, refuses disabled accounts, and opens a session with an access and refresh token.
 * Sign-ins are audited.
 */
@Service
public class LoginService {

    // Security events for operators. Accounts are named by id only: never the address that was
    // typed (it may not even be the person's) and never anything about the password.
    private static final Logger log = LoggerFactory.getLogger(LoginService.class);

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AccountSessionService accountSessionService;
    private final SecurityProperties securityProperties;
    private final AuditService auditService;
    private final String dummyPasswordHash;
 
    public LoginService(AccountRepository accountRepository, PasswordEncoder passwordEncoder,
                         JwtService jwtService, AccountSessionService accountSessionService,
                         SecurityProperties securityProperties, AuditService auditService) {

        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.accountSessionService = accountSessionService;
        this.securityProperties = securityProperties;
        this.auditService = auditService;

        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());

    }

    /**
     * M9b: an email address, or a verified phone number (anything without "@" is read as a phone number, Cambodian
     * without a country code). Not a number, or no account verified it: the same as an unknown address.
     */
    private Optional<Account> findBySignInName(String typed) {
        if (typed != null && !typed.contains("@")) {
            try {
                return accountRepository.findByPhoneE164AndPhoneVerifiedAtIsNotNull(
                        com.platform.identity.phone.PhoneNumbers.normalize(typed));
            } catch (IllegalArgumentException notANumber) {
                return Optional.empty();
            }
        }
        return accountRepository.findByNormalizedEmail(Account.normalize(typed));
    }

    @Transactional(noRollbackFor = {InvalidCredentialsException.class, AccountLockedException.class})
    public LoginResult login(LoginCommand command) {

        Optional<Account> found = findBySignInName(command.email());
 
        if (found.isEmpty()) {

            passwordEncoder.matches(command.rawPassword(), dummyPasswordHash);
            log.info("Sign-in failed for an unknown address from {}", command.ipAddress());
            throw new InvalidCredentialsException();
            
        }
 
        Account account = found.get();
        Instant now = Instant.now();
 
        if (account.isLocked(now)) {

            throw new AccountLockedException(account.getLockedUntil());
            
        }
 
        if (!passwordEncoder.matches(command.rawPassword(), account.getPasswordHash())) {

            boolean lockedNow = account.recordFailedLogin(
                    securityProperties.lockout().maxAttempts(), securityProperties.lockout().duration(), now);
            log.info("Sign-in failed for account {} from {} ({} in a row)",
                    account.getId(), command.ipAddress(), account.getFailedLoginAttempts());
            if (lockedNow) {
                log.warn("Account {} locked until {} after {} failed sign-ins",
                        account.getId(), account.getLockedUntil(), account.getFailedLoginAttempts());
                // Committed with the failure (noRollbackFor), so administrators see it in the activity log.
                auditService.record(new RecordAuditEvent(null, null, AuditActorType.SYSTEM, "ACCOUNT_LOCKED", "ACCOUNT",
                        account.getId(), Map.of("lockedUntil", account.getLockedUntil().toString(),
                        "failedAttempts", account.getFailedLoginAttempts())));
            }

            throw new InvalidCredentialsException();

        }
 
        if (account.getStatus() != AccountStatus.ACTIVE) {

            log.info("Sign-in refused for account {}: status {}", account.getId(), account.getStatus());
            throw new InvalidCredentialsException();

        }
 
        account.resetFailedLogins();
 
        AccountSessionService.IssuedSession session = accountSessionService.issueSession(
                account.getId(), command.userAgent(), command.ipAddress());
        JwtService.IssuedToken issued = jwtService.issueAccessToken(account.getId(), account.getPlatformRole(),
                session.familyId());

        return new LoginResult(account.getId(), account.getPlatformRole(), issued.token(), issued.expiresAt(),
                session.rawToken(), session.expiresAt());

    }
    
}
