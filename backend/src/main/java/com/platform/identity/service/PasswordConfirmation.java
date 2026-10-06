package com.platform.identity.service;

import com.platform.common.web.ResourceNotFoundException;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.AccountStatus;
import com.platform.identity.exception.AccountLockedException;
import com.platform.identity.exception.CurrentPasswordMismatchException;
import com.platform.identity.repository.AccountRepository;
import com.platform.security.SecurityProperties;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * "Type your password to confirm" for a signed-in account's most consequential actions
 * (deleting a child or the account). Same lockout as login: a wrong password counts as a
 * failed attempt, so a stolen access token can't be used to guess it. The caller's
 * transaction must not roll back on {@link CurrentPasswordMismatchException} or
 * {@link AccountLockedException}, or the failed attempt would be forgotten.
 */
@Component
public class PasswordConfirmation {

    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final SecurityProperties securityProperties;

    public PasswordConfirmation(AccountRepository accounts, PasswordEncoder passwordEncoder,
                                SecurityProperties securityProperties) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.securityProperties = securityProperties;
    }

    /** Returns the account, row-locked, once {@code password} is confirmed as its current password. */
    @Transactional(propagation = Propagation.MANDATORY,
            noRollbackFor = {CurrentPasswordMismatchException.class, AccountLockedException.class})
    public Account confirm(UUID accountId, String password) {
        Account account = accounts.findForUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        Instant now = Instant.now();
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccessDeniedException("Account is not active");
        }
        if (account.isLocked(now)) {
            throw new AccountLockedException(account.getLockedUntil());
        }
        if (password == null || !passwordEncoder.matches(password, account.getPasswordHash())) {
            account.recordFailedLogin(
                    securityProperties.lockout().maxAttempts(), securityProperties.lockout().duration(), now);
            throw new CurrentPasswordMismatchException();
        }
        account.resetFailedLogins();
        return account;
    }

}
