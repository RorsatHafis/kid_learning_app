package com.platform.identity.legal;

import com.platform.TestcontainersConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PD-18's integration points (Y15) once the owner has published a privacy notice and terms:
 * they are linked publicly, an account is created only by agreeing to the current version, and
 * an account agrees again to a new version.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "platform.legal.privacy-url=https://example.org/privacy",
        "platform.legal.terms-url=https://example.org/terms",
        "platform.legal.version=2026-10"})
/**
 * An account is created only by agreeing to the current legal version.
 */
@Tag("integration")
class LegalAcceptanceIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Legal-Test-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void anAccountIsCreatedOnlyByAgreeingToTheCurrentVersion() throws Exception {
        JsonNode legal = json(send("GET", "/api/v1/auth/legal", null, null), 200);
        assertThat(legal.get("privacyUrl").asString()).isEqualTo("https://example.org/privacy");
        assertThat(legal.get("termsUrl").asString()).isEqualTo("https://example.org/terms");
        assertThat(legal.get("version").asString()).isEqualTo("2026-10");

        String email = "legal-" + UUID.randomUUID() + "@example.com";
        assertThat(register(email, null).statusCode()).as("not agreed").isEqualTo(400);
        assertThat(register(email, "2025-01").statusCode()).as("an old version").isEqualTo(400);
        assertThat(jdbc.queryForObject("select count(*) from accounts where normalized_email = ?", Integer.class, email)).isZero();

        JsonNode created = json(register(email, "2026-10"), 201);
        String token = created.get("accessToken").asString();
        assertThat(json(send("GET", "/api/v1/account", null, token), 200).get("legalVersionAccepted").asString()).isEqualTo("2026-10");
        assertThat(jdbc.queryForObject("select legal_accepted_at is not null from accounts where normalized_email = ?",
                Boolean.class, email)).isTrue();

        // Agreeing again records only the current version.
        assertThat(send("POST", "/api/v1/account/legal-acceptance", "{\"version\":\"2025-01\"}", token).statusCode()).isEqualTo(409);
        assertThat(json(send("POST", "/api/v1/account/legal-acceptance", "{\"version\":\"2026-10\"}", token), 200)
                .get("legalVersionAccepted").asString()).isEqualTo("2026-10");
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action = 'LEGAL_TERMS_ACCEPTED' and actor_id = ?::uuid",
                Integer.class, UUID.fromString(created.get("accountId").asString()))).isEqualTo(1);
        assertThat(send("POST", "/api/v1/account/legal-acceptance", "{\"version\":\"2026-10\"}", null).statusCode()).isEqualTo(401);
    }

    private HttpResponse<String> register(String email, String version) throws Exception {
        return send("POST", "/api/v1/auth/register", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"" + (version == null ? "" : ",\"acceptedLegalVersion\":\""
                + version + "\"") + "}", null);
    }

    private static JsonNode json(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> send(String method, String path, String body, String token) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (token != null) req.header("Authorization", "Bearer " + token);
        return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

}
