package com.platform.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.platform.common.config.ConfigurationContract;

/**
 * Bound from application.yaml, where each value is mapped to its documented
 * environment variable (see {@link ConfigurationContract}). Validated here, at
 * startup, so a missing or unusable value stops the application instead of
 * surfacing later as failed logins or unusable tokens.
 */
@ConfigurationProperties(prefix = "platform.security")
public record SecurityProperties (Jwt jwt, Lockout lockout) {

    public SecurityProperties {
        ConfigurationContract.require(jwt != null, "platform.security.jwt is not configured");
        ConfigurationContract.require(lockout != null, "platform.security.lockout is not configured");
    }

    public record Jwt(String secret, String issuer, Duration accessTokenTtl, Duration refreshTokenTtl) {

        public Jwt {
            ConfigurationContract.requireText(secret, "JWT_SECRET");
            // HS256 needs a key of at least 256 bits.
            ConfigurationContract.require(secret.getBytes(StandardCharsets.UTF_8).length >= 32,
                    "JWT_SECRET must be at least 32 bytes (256 bits) for HS256");
            ConfigurationContract.requireText(issuer, "JWT_ISSUER");
            ConfigurationContract.requirePositive(accessTokenTtl, "JWT_ACCESS_TOKEN_TTL");
            ConfigurationContract.requirePositive(refreshTokenTtl, "JWT_REFRESH_TOKEN_TTL");
        }

    }

    /** Brute-force lockout policy - see {@link com.platform.identity.entity.Account#recordFailedLogin}. */
    public record Lockout(int maxAttempts, Duration duration) {

        public Lockout {
            ConfigurationContract.require(maxAttempts >= 1, "LOGIN_MAX_ATTEMPTS must be at least 1, was " + maxAttempts);
            // A zero or negative duration would leave the attempt limit looking configured while never locking.
            ConfigurationContract.requirePositive(duration, "LOGIN_LOCKOUT_DURATION");
        }

    }

}
