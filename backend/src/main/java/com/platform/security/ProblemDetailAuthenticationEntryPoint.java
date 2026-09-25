package com.platform.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import com.platform.common.web.CorrelationIdFilter;
import com.platform.security.jwt.JwtAuthenticationFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Battle 4D: the response to a request that is NOT authenticated - no access token,
 * or one that is malformed, forged, or expired. Always 401 with an RFC 6750
 * {@code WWW-Authenticate: Bearer} challenge ({@code error="invalid_token"} when a
 * token was presented but rejected), in the same ProblemDetail shape as
 * GlobalExceptionHandler.
 *
 * <p>Without an explicit entry point, Spring Security falls back to
 * {@code Http403ForbiddenEntryPoint} whenever no authentication mechanism (httpBasic,
 * formLogin, ...) registers one - so every unauthenticated request was answered 403.
 * 403 is reserved for "authenticated but not permitted" (AccessDeniedException ->
 * GlobalExceptionHandler), and the frontend refreshes the access token only on 401,
 * so an expired access token never triggered a refresh.
 */
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        boolean tokenRejected = Boolean.TRUE.equals(request.getAttribute(JwtAuthenticationFilter.REJECTED_TOKEN_ATTRIBUTE));
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, tokenRejected
                ? "Bearer error=\"invalid_token\", error_description=\"The access token is invalid or expired\""
                : "Bearer");

        String detail = tokenRejected ? "The access token is invalid or expired" : "Authentication is required";
        // Every value below is fixed text, a timestamp, or a correlation id that
        // CorrelationIdFilter has already constrained to [A-Za-z0-9._-] (or a UUID) -
        // nothing caller-controlled reaches the body unescaped.
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        String body = "{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401,"
                + "\"detail\":\"" + detail + "\",\"instance\":\"" + request.getRequestURI().replaceAll("[^A-Za-z0-9/._\\-]", "") + "\","
                + "\"timestamp\":\"" + Instant.now() + "\","
                + "\"correlationId\":" + (correlationId == null ? "null" : "\"" + correlationId + "\"") + "}";

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(body);
    }

}
