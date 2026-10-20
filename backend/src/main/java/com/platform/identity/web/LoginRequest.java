package com.platform.identity.web;

import jakarta.validation.constraints.NotBlank;

/**
 * Sign-in details: email and password.
 */
public record LoginRequest(

        @NotBlank
        String email,

        @NotBlank
        String password

) {
}
