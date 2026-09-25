package com.platform.identity.web;

import com.platform.identity.dto.LoginResult;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.AccountStatus;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.service.AccountSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * The signed-in account managing itself. Every endpoint acts on the authenticated
 * account only (the JWT subject) - there is no account id in any path or body.
 */
@RestController
@RequestMapping("/api/v1/account")
public class AccountController {

    private final AccountSettingsService accountSettingsService;

    public AccountController(AccountSettingsService accountSettingsService) {
        this.accountSettingsService = accountSettingsService;
    }

    public record AccountResponse(UUID id, String email, PlatformRole platformRole, AccountStatus status,
                                  Instant emailVerifiedAt, Instant createdAt) {
        static AccountResponse of(Account account) {
            return new AccountResponse(account.getId(), account.getEmail(), account.getPlatformRole(),
                    account.getStatus(), account.getEmailVerifiedAt(), account.getCreatedAt());
        }
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {
    }

    public record SignOutEverywhereResponse(int sessionsEnded) {
    }

    @GetMapping
    public AccountResponse me(@AuthenticationPrincipal UUID accountId) {
        return AccountResponse.of(accountSettingsService.get(accountId));
    }

    /**
     * Changes the password (the current one is required) and ends every session,
     * returning a fresh one - same shape as login - so this device stays signed in.
     */
    @PostMapping("/password")
    public ResponseEntity<AuthResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                                       @AuthenticationPrincipal UUID accountId,
                                                       HttpServletRequest http) {
        LoginResult result = accountSettingsService.changePassword(accountId, request.currentPassword(),
                request.newPassword(), http.getHeader("User-Agent"), http.getRemoteAddr());
        Account account = accountSettingsService.get(accountId);
        return ResponseEntity.ok(new AuthResponse(result.accountId(), account.getEmail(), result.platformRole(),
                result.accessToken(), result.expiresAt(), result.refreshToken(), result.refreshTokenExpiresAt()));
    }

    /** Ends every session on every device, this one included. */
    @PostMapping("/sign-out-everywhere")
    public SignOutEverywhereResponse signOutEverywhere(@AuthenticationPrincipal UUID accountId) {
        return new SignOutEverywhereResponse(accountSettingsService.signOutEverywhere(accountId));
    }

}
