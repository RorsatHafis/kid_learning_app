package com.platform.security;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import jakarta.servlet.DispatcherType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import com.platform.security.jwt.JwtAuthenticationFilter;
import com.platform.security.jwt.JwtService;
import com.platform.security.ratelimit.AuthRateLimitFilter;
import com.platform.security.ratelimit.RateLimitProperties;


/**
 * HTTP security: stateless JWT authentication, the few public endpoints (health, /auth, signed media URLs, payment
 * notices and the API docs where a profile enables them), CORS from configuration, and Spring's delegating
 * password encoder (bcrypt). Other actuator endpoints are for administrators; every other request needs a valid
 * token, and what it may do is decided by the services.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    public PasswordEncoder passwordEncoder() {   

        return PasswordEncoderFactories.createDelegatingPasswordEncoder();

    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwtService,
                                                           // Lazy: filters are built while the server starts, before the
                                                           // database is ready; the check first runs on a request.
                                                           @org.springframework.context.annotation.Lazy
                                                           com.platform.security.session.SessionLiveness liveness) {

        return new JwtAuthenticationFilter(jwtService, liveness);

    }

    /**
     * The frontend calls same-origin "/api/v1" paths, but whenever a proxy forwards
     * the browser's Origin while rewriting Host (the Vite dev proxy, some reverse
     * proxies), or the frontend is hosted on its own origin, the backend sees those
     * calls as cross-origin and rejects any origin not listed here with 403.
     *
     * <p>Origins come from platform.security.cors.allowed-origins, bound to
     * CORS_ALLOWED_ORIGINS in application.yaml (required outside the local profile).
     * CORS only decides which browser origins may read responses; it is not
     * authentication - every request still goes through the JWT filter and
     * authorization below.
     *
     * <p>Credentials (cookies) are not allowed: authentication is an explicitly set
     * Bearer header, which CORS governs through the allowed headers, and this API sets
     * no cookies. So there is nothing for credentialed cross-origin requests to carry.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${platform.security.cors.allowed-origins}") List<String> allowedOrigins) {

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(requireExactOrigins(allowedOrigins));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        log.info("CORS allowed origins: {}", configuration.getAllowedOrigins());
        return source;

    }

    /**
     * Fails startup unless every configured origin is an exact http(s) origin. This
     * rejects "*", "null", wildcard hosts, and paths or queries: a mistyped deployment
     * value must not widen browser access or be silently ignored.
     */
    static List<String> requireExactOrigins(List<String> origins) {

        for (String origin : origins) {
            URI uri;
            try {
                uri = new URI(origin);
            } catch (URISyntaxException e) {
                throw invalidOrigin(origin);
            }
            String scheme = uri.getScheme();
            boolean exactOrigin = ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && uri.getHost() != null
                    && uri.getRawUserInfo() == null
                    && (uri.getRawPath().isEmpty() || uri.getRawPath().equals("/"))
                    && uri.getRawQuery() == null
                    && uri.getRawFragment() == null;
            if (!exactOrigin) {
                throw invalidOrigin(origin);
            }
        }
        return origins;

    }

    private static IllegalStateException invalidOrigin(String origin) {
        return new IllegalStateException("platform.security.cors.allowed-origins (CORS_ALLOWED_ORIGINS) must list"
                + " exact origins such as https://app.example.org - scheme://host[:port], no wildcard, no path;"
                + " got '" + origin + "'");
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter,
                                                     CorsConfigurationSource corsConfigurationSource,
                                                     RateLimitProperties rateLimitProperties,
                                                     org.springframework.beans.factory.ObjectProvider<org.springframework.jdbc.core.JdbcTemplate> jdbc)
            throws Exception {
 
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                // Battle 4D: the servlet ERROR dispatch carries no authentication (the JWT
                // filter does not re-run on it), so without this a server error on any
                // request would be re-answered as an authentication failure - and a 401
                // would send the frontend into a pointless token refresh.
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                // Whatever else a profile exposes (metrics in staging, flyway locally) describes
                // the running system - operators only, never any signed-in account.
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/api/v1/auth/**").permitAll()
                // X11: media by signed URL only - the signature, issued inside a response the caller
                // could read, is the permission (MediaService). Nothing else under it is public.
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/media/*").permitAll()
                // Y43: a payment provider's notice has no session; it only makes us ask the provider (PaymentService).
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/payments/callbacks/*").permitAll()
                // M2: the landing page's statements and the public demo (published platform content, nothing stored;
                // the check is rate limited per client in AuthRateLimitFilter).
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/site/statements", "/api/v1/site/copy", "/api/v1/demo/activities").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/demo/check").permitAll()
                .anyRequest().authenticated()
            )
            // Battle 4D: 401 for "not authenticated" (no, malformed, forged, or expired
            // access token). Without this, Spring Security's fallback is 403 whenever no
            // httpBasic/formLogin is configured. "Authenticated but not permitted" stays
            // 403: that comes from AccessDeniedException -> GlobalExceptionHandler.
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(new ProblemDetailAuthenticationEntryPoint()))
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            // OD-01: budgets for the unauthenticated identity endpoints. Built here rather
            // than as a bean so Boot does not also register it as a plain servlet filter,
            // which would run it before CORS and hide the 429 from browsers.
            .addFilterAfter(new AuthRateLimitFilter(rateLimitProperties), CorsFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            // M10a: a child's device may act only within its one child's learning space (built here, not as a
            // bean, for the same reason as the rate limiter).
            .addFilterAfter(new ChildDeviceScopeFilter(jdbc), JwtAuthenticationFilter.class);
 
        return http.build();
 
    }

}