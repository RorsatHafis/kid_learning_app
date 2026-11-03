package com.platform.admin;

import com.platform.TestcontainersConfiguration;
import com.platform.curriculum.entity.AgeHub;
import com.platform.curriculum.repository.AgeHubRepository;
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
 * D-19 content production: the administrator's learning page says, for each offered platform curriculum, how many of the
 * questions children are given explain why, offer a hint, are read aloud and are complete in Khmer - by the same rule the
 * child's reader uses.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class ContentReadinessIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Content-Readiness-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AgeHubRepository ageHubRepository;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void eachOfferedCurriculumSaysHowReadyItsQuestionsAre() throws Exception {
        String admin = admin();
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String subjectId = json(post("/api/v1/studio/subjects", "{\"code\":\"RD_" + suffix + "\",\"name\":\"Ready " + suffix + "\"}",
                admin), 201).get("id").asString();
        String skillId = json(post("/api/v1/studio/subjects/" + subjectId + "/skills", "{\"code\":\"RDS_" + suffix
                + "\",\"name\":\"Count\"}", admin), 201).get("id").asString();
        String objectiveId = json(post("/api/v1/studio/subjects/" + subjectId + "/objectives", "{\"code\":\"RDO_" + suffix
                + "\",\"title\":\"Counting\"}", admin), 201).get("id").asString();
        json(post("/api/v1/studio/objectives/" + objectiveId + "/skills/" + skillId, "", admin), 200);
        String options = "\"options\":[{\"label\":\"three\",\"correct\":true},{\"label\":\"two\",\"correct\":false}]";
        // One question with everything: why, a hint and its Khmer; one with nothing but the English.
        String full = question(admin, "{\"subjectId\":\"" + subjectId + "\",\"questionType\":\"MULTIPLE_CHOICE\",\"prompt\":\"How many?\","
                + "\"difficultyLevel\":1,\"hint\":\"Count them.\",\"explanation\":\"There are three.\"," + options + ",\"skillIds\":[\"" + skillId
                + "\"],\"khmer\":{\"prompt\":\"ប៉ុន្មាន?\",\"hint\":\"រាប់វា។\",\"explanation\":\"មានបី។\",\"options\":[\"បី\",\"ពីរ\"]}}");
        String plain = question(admin, "{\"subjectId\":\"" + subjectId + "\",\"questionType\":\"MULTIPLE_CHOICE\",\"prompt\":\"Which?\","
                + "\"difficultyLevel\":1," + options + ",\"skillIds\":[\"" + skillId + "\"]}");
        json(post("/api/v1/studio/question-versions/" + full + "/publish", "", admin), 200);
        json(post("/api/v1/studio/question-versions/" + plain + "/publish", "", admin), 200);
        String lessonId = json(post("/api/v1/studio/lessons", "{\"subjectId\":\"" + subjectId + "\",\"title\":\"Count\"}", admin), 201)
                .get("id").asString();
        String activityVersionId = json(post("/api/v1/studio/lessons/" + lessonId + "/activities",
                "{\"title\":\"Count\",\"skillIds\":[\"" + skillId + "\"]}", admin), 201).get("versions").get(0).get("id").asString();
        json(put("/api/v1/studio/activity-versions/" + activityVersionId + "/items",
                "{\"items\":[{\"questionVersionId\":\"" + full + "\"},{\"questionVersionId\":\"" + plain + "\"}]}", admin), 200);
        json(post("/api/v1/studio/activity-versions/" + activityVersionId + "/publish", "", admin), 200);
        String ageHubId = ageHubRepository.save(AgeHub.create("RD_" + suffix, "Ready " + suffix, 90, 99, 99)).getId().toString();
        JsonNode curriculum = json(post("/api/v1/studio/curricula", "{\"subjectId\":\"" + subjectId + "\",\"ageHubId\":\"" + ageHubId
                + "\",\"name\":\"Ready " + suffix + "\"}", admin), 201);
        String curriculumVersion = curriculum.get("versions").get(0).get("id").asString();
        json(put("/api/v1/studio/curriculum-versions/" + curriculumVersion + "/objectives", "{\"objectiveIds\":[\"" + objectiveId + "\"]}",
                admin), 200);
        json(post("/api/v1/studio/curriculum-versions/" + curriculumVersion + "/publish", "", admin), 200);

        JsonNode row = null;
        for (JsonNode r : json(get("/api/v1/admin/learning-operations", admin), 200).get("readiness")) {
            if (r.get("curriculumId").asString().equals(curriculum.get("id").asString())) {
                row = r;
            }
        }
        assertThat(row).as("the offered curriculum is listed").isNotNull();
        assertThat(row.get("ageGroup").asString()).isEqualTo("Ready " + suffix);
        assertThat(row.get("questions").asInt()).isEqualTo(2);
        assertThat(row.get("explained").asInt()).isEqualTo(1);
        assertThat(row.get("hinted").asInt()).isEqualTo(1);
        assertThat(row.get("readAloud").asInt()).isZero();
        assertThat(row.get("khmerComplete").asInt()).isEqualTo(1);
        assertThat(row.get("khmerTranslatable").asInt()).isEqualTo(2);

        // Only administrators read it.
        String parent = json(post("/api/v1/auth/register", "{\"email\":\"readiness-parent-" + UUID.randomUUID() + "@example.com\",\"password\":\""
                + PASSWORD + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null), 201).get("accessToken").asString();
        assertThat(get("/api/v1/admin/learning-operations", parent).statusCode()).isEqualTo(403);
    }

    private String question(String admin, String body) throws Exception {
        return json(post("/api/v1/studio/questions", body, admin), 201).get("versions").get(0).get("id").asString();
    }

    // ------------------------------------------------------------------ helpers

    private String admin() throws Exception {
        String email = "readiness-admin-" + UUID.randomUUID() + "@example.com";
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
