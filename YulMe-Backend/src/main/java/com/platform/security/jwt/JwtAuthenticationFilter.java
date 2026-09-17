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

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
 
    private final JwtService jwtService;
 
    public JwtAuthenticationFilter(JwtService jwtService) {

        this.jwtService = jwtService;
        
    }
 
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
 
        String header = request.getHeader("Authorization");
 
        if (header != null && header.startsWith(BEARER_PREFIX)) {

            String token = header.substring(BEARER_PREFIX.length());

            try {

                JwtService.AuthenticatedPrincipal principal = jwtService.validateAndGetPrincipal(token);
                // Principal stays the bare UUID (every existing controller reads it via
                // "@AuthenticationPrincipal UUID accountId") - only authorities are new here,
                // spring-security's conventional "ROLE_X" prefix so hasRole("TEACHER") in
                // @PreAuthorize matches without every call site spelling out "ROLE_TEACHER".
                List<GrantedAuthority> authorities =
                        List.of(new SimpleGrantedAuthority("ROLE_" + principal.platformRole().name()));
                var authentication = UsernamePasswordAuthenticationToken.authenticated(
                        principal.accountId(), null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (InvalidTokenException e) {

 
                logger.debug("Rejected bearer token", e);

            }

        }
 
        filterChain.doFilter(request, response);
 
    }
    
}
