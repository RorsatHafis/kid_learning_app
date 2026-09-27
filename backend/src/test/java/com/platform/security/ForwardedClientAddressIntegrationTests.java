package com.platform.security;

import com.platform.TestcontainersConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Behind the reverse proxy every request arrives from the proxy, so the client's address -
 * which the sign-in, registration and recovery budgets are keyed on - comes from
 * X-Forwarded-For. Only the proxy's own entry may be believed: a client can put anything in
 * the header before the proxy appends to it. These tests use the application's real setting
 * (not a test override) and arrive from 127.0.0.1, which counts as a trusted proxy.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "platform.security.rate-limit.login-per-minute=3"})
@Tag("integration")
class ForwardedClientAddressIntegrationTests {

    @Value("${local.server.port}") private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void aClientCannotEscapeItsBudgetByInventingForwardedAddresses() throws Exception {
        // The proxy appends the real address (203.0.113.7); the client varies what comes before it.
        for (int i = 0; i < 3; i++) {
            assertThat(login("198.51.100." + i + ", 203.0.113.7").statusCode()).isEqualTo(401);
        }
        assertThat(login("198.51.100.99, 203.0.113.7").statusCode()).isEqualTo(429);
    }

    @Test
    void eachRealClientBehindTheProxyHasItsOwnBudget() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(login("203.0.113.20").statusCode()).isEqualTo(401);
        }
        assertThat(login("203.0.113.20").statusCode()).isEqualTo(429);
        // Someone else behind the same proxy is unaffected.
        assertThat(login("203.0.113.21").statusCode()).isEqualTo(401);
    }

    private HttpResponse<String> login(String forwardedFor) throws Exception {
        String body = "{\"email\":\"nobody-" + UUID.randomUUID() + "@example.com\",\"password\":\"whatever-it-is\"}";
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", forwardedFor)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

}
