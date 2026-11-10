package com.platform.learning;

import com.platform.TestcontainersConfiguration;
import com.platform.content.entity.Activity;
import com.platform.content.entity.Question;
import com.platform.content.entity.QuestionType;
import com.platform.content.service.ActivityService;
import com.platform.content.service.QuestionService;
import com.platform.curriculum.entity.AgeHub;
import com.platform.curriculum.entity.Curriculum;
import com.platform.curriculum.entity.LearningObjective;
import com.platform.curriculum.entity.Subject;
import com.platform.curriculum.repository.AgeHubRepository;
import com.platform.curriculum.repository.SubjectRepository;
import com.platform.curriculum.service.CurriculumService;
import com.platform.curriculum.service.LearningObjectiveService;
import com.platform.knowledge.entity.Skill;
import com.platform.knowledge.service.LearningObjectiveSkillService;
import com.platform.knowledge.service.SkillService;
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
 * PERF-1: starting an attempt answers with the answers already recorded on it, so the player resumes at the first
 * unanswered question without a second request. A new attempt has none; a resumed one (Battle 2A: the same attempt
 * again) has exactly what the answers endpoint returns. Who may start is unchanged.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class StartAttemptAnswersIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Start-Answers-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private AgeHubRepository ageHubRepository;
    @Autowired private LearningObjectiveService learningObjectiveService;
    @Autowired private SkillService skillService;
    @Autowired private LearningObjectiveSkillService learningObjectiveSkillService;
    @Autowired private QuestionService questionService;
    @Autowired private ActivityService activityService;
    @Autowired private CurriculumService curriculumService;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void aStartCarriesTheAnswersAlreadyRecordedOnTheAttempt() throws Exception {
        Curriculum curriculum = publishedCurriculumWithTwoQuestions();
        String parent = parent();
        String childId = json(post("/api/v1/children", "{\"displayName\":\"Resume\",\"dateOfBirth\":\"" + LocalDate.now().minusYears(7)
                + "\"}", parent), 201).get("id").asString();
        JsonNode enrollment = json(post("/api/v1/children/" + childId + "/enrollments", "{\"curriculumId\":\""
                + curriculum.getId() + "\"}", parent), 201);
        String enrollmentId = enrollment.has("enrollmentId") ? enrollment.get("enrollmentId").asString() : enrollment.get("id").asString();
        JsonNode next = json(get("/api/v1/enrollments/" + enrollmentId + "/next-activity", parent), 200);
        String start = "{\"activityVersionId\":\"" + next.get("activityVersionId").asString() + "\",\"learningPathItemId\":\""
                + next.get("id").asString() + "\"}";

        // A new attempt: nothing recorded yet.
        JsonNode fresh = json(post("/api/v1/children/" + childId + "/activity-attempts", start, parent), 201);
        String attemptId = fresh.get("id").asString();
        assertThat(fresh.get("status").asString()).isEqualTo("IN_PROGRESS");
        assertThat(fresh.get("answers").isArray()).isTrue();
        assertThat(fresh.get("answers")).isEmpty();

        // One question answered, then the child comes back: the same attempt, with that answer.
        JsonNode content = json(get("/api/v1/activity-versions/" + next.get("activityVersionId").asString() + "?childId=" + childId, parent), 200);
        String firstQuestion = content.get("items").get(0).get("questionVersionId").asString();
        json(post("/api/v1/activity-attempts/" + attemptId + "/answers", "{\"questionVersionId\":\"" + firstQuestion
                + "\",\"submittedAnswer\":\"2\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", parent), 201);

        JsonNode resumed = json(post("/api/v1/children/" + childId + "/activity-attempts", start, parent), 201);
        assertThat(resumed.get("id").asString()).as("Battle 2A: the unfinished attempt is resumed").isEqualTo(attemptId);
        assertThat(resumed.get("answers")).hasSize(1);
        JsonNode answer = resumed.get("answers").get(0);
        assertThat(answer.get("questionVersionId").asString()).isEqualTo(firstQuestion);
        assertThat(answer.get("submittedAnswer").asString()).isEqualTo("2");
        assertThat(answer.get("correct").asBoolean()).isTrue();
        assertThat(resumed.get("answers")).as("the same as the answers endpoint")
                .isEqualTo(json(get("/api/v1/activity-attempts/" + attemptId + "/answers", parent), 200));

        // Who may start is unchanged: another family can't, and learns nothing.
        HttpResponse<String> stranger = post("/api/v1/children/" + childId + "/activity-attempts", start, parent());
        assertThat(stranger.statusCode()).isIn(403, 404);
        assertThat(stranger.body()).doesNotContain(firstQuestion);
    }

    private Curriculum publishedCurriculumWithTwoQuestions() {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        UUID subjectId = subjectRepository.save(Subject.create("RSM_" + suffix, "Resume " + suffix, null)).getId();
        UUID ageHubId = ageHubRepository.save(AgeHub.create("RSM_" + suffix, "Resume " + suffix, 0, 99, 99)).getId();
        LearningObjective objective = learningObjectiveService.create(subjectId, "RSM_OBJ_" + suffix, "Objective " + suffix, null);
        Skill skill = skillService.createSkill(subjectId, "RSM_" + suffix, "Skill " + suffix, null);
        learningObjectiveSkillService.link(objective.getId(), skill.getId());
        Activity activity = activityService.createActivity(subjectId, null, "PRACTICE", "Resume " + suffix);
        var activityVersion = activityService.createDraftVersion(activity.getId(), "Add", 1, 60);
        activityService.tagSkill(activityVersion.getId(), skill.getId());
        for (int i = 0; i < 2; i++) {
            Question question = questionService.createQuestion(subjectId, QuestionType.NUMERIC);
            var draft = questionService.createDraftVersion(question.getId(), "1 + " + (i + 1) + " = ?", String.valueOf(i + 2), 1);
            questionService.tagSkill(draft.getId(), skill.getId());
            var published = questionService.publish(draft.getId());
            activityService.addItem(activityVersion.getId(), published.getId(), i, java.math.BigDecimal.ONE);
        }
        activityService.publish(activityVersion.getId());
        Curriculum curriculum = curriculumService.createCurriculum(subjectId, ageHubId, "Resume " + suffix);
        var version = curriculumService.createDraftVersion(curriculum.getId());
        curriculumService.addObjective(version.getId(), objective.getId(), 0);
        curriculumService.publish(version.getId());
        return curriculum;
    }

    private String parent() throws Exception {
        String email = "resume-parent-" + UUID.randomUUID() + "@example.com";
        String token = com.platform.TestPlans.everythingIncluded(jdbc, json(post("/api/v1/auth/register", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null), 201)).get("accessToken").asString();
        jdbc.update("update accounts set email_verified_at = now() where normalized_email = ?", email);
        return token;
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
