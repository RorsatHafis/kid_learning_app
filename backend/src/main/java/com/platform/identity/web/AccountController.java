package com.platform.identity.web;

import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.security.core.Authentication;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.security.session.AccountSessionService;
import com.platform.identity.dto.LoginResult;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.AccountStatus;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.service.AccountSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
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
    private final AccountSessionService sessions;

    public AccountController(AccountSettingsService accountSettingsService, AccountSessionService sessions) {
        this.accountSettingsService = accountSettingsService;
        this.sessions = sessions;
    }

    /** A signed-in device; {@code current} is the one making this request. */
    public record DeviceResponse(UUID id, String userAgent, Instant signedInAt, Instant lastActiveAt, boolean current) {
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

    public record EmailChangeRequest(@NotBlank String currentPassword, @NotBlank @Email @Size(max = 320) String newEmail) {
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

    /**
     * Sends a link to {@code newEmail}; the address changes when it is opened. 202 whether
     * or not the new address already has an account (that mailbox is told instead).
     */
    @PostMapping("/email")
    public ResponseEntity<Void> requestEmailChange(@Valid @RequestBody EmailChangeRequest request,
                                                   @AuthenticationPrincipal UUID accountId, HttpServletRequest http) {
        accountSettingsService.requestEmailChange(accountId, request.currentPassword(), request.newEmail(),
                http.getRemoteAddr());
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/devices")
    public List<DeviceResponse> devices(@AuthenticationPrincipal UUID accountId, Authentication authentication) {
        Object current = authentication.getDetails();
        return sessions.devices(accountId).stream()
                .map(d -> new DeviceResponse(d.id(), d.userAgent(), d.signedInAt(), d.lastActiveAt(), d.id().equals(current)))
                .toList();
    }

    /** Signs one of the account's devices out (404 for an id that isn't one of them). */
    @DeleteMapping("/devices/{deviceId}")
    public ResponseEntity<Void> signOutDevice(@AuthenticationPrincipal UUID accountId, @PathVariable UUID deviceId) {
        if (!sessions.signOutDevice(accountId, deviceId)) {
            throw new ResourceNotFoundException("Device", deviceId);
        }
        return ResponseEntity.noContent().build();
    }

    /** Ends every session on every device, this one included. */
    @PostMapping("/sign-out-everywhere")
    public SignOutEverywhereResponse signOutEverywhere(@AuthenticationPrincipal UUID accountId) {
        return new SignOutEverywhereResponse(accountSettingsService.signOutEverywhere(accountId));
    }

}
