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
 * Rows pasted straight into an activity being written (POST /studio/activity-versions/{id}/questions/import): each a
 * draft question after the activity's own, taking the activity's skills when the row has none - all or none, only into
 * a draft, only in the activity's subject - and published with the activity (Y42).
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class ActivityQuestionImportIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Activity-Import-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private final HttpClient http = HttpClient.newHttpClient();
    /** Each activity version made here, to its activity. */
    private final java.util.Map<String, String> activities = new java.util.HashMap<>();

    @Test
    void pastedRowsJoinTheActivityAndGoOutWithIt() throws Exception {
        String teacher = account(PlatformRole.TEACHER);
        String[] subject = subjectWithSkill(account(PlatformRole.ADMIN));
        String version = newActivity(teacher, subject);
        json(post("/api/v1/studio/activity-versions/" + version + "/questions", numeric(subject[0], "1 + 1 = ?", "2"), teacher), 201);

        JsonNode imported = json(post("/api/v1/studio/activity-versions/" + version + "/questions/import",
                "{\"questions\":[" + numeric(subject[0], "2 + 2 = ?", "4") + "," + numeric(subject[0], "3 + 3 = ?", "6") + "]}", teacher), 201);
        assertThat(imported).hasSize(2);

        JsonNode items = activityOf(teacher, version).get("items");
        assertThat(items).hasSize(3);
        assertThat(items.get(1).get("questionVersionId").asString()).isEqualTo(imported.get(0).get("versionId").asString());
        assertThat(items.get(2).get("questionVersionId").asString()).isEqualTo(imported.get(1).get("versionId").asString());
        JsonNode question = json(get("/api/v1/studio/questions/" + imported.get(0).get("questionId").asString(), teacher), 200);
        assertThat(question.get("skillIds").get(0).asString()).as("the activity's skill").isEqualTo(subject[1]);

        JsonNode published = json(post("/api/v1/studio/activity-versions/" + version + "/publish-with-questions", "", teacher), 200);
        published.get("versions").get(0).get("items")
                .forEach(i -> assertThat(i.get("questionVersionStatus").asString()).isEqualTo("PUBLISHED"));

        // A published activity takes nothing more.
        assertThat(post("/api/v1/studio/activity-versions/" + version + "/questions/import",
                "{\"questions\":[" + numeric(subject[0], "4 + 4 = ?", "8") + "]}", teacher).statusCode()).isEqualTo(409);
    }

    @Test
    void aRefusedPasteLeavesTheActivityAsItWas() throws Exception {
        String admin = account(PlatformRole.ADMIN);
        String[] subject = subjectWithSkill(admin);
        String[] other = subjectWithSkill(admin);
        String teacher = account(PlatformRole.TEACHER);
        String version = newActivity(teacher, subject);

        // A row of another subject: refused, nothing added.
        assertThat(post("/api/v1/studio/activity-versions/" + version + "/questions/import", "{\"questions\":["
                + numeric(subject[0], "2 + 2 = ?", "4") + "," + numeric(other[0], "3 + 3 = ?", "6") + "]}", teacher).statusCode()).isEqualTo(400);
        assertThat(activityOf(teacher, version).get("items")).isEmpty();
        assertThat(json(get("/api/v1/studio/questions?subjectId=" + subject[0], teacher), 200).get("total").asInt()).isZero();

        // Another teacher can't paste into it.
        assertThat(post("/api/v1/studio/activity-versions/" + version + "/questions/import",
                "{\"questions\":[" + numeric(subject[0], "2 + 2 = ?", "4") + "]}", account(PlatformRole.TEACHER)).statusCode()).isIn(403, 404);
        assertThat(activityOf(teacher, version).get("items")).isEmpty();
    }

    // ------------------------------------------------------------------ helpers

    private static String numeric(String subjectId, String prompt, String answer) {
        return "{\"subjectId\":\"" + subjectId + "\",\"questionType\":\"NUMERIC\",\"prompt\":\"" + prompt + "\",\"correctAnswer\":\""
                + answer + "\",\"skillIds\":[]}";
    }

    /** A subject and one skill of it: {subjectId, skillId}. */
    private String[] subjectWithSkill(String admin) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String subjectId = json(post("/api/v1/studio/subjects", "{\"code\":\"AQI_" + suffix + "\",\"name\":\"Activity import " + suffix + "\"}",
                admin), 201).get("id").asString();
        String skillId = json(post("/api/v1/studio/subjects/" + subjectId + "/skills", "{\"code\":\"AQIS_" + suffix
                + "\",\"name\":\"Double\"}", admin), 201).get("id").asString();
        return new String[]{subjectId, skillId};
    }

    private String newActivity(String token, String[] subject) throws Exception {
        String lessonId = json(post("/api/v1/studio/lessons", "{\"subjectId\":\"" + subject[0] + "\",\"title\":\"Doubles\"}", token), 201)
                .get("id").asString();
        JsonNode activity = json(post("/api/v1/studio/lessons/" + lessonId + "/activities", "{\"title\":\"Doubles quiz\",\"skillIds\":[\""
                + subject[1] + "\"]}", token), 201);
        activities.put(activity.get("versions").get(0).get("id").asString(), activity.get("id").asString());
        return activity.get("versions").get(0).get("id").asString();
    }

    /** The activity's version with this id, as its author sees it. */
    private JsonNode activityOf(String token, String versionId) throws Exception {
        String activityId = activities.get(versionId);
        for (JsonNode v : json(get("/api/v1/studio/activities/" + activityId, token), 200).get("versions")) {
            if (v.get("id").asString().equals(versionId)) {
                return v;
            }
        }
        throw new AssertionError("no version " + versionId);
    }

    private String account(PlatformRole role) throws Exception {
        String email = "activity-import-" + role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
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
