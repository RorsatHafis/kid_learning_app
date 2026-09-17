package com.platform.identity.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * API-layer request body for account registration. Deliberately a separate type
 * from {@link com.platform.identity.dto.RegisterAccountCommand} (which stays
 * transport-agnostic) so HTTP/validation concerns don't leak into the
 * application-service layer - see Section 32: "Do not expose JPA entities directly
 * as API contracts", extended here to commands as well.
 *
 * {@code idempotencyKey} should be a client-generated token (e.g. a UUID minted
 * once per registration attempt and resent unchanged on retry) so a network retry
 * of this same submit never creates two accounts.
 *
 * {@code role} is optional and defaults to PARENT (see AuthController). Public
 * self-registration is PARENT-only: any other value (TEACHER, PRINCIPAL, ADMIN) is
 * rejected by AuthController with 403. No school-onboarding/invite flow exists yet
 * to provision TEACHER/PRINCIPAL/ADMIN accounts - that's deliberately out of scope
 * for this phase, not a gap this endpoint should paper over by accepting those
 * roles from the public internet.
 */
public record RegisterRequest(

        @NotBlank
        @Email
        String email,

        @NotBlank
        String password,

        String familyName,

        @NotBlank
        String idempotencyKey,

        String role

) {
}
