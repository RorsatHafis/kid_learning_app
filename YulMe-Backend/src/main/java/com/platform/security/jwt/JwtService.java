package com.platform.security.jwt;

import com.platform.identity.entity.PlatformRole;
import com.platform.security.SecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
 
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey key;
    private final String issuer;
    private final Duration accessTokenTtl;
 
    public JwtService(SecurityProperties properties) {
        
        String secret = properties.jwt().secret();
        Assert.hasText(secret, "platform.security.jwt.secret must be set "
                + "(see application.yml / the PLATFORM_SECURITY_JWT_SECRET environment variable)");
 
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        Assert.isTrue(keyBytes.length >= 32,
                "platform.security.jwt.secret must be at least 32 bytes (256 bits) for HS256, was "
                        + keyBytes.length);
 
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.issuer = properties.jwt().issuer();
        this.accessTokenTtl = properties.jwt().accessTokenTtl();

    }

    private static final String ROLE_CLAIM = "role";

    /**
     * {@code platformRole} is embedded as a signed claim (not looked up from the DB
     * on every request) so JwtAuthenticationFilter can derive authorities without a
     * per-request account lookup, matching the filter's existing stateless/fast
     * design. This does mean a role change doesn't take effect until the account's
     * next login/token refresh - an accepted trade-off for MVP scope (role changes
     * are a rare, deliberate admin action, not something needing instant revocation).
     */
    public IssuedToken issueAccessToken(UUID accountId, PlatformRole platformRole) {

        Assert.notNull(platformRole, "platformRole must not be null");

        Instant now = Instant.now();
        Instant expiresAt = now.plus(accessTokenTtl);
 
        String token = Jwts.builder()
                .subject(accountId.toString())
                .claim(ROLE_CLAIM, platformRole.name())
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
 
        return new IssuedToken(token, expiresAt);

    }

     public UUID validateAndGetAccountId(String token) {

        return validateAndGetPrincipal(token).accountId();

    }

    /** Validates the token and extracts both identity claims the rest of the app needs. */
    public AuthenticatedPrincipal validateAndGetPrincipal(String token) {

        try {
            
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
 
            UUID accountId = UUID.fromString(claims.getSubject());
            PlatformRole role = PlatformRole.valueOf(claims.get(ROLE_CLAIM, String.class));
            return new AuthenticatedPrincipal(accountId, role);

        } catch (JwtException | IllegalArgumentException | NullPointerException e) {

            throw new InvalidTokenException("Invalid or expired access token", e);

        }

    }

    public record IssuedToken(String token, Instant expiresAt) {
    }

    public record AuthenticatedPrincipal(UUID accountId, PlatformRole platformRole) {
    }
    
}
