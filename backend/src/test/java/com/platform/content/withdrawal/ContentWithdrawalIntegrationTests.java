package com.platform.content.withdrawal;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Taking content down (Y16): a withdrawn question stops reaching children at once - the activity
 * holding it can't be read or started, the steps waiting for it are skipped and the open attempt
 * closed, a new path never gets it - while the answers already given stay. Restoring serves it again.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class ContentWithdrawalIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Withdrawal-Test-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AgeHubRepository ageHubRepository;
    @Autowired private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void aWithdrawnQuestionStopsReachingChildrenAtOnceAndAnswersAlreadyGivenStay() throws Exception {
        String admin = staff(PlatformRole.ADMIN);
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String subjectId = json(post("/api/v1/studio/subjects", "{\"code\":\"WD_" + suffix + "\",\"name\":\"Withdrawal " + suffix
                + "\"}", admin), 201).get("id").asString();
        String skillId = json(post("/api/v1/studio/subjects/" + subjectId + "/skills", "{\"code\":\"WDS_" + suffix
                + "\",\"name\":\"Counts\"}", admin), 201).get("id").asString();
        String objectiveId = json(post("/api/v1/studio/subjects/" + subjectId + "/objectives", "{\"code\":\"WDO_" + suffix
                + "\",\"title\":\"Counting\"}", admin), 201).get("id").asString();
        json(post("/api/v1/studio/objectives/" + objectiveId + "/skills/" + skillId, "", admin), 200);
        String questionVersionId = json(post("/api/v1/studio/questions", "{\"subjectId\":\"" + subjectId
                + "\",\"questionType\":\"NUMERIC\",\"prompt\":\"1 + 1 = ? " + suffix + "\",\"correctAnswer\":\"3\","
                + "\"difficultyLevel\":1,\"skillIds\":[\"" + skillId + "\"]}", admin), 201).get("versions").get(0).get("id").asString();
        json(post("/api/v1/studio/question-versions/" + questionVersionId + "/publish", "", admin), 200);
        String lessonId = json(post("/api/v1/studio/lessons", "{\"subjectId\":\"" + subjectId + "\",\"title\":\"Sums\"}", admin), 201)
                .get("id").asString();
        String activityVersionId = json(post("/api/v1/studio/lessons/" + lessonId + "/activities",
                "{\"title\":\"Wrong key " + suffix + "\",\"skillIds\":[\"" + skillId + "\"]}", admin), 201)
                .get("versions").get(0).get("id").asString();
        json(put("/api/v1/studio/activity-versions/" + activityVersionId + "/items",
                "{\"items\":[{\"questionVersionId\":\"" + questionVersionId + "\"}]}", admin), 200);
        json(post("/api/v1/studio/activity-versions/" + activityVersionId + "/publish", "", admin), 200);
        String ageHubId = ageHubRepository.save(AgeHub.create("WD_" + suffix, "Withdrawal " + suffix, 90, 99, 99)).getId().toString();
        JsonNode curriculum = json(post("/api/v1/studio/curricula", "{\"subjectId\":\"" + subjectId + "\",\"ageHubId\":\"" + ageHubId
                + "\",\"name\":\"Withdrawal " + suffix + "\"}", admin), 201);
        String curriculumId = curriculum.get("id").asString();
        String curriculumVersionId = curriculum.get("versions").get(0).get("id").asString();
        json(put("/api/v1/studio/curriculum-versions/" + curriculumVersionId + "/objectives",
                "{\"objectiveIds\":[\"" + objectiveId + "\"]}", admin), 200);
        json(post("/api/v1/studio/curriculum-versions/" + curriculumVersionId + "/publish", "", admin), 200);

        // A child starts it and answers once (the answer key is wrong: 1 + 1 = 3).
        String parent = registerParent();
        String childId = child(parent);
        String enrollmentId = json(post("/api/v1/children/" + childId + "/enrollments", "{\"curriculumId\":\"" + curriculumId + "\"}",
                parent), 201).get("id").asString();
        JsonNode step = json(get("/api/v1/enrollments/" + enrollmentId + "/journey", parent), 200).get("steps").get(0);
        String itemId = step.get("learningPathItemId").asString();
        String attemptId = json(post("/api/v1/children/" + childId + "/activity-attempts", "{\"activityVersionId\":\"" + activityVersionId
                + "\",\"learningPathItemId\":\"" + itemId + "\"}", parent), 201).get("id").asString();
        json(post("/api/v1/activity-attempts/" + attemptId + "/answers", "{\"questionVersionId\":\"" + questionVersionId
                + "\",\"submittedAnswer\":\"2\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", parent), 201);

        // Refusals: a teacher can't take down platform content; a reason is required.
        String withdrawalPath = "/api/v1/studio/question-versions/" + questionVersionId + "/withdrawal";
        assertThat(post(withdrawalPath, "{\"reason\":\"x\"}", staff(PlatformRole.TEACHER)).statusCode()).isEqualTo(403);
        assertThat(post(withdrawalPath, "{\"reason\":\" \"}", admin).statusCode()).isEqualTo(400);

        JsonNode state = json(post(withdrawalPath, "{\"reason\":\"The answer key is wrong.\"}", admin), 200);
        assertThat(state.get("withdrawal").get("reason").asString()).isEqualTo("The answer key is wrong.");
        assertThat(post(withdrawalPath, "{\"reason\":\"again\"}", admin).statusCode()).as("already down").isEqualTo(409);
        assertThat(json(get("/api/v1/studio/activity-versions/" + activityVersionId + "/withdrawal", admin), 200)
                .get("viaQuestion").asBoolean()).as("the activity holding it is down too").isTrue();

        // At once: not served, not startable, the waiting step skipped, the open attempt closed; the answer stays.
        assertThat(get("/api/v1/activity-versions/" + activityVersionId + "?childId=" + childId, parent).statusCode()).isEqualTo(404);
        assertThat(post("/api/v1/children/" + childId + "/activity-attempts", "{\"activityVersionId\":\"" + activityVersionId + "\"}",
                parent).statusCode()).isEqualTo(404);
        assertThat(jdbc.queryForObject("select status from learning_path_items where id = ?::uuid", String.class, itemId))
                .isEqualTo("SKIPPED");
        assertThat(jdbc.queryForObject("select status from activity_attempts where id = ?::uuid", String.class, attemptId))
                .isEqualTo("ABANDONED");
        assertThat(jdbc.queryForObject("select count(*) from answer_records where activity_attempt_id = ?::uuid", Integer.class,
                attemptId)).isEqualTo(1);
        assertThat(get("/api/v1/enrollments/" + enrollmentId + "/next-activity", parent).statusCode())
                .as("nothing left to serve on this path").isEqualTo(404);
        // A new path never gets it.
        String other = child(parent);
        String otherEnrollment = json(post("/api/v1/children/" + other + "/enrollments", "{\"curriculumId\":\"" + curriculumId + "\"}",
                parent), 201).get("id").asString();
        assertThat(json(get("/api/v1/enrollments/" + otherEnrollment + "/journey", parent), 200).toString())
                .doesNotContain(activityVersionId);
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action = 'CONTENT_WITHDRAWN' and metadata->>'versionId' = ?",
                Integer.class, questionVersionId)).isEqualTo(1);

        // Restored: served and placed again; what was skipped stays skipped.
        json(delete(withdrawalPath, admin), 200);
        assertThat(get("/api/v1/activity-versions/" + activityVersionId + "?childId=" + childId, parent).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select status from learning_path_items where id = ?::uuid", String.class, itemId))
                .isEqualTo("SKIPPED");
        String third = child(parent);
        String thirdEnrollment = json(post("/api/v1/children/" + third + "/enrollments", "{\"curriculumId\":\"" + curriculumId + "\"}",
                parent), 201).get("id").asString();
        assertThat(json(get("/api/v1/enrollments/" + thirdEnrollment + "/journey", parent), 200).toString()).contains(activityVersionId);
        assertThat(delete(withdrawalPath, admin).statusCode()).as("not down any more").isEqualTo(409);
    }

    @Test
    void aDraftCantBeTakenDown() throws Exception {
        String admin = staff(PlatformRole.ADMIN);
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String subjectId = json(post("/api/v1/studio/subjects", "{\"code\":\"WDD_" + suffix + "\",\"name\":\"Draft " + suffix
                + "\"}", admin), 201).get("id").asString();
        String skillId = json(post("/api/v1/studio/subjects/" + subjectId + "/skills", "{\"code\":\"WDDS_" + suffix
                + "\",\"name\":\"Adds\"}", admin), 201).get("id").asString();
        String draft = json(post("/api/v1/studio/questions", "{\"subjectId\":\"" + subjectId
                + "\",\"questionType\":\"NUMERIC\",\"prompt\":\"2 + 2 = ?\",\"correctAnswer\":\"4\",\"difficultyLevel\":1,"
                + "\"skillIds\":[\"" + skillId + "\"]}", admin), 201)
                .get("versions").get(0).get("id").asString();
        assertThat(post("/api/v1/studio/question-versions/" + draft + "/withdrawal", "{\"reason\":\"x\"}", admin).statusCode())
                .isEqualTo(409);
    }

    // ------------------------------------------------------------------

    private String staff(PlatformRole role) throws Exception {
        String email = "withdrawal-" + role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), role));
        return json(post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", null), 200)
                .get("accessToken").asString();
    }

    private String registerParent() throws Exception {
        return json(post("/api/v1/auth/register", "{\"email\":\"withdrawal-parent-" + UUID.randomUUID() + "@example.com\",\"password\":\""
                + PASSWORD + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null), 201).get("accessToken").asString();
    }

    private String child(String parent) throws Exception {
        return json(post("/api/v1/children", "{\"displayName\":\"Withdrawal Child\",\"dateOfBirth\":\""
                + LocalDate.now().minusYears(6) + "\"}", parent), 201).get("id").asString();
    }

    private static JsonNode json(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        return send("GET", path, null, token);
    }

    private HttpResponse<String> post(String path, String body, String token) throws Exception {
        return send("POST", path, body, token);
    }

    private HttpResponse<String> put(String path, String body, String token) throws Exception {
        return send("PUT", path, body, token);
    }

    private HttpResponse<String> delete(String path, String token) throws Exception {
        return send("DELETE", path, null, token);
    }

    private HttpResponse<String> send(String method, String path, String body, String token) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (token != null) req.header("Authorization", "Bearer " + token);
        return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

}
