package com.platform.identity.service;

import com.platform.audit.dto.RecordAuditEvent;
import com.platform.audit.entity.AuditActorType;
import com.platform.audit.service.AuditService;
import com.platform.common.idempotency.service.RequestFingerprint;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.AccountActionPurpose;
import com.platform.identity.entity.AccountActionToken;
import com.platform.identity.entity.AccountStatus;
import com.platform.identity.exception.InvalidActionLinkException;
import com.platform.identity.exception.TooManyEmailsException;
import com.platform.identity.repository.AccountActionTokenRepository;
import com.platform.identity.repository.AccountRepository;
import com.platform.notification.EmailMessage;
import com.platform.notification.PlatformMailProperties;
import com.platform.security.session.AccountSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Actions proven by access to the account's mailbox (CLAUDE.md §11): confirming the
 * email address, and resetting a forgotten password.
 *
 * <p>Each email carries a single-use link: 256 random bits, of which only the SHA-256
 * digest is stored. A link works once, expires (24 h to confirm an address, 30 min to
 * reset a password), and is replaced by the next link of the same purpose. Using one
 * locks its row, so two clicks of the same link cannot both succeed.
 *
 * <p>Asking for a reset never reveals whether an address has an account: the caller
 * always gets the same answer, and unknown or disabled addresses are simply not sent
 * anything. Per account, emails of one purpose are at least a minute apart and at most
 * five an hour, so the feature cannot be used to flood someone's inbox; per network,
 * the requests themselves are budgeted by {@code AuthRateLimitFilter}.
 *
 * <p>Confirming an address records when it was proven and nothing else: it never
 * changes the account's status (a principal awaiting approval stays waiting). A
 * password reset ends every session and does not sign anyone in - the person signs in
 * with the new password, through the usual lockout and status checks.
 */
@Service
public class AccountRecoveryService {

    static final Duration VERIFICATION_LINK_TTL = Duration.ofHours(24);
    static final Duration RESET_LINK_TTL = Duration.ofMinutes(30);
    static final Duration MIN_GAP_BETWEEN_EMAILS = Duration.ofSeconds(60);
    static final int MAX_EMAILS_PER_HOUR = 5;

    private static final Logger log = LoggerFactory.getLogger(AccountRecoveryService.class);

    private final AccountRepository accounts;
    private final AccountActionTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final AccountSessionService sessions;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;
    private final PlatformMailProperties mail;
    private final SecureRandom random = new SecureRandom();

    public AccountRecoveryService(AccountRepository accounts, AccountActionTokenRepository tokens,
                                  PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy,
                                  AccountSessionService sessions, AuditService auditService,
                                  ApplicationEventPublisher events, PlatformMailProperties mail) {
        this.accounts = accounts;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.sessions = sessions;
        this.auditService = auditService;
        this.events = events;
        this.mail = mail;
    }

    // ------------------------------------------------------------------ email verification

    /** Sends the first confirmation link, inside the registration transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void sendVerificationForNewAccount(Account account) {
        sendVerification(account, null, Instant.now());
    }

    /**
     * The signed-in account asks for a new confirmation link. Refused when the address is
     * already confirmed (409) or an email was sent too recently (429 with a wait).
     */
    @Transactional
    public void resendVerification(UUID accountId, String requestedIp) {
        Account account = accounts.findForUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        if (account.getStatus() == AccountStatus.DISABLED) {
            throw new AccessDeniedException("Account is disabled");
        }
        if (account.isEmailVerified()) {
            throw new IllegalStateException("This email address is already confirmed");
        }
        Instant now = Instant.now();
        waitBeforeNextEmail(accountId, AccountActionPurpose.EMAIL_VERIFICATION, now).ifPresent(wait -> {
            throw new TooManyEmailsException(wait);
        });
        sendVerification(account, requestedIp, now);
    }

    @Transactional
    public void confirmVerification(String rawToken) {
        Instant now = Instant.now();
        AccountActionToken token = usableToken(rawToken, AccountActionPurpose.EMAIL_VERIFICATION, now);
        Account account = accounts.findForUpdate(token.getAccountId()).orElseThrow(InvalidActionLinkException::new);
        if (account.getStatus() == AccountStatus.DISABLED) {
            throw new InvalidActionLinkException();
        }
        token.consume(now);
        boolean firstProof = !account.isEmailVerified();
        account.markEmailVerified(now);
        if (firstProof) {
            audit(account, "ACCOUNT_EMAIL_VERIFIED", Map.of());
        }
    }

    private void sendVerification(Account account, String requestedIp, Instant now) {
        String raw = issue(account.getId(), AccountActionPurpose.EMAIL_VERIFICATION, VERIFICATION_LINK_TTL, requestedIp, now);
        events.publishEvent(new EmailMessage(account.getEmail(), "Confirm your email for Bookeyond Kid", """
                Hello,

                Please confirm that this is your email address, so you can always get back \
                into your Bookeyond Kid account:

                %s

                This link works once and expires in 24 hours.

                If you didn't create a Bookeyond Kid account, you can ignore this email.
                """.formatted(mail.link("/verify-email", raw))));
    }

    // ------------------------------------------------------------------ password reset

    /**
     * Sends a reset link if {@code email} belongs to an account that may use one. Always
     * returns normally: callers cannot learn whether the address is registered, disabled,
     * or has had too many emails recently.
     */
    @Transactional
    public void requestPasswordReset(String email, String requestedIp) {
        Optional<Account> found = accounts.findByNormalizedEmail(Account.normalize(email))
                .flatMap(a -> accounts.findForUpdate(a.getId()));
        if (found.isEmpty() || found.get().getStatus() == AccountStatus.DISABLED) {
            return;
        }
        Account account = found.get();
        Instant now = Instant.now();
        if (waitBeforeNextEmail(account.getId(), AccountActionPurpose.PASSWORD_RESET, now).isPresent()) {
            log.info("Password reset for account {} not sent: requested again too soon", account.getId());
            return;
        }
        String raw = issue(account.getId(), AccountActionPurpose.PASSWORD_RESET, RESET_LINK_TTL, requestedIp, now);
        audit(account, "ACCOUNT_PASSWORD_RESET_REQUESTED", Map.of());
        events.publishEvent(new EmailMessage(account.getEmail(), "Reset your Bookeyond Kid password", """
                Hello,

                We received a request to reset the password for your Bookeyond Kid account. \
                To choose a new password, open this link:

                %s

                This link works once and expires in 30 minutes.

                If you didn't ask for this, you can ignore this email - your password has \
                not changed.
                """.formatted(mail.link("/reset-password", raw))));
    }

    /**
     * Sets a new password with a reset link. The new password must meet the policy before
     * the link is used, so a rejected password leaves the link usable. On success: the
     * lockout is cleared (the mailbox is a stronger proof than a password guess counter),
     * the address counts as confirmed, every other open reset link is withdrawn, and every
     * session on every device ends.
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        Instant now = Instant.now();
        AccountActionToken token = usableToken(rawToken, AccountActionPurpose.PASSWORD_RESET, now);
        Account account = accounts.findForUpdate(token.getAccountId()).orElseThrow(InvalidActionLinkException::new);
        if (account.getStatus() == AccountStatus.DISABLED) {
            throw new InvalidActionLinkException();
        }
        passwordPolicy.validate(newPassword);

        token.consume(now);
        tokens.findByAccountIdAndPurposeAndConsumedAtIsNullAndInvalidatedAtIsNull(account.getId(),
                AccountActionPurpose.PASSWORD_RESET).forEach(open -> open.invalidate(now));
        account.changePasswordHash(passwordEncoder.encode(newPassword));
        account.resetFailedLogins();
        account.markEmailVerified(now);
        int ended = sessions.revokeAllSessions(account.getId());
        audit(account, "ACCOUNT_PASSWORD_RESET", Map.of("sessionsEnded", ended));

        events.publishEvent(new EmailMessage(account.getEmail(), "Your Bookeyond Kid password was changed", """
                Hello,

                The password for your Bookeyond Kid account was just changed using a reset \
                link, and every device was signed out.

                If this was you, there is nothing else to do.

                If it wasn't, reset your password straight away at %s/forgot-password - and \
                check who can read this mailbox.
                """.formatted(mail.publicUrl())));
    }

    // ------------------------------------------------------------------ tokens

    private String issue(UUID accountId, AccountActionPurpose purpose, Duration ttl, String requestedIp, Instant now) {
        // The newest link is the only one that works.
        tokens.findByAccountIdAndPurposeAndConsumedAtIsNullAndInvalidatedAtIsNull(accountId, purpose)
                .forEach(open -> open.invalidate(now));
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.save(AccountActionToken.issue(accountId, purpose, RequestFingerprint.sha256Hex(raw), now,
                now.plus(ttl), requestedIp));
        return raw;
    }

    private AccountActionToken usableToken(String rawToken, AccountActionPurpose purpose, Instant now) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidActionLinkException();
        }
        return tokens.findForUse(RequestFingerprint.sha256Hex(rawToken.strip()), purpose)
                .filter(token -> token.isUsable(now))
                .orElseThrow(InvalidActionLinkException::new);
    }

    /** Empty when another email of this purpose may be sent now; otherwise how long to wait. */
    private Optional<Duration> waitBeforeNextEmail(UUID accountId, AccountActionPurpose purpose, Instant now) {
        Optional<AccountActionToken> latest = tokens.findFirstByAccountIdAndPurposeOrderByIssuedAtDesc(accountId, purpose);
        if (latest.isPresent()) {
            Instant allowedAt = latest.get().getIssuedAt().plus(MIN_GAP_BETWEEN_EMAILS);
            if (allowedAt.isAfter(now)) {
                return Optional.of(Duration.between(now, allowedAt));
            }
        }
        Instant windowStart = now.minus(Duration.ofHours(1));
        if (tokens.countByAccountIdAndPurposeAndIssuedAtAfter(accountId, purpose, windowStart) >= MAX_EMAILS_PER_HOUR) {
            Instant oldestInWindow = tokens
                    .findFirstByAccountIdAndPurposeAndIssuedAtAfterOrderByIssuedAtAsc(accountId, purpose, windowStart)
                    .map(AccountActionToken::getIssuedAt).orElse(now);
            return Optional.of(Duration.between(now, oldestInWindow.plus(Duration.ofHours(1))));
        }
        return Optional.empty();
    }

    private void audit(Account account, String action, Map<String, Object> metadata) {
        auditService.record(new RecordAuditEvent(null, account.getId(),
                AuditActorType.valueOf(account.getPlatformRole().name()), action, "ACCOUNT", account.getId(),
                metadata));
    }

}
