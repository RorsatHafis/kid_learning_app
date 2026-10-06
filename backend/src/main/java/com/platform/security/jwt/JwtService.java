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
        
        // Presence and minimum HS256 key length are validated when SecurityProperties binds (JWT_SECRET).
        this.key = Keys.hmacShaKeyFor(properties.jwt().secret().getBytes(StandardCharsets.UTF_8));
        this.issuer = properties.jwt().issuer();
        this.accessTokenTtl = properties.jwt().accessTokenTtl();

    }

    private static final String ROLE_CLAIM = "role";
    private static final String SESSION_CLAIM = "sid";

    /**
     * {@code platformRole} is embedded as a signed claim (not looked up from the DB
     * on every request) so JwtAuthenticationFilter can derive authorities without a
     * per-request account lookup, matching the filter's existing stateless/fast
     * design. This does mean a role change doesn't take effect until the account's
     * next login/token refresh - an accepted trade-off for MVP scope (role changes
     * are a rare, deliberate admin action, not something needing instant revocation).
     */
    public IssuedToken issueAccessToken(UUID accountId, PlatformRole platformRole) {
        return issueAccessToken(accountId, platformRole, null);
    }

    /**
     * As above, naming the sign-in (session family) the token belongs to in the
     * {@code sid} claim, so the account's device list can tell "this device" (Y2). It
     * identifies, it never authorizes: nothing is allowed or refused because of it.
     */
    public IssuedToken issueAccessToken(UUID accountId, PlatformRole platformRole, UUID sessionFamilyId) {

        Assert.notNull(platformRole, "platformRole must not be null");

        Instant now = Instant.now();
        Instant expiresAt = now.plus(accessTokenTtl);
 
        var builder = Jwts.builder()
                .subject(accountId.toString())
                .claim(ROLE_CLAIM, platformRole.name());
        if (sessionFamilyId != null) {
            builder.claim(SESSION_CLAIM, sessionFamilyId.toString());
        }
        String token = builder
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
            String sid = claims.get(SESSION_CLAIM, String.class);
            return new AuthenticatedPrincipal(accountId, role, sid == null ? null : UUID.fromString(sid));

        } catch (JwtException | IllegalArgumentException | NullPointerException e) {

            throw new InvalidTokenException("Invalid or expired access token", e);

        }

    }

    public record IssuedToken(String token, Instant expiresAt) {
    }

    /** {@code sessionFamilyId} is null for a token issued without one. */
    public record AuthenticatedPrincipal(UUID accountId, PlatformRole platformRole, UUID sessionFamilyId) {
    }
    
}
