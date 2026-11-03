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
 * Content gaps (skills nothing practises) count only what a curriculum's paths may place (PathContentScope, N7): a skill
 * of a platform curriculum practised only by a teacher's own activity is still a gap - its family children get nothing.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class CoverageGapScopeIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Content-Readiness-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AgeHubRepository ageHubRepository;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void aSkillOnlyATeachersActivityPractisesIsStillAGapForAPlatformCurriculum() throws Exception {
        String admin = admin();
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String subjectId = json(post("/api/v1/studio/subjects", "{\"code\":\"GP_" + suffix + "\",\"name\":\"Gaps " + suffix + "\"}",
                admin), 201).get("id").asString();
        String skillId = json(post("/api/v1/studio/subjects/" + subjectId + "/skills", "{\"code\":\"GPS_" + suffix
                + "\",\"name\":\"Gap skill " + suffix + "\"}", admin), 201).get("id").asString();
        String objectiveId = json(post("/api/v1/studio/subjects/" + subjectId + "/objectives", "{\"code\":\"GPO_" + suffix
                + "\",\"title\":\"Gaps\"}", admin), 201).get("id").asString();
        json(post("/api/v1/studio/objectives/" + objectiveId + "/skills/" + skillId, "", admin), 200);
        // A second skill of the objective the platform does practise, so the curriculum has something to play.
        String coveredId = json(post("/api/v1/studio/subjects/" + subjectId + "/skills", "{\"code\":\"GPC_" + suffix
                + "\",\"name\":\"Covered " + suffix + "\"}", admin), 201).get("id").asString();
        json(post("/api/v1/studio/objectives/" + objectiveId + "/skills/" + coveredId, "", admin), 200);
        String options = "\"options\":[{\"label\":\"three\",\"correct\":true},{\"label\":\"two\",\"correct\":false}]";

        // A teacher's own activity practises the skill.
        String teacher = account(PlatformRole.TEACHER);
        publishedActivity(teacher, subjectId, skillId, options, "Mine");
        publishedActivity(admin, subjectId, coveredId, options, "Covered");
        long before = gapTotal(admin);
        // The platform offers a curriculum with that skill.
        String ageHubId = ageHubRepository.save(AgeHub.create("GP_" + suffix, "Gaps " + suffix, 90, 99, 99)).getId().toString();
        JsonNode curriculum = json(post("/api/v1/studio/curricula", "{\"subjectId\":\"" + subjectId + "\",\"ageHubId\":\"" + ageHubId
                + "\",\"name\":\"Gaps " + suffix + "\"}", admin), 201);
        String curriculumVersion = curriculum.get("versions").get(0).get("id").asString();
        json(put("/api/v1/studio/curriculum-versions/" + curriculumVersion + "/objectives", "{\"objectiveIds\":[\"" + objectiveId + "\"]}",
                admin), 200);
        json(post("/api/v1/studio/curriculum-versions/" + curriculumVersion + "/publish", "", admin), 200);

        assertThat(gapTotal(admin)).as("a family child on it gets nothing for the skill: a gap").isEqualTo(before + 1);

        // Platform practice for it closes the gap.
        publishedActivity(admin, subjectId, skillId, options, "Ours");
        assertThat(gapTotal(admin)).isEqualTo(before);
    }

    private void publishedActivity(String token, String subjectId, String skillId, String options, String title) throws Exception {
        String question = question(token, "{\"subjectId\":\"" + subjectId + "\",\"questionType\":\"MULTIPLE_CHOICE\",\"prompt\":\"" + title
                + "?\",\"difficultyLevel\":1," + options + ",\"skillIds\":[\"" + skillId + "\"]}");
        json(post("/api/v1/studio/question-versions/" + question + "/publish", "", token), 200);
        String lesson = json(post("/api/v1/studio/lessons", "{\"subjectId\":\"" + subjectId + "\",\"title\":\"" + title + "\"}", token), 201)
                .get("id").asString();
        String activity = json(post("/api/v1/studio/lessons/" + lesson + "/activities", "{\"title\":\"" + title + "\",\"skillIds\":[\""
                + skillId + "\"]}", token), 201).get("versions").get(0).get("id").asString();
        json(put("/api/v1/studio/activity-versions/" + activity + "/items", "{\"items\":[{\"questionVersionId\":\"" + question + "\"}]}",
                token), 200);
        json(post("/api/v1/studio/activity-versions/" + activity + "/publish", "", token), 200);
    }

    /** Every gap counted (the panel lists only the first ones by name). */
    private long gapTotal(String admin) throws Exception {
        return json(get("/api/v1/admin/learning-operations", admin), 200).get("coverageGapTotal").asLong();
    }

    private String question(String admin, String body) throws Exception {
        return json(post("/api/v1/studio/questions", body, admin), 201).get("versions").get(0).get("id").asString();
    }

    // ------------------------------------------------------------------ helpers

    private String account(PlatformRole role) throws Exception {
        String email = "readiness-" + role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), role));
        return json(post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", null), 200)
                .get("accessToken").asString();
    }

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
