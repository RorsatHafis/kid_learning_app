package com.platform.content;

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
 * LOCALIZATION.md: reusing a question adds its published version, so the bank says that version's Khmer status
 * separately from the latest draft's - a newer draft translated into Khmer doesn't make the reused one Khmer.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class PublishedKhmerStatusIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Published-Khmer-Pass-2026";
    private static final String OPTIONS = "\"options\":[{\"label\":\"three\",\"correct\":true},{\"label\":\"two\",\"correct\":false}]";

    @Value("${local.server.port}") private int port;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void thePublishedVersionsKhmerIsToldApartFromANewerDrafts() throws Exception {
        String admin = admin();
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String subjectId = json(post("/api/v1/studio/subjects", "{\"code\":\"PK_" + suffix + "\",\"name\":\"Pub " + suffix + "\"}",
                admin), 201).get("id").asString();
        String skillId = json(post("/api/v1/studio/subjects/" + subjectId + "/skills", "{\"code\":\"PKS_" + suffix
                + "\",\"name\":\"Count\"}", admin), 201).get("id").asString();
        JsonNode created = json(post("/api/v1/studio/questions", "{\"subjectId\":\"" + subjectId + "\",\"questionType\":\"MULTIPLE_CHOICE\","
                + "\"prompt\":\"How many?\",\"difficultyLevel\":1," + OPTIONS + ",\"skillIds\":[\"" + skillId + "\"]}", admin), 201);
        String questionId = created.get("id").asString();
        String v1 = created.get("versions").get(0).get("id").asString();

        // A draft only: nothing is published, so there's nothing to reuse.
        JsonNode draftOnly = bank(admin, subjectId);
        assertThat(draftOnly.get("khmer").asString()).isEqualTo("NONE");
        assertThat(draftOnly.get("publishedKhmer").isNull()).isTrue();

        // Published in English only; then a new draft translated into Khmer.
        json(post("/api/v1/studio/question-versions/" + v1 + "/publish", "", admin), 200);
        String v2 = json(post("/api/v1/studio/questions/" + questionId + "/draft", "", admin), 200).get("versions").get(1).get("id").asString();
        json(put("/api/v1/studio/question-versions/" + v2, "{\"prompt\":\"How many?\",\"difficultyLevel\":1," + OPTIONS
                + ",\"khmer\":{\"prompt\":\"ប៉ុន្មាន?\",\"options\":[\"បី\",\"ពីរ\"]}}", admin), 200);
        JsonNode both = bank(admin, subjectId);
        assertThat(both.get("khmer").asString()).as("the latest draft").isEqualTo("COMPLETE");
        assertThat(both.get("publishedKhmer").asString()).as("what reuse adds").isEqualTo("NONE");

        // Once the translated draft is published, reuse adds the Khmer too.
        json(post("/api/v1/studio/question-versions/" + v2 + "/publish", "", admin), 200);
        assertThat(bank(admin, subjectId).get("publishedKhmer").asString()).isEqualTo("COMPLETE");
    }

    private JsonNode bank(String admin, String subjectId) throws Exception {
        return json(get("/api/v1/studio/questions?subjectId=" + subjectId, admin), 200).get("items").get(0);
    }

    // ------------------------------------------------------------------ helpers

    private String admin() throws Exception {
        String email = "published-khmer-admin-" + UUID.randomUUID() + "@example.com";
        accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), PlatformRole.ADMIN));
        return json(post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", null), 200)
                .get("accessToken").asString();
    }

    private static JsonNode json(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> get(String path, String bearer) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).GET(), bearer);
    }

    private HttpResponse<String> post(String path, String body, String bearer) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)), bearer);
    }

    private HttpResponse<String> put(String path, String body, String bearer) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body)), bearer);
    }

    private HttpResponse<String> send(HttpRequest.Builder request, String bearer) throws Exception {
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
