package com.platform.streak;

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
 * STREAK-1: finishing an activity makes today a learning day - the streak reads 1, today counted; a second activity the
 * same day is the same day. Another family can't read it (404), and learns nothing.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class StreakIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Streak-Pass-2026";

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
    void finishingAnActivityMakesTodayALearningDay() throws Exception {
        Curriculum curriculum = publishedCurriculumWithTwoQuestions();
        String parent = parent();
        String childId = json(post("/api/v1/children", "{\"displayName\":\"Flame\",\"dateOfBirth\":\"" + LocalDate.now().minusYears(7)
                + "\"}", parent), 201).get("id").asString();
        JsonNode before = json(get("/api/v1/children/" + childId + "/streak", parent), 200);
        assertThat(before.get("currentStreak").asInt()).isZero();
        assertThat(before.get("learnedToday").asBoolean()).isFalse();

        JsonNode enrollment = json(post("/api/v1/children/" + childId + "/enrollments", "{\"curriculumId\":\""
                + curriculum.getId() + "\"}", parent), 201);
        String enrollmentId = enrollment.has("enrollmentId") ? enrollment.get("enrollmentId").asString() : enrollment.get("id").asString();
        for (int activity = 0; activity < 2; activity++) {
            JsonNode next = json(get("/api/v1/enrollments/" + enrollmentId + "/next-activity", parent), 200);
            String version = next.get("activityVersionId").asString();
            String attempt = json(post("/api/v1/children/" + childId + "/activity-attempts", "{\"activityVersionId\":\"" + version
                    + "\",\"learningPathItemId\":\"" + next.get("id").asString() + "\"}", parent), 201).get("id").asString();
            for (JsonNode item : json(get("/api/v1/activity-versions/" + version + "?childId=" + childId, parent), 200).get("items")) {
                json(post("/api/v1/activity-attempts/" + attempt + "/answers", "{\"questionVersionId\":\"" + item.get("questionVersionId").asString()
                        + "\",\"submittedAnswer\":\"2\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", parent), 201);
            }
            json(post("/api/v1/activity-attempts/" + attempt + "/complete", "", parent), 200);

            JsonNode streak = json(get("/api/v1/children/" + childId + "/streak", parent), 200);
            assertThat(streak.get("currentStreak").asInt()).as("one learning day, however many activities").isEqualTo(1);
            assertThat(streak.get("longestStreak").asInt()).isEqualTo(1);
            assertThat(streak.get("learnedToday").asBoolean()).isTrue();
            if (json(get("/api/v1/enrollments/" + enrollmentId + "/journey", parent), 200).get("nextStepId").isNull()) {
                break;
            }
        }

        HttpResponse<String> stranger = get("/api/v1/children/" + childId + "/streak", parent());
        assertThat(stranger.statusCode()).isEqualTo(404);
        assertThat(stranger.body()).doesNotContain("currentStreak");
    }

    private Curriculum publishedCurriculumWithTwoQuestions() {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        UUID subjectId = subjectRepository.save(Subject.create("RSM_" + suffix, "Resume " + suffix, null)).getId();
        UUID ageHubId = ageHubRepository.save(AgeHub.create("RSM_" + suffix, "Resume " + suffix, 90, 99, 99)).getId();
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
