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
import org.springframework.jdbc.core.JdbcTemplate;
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
 * V84 (LOCALIZATION.md, screen-reader language): an activity says the language of its own words only when it is
 * known - "en" once written in the Studio, kept by its next draft and its copy - and nothing for older content.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class ActivityTextLanguageIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Activity-Language-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void anActivitySaysTheLanguageOfItsWordsOnlyWhenItIsKnown() throws Exception {
        String admin = admin();
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String subjectId = json(post("/api/v1/studio/subjects", "{\"code\":\"AL_" + suffix + "\",\"name\":\"Lang " + suffix + "\"}",
                admin), 201).get("id").asString();
        String skillId = json(post("/api/v1/studio/subjects/" + subjectId + "/skills", "{\"code\":\"ALS_" + suffix
                + "\",\"name\":\"Count\"}", admin), 201).get("id").asString();
        String lessonId = json(post("/api/v1/studio/lessons", "{\"subjectId\":\"" + subjectId + "\",\"title\":\"Count\"}", admin), 201)
                .get("id").asString();
        JsonNode activity = json(post("/api/v1/studio/lessons/" + lessonId + "/activities",
                "{\"title\":\"Count the ducks\",\"skillIds\":[\"" + skillId + "\"]}", admin), 201);
        String activityId = activity.get("id").asString();
        String versionId = activity.get("versions").get(0).get("id").asString();
        json(put("/api/v1/studio/activity-versions/" + versionId + "/introduction", "{\"text\":\"Ducks swim in a row.\"}", admin), 200);

        // Written in the Studio: English as written; with its Khmer complete, Khmer for a Khmer reader.
        JsonNode en = preview(admin, versionId, "en");
        assertThat(en.get("language").isNull()).isTrue();
        assertThat(en.get("writtenIn").asString()).isEqualTo("en");
        json(put("/api/v1/studio/activity-versions/" + versionId, "{\"difficultyLevel\":1,\"khmer\":{\"title\":\"រាប់ទា\"}}", admin), 200);
        json(put("/api/v1/studio/activity-versions/" + versionId + "/introduction",
                "{\"text\":\"Ducks swim in a row.\",\"khmer\":{\"text\":\"ទាហែលជាជួរ។\"}}", admin), 200);
        JsonNode km = preview(admin, versionId, "km");
        assertThat(km.get("language").asString()).isEqualTo("km");
        assertThat(km.get("writtenIn").isNull()).isTrue();

        // Its copy carries its words, so their language.
        String copyVersionId = json(post("/api/v1/studio/activities/" + activityId + "/duplicate", "{\"title\":\"Count the hens\"}", admin), 201)
                .get("versions").get(0).get("id").asString();
        assertThat(preview(admin, copyVersionId, "en").get("writtenIn").asString()).isEqualTo("en");

        // Older content (unknown): nothing said until a draft of it is saved in the Studio.
        jdbc.update("update activity_versions set text_language = null where id = ?::uuid", UUID.fromString(copyVersionId));
        assertThat(preview(admin, copyVersionId, "en").get("writtenIn").isNull()).isTrue();
        json(put("/api/v1/studio/activity-versions/" + copyVersionId, "{\"difficultyLevel\":2}", admin), 200);
        assertThat(preview(admin, copyVersionId, "en").get("writtenIn").asString()).isEqualTo("en");
    }

    private JsonNode preview(String admin, String versionId, String language) throws Exception {
        return json(get("/api/v1/studio/activity-versions/" + versionId + "/preview?language=" + language, admin), 200);
    }

    // ------------------------------------------------------------------ helpers

    private String admin() throws Exception {
        String email = "activity-language-admin-" + UUID.randomUUID() + "@example.com";
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
