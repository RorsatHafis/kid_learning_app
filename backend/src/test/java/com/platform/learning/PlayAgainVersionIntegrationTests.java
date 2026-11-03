package com.platform.learning;

import com.platform.TestcontainersConfiguration;
import com.platform.content.entity.Activity;
import com.platform.content.entity.Question;
import com.platform.content.entity.QuestionType;
import com.platform.content.repository.ActivityRepository;
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
 * X24: a finished book played again plays the activity's current published version. The journey step keeps the version
 * the child played; once an author revises the activity, that version is retired and a new attempt can't start on it -
 * so the step names what to play again ({@code playAgainVersionId}), and nothing for an archived activity.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class PlayAgainVersionIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Play-Again-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private AgeHubRepository ageHubRepository;
    @Autowired private LearningObjectiveService learningObjectiveService;
    @Autowired private SkillService skillService;
    @Autowired private LearningObjectiveSkillService learningObjectiveSkillService;
    @Autowired private QuestionService questionService;
    @Autowired private ActivityService activityService;
    @Autowired private ActivityRepository activityRepository;
    @Autowired private CurriculumService curriculumService;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void aFinishedBookPlaysTheActivitysCurrentVersionAndNothingOnceArchived() throws Exception {
        Offered offered = publishedCurriculum();
        String parent = parent();
        String childId = json(post("/api/v1/children", "{\"displayName\":\"Replay\",\"dateOfBirth\":\"" + LocalDate.now().minusYears(7)
                + "\"}", parent), 201).get("id").asString();
        JsonNode enrollment = json(post("/api/v1/children/" + childId + "/enrollments", "{\"curriculumId\":\""
                + offered.curriculum().getId() + "\"}", parent), 201);
        String enrollmentId = enrollment.has("enrollmentId") ? enrollment.get("enrollmentId").asString() : enrollment.get("id").asString();

        // The child finishes the first book.
        JsonNode first = step(parent, enrollmentId, 0);
        String played = first.get("activityVersionId").asString();
        String attempt = json(post("/api/v1/children/" + childId + "/activity-attempts", "{\"activityVersionId\":\"" + played
                + "\",\"learningPathItemId\":\"" + first.get("learningPathItemId").asString() + "\"}", parent), 201).get("id").asString();
        JsonNode content = json(get("/api/v1/activity-versions/" + played + "?childId=" + childId, parent), 200);
        for (JsonNode item : content.get("items")) {
            json(post("/api/v1/activity-attempts/" + attempt + "/answers", "{\"questionVersionId\":\"" + item.get("questionVersionId").asString()
                    + "\",\"submittedAnswer\":\"2\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", parent), 201);
        }
        json(post("/api/v1/activity-attempts/" + attempt + "/complete", "", parent), 200);
        JsonNode finished = step(parent, enrollmentId, 0);
        assertThat(finished.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(finished.get("playAgainVersionId").asString()).as("unrevised: the version played").isEqualTo(played);

        // The author revises the activity: what was played is retired; the step keeps it, playing again names the new one.
        var revised = activityService.publish(activityService.openDraft(offered.activityId()).getId());
        JsonNode after = step(parent, enrollmentId, 0);
        assertThat(after.get("activityVersionId").asString()).as("the step keeps the version the child played").isEqualTo(played);
        assertThat(after.get("playAgainVersionId").asString()).isEqualTo(revised.getId().toString());
        assertThat(post("/api/v1/children/" + childId + "/activity-attempts", "{\"activityVersionId\":\"" + played + "\"}", parent)
                .statusCode()).as("why: no new attempt on a retired version").isIn(404, 409);
        json(post("/api/v1/children/" + childId + "/activity-attempts", "{\"activityVersionId\":\"" + revised.getId() + "\"}", parent), 201);

        // Archived: nothing to play again.
        Activity activity = activityRepository.findById(offered.activityId()).orElseThrow();
        activity.archive();
        activityRepository.saveAndFlush(activity);
        assertThat(step(parent, enrollmentId, 0).get("playAgainVersionId").isNull()).isTrue();
    }

    /** The journey's step at this place in the path. */
    private JsonNode step(String token, String enrollmentId, int index) throws Exception {
        return json(get("/api/v1/enrollments/" + enrollmentId + "/journey", token), 200).get("steps").get(index);
    }

    private record Offered(Curriculum curriculum, UUID activityId) {
    }

    private Offered publishedCurriculum() {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        UUID subjectId = subjectRepository.save(Subject.create("RPL_" + suffix, "Replay " + suffix, null)).getId();
        UUID ageHubId = ageHubRepository.save(AgeHub.create("RPL_" + suffix, "Replay " + suffix, 0, 99, 99)).getId();
        LearningObjective objective = learningObjectiveService.create(subjectId, "RPL_OBJ_" + suffix, "Objective " + suffix, null);
        Skill skill = skillService.createSkill(subjectId, "RPL_" + suffix, "Skill " + suffix, null);
        learningObjectiveSkillService.link(objective.getId(), skill.getId());
        Question question = questionService.createQuestion(subjectId, QuestionType.NUMERIC);
        var questionDraft = questionService.createDraftVersion(question.getId(), "1 + 1 = ?", "2", 1);
        questionService.tagSkill(questionDraft.getId(), skill.getId());
        var questionVersion = questionService.publish(questionDraft.getId());
        Activity activity = activityService.createActivity(subjectId, null, "PRACTICE", "Replay " + suffix);
        var activityVersion = activityService.createDraftVersion(activity.getId(), "Add", 1, 60);
        activityService.tagSkill(activityVersion.getId(), skill.getId());
        activityService.addItem(activityVersion.getId(), questionVersion.getId(), 0, java.math.BigDecimal.ONE);
        activityService.publish(activityVersion.getId());
        Curriculum curriculum = curriculumService.createCurriculum(subjectId, ageHubId, "Replay " + suffix);
        var version = curriculumService.createDraftVersion(curriculum.getId());
        curriculumService.addObjective(version.getId(), objective.getId(), 0);
        curriculumService.publish(version.getId());
        return new Offered(curriculum, activity.getId());
    }

    private String parent() throws Exception {
        String email = "replay-parent-" + UUID.randomUUID() + "@example.com";
        String token = json(post("/api/v1/auth/register", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null), 201).get("accessToken").asString();
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
