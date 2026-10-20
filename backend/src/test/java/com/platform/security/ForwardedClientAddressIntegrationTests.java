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
/**
 * Behind the proxy each real client has its own rate-limit budget, a client can't escape it by inventing forwarded
 * addresses, and the app's own origin is recognised from the forwarded scheme and port.
 */
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

    /**
     * The web front passes on the scheme and port the browser used (X-Forwarded-Proto, X-Forwarded-Port);
     * the app's own pages are then the same origin, however the app is reached, and another site is not.
     */
    @Test
    void theAppsOwnOriginIsRecognisedFromTheForwardedSchemeAndPort() throws Exception {
        String self = "localhost:" + port;
        assertThat(login("http://" + self, "http", port, "203.0.113.31").statusCode()).as("own origin, plain http").isEqualTo(401);
        assertThat(login("https://" + self, "https", port, "203.0.113.32").statusCode()).as("own origin behind TLS").isEqualTo(401);
        assertThat(login("https://elsewhere.example", "https", port, "203.0.113.33").statusCode()).as("another site").isEqualTo(403);
        assertThat(login("http://" + self, "https", port, "203.0.113.34").statusCode()).as("http when the app is https")
                .isEqualTo(403);
        assertThat(login("http://localhost:1", "http", port, "203.0.113.35").statusCode()).as("another port").isEqualTo(403);
    }

    private HttpResponse<String> login(String origin, String proto, int forwardedPort, String forwardedFor) throws Exception {
        String body = "{\"email\":\"nobody-" + UUID.randomUUID() + "@example.com\",\"password\":\"whatever-it-is\"}";
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .header("Origin", origin)
                .header("X-Forwarded-Proto", proto)
                .header("X-Forwarded-Port", String.valueOf(forwardedPort))
                .header("X-Forwarded-For", forwardedFor)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> login(String forwardedFor) throws Exception {
        String body = "{\"email\":\"nobody-" + UUID.randomUUID() + "@example.com\",\"password\":\"whatever-it-is\"}";
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", forwardedFor)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

}
