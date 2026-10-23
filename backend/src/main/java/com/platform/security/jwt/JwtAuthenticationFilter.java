package com.platform.security.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
 
import java.io.IOException;
import java.util.List;

/**
 * Reads the bearer token on each request and, when it is valid, sets the account and role as the authenticated
 * principal. An invalid token leaves the request unauthenticated.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * Battle 4D: set when a bearer token was presented but rejected (malformed,
     * forged, or expired), so the 401 challenge can say {@code invalid_token}. The
     * request still continues unauthenticated - public endpoints stay reachable, and
     * protected ones are answered 401 by ProblemDetailAuthenticationEntryPoint.
     */
    public static final String REJECTED_TOKEN_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".REJECTED_TOKEN";

    /** M10a: the child a child's device may act for (a UUID); ChildDeviceScopeFilter enforces it. */
    public static final String CHILD_SCOPE_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".CHILD_SCOPE";

    /** The authority of a child's device: never a role's, so no role-based rule can admit it. */
    public static final String CHILD_DEVICE_AUTHORITY = "CHILD_DEVICE";
 
    private final JwtService jwtService;
    private final com.platform.security.session.SessionLiveness liveness;

    public JwtAuthenticationFilter(JwtService jwtService, com.platform.security.session.SessionLiveness liveness) {

        this.jwtService = jwtService;
        this.liveness = liveness;

    }
 
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
 
        String header = request.getHeader("Authorization");
 
        if (header != null && header.startsWith(BEARER_PREFIX)) {

            String token = header.substring(BEARER_PREFIX.length());

            try {

                JwtService.AuthenticatedPrincipal principal = jwtService.validateAndGetPrincipal(token);
                // PD-02 (Y13): a token whose sign-in has ended - signed out, password changed, account
                // suspended - is refused now, like an expired one, instead of until it expires.
                if (!liveness.isLive(principal.accountId(), principal.sessionFamilyId())) {
                    throw new InvalidTokenException("The sign-in this token belongs to has ended", null);
                }
                // Principal stays the bare UUID (every existing controller reads it via
                // "@AuthenticationPrincipal UUID accountId") - only authorities are new here,
                // spring-security's conventional "ROLE_X" prefix so hasRole("TEACHER") in
                // @PreAuthorize matches without every call site spelling out "ROLE_TEACHER".
                // M10a: a child's device acts as the parent's account (whose family gives access) but never with the
                // parent's role; the child scope it carries is enforced by ChildDeviceScopeFilter.
                List<GrantedAuthority> authorities = principal.childId() != null
                        ? List.of(new SimpleGrantedAuthority(CHILD_DEVICE_AUTHORITY))
                        : List.of(new SimpleGrantedAuthority("ROLE_" + principal.platformRole().name()));
                if (principal.childId() != null) {
                    request.setAttribute(CHILD_SCOPE_ATTRIBUTE, principal.childId());
                }
                var authentication = UsernamePasswordAuthenticationToken.authenticated(
                        principal.accountId(), null, authorities);
                // Which sign-in this is (null for older tokens): read only by the device list.
                authentication.setDetails(principal.sessionFamilyId());
                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (InvalidTokenException e) {

                request.setAttribute(REJECTED_TOKEN_ATTRIBUTE, Boolean.TRUE);
                logger.debug("Rejected bearer token", e);

            }

        }
 
        filterChain.doFilter(request, response);
 
    }
    
}
