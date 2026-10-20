package com.platform.identity.web;

import jakarta.validation.constraints.NotBlank;

/**
 * A refresh token presented to get a new access token (rotated on every use).
 */
public record RefreshRequest(

        @NotBlank
        String refreshToken

) {
}
