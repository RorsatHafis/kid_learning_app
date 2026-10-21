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
 * Y45: two proxies with public addresses in front of the backend - a web front (Vercel) and the host's edge
 * (Railway) - with only the last hop on a private network. Without more, the client's address would be the
 * web front's, shared by every family. Listing the web front's own addresses in
 * {@code SERVER_TOMCAT_REMOTEIP_TRUSTED_PROXIES} makes the address the web front appended the client's - and
 * still nothing a client writes itself counts. Here 198.18.0.0/15 stands for the web front's addresses, and the
 * request arrives from 127.0.0.1 (the private last hop).
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "platform.security.rate-limit.login-per-minute=3",
        // [.] and [0-9], not backslashes: these test properties are read like a .properties file, which drops them.
        "server.tomcat.remoteip.trusted-proxies=198[.]1[89][.][0-9]{1,3}[.][0-9]{1,3}"})
@Tag("integration")
class TrustedFrontProxyClientAddressIntegrationTests {

    @Value("${local.server.port}") private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void eachFamilyBehindTheWebFrontHasItsOwnBudget() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(login("203.0.113.40, 198.18.0.5").statusCode()).isEqualTo(401);
        }
        assertThat(login("203.0.113.40, 198.18.0.5").statusCode()).isEqualTo(429);
        // Another family through the same web front address is unaffected.
        assertThat(login("203.0.113.41, 198.18.0.5").statusCode()).isEqualTo(401);
    }

    @Test
    void whatAClientWritesBeforeTheWebFrontsEntryDoesntCount() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(login("198.51.100." + i + ", 203.0.113.50, 198.18.7.9").statusCode()).isEqualTo(401);
        }
        assertThat(login("198.51.100.77, 203.0.113.50, 198.18.7.9").statusCode()).isEqualTo(429);
    }

    @Test
    void aHopThatIsntTrustedEndsTheChainThere() throws Exception {
        // The last public hop isn't a listed web front address: it is the client, whatever comes before it.
        for (int i = 0; i < 3; i++) {
            assertThat(login("203.0.113." + (60 + i) + ", 192.0.2.10").statusCode()).isEqualTo(401);
        }
        assertThat(login("203.0.113.99, 192.0.2.10").statusCode()).isEqualTo(429);
    }

    private HttpResponse<String> login(String forwardedFor) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", forwardedFor)
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"nobody-" + UUID.randomUUID() + "@example.com\",\"password\":\"x\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

}
