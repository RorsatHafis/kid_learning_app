package com.platform.identity.registration;

import com.platform.common.idempotency.service.RequestFingerprint;
import com.platform.identity.UiLanguage;
import com.platform.identity.entity.Account;
import com.platform.identity.exception.EmailAlreadyRegisteredException;
import com.platform.identity.exception.InvalidActionLinkException;
import com.platform.identity.repository.AccountRepository;
import com.platform.notification.EmailMessage;
import com.platform.notification.PlatformMailProperties;
import com.platform.subscription.choice.PlanChoices;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owner requirement 2026-11-10: the email is verified before the account exists.
 *
 * <ol>
 *   <li>{@link #start}: the person types an address; a single-use link is emailed (30 min). The answer is always the
 *       same, whether or not the address has an account - an existing account's owner is told so by email instead of
 *       getting a link. Per address: one email a minute, five an hour (as account emails); per client the
 *       {@code AuthRateLimitFilter} budget applies.</li>
 *   <li>{@link #verify}: the link is opened (on any device) - it is used once and yields a registration grant
 *       (single use, 30 min), refused if the address got an account meanwhile.</li>
 *   <li>{@link #consumeGrant}: the registration uses the grant in the transaction that creates the account, so an
 *       address can't be registered without it and the grant can't be replayed.</li>
 * </ol>
 * Only SHA-256 hashes of the link token and of the grant are stored.
 */
@Service
public class RegistrationVerificationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationVerificationService.class);

    static final Duration LINK_TTL = Duration.ofMinutes(30);
    static final Duration GRANT_TTL = Duration.ofMinutes(30);
    static final Duration MIN_GAP_BETWEEN_EMAILS = Duration.ofSeconds(60);
    static final int MAX_EMAILS_PER_HOUR = 5;

    private final RegistrationVerificationRepository verifications;
    private final AccountRepository accounts;
    private final PlatformMailProperties mail;
    private final ApplicationEventPublisher events;
    private final PlanChoices planChoices;
    private final SecureRandom random = new SecureRandom();

    public RegistrationVerificationService(RegistrationVerificationRepository verifications, AccountRepository accounts,
                                           PlatformMailProperties mail, ApplicationEventPublisher events,
                                           PlanChoices planChoices) {
        this.planChoices = planChoices;
        this.verifications = verifications;
        this.accounts = accounts;
        this.mail = mail;
        this.events = events;
    }

    /**
     * What opening a link gives the registration form: the grant, the address it proves and (2026-11-11) the plan
     * chosen before registering, as the plan record holds it now (null: none chosen).
     */
    public record Verified(String registrationGrant, String email, Instant grantExpiresAt, PlanChoices.ChosenPlan plan) {
    }

    /** Sends the verification link without a chosen plan. */
    @Transactional
    public void start(String email, String preferredLanguage, String requestedIp) {
        start(email, preferredLanguage, requestedIp, null);
    }

    /**
     * Sends the verification link (or, for an address with an account, a "sign in instead" note). Says nothing back.
     * 2026-11-11: a plan chosen before registering ({@code planCode}, optional) must be one on offer to choose now;
     * it is kept with the request, so the browser's word about it is never needed again.
     */
    @Transactional
    public void start(String email, String preferredLanguage, String requestedIp, String planCode) {
        java.util.UUID chosenPlanId = planCode == null || planCode.isBlank() ? null : planChoices.requireChooseable(planCode).getId();
        String normalized = Account.normalize(email);
        Instant now = Instant.now();
        if (tooSoon(normalized, now)) {
            log.info("Registration verification not sent: requested again too soon");
            return;
        }
        String raw = randomToken();
        // A row is kept even for an address that already has an account (its link is never sent): the same
        // per-address pace then applies to both answers, and neither reveals which one was given.
        verifications.save(RegistrationVerification.issue(normalized, RequestFingerprint.sha256Hex(raw), now,
                now.plus(LINK_TTL), requestedIp, chosenPlanId));
        boolean khmer = UiLanguage.isKhmer(preferredLanguage);
        if (accounts.findByNormalizedEmail(normalized).isPresent()) {
            events.publishEvent(alreadyRegistered(email.strip(), khmer));
            return;
        }
        events.publishEvent(khmer
                ? new EmailMessage(email.strip(), "បញ្ជាក់អ៊ីមែលរបស់អ្នក ដើម្បីបង្កើតគណនី Bookeyond Kid", """
                សួស្ដី,

                ដើម្បីបង្កើតគណនី Bookeyond Kid ជាមួយអាសយដ្ឋាននេះ សូមបើកតំណនេះ៖

                %s

                តំណនេះប្រើបានតែម្ដងប៉ុណ្ណោះ ហើយផុតកំណត់ក្នុងរយៈពេល 30 នាទី។ អ្នកអាចបើកវានៅលើឧបករណ៍ណាមួយក៏បាន។

                ប្រសិនបើអ្នកមិនបានស្នើសុំការនេះទេ អ្នកអាចមិនអើពើនឹងអ៊ីមែលនេះបាន - គ្មានគណនីណាមួយត្រូវបានបង្កើតទេ។
                """.formatted(mail.link("/register/continue", raw)))
                : new EmailMessage(email.strip(), "Confirm your email to create your Bookeyond Kid account", """
                Hello,

                To create a Bookeyond Kid account with this address, open this link:

                %s

                This link works once and expires in 30 minutes. You can open it on any device.

                If you didn't ask for this, you can ignore this email - no account has been created.
                """.formatted(mail.link("/register/continue", raw))));
    }

    @Transactional
    public Verified verify(String rawToken) {
        return verify(rawToken, null);
    }

    /** Opening the link: used once, yields the registration grant (and the chosen plan, in {@code language}). */
    @Transactional
    public Verified verify(String rawToken, String language) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidActionLinkException();
        }
        Instant now = Instant.now();
        RegistrationVerification v = verifications.findByLinkForUpdate(RequestFingerprint.sha256Hex(rawToken.strip()))
                .filter(found -> found.linkUsable(now))
                .orElseThrow(InvalidActionLinkException::new);
        String grant = randomToken();
        v.useLink(now, RequestFingerprint.sha256Hex(grant), now.plus(GRANT_TTL));
        if (accounts.findByNormalizedEmail(v.getNormalizedEmail()).isPresent()) {
            // The address got an account meanwhile: the person signs in instead. The link stays used.
            throw new EmailAlreadyRegisteredException();
        }
        return new Verified(grant, v.getNormalizedEmail(), v.getGrantExpiresAt(),
                planChoices.describe(v.getChosenPlanId(), language).orElse(null));
    }

    /**
     * Uses the grant for {@code normalizedEmail}, in the caller's transaction (the one creating the account): a missing,
     * used, expired grant, or one for another address, is refused like a bad link. Returns the plan chosen before
     * registering (null: none).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public java.util.UUID consumeGrant(String rawGrant, String normalizedEmail) {
        if (rawGrant == null || rawGrant.isBlank()) {
            throw new RegistrationNotVerifiedException();
        }
        Instant now = Instant.now();
        RegistrationVerification v = verifications.findByGrantForUpdate(RequestFingerprint.sha256Hex(rawGrant.strip()))
                .filter(found -> found.grantUsable(now) && found.getNormalizedEmail().equals(normalizedEmail))
                .orElseThrow(RegistrationNotVerifiedException::new);
        v.useGrant(now);
        return v.getChosenPlanId();
    }

    private boolean tooSoon(String normalized, Instant now) {
        boolean withinGap = verifications.findFirstByNormalizedEmailOrderByCreatedAtDesc(normalized)
                .map(latest -> latest.getCreatedAt().plus(MIN_GAP_BETWEEN_EMAILS).isAfter(now))
                .orElse(false);
        return withinGap || verifications.countByNormalizedEmailAndCreatedAtAfter(normalized, now.minus(Duration.ofHours(1)))
                >= MAX_EMAILS_PER_HOUR;
    }

    private EmailMessage alreadyRegistered(String email, boolean khmer) {
        return khmer
                ? new EmailMessage(email, "អ្នកមានគណនី Bookeyond Kid រួចហើយ", """
                សួស្ដី,

                មាននរណាម្នាក់ (សង្ឃឹមថាជាអ្នក) បានស្នើសុំបង្កើតគណនី Bookeyond Kid ជាមួយអាសយដ្ឋាននេះ។ អ្នកមានគណនីរួចហើយ។

                ចូលគណនី៖ %s
                ភ្លេចពាក្យសម្ងាត់? %s

                ប្រសិនបើអ្នកមិនបានស្នើសុំការនេះទេ អ្នកអាចមិនអើពើនឹងអ៊ីមែលនេះបាន - គ្មានអ្វីត្រូវបានផ្លាស់ប្ដូរទេ។
                """.formatted(mail.publicUrl() + "/login", mail.publicUrl() + "/forgot-password"))
                : new EmailMessage(email, "You already have a Bookeyond Kid account", """
                Hello,

                Someone (hopefully you) asked to create a Bookeyond Kid account with this address. \
                You already have one.

                Sign in: %s
                Forgot your password? %s

                If you didn't ask for this, you can ignore this email - nothing has changed.
                """.formatted(mail.publicUrl() + "/login", mail.publicUrl() + "/forgot-password"));
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
