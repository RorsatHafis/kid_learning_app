package com.platform.review;

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
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * X25 over real HTTP against PostgreSQL: a child who has finished every step still gets a review
 * that falls due - placed on the path whose curriculum teaches its skill when their space opens,
 * once, and only by their family.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class DueReviewIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Value("${local.server.port}") private int port;
    @Autowired private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void aDueReviewReachesAChildWhoHasFinishedEveryStep() throws Exception {
        String parent = registerParent();
        String childId = json(post("/api/v1/children", "{\"displayName\":\"Review Child\",\"dateOfBirth\":\""
                + LocalDate.now().minusYears(6) + "\"}", parent), 201).get("id").asString();
        JsonNode enrollment = json(get("/api/v1/children/" + childId + "/enrollments", parent), 200).get(0);
        String enrollmentId = enrollment.get("enrollmentId").asString();
        String skillId = enrollment.get("nextStep").get("skills").get(0).get("id").asString();
        UUID path = jdbc.queryForObject("select id from learning_paths where enrollment_id = ?::uuid", UUID.class, enrollmentId);
        // Every step already done (the path's own flow would have placed reviews due at the time).
        jdbc.update("update learning_path_items set status = 'COMPLETED', completed_at = now() where learning_path_id = ?", path);
        assertThat(get("/api/v1/enrollments/" + enrollmentId + "/next-activity", parent).statusCode()).isEqualTo(404);

        // A review of one of its skills, due since yesterday; and one of a skill this curriculum doesn't teach.
        review(childId, skillId);
        UUID otherSkill = jdbc.queryForObject("""
                select s.id from skills s where s.id not in (
                  select los.skill_id from curriculum_objectives co join learning_objective_skills los on los.learning_objective_id = co.learning_objective_id
                  join enrollments e on e.curriculum_version_id = co.curriculum_version_id where e.id = ?::uuid)
                and exists (select 1 from activity_version_skills avs where avs.skill_id = s.id) limit 1""", UUID.class, enrollmentId);
        review(childId, otherSkill.toString());

        // Someone else's family can't place anything.
        assertThat(post("/api/v1/children/" + childId + "/reviews/place-due", "", registerParent()).statusCode()).isIn(403, 404);

        assertThat(json(post("/api/v1/children/" + childId + "/reviews/place-due", "", parent), 200).get("placed").asInt()).isEqualTo(1);
        JsonNode next = json(get("/api/v1/enrollments/" + enrollmentId + "/next-activity", parent), 200);
        assertThat(next.get("source").asString()).isEqualTo("REVIEW");
        JsonNode step = json(get("/api/v1/children/" + childId + "/enrollments", parent), 200).get(0).get("nextStep");
        assertThat(step.get("explanation").get("reason").asString()).isEqualTo("REVIEW");
        assertThat(step.get("explanation").get("skill").get("id").asString()).isEqualTo(skillId);

        // Asked again (a refresh): nothing more placed - one review waits at a time on a path.
        assertThat(json(post("/api/v1/children/" + childId + "/reviews/place-due", "", parent), 200).get("placed").asInt()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from learning_path_items where learning_path_id = ? and source = 'REVIEW'",
                Integer.class, path)).isEqualTo(1);
    }

    @Test
    void nothingIsPlacedWhileAStepIsWaiting() throws Exception {
        String parent = registerParent();
        String childId = json(post("/api/v1/children", "{\"displayName\":\"Busy Child\",\"dateOfBirth\":\""
                + LocalDate.now().minusYears(6) + "\"}", parent), 201).get("id").asString();
        JsonNode enrollment = json(get("/api/v1/children/" + childId + "/enrollments", parent), 200).get(0);
        review(childId, enrollment.get("nextStep").get("skills").get(0).get("id").asString());
        // The path's next step is waiting: completing it places the review, as before.
        assertThat(json(post("/api/v1/children/" + childId + "/reviews/place-due", "", parent), 200).get("placed").asInt()).isZero();
    }

    @Test
    void aBookPlayedAgainBadlyFlagsItsSkillForReviewWithoutTouchingThePath() throws Exception {
        String parent = registerParent();
        String childId = json(post("/api/v1/children", "{\"displayName\":\"Replay Child\",\"dateOfBirth\":\""
                + LocalDate.now().minusYears(6) + "\"}", parent), 201).get("id").asString();
        JsonNode enrollment = json(get("/api/v1/children/" + childId + "/enrollments", parent), 200).get(0);
        String versionId = enrollment.get("nextStep").get("activityVersionId").asString();
        String skillId = enrollment.get("nextStep").get("skills").get(0).get("id").asString();
        UUID path = jdbc.queryForObject("select id from learning_paths where enrollment_id = ?::uuid", UUID.class,
                enrollment.get("enrollmentId").asString());
        int itemsBefore = jdbc.queryForObject("select count(*) from learning_path_items where learning_path_id = ?", Integer.class, path);

        // Free practice (no path item), every answer wrong.
        String attemptId = json(post("/api/v1/children/" + childId + "/activity-attempts", "{\"activityVersionId\":\"" + versionId + "\"}",
                parent), 201).get("id").asString();
        for (JsonNode item : json(get("/api/v1/activity-versions/" + versionId + "?childId=" + childId, parent), 200).get("items")) {
            json(post("/api/v1/activity-attempts/" + attemptId + "/answers", "{\"questionVersionId\":\"" + item.get("questionVersionId").asString()
                    + "\",\"submittedAnswer\":\"999\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", parent), 201);
        }
        json(post("/api/v1/activity-attempts/" + attemptId + "/complete", "", parent), 200);

        // Smart Review has the skill now; the path is untouched and no adaptive decision was made.
        assertThat(jdbc.queryForObject("select count(*) from review_items where child_id = ?::uuid and skill_id = ?::uuid and status = 'ACTIVE'",
                Integer.class, childId, skillId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from learning_path_items where learning_path_id = ?", Integer.class, path))
                .isEqualTo(itemsBefore);
        assertThat(jdbc.queryForObject("select count(*) from adaptation_decisions where activity_attempt_id = ?::uuid", Integer.class, attemptId))
                .isZero();
    }

    private void review(String childId, String skillId) {
        UUID item = UUID.randomUUID();
        jdbc.update("insert into review_items (id, child_id, skill_id, status) values (?, ?::uuid, ?::uuid, 'ACTIVE')", item, childId, skillId);
        jdbc.update("insert into review_schedules (id, review_item_id, due_at, interval_days, ease_factor) values (?, ?, now() - interval '1 day', 1, 2.5)",
                UUID.randomUUID(), item);
    }

    private String registerParent() throws Exception {
        return json(post("/api/v1/auth/register", "{\"email\":\"review-" + UUID.randomUUID() + "@example.com\",\"password\":"
                + "\"Review-Test-Pass-2026\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null), 201).get("accessToken").asString();
    }

    private static JsonNode json(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> get(String path, String bearer) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("Authorization", "Bearer " + bearer)
                .GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body, String bearer) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

}
