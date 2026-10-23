package com.platform.child.device;

import com.platform.audit.dto.RecordAuditEvent;
import com.platform.audit.entity.AuditActorType;
import com.platform.audit.service.AuditService;
import com.platform.child.entity.Child;
import com.platform.child.repository.ChildRepository;
import com.platform.common.idempotency.service.RequestFingerprint;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.family.service.FamilyAccessGuard;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.AccountStatus;
import com.platform.identity.repository.AccountRepository;
import com.platform.security.session.AccountSessionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * M10a: opening one child's learning space on another device without signing that device in as the parent.
 *
 * <ol>
 *   <li>A parent with access to the child makes a code (6 characters, 15 minutes, one at a time per child).</li>
 *   <li>The other device enters it and is told to wait; it holds a secret only it knows.</li>
 *   <li>The parent sees which device is waiting and approves it (or not) on their own device.</li>
 *   <li>The waiting device, with its secret, collects a sign-in scoped to that child only, once.</li>
 * </ol>
 *
 * Only digests of the code and the secret are kept. A wrong, used, expired or forwarded code fails the same way and
 * says nothing about any child. A child has at most {@code platform.child-devices.max-per-child} devices (owner
 * decision D-05; 3 proposed). Signing a device out is the parent's device list (AccountSessionService#signOutDevice).
 */
@Service
public class ChildDeviceLinkService {

    static final Duration CODE_LIFETIME = Duration.ofMinutes(15);
    /** No 0/O, 1/I/L: easy to read aloud and type on a child's tablet. */
    static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    static final int CODE_LENGTH = 6;

    static final String INVALID_CODE = "That code doesn't work. Ask a grown-up for a new one.";
    static final String ENDED = "This code has ended. Ask a grown-up for a new one.";
    static final String NOT_WAITING = "No device is waiting with this code: make a new one";
    static final String TOO_MANY = "This child already has as many devices as allowed: sign one out first";

    public record NewCode(UUID id, String code, Instant expiresAt) {
    }

    /** What the parent sees while a code is open: whether a device is waiting, and which. */
    public record CodeState(UUID id, String status, String deviceUserAgent, Instant expiresAt) {
    }

    public record Claim(UUID claimId, String secret) {
    }

    /** What the waiting device gets: still waiting, or its sign-in (tokens returned once). */
    public record Collected(boolean waiting, AccountSessionService.RefreshResult session, UUID childId, String childName) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final FamilyAccessGuard guard;
    private final ChildRepository children;
    private final AccountRepository accounts;
    private final AccountSessionService sessions;
    private final AuditService audit;
    private final int maxPerChild;
    private final SecureRandom random = new SecureRandom();

    public ChildDeviceLinkService(NamedParameterJdbcTemplate jdbc, FamilyAccessGuard guard, ChildRepository children,
                                  AccountRepository accounts, AccountSessionService sessions, AuditService audit,
                                  @Value("${platform.child-devices.max-per-child:3}") int maxPerChild) {
        this.jdbc = jdbc;
        this.guard = guard;
        this.children = children;
        this.accounts = accounts;
        this.sessions = sessions;
        this.audit = audit;
        this.maxPerChild = maxPerChild;
    }

    /** A new code for this child; any earlier one that hasn't been used ends. */
    @Transactional
    public NewCode create(UUID accountId, UUID childId) {
        guard.requireChildAccess(accountId, childId);
        children.findByIdForUpdate(childId).orElseThrow(() -> new ResourceNotFoundException("Child", childId));
        requireRoom(childId);
        jdbc.update("UPDATE child_device_codes SET status = 'CANCELLED' WHERE child_id = :c AND status IN ('OPEN', 'CLAIMED', 'APPROVED')",
                Map.of("c", childId));
        Instant now = Instant.now();
        Instant expiresAt = now.plus(CODE_LIFETIME);
        for (int attempt = 0; attempt < 5; attempt++) {
            String code = newCode();
            UUID id = UUID.randomUUID();
            try {
                jdbc.update("""
                        INSERT INTO child_device_codes (id, child_id, created_by_account_id, code_hash, status, expires_at, created_at)
                        VALUES (:id, :c, :by, :h, 'OPEN', :exp, :now)
                        """, new MapSqlParameterSource("id", id).addValue("c", childId).addValue("by", accountId)
                        .addValue("h", digest(code)).addValue("exp", Timestamp.from(expiresAt)).addValue("now", Timestamp.from(now)));
            } catch (DuplicateKeyException sameAsAnOpenCode) {
                continue;
            }
            record(accountId, "CHILD_DEVICE_CODE_CREATED", childId, Map.of("codeId", id.toString()));
            return new NewCode(id, code, expiresAt);
        }
        throw new IllegalStateException("Could not make a code just now; try again");
    }

    @Transactional(readOnly = true)
    public CodeState state(UUID accountId, UUID childId, UUID codeId) {
        guard.requireChildAccess(accountId, childId);
        List<CodeState> found = jdbc.query("""
                SELECT id, status, claim_user_agent, expires_at FROM child_device_codes WHERE id = :id AND child_id = :c
                """, Map.of("id", codeId, "c", childId), (rs, i) -> {
            Instant expiresAt = rs.getTimestamp(4).toInstant();
            String status = rs.getString(2);
            boolean ended = !status.equals("DELIVERED") && !status.equals("CANCELLED") && !expiresAt.isAfter(Instant.now());
            return new CodeState(rs.getObject(1, UUID.class), ended ? "EXPIRED" : status, rs.getString(3), expiresAt);
        });
        if (found.isEmpty()) {
            throw new ResourceNotFoundException("Device code", codeId);
        }
        return found.getFirst();
    }

    /** The parent says the waiting device is their child's. */
    @Transactional
    public CodeState approve(UUID accountId, UUID childId, UUID codeId) {
        guard.requireChildAccess(accountId, childId);
        children.findByIdForUpdate(childId).orElseThrow(() -> new ResourceNotFoundException("Child", childId));
        requireRoom(childId);
        int changed = jdbc.update("""
                UPDATE child_device_codes SET status = 'APPROVED', approved_at = now()
                 WHERE id = :id AND child_id = :c AND status = 'CLAIMED' AND expires_at > now()
                """, Map.of("id", codeId, "c", childId));
        if (changed != 1) {
            throw new IllegalStateException(NOT_WAITING);
        }
        record(accountId, "CHILD_DEVICE_APPROVED", childId, Map.of("codeId", codeId.toString()));
        return state(accountId, childId, codeId);
    }

    /** The parent ends the code (not their child's device, or no longer wanted). */
    @Transactional
    public CodeState cancel(UUID accountId, UUID childId, UUID codeId) {
        guard.requireChildAccess(accountId, childId);
        int changed = jdbc.update("""
                UPDATE child_device_codes SET status = 'CANCELLED'
                 WHERE id = :id AND child_id = :c AND status IN ('OPEN', 'CLAIMED', 'APPROVED')
                """, Map.of("id", codeId, "c", childId));
        if (changed == 1) {
            record(accountId, "CHILD_DEVICE_CODE_CANCELLED", childId, Map.of("codeId", codeId.toString()));
        }
        return state(accountId, childId, codeId);
    }

    /** The other device enters the code; it then waits for the parent, holding a secret only it knows. */
    @Transactional
    public Claim claim(String code, String userAgent) {
        String normalized = normalize(code);
        if (normalized.length() != CODE_LENGTH) {
            throw new IllegalArgumentException(INVALID_CODE);
        }
        String secret = randomToken();
        List<UUID> claimed = jdbc.queryForList("""
                UPDATE child_device_codes
                   SET status = 'CLAIMED', claimed_at = now(), claim_secret_hash = :s, claim_user_agent = :ua
                 WHERE code_hash = :h AND status = 'OPEN' AND expires_at > now()
                RETURNING id
                """, new MapSqlParameterSource("s", digest(secret)).addValue("ua", truncate(userAgent))
                .addValue("h", digest(normalized)), UUID.class);
        if (claimed.size() != 1) {
            throw new IllegalArgumentException(INVALID_CODE);
        }
        return new Claim(claimed.getFirst(), secret);
    }

    /** The waiting device asks again: still waiting, or - once approved - its sign-in, given exactly once. */
    @Transactional
    public Collected collect(UUID claimId, String secret, String userAgent, String ipAddress) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT child_id, created_by_account_id, status, expires_at, claim_secret_hash
                  FROM child_device_codes WHERE id = :id FOR UPDATE
                """, Map.of("id", claimId));
        if (rows.isEmpty() || secret == null || rows.getFirst().get("claim_secret_hash") == null
                || !MessageDigest.isEqual(digest(secret).getBytes(StandardCharsets.US_ASCII),
                ((String) rows.getFirst().get("claim_secret_hash")).getBytes(StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException(INVALID_CODE);
        }
        Map<String, Object> row = rows.getFirst();
        String status = (String) row.get("status");
        Object expiry = row.get("expires_at");
        Instant expiresAt = expiry instanceof Timestamp timestamp ? timestamp.toInstant() : ((java.time.OffsetDateTime) expiry).toInstant();
        if (!expiresAt.isAfter(Instant.now()) || !(status.equals("CLAIMED") || status.equals("APPROVED"))) {
            throw new IllegalStateException(ENDED);
        }
        UUID childId = (UUID) row.get("child_id");
        if (status.equals("CLAIMED")) {
            return new Collected(true, null, childId, null);
        }
        UUID parentId = (UUID) row.get("created_by_account_id");
        // The parent who approved must still have the child, and the child must still be learning.
        Account parent = accounts.findById(parentId).filter(a -> a.getStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException(ENDED));
        Child child;
        try {
            child = guard.requireChildAccess(parentId, childId);
        } catch (ResourceNotFoundException gone) {
            throw new IllegalStateException(ENDED);
        }
        children.findByIdForUpdate(childId);
        requireRoom(childId);
        AccountSessionService.ChildSession session = sessions.issueChildSession(parent, childId, userAgent, ipAddress);
        jdbc.update("UPDATE child_device_codes SET status = 'DELIVERED', delivered_at = now(), session_family_id = :f WHERE id = :id",
                Map.of("f", session.sessionFamilyId(), "id", claimId));
        record(parentId, "CHILD_DEVICE_SIGNED_IN", childId, Map.of("codeId", claimId.toString(),
                "device", session.sessionFamilyId().toString()));
        return new Collected(false, session.tokens(), childId, child.getDisplayName());
    }

    // ------------------------------------------------------------------ helpers

    private void requireRoom(UUID childId) {
        if (sessions.liveChildDevices(childId) >= maxPerChild) {
            throw new IllegalStateException(TOO_MANY);
        }
    }

    private String newCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /** What someone typed: case, spaces and dashes don't matter. */
    static String normalize(String code) {
        return code == null ? "" : code.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String digest(String value) {
        return RequestFingerprint.sha256Hex(value);
    }

    private static String truncate(String userAgent) {
        return userAgent == null ? null : userAgent.length() > 500 ? userAgent.substring(0, 500) : userAgent;
    }

    private void record(UUID accountId, String action, UUID childId, Map<String, Object> details) {
        audit.record(new RecordAuditEvent(null, accountId, AuditActorType.PARENT, action, "CHILD", childId, details));
    }

}
