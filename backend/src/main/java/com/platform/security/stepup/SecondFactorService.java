package com.platform.security.stepup;

import com.platform.audit.dto.RecordAuditEvent;
import com.platform.audit.entity.AuditActorType;
import com.platform.audit.service.AuditService;
import com.platform.common.idempotency.service.RequestFingerprint;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.exception.CurrentPasswordMismatchException;
import com.platform.identity.repository.AccountRepository;
import com.platform.identity.service.PasswordConfirmation;
import com.platform.security.SecurityProperties;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * M9a: an administrator's second factor - an authenticator app (TOTP), with one-time recovery codes for a lost phone.
 * Turning it on needs the password and is complete once a first code is confirmed. With it on, a step-up (M8) needs
 * the password and a code; an administrator without one can't step up (the roadmap's rule that grants and payment
 * changes need a second factor). A code is never accepted twice. Wrong codes are counted on their own (the password
 * that comes with each one resets the sign-in count) and at the sign-in limit lock the account like wrong passwords.
 * The secret is kept encrypted (AES-GCM, with a key derived from the JWT secret for this purpose only) and shown once,
 * when it is set up; recovery codes are kept only as digests.
 */
@Service
public class SecondFactorService {

    static final int RECOVERY_CODES = 8;
    private static final String ISSUER = "Bookeyond Kid";

    static final String ADMINS_ONLY = "Authenticator apps are for administrator accounts";
    static final String ALREADY_ON = "Your authenticator app is already on: turn it off first to set up another";
    static final String NOT_STARTED = "Start setting up your authenticator app first";
    static final String NOT_ON = "Your authenticator app isn't on";
    static final String NEEDED = "Administrators need an authenticator app for this: turn one on from your account page";
    static final String ENTER_CODE = "Enter the 6-digit code from your authenticator app";
    static final String WRONG_CODE = "That code isn't right. Use the newest code from your authenticator app";
    static final String SELF_RESET = "Another administrator turns off your authenticator app";

    /** What setup shows once: the key to type into an authenticator app, and the same as an otpauth:// link. */
    public record Setup(String secret, String otpauthUri) {
    }

    public record Status(boolean enabled, boolean pending, int recoveryCodesLeft) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final AccountRepository accounts;
    private final PasswordConfirmation passwords;
    private final SecurityProperties security;
    private final AuditService audit;
    private final SecureRandom random = new SecureRandom();
    private final byte[] key;

    public SecondFactorService(NamedParameterJdbcTemplate jdbc, AccountRepository accounts, PasswordConfirmation passwords,
                               SecurityProperties security, AuditService audit) {
        this.jdbc = jdbc;
        this.accounts = accounts;
        this.passwords = passwords;
        this.security = security;
        this.audit = audit;
        this.key = derivedKey(security.jwt().secret());
    }

    @Transactional(readOnly = true)
    public Status status(UUID accountId) {
        List<String> state = jdbc.queryForList("SELECT status FROM account_second_factors WHERE account_id = :a", Map.of("a", accountId),
                String.class);
        Integer left = jdbc.queryForObject("SELECT count(*) FROM account_recovery_codes WHERE account_id = :a AND used_at IS NULL",
                Map.of("a", accountId), Integer.class);
        boolean enabled = state.contains("ACTIVE");
        return new Status(enabled, state.contains("PENDING"), enabled && left != null ? left : 0);
    }

    /** Starts (or starts again) setting up: a new secret, not in force until a first code confirms it. */
    @Transactional(noRollbackFor = CurrentPasswordMismatchException.class)
    public Setup setUp(UUID accountId, String password) {
        Account account = passwords.confirm(accountId, password);
        requireAdmin(account);
        if (status(accountId).enabled()) {
            throw new IllegalStateException(ALREADY_ON);
        }
        byte[] secret = Totp.newSecret();
        jdbc.update("DELETE FROM account_second_factors WHERE account_id = :a", Map.of("a", accountId));
        jdbc.update("INSERT INTO account_second_factors (account_id, secret_ciphertext, status) VALUES (:a, :s, 'PENDING')",
                new MapSqlParameterSource("a", accountId).addValue("s", encrypt(secret)));
        String base32 = Totp.base32(secret);
        return new Setup(base32, "otpauth://totp/" + uriPart(ISSUER + ":" + account.getEmail()) + "?secret=" + base32
                + "&issuer=" + uriPart(ISSUER) + "&algorithm=SHA1&digits=" + Totp.DIGITS + "&period=" + Totp.STEP_SECONDS);
    }

    /** Confirms setup with a first code; returns the recovery codes, shown this once. */
    @Transactional(noRollbackFor = SecondFactorMismatchException.class)
    public List<String> activate(UUID accountId, String code) {
        Account account = accounts.findForUpdate(accountId).orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        requireAdmin(account);
        if (account.isLocked(Instant.now())) {
            throw new com.platform.identity.exception.AccountLockedException(account.getLockedUntil());
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT secret_ciphertext, status FROM account_second_factors WHERE account_id = :a", Map.of("a", accountId));
        if (rows.isEmpty() || !"PENDING".equals(rows.getFirst().get("status"))) {
            throw new IllegalStateException(NOT_STARTED);
        }
        if (code == null || code.isBlank()) {
            throw new SecondFactorMismatchException(ENTER_CODE);
        }
        OptionalLong step = Totp.verify(decrypt((String) rows.getFirst().get("secret_ciphertext")), code, Instant.now(), null);
        if (step.isEmpty()) {
            fail(account);
        }
        jdbc.update("""
                UPDATE account_second_factors SET status = 'ACTIVE', activated_at = now(), last_used_step = :s, failed_attempts = 0
                 WHERE account_id = :a""", Map.of("s", step.getAsLong(), "a", accountId));
        jdbc.update("DELETE FROM account_recovery_codes WHERE account_id = :a", Map.of("a", accountId));
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODES; i++) {
            byte[] bytes = new byte[8];
            random.nextBytes(bytes);
            String recovery = Totp.base32(bytes).substring(0, 10);
            codes.add(recovery.substring(0, 5) + "-" + recovery.substring(5));
            jdbc.update("INSERT INTO account_recovery_codes (id, account_id, code_hash) VALUES (:id, :a, :h)",
                    new MapSqlParameterSource("id", UUID.randomUUID()).addValue("a", accountId).addValue("h", digest(recovery)));
        }
        record(account, "SECOND_FACTOR_ENABLED", Map.of("recoveryCodes", RECOVERY_CODES));
        return codes;
    }

    /**
     * What a step-up needs beyond the password (the caller has confirmed it and holds the account row locked): for an
     * administrator, a TOTP code newer than the last one accepted, or an unused recovery code, which is then used up.
     */
    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = SecondFactorMismatchException.class)
    public void requireForStepUp(Account account, String code) {
        if (account.getPlatformRole() != PlatformRole.ADMIN) {
            return;
        }
        if (!status(account.getId()).enabled()) {
            throw SecondFactorMismatchException.setupRequired(NEEDED);
        }
        verify(account, code);
    }

    /** Turns it off: the password and a current code (or a recovery code). */
    @Transactional(noRollbackFor = {CurrentPasswordMismatchException.class, SecondFactorMismatchException.class})
    public void turnOff(UUID accountId, String password, String code) {
        Account account = passwords.confirm(accountId, password);
        if (!status(accountId).enabled()) {
            throw new IllegalStateException(NOT_ON);
        }
        verify(account, code);
        remove(accountId);
        record(account, "SECOND_FACTOR_DISABLED", Map.of());
    }

    /**
     * Another administrator turns off a colleague's authenticator app (a lost phone and recovery codes); behind a
     * step-up, which needs the acting administrator's own code.
     */
    @Transactional
    public void resetFor(UUID adminId, UUID accountId) {
        Account admin = accounts.findById(adminId).orElseThrow(() -> new ResourceNotFoundException("Account", adminId));
        requireAdmin(admin);
        if (adminId.equals(accountId)) {
            throw new AccessDeniedException(SELF_RESET);
        }
        accounts.findForUpdate(accountId).orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        Status status = status(accountId);
        if (!status.enabled() && !status.pending()) {
            throw new IllegalStateException(NOT_ON);
        }
        remove(accountId);
        audit.record(new RecordAuditEvent(null, adminId, AuditActorType.ADMIN, "SECOND_FACTOR_RESET", "ACCOUNT", accountId, Map.of()));
    }

    // ------------------------------------------------------------------ helpers

    private void verify(Account account, String code) {
        if (code == null || code.isBlank()) {
            throw new SecondFactorMismatchException(ENTER_CODE);
        }
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT secret_ciphertext, last_used_step FROM account_second_factors WHERE account_id = :a AND status = 'ACTIVE'",
                Map.of("a", account.getId()));
        Long lastStep = row.get("last_used_step") == null ? null : ((Number) row.get("last_used_step")).longValue();
        OptionalLong step = Totp.verify(decrypt((String) row.get("secret_ciphertext")), code, Instant.now(), lastStep);
        if (step.isPresent()) {
            jdbc.update("UPDATE account_second_factors SET last_used_step = :s, failed_attempts = 0 WHERE account_id = :a",
                    Map.of("s", step.getAsLong(), "a", account.getId()));
            return;
        }
        String recovery = code.replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
        int used = recovery.length() == 10 ? jdbc.update("""
                UPDATE account_recovery_codes SET used_at = now()
                 WHERE id = (SELECT id FROM account_recovery_codes WHERE account_id = :a AND code_hash = :h AND used_at IS NULL LIMIT 1)
                """, Map.of("a", account.getId(), "h", digest(recovery))) : 0;
        if (used == 1) {
            jdbc.update("UPDATE account_second_factors SET failed_attempts = 0 WHERE account_id = :a", Map.of("a", account.getId()));
            record(account, "RECOVERY_CODE_USED", Map.of());
            return;
        }
        fail(account);
    }

    private void fail(Account account) {
        Integer failed = jdbc.queryForObject(
                "UPDATE account_second_factors SET failed_attempts = failed_attempts + 1 WHERE account_id = :a RETURNING failed_attempts",
                Map.of("a", account.getId()), Integer.class);
        if (failed != null && failed >= security.lockout().maxAttempts()) {
            // Locks the account as the sign-in limit does; the count starts again after the lockout.
            account.recordFailedLogin(1, security.lockout().duration(), Instant.now());
            accounts.saveAndFlush(account);
            jdbc.update("UPDATE account_second_factors SET failed_attempts = 0 WHERE account_id = :a", Map.of("a", account.getId()));
            record(account, "SECOND_FACTOR_LOCKED", Map.of("failedCodes", failed));
        }
        throw new SecondFactorMismatchException(WRONG_CODE);
    }

    private void remove(UUID accountId) {
        jdbc.update("DELETE FROM account_recovery_codes WHERE account_id = :a", Map.of("a", accountId));
        jdbc.update("DELETE FROM account_second_factors WHERE account_id = :a", Map.of("a", accountId));
    }

    private static void requireAdmin(Account account) {
        if (account.getPlatformRole() != PlatformRole.ADMIN) {
            throw new AccessDeniedException(ADMINS_ONLY);
        }
    }

    private void record(Account account, String action, Map<String, Object> details) {
        audit.record(new RecordAuditEvent(null, account.getId(), AuditActorType.valueOf(account.getPlatformRole().name()), action,
                "ACCOUNT", account.getId(), details));
    }

    private static String uriPart(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String digest(String text) {
        return RequestFingerprint.sha256Hex(text);
    }

    private static byte[] derivedKey(String jwtSecret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal("bookeyond-second-factor-secret-v1".getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private String encrypt(byte[] secret) {
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            byte[] sealed = cipher.doFinal(secret);
            byte[] out = new byte[iv.length + sealed.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(sealed, 0, out, iv.length, sealed.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private byte[] decrypt(String stored) {
        try {
            byte[] in = Base64.getDecoder().decode(stored);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, in, 0, 12));
            return cipher.doFinal(in, 12, in.length - 12);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("An authenticator secret can't be read: has JWT_SECRET changed?", e);
        }
    }

}
