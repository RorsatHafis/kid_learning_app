package com.platform.security.session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 *   session in that token's family, not just the one presented, per OAuth 2.0
 *   refresh-token-rotation best practice (draft-ietf-oauth-security-topics,
 *   "Refresh Token Automatic Reuse Detection").
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
    private final SecureRandom secureRandom = new SecureRandom();

    public AccountSessionService(AccountSessionRepository sessions, AccountRepository accounts,
                                  JwtService jwtService, SecurityProperties securityProperties) {

        this.sessions = sessions;
        this.accounts = accounts;
        this.jwtService = jwtService;
        this.refreshTokenTtl = securityProperties.jwt().refreshTokenTtl();

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

        return new IssuedSession(rawToken, session.getId(), session.getExpiresAt());

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

        AccountSession presented = sessions.findByRefreshTokenHash(presentedHash)
                .orElseThrow(InvalidSessionException::new);

        if (presented.getStatus() == AccountSessionStatus.ROTATED
                || presented.getStatus() == AccountSessionStatus.REVOKED) {

            // A token that was already consumed (or already revoked) being presented
            // again is exactly the reuse signal this method exists to catch - a
            // legitimate client only ever holds the newest token in the chain.
            revokeFamily(presented.getId());
            throw new InvalidSessionException();

        }

        if (!presented.isUsable(now)) {

            // Ordinary time expiry (or a future cleanup job's EXPIRED status) - not
            // evidence of reuse, so no family revocation, just a clean rejection.
            throw new InvalidSessionException();

        }

        Account account = accounts.findById(presented.getAccountId()).orElse(null);

        if (account == null || account.getStatus() != AccountStatus.ACTIVE) {

            // The account was disabled (or is otherwise no longer eligible) since this
            // session was issued - refresh must not resurrect access for it. This is
            // not a reuse signal (nothing about the token itself was replayed), so
            // only this one session is revoked, not its whole family.
            sessions.revokeIfRevocable(presented.getId(), now);
            throw new InvalidSessionException();

        }

        String newRawToken = generateRawToken();
        AccountSession next = AccountSession.issue(
                account.getId(), hash(newRawToken), now, now.plus(refreshTokenTtl), userAgent, ipAddress);
        sessions.save(next);

        int rotated = sessions.rotateIfActive(presented.getId(), next.getId(), now);

        if (rotated == 0) {

            // Lost a race against another request that rotated/revoked this exact
            // session between our lookup and this update - either genuine concurrent
            // reuse or two legitimate calls racing on one single-use token. Either
            // way the session we just minted must not survive as a valid credential,
            // and the whole family must be treated as compromised.
            sessions.revokeIfRevocable(next.getId(), now);
            revokeFamily(presented.getId());
            throw new InvalidSessionException();

        }

        JwtService.IssuedToken accessToken = jwtService.issueAccessToken(account.getId(), account.getPlatformRole());

        return new RefreshResult(account.getId(), account.getEmail(), account.getPlatformRole(),
                accessToken.token(), accessToken.expiresAt(), newRawToken, next.getExpiresAt());

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
                revokeFamily(session.getId());
                return;

            }

            sessions.revokeIfRevocable(session.getId(), now);

        });

    }

    /**
     * Revokes every session descended from the same login/registration as the
     * session identified by {@code memberId}: finds the root of the {@code
     * replacedBySessionId} chain, reads the *entire* chain from root to its
     * current tip, and only then revokes every node in it. Safe to call with any
     * member of the family, in any state.
     *
     * <p>The read pass and the write pass are deliberately separate. {@link
     * AccountSessionRepository#revokeIfRevocable} clears {@code
     * replacedBySessionId} on the row it revokes (required to satisfy the V9
     * migration's rotated-consistency CHECK constraint once a row is no longer
     * ROTATED) - interleaving reads and writes while walking forward would mean
     * each revoke erases the very pointer needed to find the next node, silently
     * truncating the walk after the first hop.
     */
    private void revokeFamily(UUID memberId) {

        Instant now = Instant.now();
        UUID rootId = findRootId(memberId);

        for (UUID id : collectChainIdsForward(rootId)) {
            sessions.revokeIfRevocable(id, now);
        }

    }

    /**
     * Read-only: {@code rootId} followed by every descendant, in chain order, ending at the current tip.
     *
     * <p>Battle 4D: no length limit. Every refresh adds a link and extends the session's
     * expiry, so a regularly used session's chain grows without bound; the previous
     * 200-hop cap threw IllegalStateException on long chains, rolling back reuse
     * detection - replay protection failed open. A corrupted (cyclic) chain, which the
     * schema's rotation rules cannot produce, is detected exactly via {@code visited} and
     * ends the walk: everything collected is still revoked (fail closed).
     */
    private List<UUID> collectChainIdsForward(UUID rootId) {

        List<UUID> ids = new ArrayList<>();
        Set<UUID> visited = new HashSet<>();
        UUID currentId = rootId;

        while (currentId != null && visited.add(currentId)) {

            ids.add(currentId);

            currentId = sessions.findById(currentId)
                    .map(AccountSession::getReplacedBySessionId)
                    .orElse(null);

        }

        return ids;

    }

    /** The first session of {@code memberId}'s chain. Unbounded, cycle-safe - see {@link #collectChainIdsForward}. */
    private UUID findRootId(UUID memberId) {

        UUID currentId = memberId;
        Set<UUID> visited = new HashSet<>();

        while (true) {

            if (!visited.add(currentId)) {
                // Corrupted cyclic chain: any member is a valid starting point; the forward walk
                // from it collects the whole cycle, so the family is still revoked in full.
                return currentId;
            }

            Optional<AccountSession> parent = sessions.findByReplacedBySessionId(currentId);

            if (parent.isEmpty()) {
                return currentId;
            }

            currentId = parent.get().getId();

        }

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

    public record IssuedSession(String rawToken, UUID sessionId, Instant expiresAt) {
    }

    public record RefreshResult(UUID accountId, String email, PlatformRole platformRole, String accessToken,
                                 Instant accessTokenExpiresAt, String refreshToken, Instant refreshTokenExpiresAt) {
    }

}
