package com.platform.identity.web;

import com.platform.identity.dto.LoginCommand;
import com.platform.identity.dto.LoginResult;
import com.platform.identity.dto.RegisterAccountCommand;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.service.LoginService;
import com.platform.identity.service.RegisterAccountService;
import com.platform.security.jwt.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The two endpoints every other authenticated endpoint depends on. Deliberately
 * thin: this controller only translates HTTP &lt;-&gt; the existing
 * {@link RegisterAccountService} / {@link LoginService}, with no business logic of
 * its own beyond resolving/validating the requested {@link PlatformRole}. Every
 * other failure mode (weak password, email already registered, invalid
 * credentials, locked account, bean-validation errors) is already mapped to the
 * right HTTP status by {@link com.platform.common.web.GlobalExceptionHandler} -
 * this class does not need its own try/catch blocks for those.
 *
 * Registration immediately issues an access token (via the same {@link JwtService}
 * {@link LoginService} uses) so a newly registered account lands straight in an
 * authenticated session, matching the "register or log in -&gt; enter" step of the
 * validator journey without a required second login call. This is a thin,
 * side-effect-free composition at the controller layer, not a change to either
 * service.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RegisterAccountService registerAccountService;
    private final LoginService loginService;
    private final JwtService jwtService;

    public AuthController(RegisterAccountService registerAccountService, LoginService loginService,
                           JwtService jwtService) {
        this.registerAccountService = registerAccountService;
        this.loginService = loginService;
        this.jwtService = jwtService;
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

        Account account = registerAccountService.register(new RegisterAccountCommand(
                request.email(), request.password(), request.familyName(), request.idempotencyKey(),
                requestedRole));

        if (account.getStatus() != com.platform.identity.entity.AccountStatus.ACTIVE) {
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(new AuthResponse(account.getId(), account.getEmail(), account.getPlatformRole(), null, null));
        }
        JwtService.IssuedToken issued = jwtService.issueAccessToken(account.getId(), account.getPlatformRole());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AuthResponse(account.getId(), account.getEmail(), account.getPlatformRole(),
                        issued.token(), issued.expiresAt()));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResult result = loginService.login(new LoginCommand(request.email(), request.password()));

        return ResponseEntity.ok(new AuthResponse(result.accountId(), request.email(), result.platformRole(),
                result.accessToken(), result.expiresAt()));
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

}
