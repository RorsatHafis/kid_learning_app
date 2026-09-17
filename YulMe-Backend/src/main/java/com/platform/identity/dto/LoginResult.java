package com.platform.identity.dto;

import com.platform.identity.entity.PlatformRole;

import java.time.Instant;
import java.util.UUID;

public record LoginResult(UUID accountId, PlatformRole platformRole, String accessToken, Instant expiresAt) {
    
}
