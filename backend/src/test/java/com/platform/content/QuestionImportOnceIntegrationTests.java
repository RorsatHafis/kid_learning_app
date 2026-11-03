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
 * A paste is added once: the same request again under its idempotency key (a retry after a lost response, a second tab)
 * is refused instead of adding every row twice; a key reused for other rows is refused; a paste that failed may be
 * retried under its key.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class QuestionImportOnceIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Question-Import-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void theSamePasteTwiceIsAddedOnce() throws Exception {
        String author = account(PlatformRole.ADMIN);
        String[] subject = subjectWithSkill(author);
        String key = UUID.randomUUID().toString();
        String body = "{\"questions\":[" + row(subject, "NUMERIC", "2 + 2 = ?", "4", "") + "],\"idempotencyKey\":\"" + key + "\"}";

        json(post("/api/v1/studio/questions/import", body, author), 201);
        HttpResponse<String> again = post("/api/v1/studio/questions/import", body, author);
        assertThat(again.statusCode()).isEqualTo(409);
        assertThat(again.body()).contains("These rows were added already");
        assertThat(bankSize(subject[0], author)).isEqualTo(1);

        // The same key for other rows: refused, nothing added.
        String other = "{\"questions\":[" + row(subject, "NUMERIC", "3 + 3 = ?", "6", "") + "],\"idempotencyKey\":\"" + key + "\"}";
        assertThat(post("/api/v1/studio/questions/import", other, author).statusCode()).isEqualTo(409);
        assertThat(bankSize(subject[0], author)).isEqualTo(1);
    }

    @Test
    void aFailedPasteCanBeTriedAgainUnderItsKey() throws Exception {
        String author = account(PlatformRole.ADMIN);
        String[] subject = subjectWithSkill(author);
        String[] other = subjectWithSkill(author);
        String key = UUID.randomUUID().toString();
        String foreignSkill = "{\"subjectId\":\"" + subject[0] + "\",\"questionType\":\"NUMERIC\",\"prompt\":\"3 + 3 = ?\","
                + "\"correctAnswer\":\"6\",\"skillIds\":[\"" + other[1] + "\"]}";
        String failing = "{\"questions\":[" + foreignSkill + "],\"idempotencyKey\":\"" + key + "\"}";

        assertThat(post("/api/v1/studio/questions/import", failing, author).statusCode()).isEqualTo(400);
        // Not "already being processed": the failure released the key.
        assertThat(post("/api/v1/studio/questions/import", failing, author).statusCode()).isEqualTo(400);
        assertThat(bankSize(subject[0], author)).isZero();
    }

    @Test
    void anActivityGetsAPastedRowOnce() throws Exception {
        String teacher = account(PlatformRole.TEACHER);
        String[] subject = subjectWithSkill(account(PlatformRole.ADMIN));
        String lessonId = json(post("/api/v1/studio/lessons", "{\"subjectId\":\"" + subject[0] + "\",\"title\":\"Once\"}", teacher), 201)
                .get("id").asString();
        JsonNode activity = json(post("/api/v1/studio/lessons/" + lessonId + "/activities", "{\"title\":\"Once\",\"skillIds\":[\""
                + subject[1] + "\"]}", teacher), 201);
        String version = activity.get("versions").get(0).get("id").asString();
        String body = "{\"questions\":[" + row(subject, "NUMERIC", "5 + 5 = ?", "10", "") + "],\"idempotencyKey\":\""
                + UUID.randomUUID() + "\"}";

        json(post("/api/v1/studio/activity-versions/" + version + "/questions/import", body, teacher), 201);
        assertThat(post("/api/v1/studio/activity-versions/" + version + "/questions/import", body, teacher).statusCode()).isEqualTo(409);
        JsonNode items = json(get("/api/v1/studio/activities/" + activity.get("id").asString(), teacher), 200).get("versions").get(0).get("items");
        assertThat(items).hasSize(1);
    }

    // ------------------------------------------------------------------ helpers

    private static String row(String[] subject, String type, String prompt, String answer, String more) {
        return "{\"subjectId\":\"" + subject[0] + "\",\"questionType\":\"" + type + "\",\"prompt\":\"" + prompt + "\""
                + (answer == null ? "" : ",\"correctAnswer\":\"" + answer + "\"") + ",\"skillIds\":[\"" + subject[1] + "\"]" + more + "}";
    }

    /** A subject and one skill of it: {subjectId, skillId}. */
    private String[] subjectWithSkill(String admin) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String subjectId = json(post("/api/v1/studio/subjects", "{\"code\":\"QI_" + suffix + "\",\"name\":\"Import " + suffix + "\"}",
                admin), 201).get("id").asString();
        String skillId = json(post("/api/v1/studio/subjects/" + subjectId + "/skills", "{\"code\":\"QIS_" + suffix
                + "\",\"name\":\"Add\"}", admin), 201).get("id").asString();
        return new String[]{subjectId, skillId};
    }

    private int bankSize(String subjectId, String bearer) throws Exception {
        return json(get("/api/v1/studio/questions?subjectId=" + subjectId, bearer), 200).get("total").asInt();
    }

    private String account(PlatformRole role) throws Exception {
        String email = "question-import-" + role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), role));
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
