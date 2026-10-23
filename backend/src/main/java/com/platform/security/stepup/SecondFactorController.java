package com.platform.security.stepup;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * M9a: an administrator's authenticator app over HTTP - its state, setting it up (password, then a first code, which
 * returns the recovery codes once), turning it off, and another administrator turning off a colleague's.
 */
@RestController
public class SecondFactorController {

    public record PasswordRequest(@NotBlank String password) {
    }

    public record CodeRequest(@NotBlank @Size(max = 20) String code) {
    }

    public record TurnOffRequest(@NotBlank String password, @NotBlank @Size(max = 20) String code) {
    }

    public record RecoveryCodes(List<String> recoveryCodes) {
    }

    private final SecondFactorService service;

    public SecondFactorController(SecondFactorService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/account/second-factor")
    public SecondFactorService.Status status(@AuthenticationPrincipal UUID accountId) {
        return service.status(accountId);
    }

    @PostMapping("/api/v1/account/second-factor/setup")
    public SecondFactorService.Setup setUp(@AuthenticationPrincipal UUID accountId, @Valid @RequestBody PasswordRequest request) {
        return service.setUp(accountId, request.password());
    }

    @PostMapping("/api/v1/account/second-factor/activate")
    public RecoveryCodes activate(@AuthenticationPrincipal UUID accountId, @Valid @RequestBody CodeRequest request) {
        return new RecoveryCodes(service.activate(accountId, request.code()));
    }

    @PostMapping("/api/v1/account/second-factor/off")
    public ResponseEntity<Void> turnOff(@AuthenticationPrincipal UUID accountId, @Valid @RequestBody TurnOffRequest request) {
        service.turnOff(accountId, request.password(), request.code());
        return ResponseEntity.noContent().build();
    }

    @RequiresStepUp
    @PostMapping("/api/v1/admin/accounts/{accountId}/second-factor/reset")
    public ResponseEntity<Void> reset(@AuthenticationPrincipal UUID adminId, @PathVariable UUID accountId) {
        service.resetFor(adminId, accountId);
        return ResponseEntity.noContent().build();
    }

}
