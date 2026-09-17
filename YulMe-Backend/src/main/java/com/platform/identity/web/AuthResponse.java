package com.platform.identity.web;

import com.platform.identity.entity.PlatformRole;

import java.time.Instant;
import java.util.UUID;

public record AuthResponse(

        UUID accountId,
        String email,
        PlatformRole platformRole,
        String accessToken,
        Instant expiresAt

) {
}
