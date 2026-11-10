package com.platform.identity.registration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Owner requirement 2026-11-10: verify the email first, then register (see {@link RegistrationVerificationService}). */
@RestController
@RequestMapping("/api/v1/auth/registration")
public class RegistrationVerificationController {

    private final RegistrationVerificationService verifications;

    public RegistrationVerificationController(RegistrationVerificationService verifications) {
        this.verifications = verifications;
    }

    public record StartRequest(@NotBlank @Email @Size(max = 320) String email, @Size(max = 8) String preferredLanguage) {
    }

    public record VerifyRequest(@NotBlank @Size(max = 128) String token) {
    }

    /** Always 202: the same answer whether or not the address has an account, or was asked about a moment ago. */
    @PostMapping("/start")
    public ResponseEntity<Void> start(@Valid @RequestBody StartRequest request, HttpServletRequest http) {
        verifications.start(request.email(), request.preferredLanguage(), http.getRemoteAddr());
        return ResponseEntity.accepted().build();
    }

    /** The emailed link, opened: the registration grant and the address it proves. */
    @PostMapping("/verify")
    public RegistrationVerificationService.Verified verify(@Valid @RequestBody VerifyRequest request) {
        return verifications.verify(request.token());
    }
}
