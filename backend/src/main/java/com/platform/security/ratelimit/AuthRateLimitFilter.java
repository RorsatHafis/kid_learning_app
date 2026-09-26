package com.platform.security.ratelimit;

import java.io.IOException;
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

/**
 * Refuses a client address that exceeds its budget on an unauthenticated identity
 * endpoint with 429 and a {@code Retry-After} header, before the request reaches
 * the controller - so a refused refresh never consumes (rotates) the refresh token
 * and the client can simply retry it later.
 *
 * <p>The client address is {@link HttpServletRequest#getRemoteAddr()}. Behind a
 * reverse proxy that is only the real client when the proxy <em>replaces</em>
 * X-Forwarded-For and the application honours it (see OD-12); otherwise every
 * client shares the proxy's address, or a client can choose its own.
 *
 * <p>Registered inside the security filter chain after CORS (see SecurityConfig), so
 * a browser can read the 429.
 */
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimitFilter.class);

    private final Map<String, TokenBucketLimiter> limiters;

    public AuthRateLimitFilter(RateLimitProperties properties) {
        this(properties, System::nanoTime);
    }

    AuthRateLimitFilter(RateLimitProperties properties, LongSupplier nanoClock) {
        TokenBucketLimiter refresh = new TokenBucketLimiter(properties.refreshPerMinute(), Duration.ofMinutes(1), nanoClock);
        this.limiters = Map.of(
                "/api/v1/auth/login", new TokenBucketLimiter(properties.loginPerMinute(), Duration.ofMinutes(1), nanoClock),
                "/api/v1/auth/register", new TokenBucketLimiter(properties.registerPerHour(), Duration.ofHours(1), nanoClock),
                // Logout presents a refresh token too; it shares the refresh budget.
                "/api/v1/auth/refresh", refresh,
                "/api/v1/auth/logout", refresh);
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
        response.getWriter().write("""
                {"type":"about:blank","title":"Too Many Requests","status":429,\
                "detail":"Too many requests from this network. Please wait and try again.",\
                "instance":"%s","retryAfterSeconds":%d}""".formatted(path, retryAfterSeconds));
    }

}
