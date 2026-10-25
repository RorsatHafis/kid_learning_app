package com.platform.identity.web;

import com.platform.common.web.PageResponse;
import java.util.List;
import org.springframework.web.bind.annotation.RequestParam;
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
import org.springframework.web.bind.annotation.PutMapping;
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

    private final com.platform.security.stepup.StepUpService stepUp;

    public AccountController(AccountSettingsService accountSettingsService, AccountSessionService sessions,
                             com.platform.security.stepup.StepUpService stepUp) {
        this.stepUp = stepUp;
        this.accountSettingsService = accountSettingsService;
        this.sessions = sessions;
    }

    /**
     * A signed-in device; {@code current} is the one making this request; {@code childId} is set for a child's device
     * (M10a) - a sign-in that opens only that child's space.
     */
    public record DeviceResponse(UUID id, String userAgent, Instant signedInAt, Instant lastActiveAt, boolean current, UUID childId) {
    }

    /**
     * {@code eraseAfter}: when the account will be erased, if its owner asked; null otherwise.
     * {@code legalVersionAccepted}: the version of the terms and privacy notice it agreed to (Y15).
     * {@code phone}: its verified phone number (E.164), M9b; null when it has none.
     */
    public record AccountResponse(UUID id, String email, PlatformRole platformRole, AccountStatus status,
                                  Instant emailVerifiedAt, Instant createdAt, Instant eraseAfter, String legalVersionAccepted,
                                  String preferredLanguage, String phone, Instant phoneVerifiedAt) {
        static AccountResponse of(Account account) {
            return new AccountResponse(account.getId(), account.getEmail(), account.getPlatformRole(),
                    account.getStatus(), account.getEmailVerifiedAt(), account.getCreatedAt(), account.getEraseAfter(),
                    account.getLegalVersionAccepted(), account.getPreferredLanguage(), account.getPhoneE164(),
                    account.getPhoneVerifiedAt());
        }
    }

    /** The interface language an account chooses (en or km); content is never translated by it. */
    public record LanguageChange(@NotBlank @jakarta.validation.constraints.Pattern(regexp = com.platform.identity.UiLanguage.PATTERN)
                                 String language) {
    }

    public record LegalAcceptance(@NotBlank @Size(max = 40) String version) {
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
                result.accessToken(), result.expiresAt(), result.refreshToken(), result.refreshTokenExpiresAt(),
                account.getPreferredLanguage()));
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

    /** M7: at most this many devices a page. */
    static final int MAX_DEVICES_PAGE = 20;

    /**
     * Where the account is signed in, a page at a time (M7): this device first, then the most recently active. A
     * person's live sign-ins are few (they expire), so the page is cut from the whole list, which also keeps "this
     * device" first on page 0 however the rest sort.
     */
    @GetMapping("/devices")
    public PageResponse<DeviceResponse> devices(@AuthenticationPrincipal UUID accountId, Authentication authentication,
                                                @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "6") int size) {
        if (size > MAX_DEVICES_PAGE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_DEVICES_PAGE);
        }
        PageResponse.Request request = new PageResponse.Request(page, size);
        Object current = authentication.getDetails();
        List<DeviceResponse> all = sessions.devices(accountId).stream()
                .map(d -> new DeviceResponse(d.id(), d.userAgent(), d.signedInAt(), d.lastActiveAt(), d.id().equals(current), d.childId()))
                .sorted(java.util.Comparator.comparing((DeviceResponse d) -> !d.current()))
                .toList();
        int from = (int) Math.min(all.size(), (long) request.offset());
        return new PageResponse<>(all.subList(from, Math.min(all.size(), from + size)), page, size, all.size());
    }

    public record StepUpRequest(@jakarta.validation.constraints.NotBlank String password,
                                @Size(max = 20) String code) {
    }

    public record StepUpResponse(String stepUpToken, Instant expiresAt) {
    }

    /**
     * M8: re-enter the password for a few minutes of actions that move money or access (RequiresStepUp). Bound to this
     * sign-in; a wrong password counts towards the lockout. An administrator also gives an authenticator code (M9a).
     */
    @org.springframework.web.bind.annotation.PostMapping("/step-up")
    public StepUpResponse stepUp(@AuthenticationPrincipal UUID accountId, Authentication authentication,
                                 @Valid @RequestBody StepUpRequest request) {
        UUID sessionFamilyId = authentication.getDetails() instanceof UUID sid ? sid : null;
        var issued = stepUp.stepUp(accountId, sessionFamilyId, request.password(), request.code());
        return new StepUpResponse(issued.token(), issued.expiresAt());
    }

    /** Signs one of the account's devices out (404 for an id that isn't one of them). */
    @DeleteMapping("/devices/{deviceId}")
    public ResponseEntity<Void> signOutDevice(@AuthenticationPrincipal UUID accountId, @PathVariable UUID deviceId) {
        if (!sessions.signOutDevice(accountId, deviceId)) {
            throw new ResourceNotFoundException("Device", deviceId);
        }
        return ResponseEntity.noContent().build();
    }

    /** Y15: agrees to the current terms and privacy notice (409 if that isn't the current version, or none exists). */
    @PostMapping("/legal-acceptance")
    public AccountResponse acceptLegal(@AuthenticationPrincipal UUID accountId, @Valid @RequestBody LegalAcceptance body) {
        return AccountResponse.of(accountSettingsService.acceptLegal(accountId, body.version()));
    }

    /** The interface language this account uses everywhere it signs in (en or km). */
    @PutMapping("/language")
    public AccountResponse changeLanguage(@AuthenticationPrincipal UUID accountId, @Valid @RequestBody LanguageChange body) {
        return AccountResponse.of(accountSettingsService.changeLanguage(accountId, body.language()));
    }

    /** Ends every session on every device, this one included. */
    @PostMapping("/sign-out-everywhere")
    public SignOutEverywhereResponse signOutEverywhere(@AuthenticationPrincipal UUID accountId) {
        return new SignOutEverywhereResponse(accountSettingsService.signOutEverywhere(accountId));
    }

}
