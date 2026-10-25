package com.platform.identity.phone;

import com.platform.identity.repository.AccountRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/** M9b over HTTP: a phone number on the signed-in account - ask for a code, confirm it, remove the number. */
@RestController
public class PhoneController {

    public record CodeRequest(@NotBlank String password, @NotBlank @Size(max = 40) String phone) {
    }

    public record ConfirmRequest(@NotBlank @Size(max = 12) String code) {
    }

    public record RemoveRequest(@NotBlank String password) {
    }

    /**
     * {@code available}: whether texts can be sent at all here (a provider is set up). {@code phone} is E.164;
     * {@code display} the same number as people write it.
     */
    public record PhoneStatus(boolean available, String phone, String display, Instant verifiedAt) {

        static PhoneStatus of(boolean available, String phone, Instant verifiedAt) {
            return new PhoneStatus(available, phone, PhoneNumbers.display(phone), verifiedAt);
        }
    }

    private final PhoneVerificationService phones;
    private final SmsSender sms;
    private final AccountRepository accounts;

    public PhoneController(PhoneVerificationService phones, SmsSender sms, AccountRepository accounts) {
        this.phones = phones;
        this.sms = sms;
        this.accounts = accounts;
    }

    @GetMapping("/api/v1/account/phone")
    public PhoneStatus status(@AuthenticationPrincipal UUID accountId) {
        var account = accounts.findById(accountId).orElseThrow();
        return PhoneStatus.of(sms.available(), account.getPhoneE164(), account.getPhoneVerifiedAt());
    }

    @PostMapping("/api/v1/account/phone/code")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public PhoneVerificationService.CodeSent requestCode(@AuthenticationPrincipal UUID accountId, @Valid @RequestBody CodeRequest request) {
        return phones.request(accountId, request.password(), request.phone());
    }

    @PostMapping("/api/v1/account/phone/confirm")
    public PhoneStatus confirm(@AuthenticationPrincipal UUID accountId, @Valid @RequestBody ConfirmRequest request) {
        var account = phones.confirm(accountId, request.code());
        return PhoneStatus.of(true, account.getPhoneE164(), account.getPhoneVerifiedAt());
    }

    @PostMapping("/api/v1/account/phone/remove")
    public PhoneStatus remove(@AuthenticationPrincipal UUID accountId, @Valid @RequestBody RemoveRequest request) {
        phones.remove(accountId, request.password());
        return PhoneStatus.of(sms.available(), null, null);
    }

}
