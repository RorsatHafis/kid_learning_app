package com.platform.learning;

import com.platform.TestcontainersConfiguration;
import com.platform.content.repository.ActivityItemRepository;
import com.platform.content.service.QuestionService;
import com.platform.learning.entity.ActivityAttempt;
import com.platform.learning.service.ActivityAttemptService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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
 * X19 over real HTTP against PostgreSQL: one skill, explained to its family - where it stands, the
 * activities that gave evidence on it with their answers and the standing after each, and what is
 * waiting on the path for it. Only the child's family; a skill outside the child's curricula is not
 * found.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class SkillStoryIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Value("${local.server.port}") private int port;
    @Autowired private ActivityAttemptService activityAttemptService;
    @Autowired private ActivityItemRepository activityItemRepository;
    @Autowired private QuestionService questionService;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void aSkillIsExplainedFromTheChildsOwnAnswersAndPath() throws Exception {
        String parent = registerParent();
        String childId = createChild(parent);
        JsonNode enrollment = json(get("/api/v1/children/" + childId + "/enrollments", parent), 200).get(0);
        JsonNode first = enrollment.get("nextStep");
        String skillId = first.get("skills").get(0).get("id").asString();
        String path = "/api/v1/children/" + childId + "/skills/" + skillId;

        // Before any answer: not started, nothing behind it, the first step waiting for it.
        JsonNode before = json(get(path, parent), 200);
        assertThat(before.get("now").get("standing").asString()).isEqualTo("NOT_STARTED");
        assertThat(before.get("activities")).isEmpty();
        assertThat(before.get("waiting").valueStream().map(w -> w.get("learningPathItemId").asString()))
                .contains(first.get("learningPathItemId").asString());
        assertThat(before.get("objectiveTitle").asString()).isNotBlank();

        // Play the first step, every answer right.
        UUID child = UUID.fromString(childId);
        UUID versionId = UUID.fromString(first.get("activityVersionId").asString());
        ActivityAttempt attempt = activityAttemptService.startAttempt(child, versionId,
                UUID.fromString(first.get("learningPathItemId").asString()), null);
        int questions = 0;
        for (var item : activityItemRepository.findByActivityVersionIdOrderBySequenceOrderAsc(versionId)) {
            activityAttemptService.submitAnswer(attempt.getId(), item.getQuestionVersionId(),
                    questionService.getVersion(item.getQuestionVersionId()).getCorrectAnswer(), 3, UUID.randomUUID().toString());
            questions++;
        }
        activityAttemptService.completeAttempt(attempt.getId());

        JsonNode after = json(get(path, parent), 200);
        assertThat(after.get("activitiesInAll").asInt()).isEqualTo(1);
        JsonNode played = after.get("activities").get(0);
        assertThat(played.get("attemptId").asString()).isEqualTo(attempt.getId().toString());
        assertThat(played.get("activityTitle").asString()).isEqualTo(first.get("title").asString());
        assertThat(played.get("answers").asInt()).isPositive().isLessThanOrEqualTo(questions);
        assertThat(played.get("correct").asInt()).isEqualTo(played.get("answers").asInt());
        // The standing after the activity is the same claim as the skill's standing now.
        assertThat(played.get("standingAfter").asString()).isEqualTo(after.get("now").get("standing").asString());
        assertThat(played.get("evidenceAfter").asInt()).isEqualTo(after.get("now").get("scoredAnswers").asInt());
        // The finished step no longer waits.
        assertThat(after.get("waiting").valueStream().map(w -> w.get("learningPathItemId").asString()))
                .doesNotContain(first.get("learningPathItemId").asString());

        // Another family can't read it; a skill outside the child's curricula is not found.
        assertThat(get(path, registerParent()).statusCode()).isIn(403, 404);
        assertThat(get("/api/v1/children/" + childId + "/skills/" + UUID.randomUUID(), parent).statusCode()).isEqualTo(404);
        assertThat(get(path, null).statusCode()).isEqualTo(401);
    }

    // ------------------------------------------------------------------ HTTP

    private String registerParent() throws Exception {
        return json(post("/api/v1/auth/register", "{\"email\":\"story-" + UUID.randomUUID() + "@example.com\",\"password\":"
                + "\"Story-Test-Pass-2026\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null), 201).get("accessToken").asString();
    }

    private String createChild(String token) throws Exception {
        return json(post("/api/v1/children", "{\"displayName\":\"Story Child\",\"dateOfBirth\":\"" + LocalDate.now().minusYears(6)
                + "\"}", token), 201).get("id").asString();
    }

    private static JsonNode json(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> get(String path, String bearer) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
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
