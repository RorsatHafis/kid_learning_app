package com.platform.identity.web;

import com.platform.identity.entity.PlatformRole;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code refreshToken}/{@code refreshTokenExpiresAt} are null exactly when
 * {@code accessToken} is: the PENDING_VERIFICATION (principal-awaiting-approval)
 * registration branch in {@link AuthController} issues neither, since no
 * session should exist for an account that cannot yet authenticate.
 */
public record AuthResponse(

        UUID accountId,
        String email,
        PlatformRole platformRole,
        String accessToken,
        Instant expiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt

) {
}
