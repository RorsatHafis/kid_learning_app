package com.platform.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform.security")
public record SecurityProperties (Jwt jwt, Lockout lockout) {

    public record Jwt(String secret, String issuer, Duration accessTokenTtl) {
    }
 
    /** Brute-force lockout policy - see {@link com.platform.identity.entity.Account#recordFailedLogin}. */
    public record Lockout(int maxAttempts, Duration duration) {
    }
    
}
