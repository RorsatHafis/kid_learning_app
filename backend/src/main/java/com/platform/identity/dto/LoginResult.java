package com.platform.identity.dto;

import com.platform.identity.entity.PlatformRole;

import java.time.Instant;
import java.util.UUID;

/**
 * What a successful login produces: the account, its role, the access token with its expiry, and the refresh token
 * for the session.
 */
public record LoginResult(UUID accountId, PlatformRole platformRole, String accessToken, Instant expiresAt,
                           String refreshToken, Instant refreshTokenExpiresAt) {

}
