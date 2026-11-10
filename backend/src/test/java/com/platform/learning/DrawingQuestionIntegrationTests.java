package com.platform.learning;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.TestcontainersConfiguration;
import com.platform.content.entity.Activity;
import com.platform.content.entity.Question;
import com.platform.content.entity.QuestionType;
import com.platform.content.service.ActivityService;
import com.platform.content.service.QuestionService;
import com.platform.curriculum.entity.Curriculum;
import com.platform.curriculum.entity.LearningObjective;
import com.platform.curriculum.entity.Subject;
import com.platform.curriculum.repository.SubjectRepository;
import com.platform.curriculum.service.CurriculumService;
import com.platform.curriculum.service.LearningObjectiveService;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.repository.AccountRepository;
import com.platform.knowledge.entity.Skill;
import com.platform.knowledge.service.LearningObjectiveSkillService;
import com.platform.knowledge.service.SkillService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.UUID;
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

/**
 * Owner-approved 2026-11-10 (V91): the Drawing / Handwriting question type, over real HTTP and PostgreSQL. A drawing is
 * required to finish and is kept as the child's answer, but never scored (no score, mastery or Smart Review); a blank
 * drawing doesn't count; the family and the class that assigned it review it and leave feedback.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class DrawingQuestionIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Drawing-Test-Pass-2026";
    private static final String DRAWN = "{\"strokes\":[[[0.2,0.2],[0.4,0.6],[0.6,0.2]],[[0.3,0.4],[0.5,0.4]]]}";

    @Value("${local.server.port}") private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private LearningObjectiveService learningObjectiveService;
    @Autowired private SkillService skillService;
    @Autowired private LearningObjectiveSkillService learningObjectiveSkillService;
    @Autowired private ActivityService activityService;
    @Autowired private QuestionService questionService;
    @Autowired private CurriculumService curriculumService;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void anAuthorSetsTheGuideAndOnlyAWholeGuideIsAccepted() throws Exception {
        String admin = token(PlatformRole.ADMIN);
        UUID subjectId = subjectRepository.save(Subject.create("DRW_" + suffix(), "Drawing " + suffix(), null)).getId();
        Skill skill = skillService.createSkill(subjectId, "DRW_" + suffix(), "Writing A", null);
        JsonNode created = json(post("/api/v1/studio/questions", "{\"subjectId\":\"" + subjectId + "\",\"questionType\":\"DRAWING\","
                + "\"prompt\":\"Trace the letter A.\",\"skillIds\":[\"" + skill.getId() + "\"]}", admin), 201);
        String versionId = created.get("versions").get(0).get("id").asString();
        String guide = "/api/v1/studio/question-versions/" + versionId + "/drawing-guide";

        // Not publishable without a guide.
        assertThat(json(get("/api/v1/studio/question-versions/" + versionId + "/validation", admin), 200).toString())
                .contains("DRAWING_GUIDE_MISSING");
        for (String bad : new String[] {"{}", "{\"mode\":\"PAINT\"}", "{\"mode\":\"TRACE\"}",
                "{\"mode\":\"FREE\",\"starts\":[[2,0.5]]}", "{\"mode\":\"GUIDED\",\"reference\":\"" + "A".repeat(41) + "\"}"}) {
            assertThat(put(guide, bad, admin).statusCode()).as(bad).isEqualTo(400);
        }
        JsonNode saved = json(put(guide, "{\"mode\":\"GUIDED\",\"reference\":\"A\",\"referenceKm\":\"ក\",\"starts\":[[0.5,0.1]],"
                + "\"arrows\":[[0.5,0.1,0.2,0.9]],\"baseline\":true,\"unknown\":1}", admin), 200);
        JsonNode stored = saved.get("versions").get(0).get("drawingGuide");
        assertThat(stored.get("mode").asString()).isEqualTo("GUIDED");
        assertThat(stored.get("referenceKm").asString()).isEqualTo("ក");
        assertThat(stored.has("unknown")).as("only the guide's own fields are kept").isFalse();
        assertThat(json(get("/api/v1/studio/question-versions/" + versionId + "/validation", admin), 200).toString())
                .doesNotContain("DRAWING_GUIDE_MISSING");
    }

    @Test
    void aDrawingIsRequiredKeptUnscoredAndReviewedBlankDoesNotCount() throws Exception {
        Made made = made(true);
        String email = "drawing-parent-" + UUID.randomUUID() + "@example.com";
        String parent = registerParent(email);
        String childId = json(post("/api/v1/children", "{\"displayName\":\"Dara\",\"dateOfBirth\":\"" + LocalDate.now().minusYears(6)
                + "\",\"familyChoosesSubjects\":true}", parent), 201).get("id").asString();
        String enrollment = json(post("/api/v1/children/" + childId + "/enrollments", "{\"curriculumId\":\"" + made.curriculumId() + "\"}",
                parent), 201).get("id").asString();
        JsonNode next = json(get("/api/v1/enrollments/" + enrollment + "/next-activity", parent), 200);
        String attempt = json(post("/api/v1/children/" + childId + "/activity-attempts", "{\"activityVersionId\":\""
                + next.get("activityVersionId").asString() + "\",\"learningPathItemId\":\"" + next.get("id").asString() + "\"}", parent), 201)
                .get("id").asString();

        // The child is given the guide with the question.
        JsonNode content = json(get("/api/v1/activity-versions/" + next.get("activityVersionId").asString() + "?childId=" + childId, parent), 200);
        assertThat(content.toString()).contains("\"drawingGuide\":{\"mode\":\"TRACE\"");

        // Blank, or not a drawing: refused, nothing recorded.
        for (String blank : new String[] {"{\"strokes\":[]}", "{\"strokes\":[[[0.5,0.5]]]}", "not a drawing", "{\"strokes\":[[[1.5,0.2],[0.3,0.3]]]}"}) {
            assertThat(answer(attempt, made.drawingVersion(), blank, parent).statusCode()).as(blank).isEqualTo(422);
        }
        assertThat(post("/api/v1/activity-attempts/" + attempt + "/complete", "", parent).statusCode())
                .as("the drawing is required").isEqualTo(409);

        JsonNode drawing = json(answer(attempt, made.drawingVersion(), DRAWN, parent), 201);
        assertThat(drawing.get("scored").asBoolean()).isFalse();
        assertThat(json(answer(attempt, made.numericVersion(), "2", parent), 201).get("correct").asBoolean()).isTrue();
        JsonNode completed = json(post("/api/v1/activity-attempts/" + attempt + "/complete", "", parent), 200);
        assertThat(completed.get("score").asDouble()).as("the drawing is outside the score").isEqualTo(100.0);
        // No mastery evidence from the drawing (its skill is the drawing's own); the numeric answer's skill has it.
        assertThat(jdbc.queryForObject("select count(*) from learner_skill_history where child_id = ?::uuid and skill_id = ?::uuid",
                Integer.class, UUID.fromString(childId), made.drawingSkill())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from learning_path_items where id = ?::uuid and status = 'COMPLETED'",
                Integer.class, UUID.fromString(next.get("id").asString()))).as("the path moves on").isEqualTo(1);
        assertThat(jdbc.queryForObject("select current_streak from learner_streaks where child_id = ?::uuid", Integer.class,
                UUID.fromString(childId))).as("a genuine learning day").isPositive();

        // The family reviews it and leaves feedback; nobody else does.
        JsonNode drawings = json(get("/api/v1/children/" + childId + "/drawings", parent), 200);
        assertThat(drawings).hasSize(1);
        assertThat(drawings.get(0).get("strokes").get("strokes")).hasSize(2);
        assertThat(drawings.get(0).get("prompt").asString()).isEqualTo("Trace the letter A.");
        String answerId = drawings.get(0).get("answerId").asString();
        String feedback = "/api/v1/children/" + childId + "/drawings/" + answerId + "/feedback";
        assertThat(post(feedback, "{\"body\":\"  \"}", parent).statusCode()).isEqualTo(400);
        JsonNode reviewed = json(post(feedback, "{\"body\":\"Lovely straight lines!\"}", parent), 200);
        assertThat(reviewed.get("feedback").get(0).get("body").asString()).isEqualTo("Lovely straight lines!");
        assertThat(reviewed.get("feedback").get(0).get("mine").asBoolean()).isTrue();
        String stranger = registerParent("drawing-stranger-" + UUID.randomUUID() + "@example.com");
        assertThat(get("/api/v1/children/" + childId + "/drawings", stranger).statusCode()).isEqualTo(404);
        assertThat(post(feedback, "{\"body\":\"Hi\"}", stranger).statusCode()).isEqualTo(404);
        String teacher = token(PlatformRole.TEACHER);
        assertThat(get("/api/v1/children/" + childId + "/drawings", teacher).statusCode()).isEqualTo(404);

        // The family's export keeps it, as a drawing with its feedback - not as a wrong answer.
        String export = get("/api/v1/account/data-export", parent).body();
        assertThat(export).contains("(a drawing)").contains("Lovely straight lines!");
    }

    @Test
    void anActivityOfDrawingsOnlyFinishesWithNoScore() throws Exception {
        Made made = made(false);
        String parent = registerParent("drawing-only-" + UUID.randomUUID() + "@example.com");
        String childId = json(post("/api/v1/children", "{\"displayName\":\"Sokha\",\"dateOfBirth\":\"" + LocalDate.now().minusYears(6)
                + "\",\"familyChoosesSubjects\":true}", parent), 201).get("id").asString();
        String enrollment = json(post("/api/v1/children/" + childId + "/enrollments", "{\"curriculumId\":\"" + made.curriculumId() + "\"}",
                parent), 201).get("id").asString();
        JsonNode next = json(get("/api/v1/enrollments/" + enrollment + "/next-activity", parent), 200);
        String attempt = json(post("/api/v1/children/" + childId + "/activity-attempts", "{\"activityVersionId\":\""
                + next.get("activityVersionId").asString() + "\",\"learningPathItemId\":\"" + next.get("id").asString() + "\"}", parent), 201)
                .get("id").asString();
        json(answer(attempt, made.drawingVersion(), DRAWN, parent), 201);
        JsonNode completed = json(post("/api/v1/activity-attempts/" + attempt + "/complete", "", parent), 200);
        assertThat(completed.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(completed.get("score").isNull()).isTrue();
    }

    // ---------------------------------------------------------------------------------------------------------------

    private record Made(String curriculumId, String drawingVersion, String numericVersion, UUID drawingSkill) {
    }

    /** A published platform curriculum whose one activity has a TRACE drawing (and a sum when {@code withSum}). */
    private Made made(boolean withSum) {
        String suffix = suffix();
        UUID subjectId = subjectRepository.save(Subject.create("DRW_" + suffix, "Drawing " + suffix, null)).getId();
        LearningObjective objective = learningObjectiveService.create(subjectId, "DRW_OBJ_" + suffix, "Objective " + suffix, null);
        Skill drawingSkill = skillService.createSkill(subjectId, "DRW_W_" + suffix, "Writing " + suffix, null);
        Skill sumSkill = skillService.createSkill(subjectId, "DRW_S_" + suffix, "Sums " + suffix, null);
        learningObjectiveSkillService.link(objective.getId(), drawingSkill.getId());
        learningObjectiveSkillService.link(objective.getId(), sumSkill.getId());
        Activity activity = activityService.createActivity(subjectId, null, "PRACTICE", "Drawing " + suffix);
        var activityVersion = activityService.createDraftVersion(activity.getId(), "Draw", 1, 60);
        activityService.tagSkill(activityVersion.getId(), drawingSkill.getId());

        Question drawing = questionService.createQuestion(subjectId, QuestionType.DRAWING);
        var drawingDraft = questionService.createDraftVersion(drawing.getId(), "Trace the letter A.", null, 1);
        questionService.tagSkill(drawingDraft.getId(), drawingSkill.getId());
        questionService.setDrawingGuide(drawingDraft.getId(), "{\"mode\":\"TRACE\",\"reference\":\"A\",\"baseline\":true,\"grid\":false}");
        var drawingVersion = questionService.publish(drawingDraft.getId());
        activityService.addItem(activityVersion.getId(), drawingVersion.getId(), 0, java.math.BigDecimal.ONE);
        String numericId = null;
        if (withSum) {
            Question sum = questionService.createQuestion(subjectId, QuestionType.NUMERIC);
            var sumDraft = questionService.createDraftVersion(sum.getId(), "1 + 1 = ?", "2", 1);
            questionService.tagSkill(sumDraft.getId(), sumSkill.getId());
            var sumVersion = questionService.publish(sumDraft.getId());
            activityService.addItem(activityVersion.getId(), sumVersion.getId(), 1, java.math.BigDecimal.ONE);
            numericId = sumVersion.getId().toString();
        }
        activityService.publish(activityVersion.getId());
        Curriculum curriculum = curriculumService.createCurriculum(subjectId, null, "Drawing " + suffix, 90, 99, null);
        var version = curriculumService.createDraftVersion(curriculum.getId());
        curriculumService.addObjective(version.getId(), objective.getId(), 0);
        curriculumService.publish(version.getId());
        return new Made(curriculum.getId().toString(), drawingVersion.getId().toString(), numericId, drawingSkill.getId());
    }

    private HttpResponse<String> answer(String attempt, String versionId, String submitted, String bearer) throws Exception {
        return post("/api/v1/activity-attempts/" + attempt + "/answers", "{\"questionVersionId\":\"" + versionId + "\",\"submittedAnswer\":"
                + JSON.writeValueAsString(submitted) + ",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", bearer);
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String registerParent(String email) throws Exception {
        return json(post("/api/v1/auth/register", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\","
                + "\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null), 201).get("accessToken").asString();
    }

    private String token(PlatformRole role) throws Exception {
        String email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), role));
        String token = json(post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", null), 200)
                .get("accessToken").asString();
        return role == PlatformRole.ADMIN ? com.platform.TestAdmin.stepUp(port, token, PASSWORD) : token;
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

    private HttpResponse<String> send(HttpRequest.Builder builder, String bearer) throws Exception {
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
            com.platform.TestAdmin.withStepUp(builder, bearer);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
