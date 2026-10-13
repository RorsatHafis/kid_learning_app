package com.platform.identity.web;

import com.platform.identity.dto.LoginCommand;
import com.platform.identity.dto.LoginResult;
import com.platform.identity.dto.RegisterAccountCommand;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.service.LoginService;
import com.platform.identity.service.RegisterAccountService;
import com.platform.security.jwt.JwtService;
import com.platform.security.session.AccountSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Every endpoint every other authenticated endpoint depends on. Deliberately
 * thin: this controller only translates HTTP &lt;-&gt; the existing {@link
 * RegisterAccountService} / {@link LoginService} / {@link AccountSessionService},
 * with no business logic of its own beyond resolving/validating the requested
 * {@link PlatformRole} and reading {@code User-Agent}/remote-address off the
 * current request (see {@link #currentUserAgent()}/{@link #currentIpAddress()})
 * to pass through as session context. Every other failure mode (weak password,
 * email already registered, invalid credentials, locked account, invalid/
 * expired/reused refresh token, bean-validation errors) is already mapped to
 * the right HTTP status by {@link com.platform.common.web.GlobalExceptionHandler}
 * - this class does not need its own try/catch blocks for those.
 *
 * {@code register}/{@code login} deliberately keep their original single-
 * argument signatures (no added {@link HttpServletRequest} parameter) - they are
 * called directly, outside any HTTP request, by {@code
 * PublicRegistrationRoleSecurityTests}. Reading the request via {@link
 * RequestContextHolder} instead, with a graceful null fallback when nothing is
 * thread-bound, keeps that call pattern working unchanged while still capturing
 * real request context on the real HTTP path.
 *
 * Registration immediately issues an access token and refresh-token session
 * (via the same {@link JwtService} / {@link AccountSessionService} {@link
 * LoginService} uses) so a newly registered account lands straight in an
 * authenticated session, matching the "register or log in -&gt; enter" step of
 * the validator journey without a required second login call. This is a thin,
 * side-effect-free composition at the controller layer, not a change to either
 * service.
 *
 * SESSION LIFECYCLE (Battle 1)
 * ----------------------------------------------------------------------------
 * - {@code accessToken} is a short-lived, stateless JWT (see {@link
 *   JwtService}) sent as a Bearer token on every other authenticated request.
 *   When it expires, protected endpoints return 401.
 * - {@code refreshToken} is a long-lived, server-tracked opaque bearer value
 *   (only its hash is ever persisted - see {@link AccountSessionService}). On
 *   401, the client calls {@link #refresh} with it to obtain a new access token
 *   *and* a new refresh token (rotation - the old refresh token stops working
 *   the instant this succeeds), then safely retries the original request.
 * - {@link #refresh} failing (unknown/expired/already-used/revoked token) always
 *   comes back as 401 with the same generic message - the client's only correct
 *   response is to discard both tokens and send the user back through {@link
 *   #login}. It never looks like success.
 * - {@link #logout} revokes the session server-side so the presented refresh
 *   token can never be used again, even though the still-live access token (if
 *   any) keeps working until its own short natural expiry.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RegisterAccountService registerAccountService;
    private final LoginService loginService;
    private final JwtService jwtService;
    private final AccountSessionService accountSessionService;

    private final com.platform.identity.legal.LegalDocuments legal;
    private final com.platform.identity.repository.AccountRepository accounts;

    public AuthController(RegisterAccountService registerAccountService, LoginService loginService,
                           JwtService jwtService, AccountSessionService accountSessionService,
                          com.platform.identity.legal.LegalDocuments legal,
                          com.platform.identity.repository.AccountRepository accounts) {
        this.legal = legal;
        this.accounts = accounts;
        this.registerAccountService = registerAccountService;
        this.loginService = loginService;
        this.jwtService = jwtService;
        this.accountSessionService = accountSessionService;
    }

    /** The account's interface language, so a sign-in on any device opens in it. */
    private String languageOf(java.util.UUID accountId) {
        return accounts.findById(accountId).map(Account::getPreferredLanguage).orElse(com.platform.identity.UiLanguage.ENGLISH);
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {

        PlatformRole requestedRole = parseRole(request.role());

        // Public self-registration supports PARENT and PRINCIPAL. Principal accounts
        // are created pending admin approval and receive no session until approved.
        if (requestedRole != null && requestedRole != PlatformRole.PARENT
                && requestedRole != PlatformRole.PRINCIPAL) {
            throw new AccessDeniedException(
                    "Public registration cannot create " + requestedRole + " accounts");
        }

        // Y15 (PD-18): once the owner has published terms and a privacy notice, an account is created
        // only by someone who agreed to the current version of them.
        if (legal.configured() && !legal.version().equals(request.acceptedLegalVersion())) {
            throw new IllegalArgumentException("Agree to the terms of use and the privacy notice to create an account");
        }
        Account account = registerAccountService.register(new RegisterAccountCommand(
                request.email(), request.password(), request.familyName(), request.idempotencyKey(),
                requestedRole, legal.configured() ? legal.version() : null, request.preferredLanguage()));

        if (account.getStatus() != com.platform.identity.entity.AccountStatus.ACTIVE) {
            // Pending-approval accounts (principal awaiting admin sign-off) get no
            // access token and no refresh-token session - there is nothing yet for
            // either to authenticate.
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(new AuthResponse(account.getId(), account.getEmail(), account.getPlatformRole(),
                            null, null, null, null, account.getPreferredLanguage()));
        }

        AccountSessionService.IssuedSession session = accountSessionService.issueSession(
                account.getId(), currentUserAgent(), currentIpAddress());
        JwtService.IssuedToken issued = jwtService.issueAccessToken(account.getId(), account.getPlatformRole(),
                session.familyId());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AuthResponse(account.getId(), account.getEmail(), account.getPlatformRole(),
                        issued.token(), issued.expiresAt(), session.rawToken(), session.expiresAt(),
                        account.getPreferredLanguage()));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {

        LoginResult result = loginService.login(
                new LoginCommand(request.email(), request.password(), currentUserAgent(), currentIpAddress()));

        return ResponseEntity.ok(new AuthResponse(result.accountId(), request.email(), result.platformRole(),
                result.accessToken(), result.expiresAt(), result.refreshToken(), result.refreshTokenExpiresAt(),
                languageOf(result.accountId())));
    }

    /**
     * Exchanges a valid, not-yet-consumed refresh token for a new access token
     * and a rotated replacement refresh token. See the class-level javadoc and
     * {@link AccountSessionService#refresh} for the full validation/rotation/
     * reuse-detection behavior. On any failure this throws {@link
     * com.platform.security.session.InvalidSessionException}, mapped by {@link
     * com.platform.common.web.GlobalExceptionHandler} to a 401 the client should
     * treat as "sign in again" - never as a soft/retryable error.
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {

        AccountSessionService.RefreshResult result =
                accountSessionService.refresh(request.refreshToken(), currentUserAgent(), currentIpAddress());

        return ResponseEntity.ok(new AuthResponse(result.accountId(), result.email(), result.platformRole(),
                result.accessToken(), result.accessTokenExpiresAt(), result.refreshToken(),
                result.refreshTokenExpiresAt(), languageOf(result.accountId())));
    }

    /**
     * Revokes the server-side session backing the presented refresh token, so it
     * can never be refreshed again. Idempotent and always returns 204, even if
     * the token was already invalid/unknown - see {@link
     * AccountSessionService#logout} for why. Deleting tokens on the client
     * alone is not logout; this call is what actually ends the session.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {

        accountSessionService.logout(request.refreshToken());

        return ResponseEntity.noContent().build();
    }

    private static PlatformRole parseRole(String rawRole) {

        if (rawRole == null || rawRole.isBlank()) {
            return null;
        }

        try {
            return PlatformRole.valueOf(rawRole.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown role: " + rawRole, e);
        }

    }

    private static String currentUserAgent() {

        HttpServletRequest request = currentHttpRequestOrNull();
        return request == null ? null : request.getHeader("User-Agent");

    }

    private static String currentIpAddress() {

        HttpServletRequest request = currentHttpRequestOrNull();
        return request == null ? null : request.getRemoteAddr();

    }

    /**
     * Null outside an actual HTTP request - notably when {@code
     * PublicRegistrationRoleSecurityTests} calls {@link #register} directly as a
     * plain Java method call with no request thread-bound. {@code
     * AccountSession.userAgent}/{@code ipAddress} are nullable columns
     * specifically to accommodate this (see the V9 migration) - they are
     * optional forensic/audit context, never used for authentication or
     * authorization decisions.
     */
    private static HttpServletRequest currentHttpRequestOrNull() {

        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();

        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }

        return null;

    }

}
