package com.platform.identity.web;

import com.platform.identity.service.AccountRecoveryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Email-proven account actions (see {@link AccountRecoveryService}). The links in the
 * emails open app pages, which post the token from the URL fragment here - the token
 * never travels in a URL the server or a proxy would log.
 */
@RestController
@RequestMapping("/api/v1")
public class AccountRecoveryController {

    private final AccountRecoveryService recovery;
    private final com.platform.identity.phone.PhonePasswordReset phoneReset;

    public AccountRecoveryController(AccountRecoveryService recovery, com.platform.identity.phone.PhonePasswordReset phoneReset) {
        this.recovery = recovery;
        this.phoneReset = phoneReset;
    }

    /** M9b (D-15): a reset code by text, to the account's verified phone. */
    public record PhoneResetRequest(@NotBlank @Size(max = 40) String phone) {
    }

    public record PhoneResetConfirmRequest(@NotBlank @Size(max = 40) String phone, @NotBlank @Size(max = 12) String code,
                                           @NotBlank String newPassword) {
    }

    /** Always 202, whether or not the number belongs to an account (503 only when texts aren't set up at all). */
    @PostMapping("/auth/password-reset/phone")
    public ResponseEntity<Void> requestPhoneReset(@Valid @RequestBody PhoneResetRequest request) {
        phoneReset.request(request.phone());
        return ResponseEntity.accepted().build();
    }

    /** 204 with the new password set and every session ended; 422 for any wrong, used or expired code. */
    @PostMapping("/auth/password-reset/phone/confirm")
    public ResponseEntity<Void> resetWithPhoneCode(@Valid @RequestBody PhoneResetConfirmRequest request) {
        phoneReset.reset(request.phone(), request.code(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    public record PasswordResetRequest(@NotBlank @Email @Size(max = 320) String email) {
    }

    public record PasswordResetConfirmRequest(@NotBlank @Size(max = 128) String token,
                                              @NotBlank String newPassword) {
    }

    public record EmailVerificationConfirmRequest(@NotBlank @Size(max = 128) String token) {
    }

    /** Always 202, whether or not the address has an account. */
    @PostMapping("/auth/password-reset")
    public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request,
                                                     HttpServletRequest http) {
        recovery.requestPasswordReset(request.email(), http.getRemoteAddr());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/auth/password-reset/confirm")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody PasswordResetConfirmRequest request) {
        recovery.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    /** First password for an account created by a school invitation. Signs nobody in. */
    @PostMapping("/auth/account-setup/confirm")
    public ResponseEntity<Void> completeAccountSetup(@Valid @RequestBody PasswordResetConfirmRequest request) {
        recovery.completeAccountSetup(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    /** Unauthenticated: the link may be opened on a device that isn't signed in. */
    @PostMapping("/auth/email-verification/confirm")
    public ResponseEntity<Void> confirmEmail(@Valid @RequestBody EmailVerificationConfirmRequest request) {
        recovery.confirmVerification(request.token());
        return ResponseEntity.noContent().build();
    }

    /** Opened from the new mailbox: moves the sign-in address. Unauthenticated, like the others. */
    @PostMapping("/auth/email-change/confirm")
    public ResponseEntity<Void> confirmEmailChange(@Valid @RequestBody EmailVerificationConfirmRequest request) {
        recovery.confirmEmailChange(request.token());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/account/email-verification")
    public ResponseEntity<Void> resendVerification(@AuthenticationPrincipal UUID accountId, HttpServletRequest http) {
        recovery.resendVerification(accountId, http.getRemoteAddr());
        return ResponseEntity.accepted().build();
    }

}
