package com.platform.identity.service;

import com.platform.audit.dto.RecordAuditEvent;
import com.platform.audit.entity.AuditActorType;
import com.platform.audit.service.AuditService;
import com.platform.common.idempotency.service.RequestFingerprint;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.identity.UiLanguage;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.AccountActionPurpose;
import com.platform.identity.entity.AccountActionToken;
import com.platform.identity.entity.AccountStatus;
import com.platform.identity.entity.PlatformRole;
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
 * <p>Every email is written in its recipient's interface language (the account's
 * {@code preferredLanguage}: English or Khmer). Only the words change: the link, its token
 * and its expiry are the same in both.
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
    static final Duration SETUP_LINK_TTL = Duration.ofDays(7);
    static final Duration EMAIL_CHANGE_LINK_TTL = Duration.ofHours(24);
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
        // M4: a principal who signed up opens their account by confirming the address - no administrator's approval.
        // What they can do is still gated: a school's services start only once its payment is confirmed (Y44).
        if (account.getPlatformRole() == PlatformRole.PRINCIPAL && account.getStatus() == AccountStatus.PENDING_VERIFICATION) {
            account.approve();
            audit(account, "PRINCIPAL_SELF_ACTIVATED", Map.of());
        }
    }

    private void sendVerification(Account account, String requestedIp, Instant now) {
        String raw = issue(account.getId(), AccountActionPurpose.EMAIL_VERIFICATION, VERIFICATION_LINK_TTL, requestedIp, now);
        events.publishEvent(UiLanguage.isKhmer(account.getPreferredLanguage())
                ? new EmailMessage(account.getEmail(), "បញ្ជាក់អ៊ីមែលរបស់អ្នកសម្រាប់ Bookeyond Kid", """
                សួស្ដី,

                សូមបញ្ជាក់ថា នេះជាអាសយដ្ឋានអ៊ីមែលរបស់អ្នក ដើម្បីឱ្យអ្នកអាចចូលគណនី Bookeyond Kid របស់អ្នកវិញបានជានិច្ច៖

                %s

                តំណនេះប្រើបានតែម្ដងប៉ុណ្ណោះ ហើយផុតកំណត់ក្នុងរយៈពេល 24 ម៉ោង។

                ប្រសិនបើអ្នកមិនបានបង្កើតគណនី Bookeyond Kid ទេ អ្នកអាចមិនអើពើនឹងអ៊ីមែលនេះបាន។
                """.formatted(mail.link("/verify-email", raw)))
                : new EmailMessage(account.getEmail(), "Confirm your email for Bookeyond Kid", """
                Hello,

                Please confirm that this is your email address, so you can always get back \
                into your Bookeyond Kid account:

                %s

                This link works once and expires in 24 hours.

                If you didn't create a Bookeyond Kid account, you can ignore this email.
                """.formatted(mail.link("/verify-email", raw))));
    }

    // ------------------------------------------------------------------ email change

    /**
     * Sends the link that moves {@code account}'s sign-in address to {@code newEmail}, to
     * that new address. The caller has already proven the current password and holds the
     * account's row lock. Nothing changes until the link is opened.
     *
     * <p>The answer never says whether {@code newEmail} already has an account: if it
     * does, that mailbox is told (it belongs to someone who can already sign in with it)
     * and no link is sent. Same per-account email limits as every other link (429).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void sendEmailChange(Account account, String newEmail, String requestedIp) {
        String wanted = newEmail.strip();
        if (Account.normalize(wanted).equals(account.getNormalizedEmail())) {
            throw new IllegalStateException("This is already your sign-in email");
        }
        Instant now = Instant.now();
        waitBeforeNextEmail(account.getId(), AccountActionPurpose.EMAIL_CHANGE, now).ifPresent(wait -> {
            throw new TooManyEmailsException(wait);
        });
        Optional<Account> holder = accounts.findByNormalizedEmail(Account.normalize(wanted));
        if (holder.isPresent()) {
            // Counts against the limit like a real link, so the timing and the limits look the same.
            tokens.findByAccountIdAndPurposeAndConsumedAtIsNullAndInvalidatedAtIsNull(account.getId(),
                    AccountActionPurpose.EMAIL_CHANGE).forEach(open -> open.invalidate(now));
            AccountActionToken placeholder = AccountActionToken.issueEmailChange(account.getId(), wanted,
                    RequestFingerprint.sha256Hex(randomToken()), now, now.plus(EMAIL_CHANGE_LINK_TTL), requestedIp);
            placeholder.invalidate(now);
            tokens.save(placeholder);
            audit(account, "ACCOUNT_EMAIL_CHANGE_REQUESTED", Map.of());
            // Told in the language of the account that owns this address - its owner, not the asker.
            events.publishEvent(UiLanguage.isKhmer(holder.get().getPreferredLanguage())
                    ? new EmailMessage(wanted, "មាននរណាម្នាក់បានព្យាយាមប្រើអាសយដ្ឋាននេះនៅលើ Bookeyond Kid", """
                    សួស្ដី,

                    មាននរណាម្នាក់ដែលបានចូលគណនី Bookeyond Kid មួយ បានស្នើសុំប្ដូរគណនីនោះមកប្រើអាសយដ្ឋានអ៊ីមែលនេះ។ អាសយដ្ឋាននេះមានគណនី Bookeyond Kid ផ្ទាល់ខ្លួនរួចហើយ ដូច្នេះគ្មានអ្វីត្រូវបានផ្លាស់ប្ដូរទេ។

                    ប្រសិនបើអ្នកភ្លេចពាក្យសម្ងាត់ អ្នកអាចកំណត់វាឡើងវិញនៅ៖

                    %s/forgot-password

                    ប្រសិនបើអ្នកមិនដឹងថានេះជារឿងអ្វីទេ អ្នកអាចមិនអើពើនឹងអ៊ីមែលនេះបាន។
                    """.formatted(mail.publicUrl()))
                    : new EmailMessage(wanted, "Someone tried to use this address on Bookeyond Kid", """
                    Hello,

                    Someone signed in to a Bookeyond Kid account asked to move it to this email \
                    address. This address already has its own Bookeyond Kid account, so nothing \
                    was changed.

                    If you forgot your password, you can reset it at %s/forgot-password.

                    If you don't know what this is about, you can ignore this email.
                    """.formatted(mail.publicUrl())));
            return;
        }
        tokens.findByAccountIdAndPurposeAndConsumedAtIsNullAndInvalidatedAtIsNull(account.getId(),
                AccountActionPurpose.EMAIL_CHANGE).forEach(open -> open.invalidate(now));
        String raw = randomToken();
        tokens.save(AccountActionToken.issueEmailChange(account.getId(), wanted, RequestFingerprint.sha256Hex(raw), now,
                now.plus(EMAIL_CHANGE_LINK_TTL), requestedIp));
        audit(account, "ACCOUNT_EMAIL_CHANGE_REQUESTED", Map.of());
        events.publishEvent(UiLanguage.isKhmer(account.getPreferredLanguage())
                ? new EmailMessage(wanted, "បញ្ជាក់អ៊ីមែលថ្មីរបស់អ្នកសម្រាប់ Bookeyond Kid", """
                សួស្ដី,

                អ្នកបានស្នើសុំចូល Bookeyond Kid ដោយប្រើអាសយដ្ឋានអ៊ីមែលនេះ ចាប់ពីពេលនេះតទៅ។ ដើម្បីធ្វើការផ្លាស់ប្ដូរនេះ សូមបើកតំណនេះ៖

                %s

                តំណនេះប្រើបានតែម្ដងប៉ុណ្ណោះ ហើយផុតកំណត់ក្នុងរយៈពេល 24 ម៉ោង។ រហូតដល់អ្នកបើកវា អ្នកនៅតែចូលដោយប្រើអាសយដ្ឋានបច្ចុប្បន្នរបស់អ្នក។

                ប្រសិនបើអ្នកមិនបានស្នើសុំការនេះទេ អ្នកអាចមិនអើពើនឹងអ៊ីមែលនេះបាន។
                """.formatted(mail.link("/confirm-email-change", raw)))
                : new EmailMessage(wanted, "Confirm your new email for Bookeyond Kid", """
                Hello,

                You asked to sign in to Bookeyond Kid with this email address from now on. \
                To make the change, open this link:

                %s

                This link works once and expires in 24 hours. Until you open it, you keep \
                signing in with your current address.

                If you didn't ask for this, you can ignore this email.
                """.formatted(mail.link("/confirm-email-change", raw))));
    }

    /**
     * Moves the sign-in address to the one the link was sent to. Refused (409) if that
     * address has meanwhile been taken by another account. On success the new address
     * counts as confirmed, every other open link - all of which went to the old address -
     * stops working, and the old address is told. Sessions are kept.
     */
    @Transactional
    public void confirmEmailChange(String rawToken) {
        Instant now = Instant.now();
        AccountActionToken token = usableToken(rawToken, AccountActionPurpose.EMAIL_CHANGE, now);
        Account account = accounts.findForUpdate(token.getAccountId()).orElseThrow(InvalidActionLinkException::new);
        if (account.getStatus() == AccountStatus.DISABLED) {
            throw new InvalidActionLinkException();
        }
        String newEmail = token.getNewEmail();
        if (accounts.findByNormalizedEmail(Account.normalize(newEmail))
                .filter(other -> !other.getId().equals(account.getId())).isPresent()) {
            throw new IllegalStateException("This email address now belongs to another account");
        }
        String oldEmail = account.getEmail();
        token.consume(now);
        tokens.findByAccountIdAndConsumedAtIsNullAndInvalidatedAtIsNull(account.getId())
                .forEach(open -> open.invalidate(now));
        account.changeEmail(newEmail, now);
        accounts.flush();
        audit(account, "ACCOUNT_EMAIL_CHANGED", Map.of());
        events.publishEvent(UiLanguage.isKhmer(account.getPreferredLanguage())
                ? new EmailMessage(oldEmail, "អ៊ីមែលសម្រាប់ចូល Bookeyond Kid របស់អ្នកត្រូវបានផ្លាស់ប្ដូរ", """
                សួស្ដី,

                គណនី Bookeyond Kid ដែលធ្លាប់ប្រើអាសយដ្ឋាននេះ ឥឡូវចូលដោយប្រើ %s ។ អ៊ីមែលអំពីគណនីនេះ រួមទាំងការកំណត់ពាក្យសម្ងាត់ឡើងវិញ នឹងផ្ញើទៅទីនោះ ចាប់ពីពេលនេះតទៅ។

                ប្រសិនបើនេះជាអ្នក មិនចាំបាច់ធ្វើអ្វីទៀតទេ។

                ប្រសិនបើមិនមែនជាអ្នកទេ ប្រហែលជាមាននរណាម្នាក់ដឹងពាក្យសម្ងាត់របស់អ្នក៖ សូមទាក់ទងអ្នកដែលគ្រប់គ្រង Bookeyond Kid សម្រាប់គ្រួសារ ឬសាលារបស់អ្នកភ្លាមៗ។
                """.formatted(masked(newEmail)))
                : new EmailMessage(oldEmail, "Your Bookeyond Kid sign-in email was changed", """
                Hello,

                The Bookeyond Kid account that used this address now signs in with %s. \
                Emails about the account, including password resets, go there from now on.

                If this was you, there is nothing else to do.

                If it wasn't, someone may know your password: contact the person who runs \
                Bookeyond Kid for your family or school straight away.
                """.formatted(masked(newEmail))));
    }

    /** {@code an***@example.com}: enough for the owner to recognise, not enough to harvest. */
    static String masked(String email) {
        int at = email.indexOf('@');
        if (at <= 0) return "***";
        String local = email.substring(0, at);
        return local.substring(0, Math.min(2, local.length())) + "***" + email.substring(at);
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
        events.publishEvent(UiLanguage.isKhmer(account.getPreferredLanguage())
                ? new EmailMessage(account.getEmail(), "កំណត់ពាក្យសម្ងាត់ Bookeyond Kid របស់អ្នកឡើងវិញ", """
                សួស្ដី,

                យើងបានទទួលសំណើកំណត់ពាក្យសម្ងាត់ឡើងវិញ សម្រាប់គណនី Bookeyond Kid របស់អ្នក។ ដើម្បីជ្រើសរើសពាក្យសម្ងាត់ថ្មី សូមបើកតំណនេះ៖

                %s

                តំណនេះប្រើបានតែម្ដងប៉ុណ្ណោះ ហើយផុតកំណត់ក្នុងរយៈពេល 30 នាទី។

                ប្រសិនបើអ្នកមិនបានស្នើសុំការនេះទេ អ្នកអាចមិនអើពើនឹងអ៊ីមែលនេះបាន - ពាក្យសម្ងាត់របស់អ្នកមិនបានផ្លាស់ប្ដូរទេ។
                """.formatted(mail.link("/reset-password", raw)))
                : new EmailMessage(account.getEmail(), "Reset your Bookeyond Kid password", """
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
        Account account = setPasswordWithLink(rawToken, AccountActionPurpose.PASSWORD_RESET, newPassword,
                "ACCOUNT_PASSWORD_RESET");

        events.publishEvent(UiLanguage.isKhmer(account.getPreferredLanguage())
                ? new EmailMessage(account.getEmail(), "ពាក្យសម្ងាត់ Bookeyond Kid របស់អ្នកត្រូវបានផ្លាស់ប្ដូរ", """
                សួស្ដី,

                ពាក្យសម្ងាត់សម្រាប់គណនី Bookeyond Kid របស់អ្នក ទើបតែត្រូវបានផ្លាស់ប្ដូរ ដោយប្រើតំណកំណត់ឡើងវិញ ហើយឧបករណ៍ទាំងអស់ត្រូវបានចាកចេញពីគណនី។

                ប្រសិនបើនេះជាអ្នក មិនចាំបាច់ធ្វើអ្វីទៀតទេ។

                ប្រសិនបើមិនមែនជាអ្នកទេ សូមកំណត់ពាក្យសម្ងាត់របស់អ្នកឡើងវិញភ្លាមៗនៅ៖

                %s/forgot-password

                ហើយពិនិត្យមើលថា តើនរណាខ្លះអាចអានប្រអប់សំបុត្រនេះបាន។
                """.formatted(mail.publicUrl()))
                : new EmailMessage(account.getEmail(), "Your Bookeyond Kid password was changed", """
                Hello,

                The password for your Bookeyond Kid account was just changed using a reset \
                link, and every device was signed out.

                If this was you, there is nothing else to do.

                If it wasn't, reset your password straight away at %s/forgot-password - and \
                check who can read this mailbox.
                """.formatted(mail.publicUrl())));
    }

    // ------------------------------------------------------------------ account setup (invitations)

    /**
     * Who created an account and why, for the email carrying its setup link:
     * {@code subject}, the {@code reason} sentence that opens the email, and what to do
     * after signing in ({@code afterSignIn}, may be empty) - written in the account's
     * interface language, which the rest of the email is in too.
     */
    public record SetupInvitation(String subject, String reason, String afterSignIn) {
    }

    /**
     * Emails the link with which the owner of an account created for them - by a school's
     * invitation or by an administrator - chooses their first password. The account was
     * created with a random password nobody knows. Within the transaction that created or
     * invited them; subject to the same per-account limits as every other link.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void sendAccountSetup(Account account, SetupInvitation invitation, String requestedIp) {
        Instant now = Instant.now();
        waitBeforeNextEmail(account.getId(), AccountActionPurpose.ACCOUNT_SETUP, now).ifPresent(wait -> {
            throw new TooManyEmailsException(wait);
        });
        String raw = issue(account.getId(), AccountActionPurpose.ACCOUNT_SETUP, SETUP_LINK_TTL, requestedIp, now);
        events.publishEvent(UiLanguage.isKhmer(account.getPreferredLanguage())
                ? new EmailMessage(account.getEmail(), invitation.subject(), """
                សួស្ដី,

                %s ដើម្បីចាប់ផ្ដើម សូមជ្រើសរើសពាក្យសម្ងាត់សម្រាប់គណនីរបស់អ្នក៖

                %s

                តំណនេះប្រើបានតែម្ដងប៉ុណ្ណោះ ហើយផុតកំណត់ក្នុងរយៈពេល 7 ថ្ងៃ។ ប្រសិនបើវាផុតកំណត់ហើយ សូមប្រើ "ភ្លេចពាក្យសម្ងាត់?" នៅលើទំព័រចូល ដើម្បីទទួលបានតំណថ្មី។%s

                ប្រសិនបើអ្នកមិនបានរំពឹងថានឹងទទួលអ៊ីមែលនេះទេ អ្នកអាចមិនអើពើនឹងវាបាន។
                """.formatted(invitation.reason(), mail.link("/set-up-account", raw),
                invitation.afterSignIn().isBlank() ? "" : "\n\n" + invitation.afterSignIn()))
                : new EmailMessage(account.getEmail(), invitation.subject(), """
                Hello,

                %s To get started, choose a password for your account:

                %s

                This link works once and expires in 7 days. If it has expired, use \
                "Forgot your password?" on the log-in page to get a new one.%s

                If you weren't expecting this, you can ignore this email.
                """.formatted(invitation.reason(), mail.link("/set-up-account", raw),
                invitation.afterSignIn().isBlank() ? "" : "\n\n" + invitation.afterSignIn())));
    }

    /**
     * True for an account created by an invitation whose owner has not chosen a password
     * yet - no setup link and no reset link has ever been used. Such a person is sent a
     * new setup link rather than told to sign in.
     */
    @Transactional(readOnly = true)
    public boolean awaitingAccountSetup(UUID accountId) {
        return tokens.existsByAccountIdAndPurpose(accountId, AccountActionPurpose.ACCOUNT_SETUP)
                && !tokens.existsByAccountIdAndPurposeAndConsumedAtIsNotNull(accountId, AccountActionPurpose.ACCOUNT_SETUP)
                && !tokens.existsByAccountIdAndPurposeAndConsumedAtIsNotNull(accountId, AccountActionPurpose.PASSWORD_RESET);
    }

    /** Chooses the first password with an invitation link. Like a reset, it signs nobody in. */
    @Transactional
    public void completeAccountSetup(String rawToken, String newPassword) {
        setPasswordWithLink(rawToken, AccountActionPurpose.ACCOUNT_SETUP, newPassword, "ACCOUNT_SETUP_COMPLETED");
    }

    /**
     * Sets a password with an emailed link. The new password must meet the policy before the
     * link is used, so a rejected password leaves the link usable. On success: the lockout is
     * cleared, the address counts as confirmed, other open links of this purpose are
     * withdrawn, and every session on every device ends.
     */
    private Account setPasswordWithLink(String rawToken, AccountActionPurpose purpose, String newPassword,
                                        String auditAction) {
        Instant now = Instant.now();
        AccountActionToken token = usableToken(rawToken, purpose, now);
        Account account = accounts.findForUpdate(token.getAccountId()).orElseThrow(InvalidActionLinkException::new);
        if (account.getStatus() == AccountStatus.DISABLED) {
            throw new InvalidActionLinkException();
        }
        passwordPolicy.validate(newPassword, account.getEmail());

        token.consume(now);
        tokens.findByAccountIdAndPurposeAndConsumedAtIsNullAndInvalidatedAtIsNull(account.getId(), purpose)
                .forEach(open -> open.invalidate(now));
        account.changePasswordHash(passwordEncoder.encode(newPassword));
        account.resetFailedLogins();
        account.markEmailVerified(now);
        int ended = sessions.revokeAllSessions(account.getId());
        audit(account, auditAction, Map.of("sessionsEnded", ended));
        return account;
    }

    // ------------------------------------------------------------------ tokens

    private String issue(UUID accountId, AccountActionPurpose purpose, Duration ttl, String requestedIp, Instant now) {
        // The newest link is the only one that works.
        tokens.findByAccountIdAndPurposeAndConsumedAtIsNullAndInvalidatedAtIsNull(accountId, purpose)
                .forEach(open -> open.invalidate(now));
        String raw = randomToken();
        tokens.save(AccountActionToken.issue(accountId, purpose, RequestFingerprint.sha256Hex(raw), now,
                now.plus(ttl), requestedIp));
        return raw;
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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
