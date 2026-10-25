package com.platform.security.ratelimit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.function.LongSupplier;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import com.platform.common.web.ServerText;

/**
 * Refuses a client address that exceeds its budget on an unauthenticated identity
 * endpoint with 429 and a {@code Retry-After} header, before the request reaches
 * the controller - so a refused refresh never consumes (rotates) the refresh token
 * and the client can simply retry it later.
 *
 * <p>The client address is {@link HttpServletRequest#getRemoteAddr()}. Behind the
 * reverse proxy Tomcat derives it from X-Forwarded-For, believing only entries added
 * by trusted proxies ({@code server.forward-headers-strategy: native}), so a client
 * can neither share the proxy's budget with everyone nor choose its own address.
 *
 * <p>Registered inside the security filter chain after CORS (see SecurityConfig), so
 * a browser can read the 429.
 */
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimitFilter.class);

    static final int DEMO_CHECKS_PER_MINUTE = 60;
    static final int CHILD_DEVICE_CLAIMS_PER_MINUTE = 10;
    static final int CHILD_DEVICE_COLLECTS_PER_MINUTE = 60;

    private final Map<String, TokenBucketLimiter> limiters;

    public AuthRateLimitFilter(RateLimitProperties properties) {
        this(properties, System::nanoTime);
    }

    AuthRateLimitFilter(RateLimitProperties properties, LongSupplier nanoClock) {
        TokenBucketLimiter login = new TokenBucketLimiter(properties.loginPerMinute(), Duration.ofMinutes(1), nanoClock);
        TokenBucketLimiter refresh = new TokenBucketLimiter(properties.refreshPerMinute(), Duration.ofMinutes(1), nanoClock);
        // Asking for a reset, by email or by text, draws on one budget per network.
        TokenBucketLimiter recovery = new TokenBucketLimiter(properties.recoveryPerHour(), Duration.ofHours(1), nanoClock);
        this.limiters = Map.ofEntries(
                Map.entry("/api/v1/auth/login", login),
                // Using an emailed link is a sign-in-like step: it shares the login budget.
                Map.entry("/api/v1/auth/password-reset/confirm", login),
                // M9b: a texted reset code is a sign-in-like step too.
                Map.entry("/api/v1/auth/password-reset/phone/confirm", login),
                Map.entry("/api/v1/auth/email-verification/confirm", login),
                Map.entry("/api/v1/auth/account-setup/confirm", login),
                Map.entry("/api/v1/auth/email-change/confirm", login),
                Map.entry("/api/v1/auth/register", new TokenBucketLimiter(properties.registerPerHour(), Duration.ofHours(1), nanoClock)),
                Map.entry("/api/v1/auth/password-reset", recovery),
                Map.entry("/api/v1/auth/password-reset/phone", recovery),
                // Logout presents a refresh token too; it shares the refresh budget.
                Map.entry("/api/v1/auth/refresh", refresh),
                Map.entry("/api/v1/auth/logout", refresh),
                // M2: the public demo's answer check needs no account; a generous budget keeps it from being a free
                // scoring service for scripts while a visitor never meets it.
                Map.entry("/api/v1/demo/check", new TokenBucketLimiter(DEMO_CHECKS_PER_MINUTE, Duration.ofMinutes(1), nanoClock)),
                // M10a: a child's device enters a parent's code (few tries: the code space is the guard), then waits for
                // the parent's approval, asking every few seconds.
                Map.entry("/api/v1/auth/child-device/claim", new TokenBucketLimiter(CHILD_DEVICE_CLAIMS_PER_MINUTE, Duration.ofMinutes(1), nanoClock)),
                Map.entry("/api/v1/auth/child-device/collect", new TokenBucketLimiter(CHILD_DEVICE_COLLECTS_PER_MINUTE, Duration.ofMinutes(1), nanoClock)));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !limiters.containsKey(path(request));
    }

    /**
     * The decoded path the dispatcher routes on - not the raw request URI, where
     * {@code /api/v1/auth/%6Cogin} would miss the budget yet still reach login.
     */
    private static String path(HttpServletRequest request) {
        String pathInfo = request.getPathInfo();
        return request.getServletPath() + (pathInfo == null ? "" : pathInfo);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String path = path(request);
        Duration wait = limiters.get(path).tryAcquire(request.getRemoteAddr());
        if (wait.isZero()) {
            chain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = Math.max(1, (wait.toMillis() + 999) / 1000);
        log.warn("Rate limit exceeded on {} from {}; retry after {}s", path, request.getRemoteAddr(), retryAfterSeconds);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("""
                {"type":"about:blank","title":"Too Many Requests","status":429,\
                "detail":"%s",\
                "instance":"%s","retryAfterSeconds":%d}""".formatted(
                ServerText.forRequest(request, ServerText.FILTER_RATE_LIMITED, 429), path, retryAfterSeconds));
    }

}
