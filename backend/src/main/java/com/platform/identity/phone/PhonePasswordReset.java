package com.platform.identity.phone;

import com.platform.common.idempotency.service.RequestFingerprint;
import com.platform.identity.UiLanguage;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.AccountStatus;
import com.platform.identity.repository.AccountRepository;
import com.platform.identity.service.AccountRecoveryService;
import com.platform.identity.service.PasswordPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * M9b (owner decision D-15): a forgotten password reset with a code texted to the account's verified phone. Email
 * stays required; this is a second way back in.
 *
 * <p>Asking always answers the same, whatever the number: an unknown or unverified number, a disabled account, or a
 * request too soon (once a minute per account, 5 texts an hour per number) sends nothing and says nothing. The code
 * lasts 10 minutes and allows 5 tries; every refusal reads the same, so nobody learns whether a number has an account.
 * A new password must meet the policy before the code is used (a rejected password leaves it usable).
 */
@Service
public class PhonePasswordReset {

    private static final Logger log = LoggerFactory.getLogger(PhonePasswordReset.class);
    static final String NOT_RIGHT = "That code isn't right, or it has expired: ask for a new one";

    private final NamedParameterJdbcTemplate jdbc;
    private final AccountRepository accounts;
    private final AccountRecoveryService recovery;
    private final PasswordPolicy passwordPolicy;
    private final SmsSender sms;
    private final SecureRandom random = new SecureRandom();

    public PhonePasswordReset(NamedParameterJdbcTemplate jdbc, AccountRepository accounts, AccountRecoveryService recovery,
                              PasswordPolicy passwordPolicy, SmsSender sms) {
        this.jdbc = jdbc;
        this.accounts = accounts;
        this.recovery = recovery;
        this.passwordPolicy = passwordPolicy;
        this.sms = sms;
    }

    /** Texts a reset code if {@code typed} is a verified number of an account that may use one. Always returns normally. */
    @Transactional
    public void request(String typed) {
        if (!sms.available()) {
            throw new PhoneUnavailableException();
        }
        Optional<Account> found = accountFor(typed).flatMap(a -> accounts.findForUpdate(a.getId()));
        if (found.isEmpty() || found.get().getStatus() == AccountStatus.DISABLED) {
            return;
        }
        Account account = found.get();
        String phone = account.getPhoneE164();
        Instant now = Instant.now();
        Instant last = jdbc.query("SELECT max(issued_at) FROM phone_verifications WHERE account_id = :a AND purpose = 'RESET'",
                Map.of("a", account.getId()), rs -> rs.next() && rs.getTimestamp(1) != null ? rs.getTimestamp(1).toInstant() : null);
        Integer byNumber = jdbc.queryForObject("SELECT count(*) FROM phone_verifications WHERE phone_e164 = :p AND issued_at > :h AND sent",
                new MapSqlParameterSource("p", phone).addValue("h", Timestamp.from(now.minus(Duration.ofHours(1)))), Integer.class);
        if ((last != null && last.plus(PhoneVerificationService.RESEND_AFTER).isAfter(now))
                || (byNumber != null && byNumber >= PhoneVerificationService.PER_HOUR)) {
            log.info("Phone reset for account {} not sent: asked too soon or too often", account.getId());
            return;
        }
        jdbc.update("""
                UPDATE phone_verifications SET superseded_at = :n
                 WHERE account_id = :a AND purpose = 'RESET' AND confirmed_at IS NULL AND superseded_at IS NULL
                """, new MapSqlParameterSource("n", Timestamp.from(now)).addValue("a", account.getId()));
        UUID id = UUID.randomUUID();
        String code = String.format("%06d", random.nextInt(1_000_000));
        jdbc.update("""
                INSERT INTO phone_verifications (id, account_id, phone_e164, code_hash, issued_at, expires_at, sent, purpose)
                VALUES (:id, :a, :p, :h, :i, :e, TRUE, 'RESET')
                """, new MapSqlParameterSource("id", id).addValue("a", account.getId()).addValue("p", phone)
                .addValue("h", RequestFingerprint.sha256Hex(id + ":" + code)).addValue("i", Timestamp.from(now))
                .addValue("e", Timestamp.from(now.plus(PhoneVerificationService.CODE_LIFETIME))));
        sms.send(phone, UiLanguage.isKhmer(account.getPreferredLanguage())
                ? "លេខកូដកំណត់ពាក្យសម្ងាត់ Bookeyond Kid របស់អ្នកគឺ " + code + "។ វាប្រើបាន 10 នាទី។ ប្រសិនបើអ្នកមិនបានស្នើ សូមកុំអើពើ។"
                : "Your Bookeyond Kid password reset code is " + code + ". It works for 10 minutes. If you didn't ask, ignore this.");
    }

    /** Sets a new password with the newest reset code texted to {@code typed}; every session ends. */
    @Transactional(noRollbackFor = PhoneCodeMismatchException.class)
    public void reset(String typed, String code, String newPassword) {
        Optional<Account> found = accountFor(typed).flatMap(a -> accounts.findForUpdate(a.getId()));
        if (found.isEmpty() || found.get().getStatus() == AccountStatus.DISABLED) {
            throw new PhoneCodeMismatchException(NOT_RIGHT);
        }
        Account account = found.get();
        List<Map<String, Object>> open = jdbc.queryForList("""
                SELECT id, code_hash, expires_at, attempts FROM phone_verifications
                 WHERE account_id = :a AND purpose = 'RESET' AND confirmed_at IS NULL AND superseded_at IS NULL
                 ORDER BY issued_at DESC LIMIT 1
                """, Map.of("a", account.getId()));
        if (open.isEmpty()) {
            throw new PhoneCodeMismatchException(NOT_RIGHT);
        }
        Map<String, Object> row = open.getFirst();
        UUID id = (UUID) row.get("id");
        String given = code == null ? "" : code.replaceAll("\\s", "");
        boolean usable = ((Timestamp) row.get("expires_at")).toInstant().isAfter(Instant.now())
                && ((Number) row.get("attempts")).intValue() < PhoneVerificationService.MAX_TRIES;
        boolean right = usable && given.matches("\\d{6}") && java.security.MessageDigest.isEqual(
                RequestFingerprint.sha256Hex(id + ":" + given).getBytes(), ((String) row.get("code_hash")).getBytes());
        if (!right) {
            jdbc.update("UPDATE phone_verifications SET attempts = attempts + 1 WHERE id = :id", Map.of("id", id));
            throw new PhoneCodeMismatchException(NOT_RIGHT);
        }
        // The policy first: a rejected password leaves the code usable.
        passwordPolicy.validate(newPassword, account.getEmail());
        jdbc.update("UPDATE phone_verifications SET confirmed_at = now() WHERE id = :id", Map.of("id", id));
        recovery.resetPasswordAfterPhoneCode(account, newPassword);
    }

    /** The account whose verified number this is, if the text is a number at all. */
    private Optional<Account> accountFor(String typed) {
        try {
            return accounts.findByPhoneE164AndPhoneVerifiedAtIsNotNull(PhoneNumbers.normalize(typed));
        } catch (IllegalArgumentException notANumber) {
            return Optional.empty();
        }
    }

}
