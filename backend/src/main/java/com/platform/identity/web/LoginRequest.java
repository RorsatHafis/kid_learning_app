package com.platform.identity.web;

import jakarta.validation.constraints.NotBlank;

/**
 * Sign-in details: email and password. M9b: {@code email} may instead be the account's verified phone number (the
 * field keeps its name for existing clients).
 */
public record LoginRequest(

        @NotBlank
        String email,

        @NotBlank
        String password

) {
}
