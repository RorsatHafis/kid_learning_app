package com.platform.identity.phone;

import com.platform.audit.dto.RecordAuditEvent;
import com.platform.audit.entity.AuditActorType;
import com.platform.audit.service.AuditService;
import com.platform.common.idempotency.service.RequestFingerprint;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.common.web.TooManyAttemptsException;
import com.platform.identity.UiLanguage;
import com.platform.identity.entity.Account;
import com.platform.identity.exception.CurrentPasswordMismatchException;
import com.platform.identity.repository.AccountRepository;
import com.platform.identity.service.PasswordConfirmation;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M9b: a phone number on an account, proved by a 6-digit code sent to it by text message.
 *
 * <p>Asking for a code needs the password (the number becomes a way to reach the account). A code lasts 10 minutes,
 * allows 5 tries, and is kept only as a digest; asking again replaces the previous one. An account can ask once a
 * minute and 5 times an hour, and a number receives at most 5 codes an hour. A number verified on another account is
 * answered exactly like any other but no text is sent, so nobody learns whose number it is; it can't be verified here.
 * Confirming the code makes it the account's verified number (one account per number, decided under a lock on the
 * number).
 */
@Service
public class PhoneVerificationService {

    static final Duration CODE_LIFETIME = Duration.ofMinutes(10);
    static final int MAX_TRIES = 5;
    static final Duration RESEND_AFTER = Duration.ofMinutes(1);
    static final int PER_HOUR = 5;

    static final String SAME_NUMBER = "That's already your verified number";
    static final String WAIT = "Wait a minute before asking for another code";
    static final String TOO_MANY = "Too many codes asked for in the last hour: try again later";
    static final String NO_CODE = "Ask for a code first";
    static final String EXPIRED = "That code has expired: ask for a new one";
    static final String NO_TRIES_LEFT = "Too many wrong tries: ask for a new code";
    static final String WRONG = "That code isn't right";
    static final String TAKEN = "This number is verified on another account, so it can't be added here";
    static final String NO_PHONE = "There's no phone number on this account";

    /** What asking for a code answers: where it went (masked) and until when it works. */
    public record CodeSent(String phone, Instant expiresAt) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final AccountRepository accounts;
    private final PasswordConfirmation passwords;
    private final SmsSender sms;
    private final AuditService audit;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @org.springframework.beans.factory.annotation.Autowired
    public PhoneVerificationService(NamedParameterJdbcTemplate jdbc, AccountRepository accounts, PasswordConfirmation passwords,
                                    SmsSender sms, AuditService audit) {
        this(jdbc, accounts, passwords, sms, audit, Clock.systemUTC());
    }

    PhoneVerificationService(NamedParameterJdbcTemplate jdbc, AccountRepository accounts, PasswordConfirmation passwords,
                             SmsSender sms, AuditService audit, Clock clock) {
        this.jdbc = jdbc;
        this.accounts = accounts;
        this.passwords = passwords;
        this.sms = sms;
        this.audit = audit;
        this.clock = clock;
    }

    /** Sends a code to {@code typed} (normalized), for this account to confirm. */
    @Transactional(noRollbackFor = CurrentPasswordMismatchException.class)
    public CodeSent request(UUID accountId, String password, String typed) {
        if (!sms.available()) {
            throw new PhoneUnavailableException();
        }
        Account account = passwords.confirm(accountId, password);
        String phone = PhoneNumbers.normalize(typed);
        if (phone.equals(account.getPhoneE164()) && account.getPhoneVerifiedAt() != null) {
            throw new IllegalStateException(SAME_NUMBER);
        }
        Instant now = clock.instant();
        Instant last = jdbc.query("SELECT max(issued_at) FROM phone_verifications WHERE account_id = :a", Map.of("a", accountId),
                rs -> rs.next() && rs.getTimestamp(1) != null ? rs.getTimestamp(1).toInstant() : null);
        if (last != null && last.plus(RESEND_AFTER).isAfter(now)) {
            throw new TooManyAttemptsException(WAIT, Duration.between(now, last.plus(RESEND_AFTER)));
        }
        Timestamp hourAgo = Timestamp.from(now.minus(Duration.ofHours(1)));
        Integer byAccount = jdbc.queryForObject("SELECT count(*) FROM phone_verifications WHERE account_id = :a AND issued_at > :h",
                new MapSqlParameterSource("a", accountId).addValue("h", hourAgo), Integer.class);
        Integer byNumber = jdbc.queryForObject("SELECT count(*) FROM phone_verifications WHERE phone_e164 = :p AND issued_at > :h AND sent",
                new MapSqlParameterSource("p", phone).addValue("h", hourAgo), Integer.class);
        if ((byAccount != null && byAccount >= PER_HOUR) || (byNumber != null && byNumber >= PER_HOUR)) {
            throw new TooManyAttemptsException(TOO_MANY, Duration.ofHours(1));
        }
        jdbc.update("UPDATE phone_verifications SET superseded_at = :n WHERE account_id = :a AND confirmed_at IS NULL AND superseded_at IS NULL",
                new MapSqlParameterSource("n", Timestamp.from(now)).addValue("a", accountId));
        boolean takenElsewhere = verifiedElsewhere(phone, accountId);
        UUID id = UUID.randomUUID();
        String code = String.format("%06d", random.nextInt(1_000_000));
        Instant expires = now.plus(CODE_LIFETIME);
        jdbc.update("""
                INSERT INTO phone_verifications (id, account_id, phone_e164, code_hash, issued_at, expires_at, sent)
                VALUES (:id, :a, :p, :h, :i, :e, :s)
                """, new MapSqlParameterSource("id", id).addValue("a", accountId).addValue("p", phone).addValue("h", digest(id, code))
                .addValue("i", Timestamp.from(now)).addValue("e", Timestamp.from(expires)).addValue("s", !takenElsewhere));
        if (!takenElsewhere) {
            sms.send(phone, text(account, code));
        }
        record(account, "PHONE_CODE_REQUESTED", Map.of("phone", PhoneNumbers.masked(phone)));
        return new CodeSent(PhoneNumbers.masked(phone), expires);
    }

    /** Confirms the newest code; the number becomes the account's verified number. */
    @Transactional(noRollbackFor = PhoneCodeMismatchException.class)
    public Account confirm(UUID accountId, String code) {
        Account account = accounts.findForUpdate(accountId).orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        List<Map<String, Object>> open = jdbc.queryForList("""
                SELECT id, phone_e164, code_hash, expires_at, attempts, sent FROM phone_verifications
                 WHERE account_id = :a AND confirmed_at IS NULL AND superseded_at IS NULL ORDER BY issued_at DESC LIMIT 1
                """, Map.of("a", accountId));
        if (open.isEmpty()) {
            throw new IllegalStateException(NO_CODE);
        }
        Map<String, Object> row = open.getFirst();
        UUID id = (UUID) row.get("id");
        Instant now = clock.instant();
        if (((Timestamp) row.get("expires_at")).toInstant().isBefore(now)) {
            throw new IllegalStateException(EXPIRED);
        }
        if (((Number) row.get("attempts")).intValue() >= MAX_TRIES) {
            throw new IllegalStateException(NO_TRIES_LEFT);
        }
        String given = code == null ? "" : code.replaceAll("\\s", "");
        boolean right = given.matches("\\d{6}") && Boolean.TRUE.equals(row.get("sent"))
                && java.security.MessageDigest.isEqual(digest(id, given).getBytes(), ((String) row.get("code_hash")).getBytes());
        if (!right) {
            jdbc.update("UPDATE phone_verifications SET attempts = attempts + 1 WHERE id = :id", Map.of("id", id));
            throw new PhoneCodeMismatchException(WRONG);
        }
        String phone = (String) row.get("phone_e164");
        // One account per verified number: decided under the number's own lock.
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended('phone:' || :p, 0))::text", Map.of("p", phone), String.class);
        if (verifiedElsewhere(phone, accountId)) {
            throw new IllegalStateException(TAKEN);
        }
        jdbc.update("UPDATE phone_verifications SET confirmed_at = :n WHERE id = :id",
                new MapSqlParameterSource("n", Timestamp.from(now)).addValue("id", id));
        account.verifyPhone(phone, now);
        accounts.saveAndFlush(account);
        record(account, "PHONE_VERIFIED", Map.of("phone", PhoneNumbers.masked(phone)));
        return account;
    }

    /** Removes the account's phone number (the password again). */
    @Transactional(noRollbackFor = CurrentPasswordMismatchException.class)
    public Account remove(UUID accountId, String password) {
        Account account = passwords.confirm(accountId, password);
        if (account.getPhoneE164() == null) {
            throw new IllegalStateException(NO_PHONE);
        }
        String phone = account.getPhoneE164();
        account.removePhone();
        accounts.saveAndFlush(account);
        jdbc.update("UPDATE phone_verifications SET superseded_at = now() WHERE account_id = :a AND confirmed_at IS NULL AND superseded_at IS NULL",
                Map.of("a", accountId));
        record(account, "PHONE_REMOVED", Map.of("phone", PhoneNumbers.masked(phone)));
        return account;
    }

    // ------------------------------------------------------------------ helpers

    private boolean verifiedElsewhere(String phone, UUID accountId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM accounts WHERE phone_e164 = :p AND phone_verified_at IS NOT NULL AND id <> :a",
                new MapSqlParameterSource("p", phone).addValue("a", accountId), Integer.class);
        return n != null && n > 0;
    }

    private static String text(Account account, String code) {
        return UiLanguage.isKhmer(account.getPreferredLanguage())
                ? "លេខកូដ Bookeyond Kid របស់អ្នកគឺ " + code + "។ វាប្រើបាន 10 នាទី។ កុំចែករំលែកវាជាមួយនរណាម្នាក់។"
                : "Your Bookeyond Kid code is " + code + ". It works for 10 minutes. Don't share it with anyone.";
    }

    private static String digest(UUID id, String code) {
        return RequestFingerprint.sha256Hex(id + ":" + code);
    }

    private void record(Account account, String action, Map<String, Object> details) {
        audit.record(new RecordAuditEvent(null, account.getId(), AuditActorType.valueOf(account.getPlatformRole().name()), action,
                "ACCOUNT", account.getId(), details));
    }

}
