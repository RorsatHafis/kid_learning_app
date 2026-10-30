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
 * V85 (LOCALIZATION.md): a lesson's title in English and Khmer - the Khmer saved beside the English, kept when only the
 * English is renamed, removed when emptied, and listed with the lesson in the Studio.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class LessonKhmerTitleIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Lesson-Khmer-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void aLessonsKhmerTitleIsKeptBesideItsEnglish() throws Exception {
        String admin = admin();
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String subjectId = json(post("/api/v1/studio/subjects", "{\"code\":\"LK_" + suffix + "\",\"name\":\"Lessons " + suffix + "\"}",
                admin), 201).get("id").asString();
        String lessonId = json(post("/api/v1/studio/lessons", "{\"subjectId\":\"" + subjectId + "\",\"title\":\"Farm animals\"}", admin), 201)
                .get("id").asString();
        assertThat(lesson(admin, subjectId).get("khmer").get("name").isNull()).isTrue();

        status(put("/api/v1/studio/lessons/" + lessonId, "{\"name\":\"Farm animals\",\"khmer\":{\"name\":\"សត្វកសិដ្ឋាន\"}}", admin), 204);
        JsonNode both = lesson(admin, subjectId);
        assertThat(both.get("title").asString()).isEqualTo("Farm animals");
        assertThat(both.get("khmer").get("name").asString()).isEqualTo("សត្វកសិដ្ឋាន");

        // Renaming the English alone keeps the Khmer; emptying the Khmer removes it.
        status(put("/api/v1/studio/lessons/" + lessonId, "{\"name\":\"Animals on the farm\"}", admin), 204);
        JsonNode renamed = lesson(admin, subjectId);
        assertThat(renamed.get("title").asString()).isEqualTo("Animals on the farm");
        assertThat(renamed.get("khmer").get("name").asString()).isEqualTo("សត្វកសិដ្ឋាន");
        status(put("/api/v1/studio/lessons/" + lessonId, "{\"name\":\"Animals on the farm\",\"khmer\":{\"name\":\" \"}}", admin), 204);
        assertThat(lesson(admin, subjectId).get("khmer").get("name").isNull()).isTrue();
    }

    private JsonNode lesson(String admin, String subjectId) throws Exception {
        return json(get("/api/v1/studio/lessons?subjectId=" + subjectId, admin), 200).get("items").get(0);
    }

    // ------------------------------------------------------------------ helpers

    private String admin() throws Exception {
        String email = "lesson-khmer-admin-" + UUID.randomUUID() + "@example.com";
        accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), PlatformRole.ADMIN));
        return json(post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", null), 200)
                .get("accessToken").asString();
    }

    private static void status(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
    }

    private static JsonNode json(HttpResponse<String> response, int expectedStatus) {
        status(response, expectedStatus);
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
