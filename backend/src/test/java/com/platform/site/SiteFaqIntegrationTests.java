package com.platform.site;

import com.platform.TestcontainersConfiguration;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.repository.AccountRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
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
 * LAND-1: the landing page's questions and answers - shown to anyone, written only by administrators in both
 * languages, ordered and shown or hidden, an edit over someone else's refused; nothing shown that isn't written.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class SiteFaqIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Faq-Test-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void administratorsWriteTheQuestionsAndAnyoneReadsTheShownOnes() throws Exception {
        JsonNode seeded = json(send("GET", "/api/v1/site/faq", null, null), 200);
        assertThat(seeded.size()).as("the first questions (V86), read by anyone").isGreaterThanOrEqualTo(6);
        assertThat(seeded.get(0).get("questionEn").asString()).isEqualTo("Which ages is Bookeyond Kid for?");
        assertThat(seeded.get(0).has("version")).as("no editing fields in public").isFalse();

        String admin = staff(PlatformRole.ADMIN);
        String body = "{\"questionEn\":\"Do I need a credit card?\",\"questionKm\":\"តើខ្ញុំត្រូវការកាតឥណទានទេ?\","
                + "\"answerEn\":\"No.\",\"answerKm\":\"ទេ។\",\"position\":9,\"visible\":true}";
        JsonNode created = json(send("POST", "/api/v1/admin/site-faq", body, admin), 201);
        String id = created.get("id").asString();
        assertThat(questions()).contains("Do I need a credit card?");

        // Hidden: no longer on the page; an edit over an older version is refused.
        String hidden = body.replace("\"visible\":true", "\"visible\":false,\"version\":" + created.get("version").asLong());
        json(send("PUT", "/api/v1/admin/site-faq/" + id, hidden, admin), 200);
        assertThat(questions()).doesNotContain("Do I need a credit card?");
        assertThat(send("PUT", "/api/v1/admin/site-faq/" + id, hidden, admin).statusCode()).as("stale version").isEqualTo(409);

        // Both languages, question and answer, are required.
        assertThat(send("POST", "/api/v1/admin/site-faq", body.replace("\"answerKm\":\"ទេ។\"", "\"answerKm\":\" \""), admin).statusCode())
                .isEqualTo(400);

        // Only administrators manage them.
        String parent = staff(PlatformRole.PARENT);
        assertThat(send("POST", "/api/v1/admin/site-faq", body, parent).statusCode()).isIn(403, 404);
        assertThat(send("GET", "/api/v1/admin/site-faq", null, parent).statusCode()).isIn(403, 404);

        assertThat(send("DELETE", "/api/v1/admin/site-faq/" + id, null, admin).statusCode()).isEqualTo(204);
        assertThat(json(send("GET", "/api/v1/admin/site-faq", null, admin), 200).toString()).doesNotContain(id);
    }

    private String questions() throws Exception {
        return json(send("GET", "/api/v1/site/faq", null, null), 200).toString();
    }

    private String staff(PlatformRole role) throws Exception {
        String email = "faq-" + role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), role));
        return json(send("POST", "/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", null), 200)
                .get("accessToken").asString();
    }

    private static JsonNode json(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> send(String method, String path, String body, String bearer) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
