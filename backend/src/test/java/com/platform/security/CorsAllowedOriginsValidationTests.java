package com.platform.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Battle N5: the configured CORS origins must be exact http(s) origins. A deployment
 * value that would widen browser access, or silently match nothing, fails startup.
 */
class CorsAllowedOriginsValidationTests {

    @Test
    void exactOriginsAreAccepted() {
        List<String> origins = List.of("http://localhost:5173", "https://app.example.org",
                "https://app.example.org:8443", "https://app.example.org/");

        assertThat(SecurityConfig.requireExactOrigins(origins)).isEqualTo(origins);
        assertThat(SecurityConfig.requireExactOrigins(List.of())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"*", "null", "", "https://*.example.org", "*.example.org", "app.example.org",
            "https://app.example.org/app", "https://app.example.org?x=1", "https://app.example.org#x",
            "https://user@app.example.org", "ftp://app.example.org", "file:///tmp", "https://"})
    void anythingButAnExactOriginFailsStartup(String origin) {
        assertThatThrownBy(() -> SecurityConfig.requireExactOrigins(List.of("https://app.example.org", origin)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exact origins");
    }

}
