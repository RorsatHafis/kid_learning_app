package com.platform.security.session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.platform.audit.dto.RecordAuditEvent;
import com.platform.audit.entity.AuditActorType;
import com.platform.audit.service.AuditService;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.AccountStatus;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.repository.AccountRepository;
import com.platform.security.SecurityProperties;
import com.platform.security.jwt.JwtService;

/**
 * Owns the full server-side refresh-token session lifecycle backing {@link
 * com.platform.identity.web.AuthController}: issuing a session at
 * login/registration, rotating it on every successful refresh, detecting reuse
 * of an already-consumed token, and revoking a session (or a whole compromised
 * "family" of rotated descendants) on logout or reuse.
 *
 * SECURITY MODEL
 * ----------------------------------------------------------------------------
 * - Only {@link #hash(String)} (SHA-256 hex, 64 lowercase characters, matching
 *   the V9 migration's {@code refresh_token_hash CHAR(64)} column) of the raw
 *   refresh token is ever persisted, via {@link AccountSession#issue}. The raw
 *   token exists only in memory on the issuing/refreshing request and in that
 *   one response body - it is never logged and never appears in an exception
 *   message (every failure path here throws the same generic {@link
 *   InvalidSessionException}).
 * - Refresh tokens are single-use. Every successful refresh atomically marks
 *   the presented session ROTATED and creates a brand-new ACTIVE session linked
 *   via {@code replacedBySessionId}, forming a "family" chain that traces back
 *   to the original login/registration session.
 * - Presenting an already-ROTATED or already-REVOKED token is treated as
 *   evidence the token leaked - a legitimate client only ever holds the
 *   *latest* token in the chain. {@link #refresh} responds by revoking every
 *   session in that token's family ({@code family_id}, V37), not just the one
 *   presented, per OAuth 2.0 refresh-token-rotation best practice (RFC 9700,
 *   "refresh token rotation" with reuse detection).
 * - One exception, the lost response (PD-03, N19): a refresh can succeed on the
 *   server while its response never reaches the client (a reload or navigation
 *   mid-request, a dropped connection), leaving the client holding the token it
 *   just rotated away. A retry of that token is honoured only when <em>all</em>
 *   of these hold: it arrives within the short reuse grace window of the first
 *   rotation; the replacement issued then has never been used; and it comes from
 *   the same user agent the replacement was issued to. The unused replacement is
 *   revoked and a new one issued in its place, so the family never has more than
 *   one live token and any later use of a superseded token still trips reuse
 *   detection. Anything else - a used replacement, another client, a late retry -
 *   is replay, exactly as before. Recoveries and detections are audited.
 * - Rotation and revocation are conditional, single-statement bulk updates
 *   ({@link AccountSessionRepository#rotateIfActive}, {@link
 *   AccountSessionRepository#revokeIfRevocable}), each guarded by a {@code
 *   WHERE status = ...} clause, not a read-modify-save on the loaded entity.
 *   Two concurrent refresh calls presenting the same still-active token
 *   therefore race safely at the database: exactly one UPDATE affects a row,
 *   and the loser is treated identically to a reuse attempt (its own
 *   newly-minted session is immediately revoked and the whole family is
 *   revoked), rather than relying on catching a Hibernate optimistic-lock
 *   failure mid-transaction.
 */
@Service
public class AccountSessionService {

    /** 256 bits of entropy - well beyond what's brute-forceable, and the standard size for bearer session tokens. */
    private static final int RAW_TOKEN_BYTES = 32;


    private final AccountSessionRepository sessions;
    private final AccountRepository accounts;
    private final JwtService jwtService;
    private final Duration refreshTokenTtl;
    private final Duration refreshReuseGrace;
    private final AuditService auditService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AccountSessionService(AccountSessionRepository sessions, AccountRepository accounts,
                                  JwtService jwtService, SecurityProperties securityProperties,
                                  AuditService auditService) {

        this.sessions = sessions;
        this.accounts = accounts;
        this.jwtService = jwtService;
        this.refreshTokenTtl = securityProperties.jwt().refreshTokenTtl();
        this.refreshReuseGrace = securityProperties.jwt().refreshReuseGrace();
        this.auditService = auditService;

    }

    /**
     * Starts a brand-new session at login or registration. Returns the raw token
     * once - the caller (LoginService / AuthController) must hand it straight to
     * the client and must never persist or log it itself.
     */
    @Transactional
    public IssuedSession issueSession(UUID accountId, String userAgent, String ipAddress) {

        Instant now = Instant.now();
        String rawToken = generateRawToken();

        AccountSession session = AccountSession.issue(
                accountId, hash(rawToken), now, now.plus(refreshTokenTtl), userAgent, ipAddress);

        sessions.save(session);

        return new IssuedSession(rawToken, session.getId(), session.getFamilyId(), session.getExpiresAt());

    }

    /**
     * Validates the presented raw refresh token, rotates it, and re-issues both
     * an access token and a replacement refresh token. Every failure path -
     * unknown token, time-expired token, already-rotated/revoked token (reuse),
     * disabled/missing account, or a lost rotation race - throws the same {@link
     * InvalidSessionException} so a client can never distinguish "ordinary
     * expiry" from "we just detected reuse and revoked your session family"
     * from the response alone.
     *
     * <p>Battle 4: {@code noRollbackFor = InvalidSessionException.class} is what
     * makes the revocations below durable. Every revoking branch (reuse, lost
     * rotation race, disabled account) writes via {@link #revokeFamily} /
     * {@link AccountSessionRepository#revokeIfRevocable} and then throws
     * InvalidSessionException - a RuntimeException, which under the default
     * rollback rule would silently undo those revocations while still telling
     * the caller "rejected". Same pattern as {@code LoginService#login}, which
     * must likewise persist a failed-login counter while throwing. This relies
     * on this method owning its transaction: its only caller, {@code
     * AuthController#refresh}, is non-transactional, and it must stay that way -
     * an enclosing transaction would apply its own rollback rule to this
     * exception and undo the revocations again.
     */
    @Transactional(noRollbackFor = InvalidSessionException.class)
    public RefreshResult refresh(String rawRefreshToken, String userAgent, String ipAddress) {

        String presentedHash = hash(rawRefreshToken);
        Instant now = Instant.now();

        // Row-locked: every refresh presenting this token is serialized behind this one.
        AccountSession presented = sessions.findForRefreshByRefreshTokenHash(presentedHash)
                .orElseThrow(InvalidSessionException::new);

        if (presented.getStatus() == AccountSessionStatus.ROTATED) {

            Optional<RefreshResult> recovered = recoverLostResponse(presented, userAgent, ipAddress, now);
            if (recovered.isPresent()) {
                return recovered.get();
            }

        }

        if (presented.getStatus() == AccountSessionStatus.ROTATED
                || presented.getStatus() == AccountSessionStatus.REVOKED) {

            // A token that was already consumed (or already revoked) being presented
            // again is exactly the reuse signal this method exists to catch - a
            // legitimate client only ever holds the newest token in the chain.
            revokeFamily(presented, "REFRESH_REPLAYED");
            throw new InvalidSessionException();

        }

        if (!presented.isUsable(now)) {

            // Ordinary time expiry (or a future cleanup job's EXPIRED status) - not
            // evidence of reuse, so no family revocation, just a clean rejection.
            throw new InvalidSessionException();

        }

        Account account = activeAccount(presented);

        if (account == null) {

            // The account was disabled (or is otherwise no longer eligible) since this
            // session was issued - refresh must not resurrect access for it. This is
            // not a reuse signal (nothing about the token itself was replayed), so
            // only this one session is revoked, not its whole family.
            sessions.revokeIfRevocable(presented.getId(), now);
            throw new InvalidSessionException();

        }

        String newRawToken = generateRawToken();
        AccountSession next = successorOf(presented, newRawToken, userAgent, ipAddress, now);

        int rotated = sessions.rotateIfActive(presented.getId(), next.getId(), now);

        if (rotated == 0) {

            // Cannot happen while the presented row is locked; kept fail-closed: the
            // session just minted must not survive, and the family is treated as compromised.
            sessions.revokeIfRevocable(next.getId(), now);
            revokeFamily(presented, "REFRESH_ROTATION_RACE");
            throw new InvalidSessionException();

        }

        return result(account, newRawToken, next);

    }

    /**
     * The lost-response retry described in the class javadoc: honoured only within the
     * grace window of the first rotation, only while the replacement issued then has
     * never been used, and only for the user agent it was issued to. Empty means "not a
     * recoverable retry" - the caller then treats the token as replayed.
     */
    private Optional<RefreshResult> recoverLostResponse(AccountSession presented, String userAgent, String ipAddress,
                                                        Instant now) {

        if (refreshReuseGrace.isZero()
                || presented.getRotatedAt() == null
                || !now.isBefore(presented.getRotatedAt().plus(refreshReuseGrace))
                || presented.getReplacedBySessionId() == null) {
            return Optional.empty();
        }

        AccountSession replacement = sessions.findById(presented.getReplacedBySessionId()).orElse(null);
        if (replacement == null
                || !replacement.isUsable(now)
                || !replacement.sameUserAgent(userAgent)) {
            return Optional.empty();
        }

        Account account = activeAccount(presented);
        if (account == null || sessions.revokeIfActive(replacement.getId(), now) != 1) {
            return Optional.empty();
        }

        String newRawToken = generateRawToken();
        AccountSession next = successorOf(presented, newRawToken, userAgent, ipAddress, now);
        if (sessions.repointRotated(presented.getId(), next.getId()) != 1) {
            // Fail closed: the presented session is no longer ROTATED.
            sessions.revokeIfRevocable(next.getId(), now);
            return Optional.empty();
        }

        audit(account, "SESSION_REFRESH_RETRY_RECOVERED", presented.getFamilyId(),
                java.util.Map.of("sessionId", presented.getId()));
        return Optional.of(result(account, newRawToken, next));

    }

    private AccountSession successorOf(AccountSession presented, String rawToken, String userAgent, String ipAddress,
                                       Instant now) {

        AccountSession next = AccountSession.successor(presented, hash(rawToken), now, now.plus(refreshTokenTtl), userAgent, ipAddress);
        sessions.save(next);
        sessions.flush();
        return next;

    }

    private Account activeAccount(AccountSession session) {

        return accounts.findById(session.getAccountId())
                .filter(account -> account.getStatus() == AccountStatus.ACTIVE)
                .orElse(null);

    }

    private RefreshResult result(Account account, String rawToken, AccountSession session) {

        JwtService.IssuedToken accessToken = jwtService.issueAccessToken(account.getId(), account.getPlatformRole(),
                session.getFamilyId(), session.getChildId());
        // M10a: a child's device is never told the parent's email address.
        return new RefreshResult(account.getId(), session.getChildId() == null ? account.getEmail() : null, account.getPlatformRole(),
                accessToken.token(), accessToken.expiresAt(), rawToken, session.getExpiresAt(), session.getChildId());

    }

    /**
     * Revokes only the one session matching the presented refresh token.
     * Deliberately idempotent and silent about whether a matching session
     * existed - logout is a "make sure this token can never be used again"
     * instruction, not a query, and treating an unknown/already-invalid token as
     * an error would both leak information and give the caller nothing useful to
     * react to. Deleting the token client-side is not equivalent to calling this:
     * without it, the hash stays ACTIVE/ROTATED server-side and remains usable
     * (or, if already rotated, still trips reuse detection) until it expires.
     *
     * <p>Presenting the current (ACTIVE) token revokes only this single session - not
     * its family: logging out is an ordinary, expected action on one device and must
     * not silently end every other session/device sharing the same login lineage.
     * Presenting an already-ROTATED token is replay and revokes the family (Battle 4D;
     * see the inline comment for why revoking just that row would defeat reuse detection).
     */
    @Transactional
    public void logout(String rawRefreshToken) {

        String presentedHash = hash(rawRefreshToken);
        Instant now = Instant.now();

        sessions.findByRefreshTokenHash(presentedHash).ifPresent(session -> {

            if (session.getStatus() == AccountSessionStatus.ROTATED) {

                // Battle 4D: a legitimate client only ever holds the newest token, so an
                // already-rotated token presented here is replay - exactly as in refresh().
                // Revoking just this row would also clear its replacedBySessionId (required
                // by ck_account_sessions_rotated_consistency) and cut the chain: whoever
                // holds the newer tokens (e.g. a thief who refreshed first and then "logged
                // out" the stolen token) would then survive a later reuse detection, which
                // walks the chain. revokeFamily reads the whole chain before revoking any of it.
                revokeFamily(session, "LOGOUT_WITH_ROTATED_TOKEN");
                return;

            }

            sessions.revokeIfRevocable(session.getId(), now);

        });

    }

    /**
     * Revokes every usable session the account has, on every device. Access tokens
     * already issued stay valid until their own short expiry (PD-02).
     *
     * @return how many live sessions (signed-in devices) were ended - the ACTIVE rows;
     *         ROTATED rows are earlier links of those same sessions' refresh chains
     */
    @Transactional
    public int revokeAllSessions(UUID accountId) {

        int live = (int) sessions.countByAccountIdAndStatus(accountId, AccountSessionStatus.ACTIVE);
        sessions.revokeAllForAccount(accountId, Instant.now());
        return live;

    }

    /**
     * Reuse detected: revokes every usable session of the presented session's family
     * (one statement over {@code family_id}) and records it for security monitoring.
     */
    private void revokeFamily(AccountSession member, String reason) {

        int revoked = sessions.revokeFamily(member.getFamilyId(), Instant.now());
        accounts.findById(member.getAccountId()).ifPresent(account -> audit(account, "SESSION_REUSE_DETECTED",
                member.getFamilyId(), java.util.Map.of("reason", reason, "sessionsRevoked", revoked)));

    }

    private void audit(Account account, String action, UUID familyId, java.util.Map<String, Object> metadata) {

        auditService.record(new RecordAuditEvent(null, account.getId(),
                AuditActorType.valueOf(account.getPlatformRole().name()), action, "SESSION_FAMILY", familyId, metadata));

    }

    private String generateRawToken() {

        byte[] bytes = new byte[RAW_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

    }

    /** Matches {@link com.platform.common.idempotency.service.RequestFingerprint#sha256Hex} exactly, kept local to this security-critical class rather than shared. */
    private static String hash(String rawToken) {

        try {

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hashBytes);

        } catch (NoSuchAlgorithmException e) {

            // SHA-256 is a mandatory JDK algorithm (JLS/JCA baseline) - this can't actually happen.
            throw new IllegalStateException("SHA-256 unavailable", e);

        }

    }

    public record IssuedSession(String rawToken, UUID sessionId, UUID familyId, Instant expiresAt) {
    }

    /**
     * One signed-in device: a session family with a live session. {@code signedInAt} is
     * when that sign-in began; {@code lastActiveAt} is its latest refresh (a device in use
     * refreshes about every quarter of an hour).
     */
    public record SignedInDevice(UUID id, String userAgent, Instant signedInAt, Instant lastActiveAt, UUID childId) {
    }

    /** The account's signed-in devices, most recently active first. */
    @Transactional(readOnly = true)
    public java.util.List<SignedInDevice> devices(UUID accountId) {

        Instant now = Instant.now();
        java.util.Map<UUID, AccountSession> newestPerFamily = new java.util.LinkedHashMap<>();
        for (AccountSession live : sessions.findLive(accountId, now)) {
            newestPerFamily.putIfAbsent(live.getFamilyId(), live);
        }
        if (newestPerFamily.isEmpty()) {
            return java.util.List.of();
        }
        java.util.Map<UUID, Instant> began = new java.util.HashMap<>();
        for (Object[] row : sessions.familyStarts(newestPerFamily.keySet())) {
            began.put((UUID) row[0], (Instant) row[1]);
        }
        return newestPerFamily.values().stream()
                .map(s -> new SignedInDevice(s.getFamilyId(), s.getUserAgent(),
                        began.getOrDefault(s.getFamilyId(), s.getIssuedAt()), s.getIssuedAt(), s.getChildId()))
                .toList();

    }

    /**
     * Signs one device out: ends its whole session family. False when the account has no
     * such sign-in (the caller answers 404, the same as for someone else's). Its access
     * token, if any, stays valid until its own short expiry (PD-02).
     */
    @Transactional
    public boolean signOutDevice(UUID accountId, UUID familyId) {

        if (!sessions.existsByAccountIdAndFamilyId(accountId, familyId)) {
            return false;
        }
        int revoked = sessions.revokeFamily(familyId, Instant.now());
        accounts.findById(accountId).ifPresent(account -> audit(account, "ACCOUNT_DEVICE_SIGNED_OUT", familyId,
                java.util.Map.of("sessionsRevoked", revoked)));
        return true;

    }

    /** {@code childId}: set for a child's device (M10a), whose sign-in may act only for that child. */
    public record RefreshResult(UUID accountId, String email, PlatformRole platformRole, String accessToken,
                                 Instant accessTokenExpiresAt, String refreshToken, Instant refreshTokenExpiresAt, UUID childId) {
    }

    /**
     * M10a: a sign-in for a child's device - the parent's account, scoped to one child - with its access token. The
     * raw refresh token is returned once, to hand straight to that device.
     */
    @Transactional
    public ChildSession issueChildSession(Account account, UUID childId, String userAgent, String ipAddress) {

        Instant now = Instant.now();
        String rawToken = generateRawToken();
        AccountSession session = AccountSession.issueForChild(account.getId(), childId, hash(rawToken), now, now.plus(refreshTokenTtl),
                userAgent, ipAddress);
        sessions.save(session);
        return new ChildSession(result(account, rawToken, session), session.getFamilyId());

    }

    /** A child's device's new sign-in: its tokens, and which sign-in (session family) it is, for the device list. */
    public record ChildSession(RefreshResult tokens, UUID sessionFamilyId) {
    }

    /** M10a: how many devices are signed in for this child now (live child-scoped sign-ins). */
    @Transactional(readOnly = true)
    public long liveChildDevices(UUID childId) {

        return sessions.countLiveChildFamilies(childId, Instant.now());

    }

}
