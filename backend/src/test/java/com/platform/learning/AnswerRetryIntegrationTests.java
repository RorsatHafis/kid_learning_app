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
import com.platform.knowledge.entity.Skill;
import com.platform.curriculum.entity.Subject;
import com.platform.curriculum.repository.SubjectRepository;
import com.platform.curriculum.service.CurriculumService;
import com.platform.curriculum.service.LearningObjectiveService;
import com.platform.knowledge.service.LearningObjectiveSkillService;
import com.platform.knowledge.service.SkillService;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

/**
 * D-20 (owner decision 2026-11-11) over real HTTP and PostgreSQL: one retry with feedback after a wrong answer. The
 * right answer stays hidden while the retry is open; the question counts once - a right second try as weaker evidence
 * (0.5 of a right first answer), a declined or wrong one as a miss; an open retry closes as a miss when the child
 * answers another question or finishes; true-or-false gets no retry; the score counts right first answers; a hinted
 * first answer still moves no mastery (D-16).
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class AnswerRetryIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Retry-Test-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private LearningObjectiveService learningObjectiveService;
    @Autowired private SkillService skillService;
    @Autowired private LearningObjectiveSkillService learningObjectiveSkillService;
    @Autowired private ActivityService activityService;
    @Autowired private QuestionService questionService;
    @Autowired private CurriculumService curriculumService;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void aWrongAnswerGetsOneRetryAndTheQuestionCountsOnceWeakerWhenRightTheSecondTime() throws Exception {
        Made made = made();
        Play play = play(made);

        // A wrong first answer: one retry, the right answer held back, no evidence yet.
        JsonNode first = json(answer(play, made.versions().get(0), "3", false), 201);
        assertThat(first.get("correct").asBoolean()).isFalse();
        assertThat(first.get("retryAvailable").asBoolean()).isTrue();
        assertThat(first.get("correctAnswer").isNull()).isTrue();
        assertThat(evidence(play, made.skills().get(0))).isZero();
        // Resuming shows the open retry the same way.
        JsonNode listed = json(get("/api/v1/activity-attempts/" + play.attempt() + "/answers", play.parent()), 200);
        assertThat(listed.get(0).get("retryAvailable").asBoolean()).isTrue();
        assertThat(listed.get(0).get("correctAnswer").isNull()).isTrue();

        // Right the second time: weaker evidence (0.5), counted once; the first answer stays the record.
        String retry = "/api/v1/activity-attempts/" + play.attempt() + "/answers/" + first.get("id").asString() + "/retry";
        JsonNode second = json(post(retry, "{\"submittedAnswer\":\"2\"}", play.parent()), 200);
        assertThat(second.get("retry").get("correct").asBoolean()).isTrue();
        assertThat(second.get("retryAvailable").asBoolean()).isFalse();
        assertThat(second.get("correctAnswer").isNull()).isFalse();
        assertThat(second.get("correct").asBoolean()).as("the first answer is the record").isFalse();
        assertThat(mastery(play, made.skills().get(0))).isEqualByComparingTo("0.5000");
        assertThat(evidence(play, made.skills().get(0))).isEqualTo(1);
        // The same second answer again (a lost reply) changes nothing; another is refused.
        assertThat(post(retry, "{\"submittedAnswer\":\"2\"}", play.parent()).statusCode()).isEqualTo(200);
        assertThat(post(retry, "{\"submittedAnswer\":\"7\"}", play.parent()).statusCode()).isEqualTo(409);
        assertThat(evidence(play, made.skills().get(0))).isEqualTo(1);

        // Asking to see the answer instead: a miss, and the answer is shown.
        JsonNode wrong = json(answer(play, made.versions().get(1), "5", false), 201);
        String reveal = "/api/v1/activity-attempts/" + play.attempt() + "/answers/" + wrong.get("id").asString() + "/reveal";
        JsonNode revealed = json(post(reveal, "", play.parent()), 200);
        assertThat(revealed.get("revealed").asBoolean()).isTrue();
        assertThat(revealed.get("correctAnswer").isNull()).isFalse();
        assertThat(mastery(play, made.skills().get(1))).isEqualByComparingTo("0.0000");
        assertThat(evidence(play, made.skills().get(1))).isEqualTo(1);
        json(post(reveal, "", play.parent()), 200);
        assertThat(evidence(play, made.skills().get(1))).isEqualTo(1);

        // Moving on with a retry open closes it as a miss; true-or-false gets no retry (its miss counts at once).
        json(answer(play, made.versions().get(2), "7", false), 201);
        JsonNode trueFalse = json(answer(play, made.versions().get(3), "false", false), 201);
        assertThat(trueFalse.get("retryAvailable").asBoolean()).isFalse();
        assertThat(trueFalse.get("correctAnswer").isNull()).isFalse();
        assertThat(evidence(play, made.skills().get(2))).isEqualTo(1);
        assertThat(mastery(play, made.skills().get(2))).isEqualByComparingTo("0.0000");
        assertThat(evidence(play, made.skills().get(3))).isEqualTo(1);

        // The score counts right first answers.
        JsonNode completed = json(post("/api/v1/activity-attempts/" + play.attempt() + "/complete", "", play.parent()), 200);
        assertThat(completed.get("score").asDouble()).isEqualTo(0.0);
        assertThat(jdbc.queryForObject("select count(*) from answer_retries r join answer_records a on a.id = r.answer_record_id "
                + "where a.activity_attempt_id = ?::uuid", Integer.class, UUID.fromString(play.attempt()))).isEqualTo(3);
        // The parent's skill page tells it as it was: wrong first, right on the second try.
        JsonNode story = json(get("/api/v1/children/" + play.childId() + "/skills/" + made.skills().get(0), play.parent()), 200);
        assertThat(story.get("activities").get(0).get("correct").asInt()).isZero();
        assertThat(story.get("activities").get(0).get("rightOnSecondTry").asInt()).isEqualTo(1);
    }

    @Test
    void finishingClosesAnOpenRetryAsAMissAndAHintedFirstAnswerMovesNoMastery() throws Exception {
        Made made = made();
        Play play = play(made);
        json(answer(play, made.versions().get(0), "2", false), 201);
        json(answer(play, made.versions().get(1), "4", false), 201);
        // Answered with the hint open, wrong: a retry, but neither try is evidence (D-16).
        JsonNode hinted = json(answer(play, made.versions().get(2), "1", true), 201);
        assertThat(hinted.get("retryAvailable").asBoolean()).isTrue();
        json(post("/api/v1/activity-attempts/" + play.attempt() + "/answers/" + hinted.get("id").asString() + "/retry",
                "{\"submittedAnswer\":\"6\"}", play.parent()), 200);
        assertThat(evidence(play, made.skills().get(2))).isZero();
        // The last answer wrong and left open: finishing closes it as a miss before the score and decisions.
        JsonNode open = json(answer(play, made.versions().get(3), "false", false), 201);
        assertThat(open.get("retryAvailable").asBoolean()).isFalse();
        JsonNode completed = json(post("/api/v1/activity-attempts/" + play.attempt() + "/complete", "", play.parent()), 200);
        assertThat(completed.get("score").asDouble()).isEqualTo(50.0);
        assertThat(mastery(play, made.skills().get(0))).isEqualByComparingTo("1.0000");
        // A retry can't be used once the activity is over.
        assertThat(post("/api/v1/activity-attempts/" + play.attempt() + "/answers/" + hinted.get("id").asString() + "/reveal", "",
                play.parent()).statusCode()).isEqualTo(409);
    }

    @Test
    void anOpenRetryLeftAtTheEndClosesAsAMissWhenTheActivityFinishes() throws Exception {
        Made made = made();
        Play play = play(made);
        json(answer(play, made.versions().get(0), "2", false), 201);
        json(answer(play, made.versions().get(1), "4", false), 201);
        json(answer(play, made.versions().get(3), "true", false), 201);
        JsonNode open = json(answer(play, made.versions().get(2), "1", false), 201);
        assertThat(open.get("retryAvailable").asBoolean()).isTrue();
        assertThat(evidence(play, made.skills().get(2))).isZero();
        json(post("/api/v1/activity-attempts/" + play.attempt() + "/complete", "", play.parent()), 200);
        assertThat(evidence(play, made.skills().get(2))).isEqualTo(1);
        assertThat(mastery(play, made.skills().get(2))).isEqualByComparingTo("0.0000");
        // Someone else can't touch the child's retry.
        String stranger = registerParent();
        assertThat(post("/api/v1/activity-attempts/" + play.attempt() + "/answers/" + open.get("id").asString() + "/reveal", "",
                stranger).statusCode()).isIn(403, 404);
    }

    // ---------------------------------------------------------------------------------------------------------------

    /** Three typed sums (1+1, 2+2, 3+3) and a true-or-false, each on its own skill, in one published activity. */
    private record Made(String curriculumId, List<String> versions, List<UUID> skills) {
    }

    private record Play(String parent, String childId, String attempt) {
    }

    private Made made() {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        UUID subjectId = subjectRepository.save(Subject.create("RTY_" + suffix, "Retry " + suffix, null)).getId();
        LearningObjective objective = learningObjectiveService.create(subjectId, "RTY_OBJ_" + suffix, "Objective " + suffix, null);
        Activity activity = activityService.createActivity(subjectId, null, "PRACTICE", "Retry " + suffix);
        var activityVersion = activityService.createDraftVersion(activity.getId(), "Sums", 1, 60);
        List<String> versions = new ArrayList<>();
        List<UUID> skills = new ArrayList<>();
        Object[][] questions = {{QuestionType.NUMERIC, "1 + 1 = ?", "2"}, {QuestionType.NUMERIC, "2 + 2 = ?", "4"},
                {QuestionType.NUMERIC, "3 + 3 = ?", "6"}, {QuestionType.TRUE_FALSE, "1 + 1 = 2", "true"}};
        for (int i = 0; i < questions.length; i++) {
            Skill skill = skillService.createSkill(subjectId, "RTY_S" + i + "_" + suffix, "Skill " + i + " " + suffix, null);
            learningObjectiveSkillService.link(objective.getId(), skill.getId());
            if (i == 0) activityService.tagSkill(activityVersion.getId(), skill.getId());
            Question question = questionService.createQuestion(subjectId, (QuestionType) questions[i][0]);
            var draft = questionService.createDraftVersion(question.getId(), (String) questions[i][1], (String) questions[i][2], 1);
            questionService.tagSkill(draft.getId(), skill.getId());
            var published = questionService.publish(draft.getId());
            activityService.addItem(activityVersion.getId(), published.getId(), i, BigDecimal.ONE);
            versions.add(published.getId().toString());
            skills.add(skill.getId());
        }
        activityService.publish(activityVersion.getId());
        Curriculum curriculum = curriculumService.createCurriculum(subjectId, null, "Retry " + suffix, 90, 99, null);
        var version = curriculumService.createDraftVersion(curriculum.getId());
        curriculumService.addObjective(version.getId(), objective.getId(), 0);
        curriculumService.publish(version.getId());
        return new Made(curriculum.getId().toString(), versions, skills);
    }

    private Play play(Made made) throws Exception {
        String parent = registerParent();
        String childId = json(post("/api/v1/children", "{\"displayName\":\"Dara\",\"dateOfBirth\":\"" + LocalDate.now().minusYears(6)
                + "\",\"familyChoosesSubjects\":true}", parent), 201).get("id").asString();
        String enrollment = json(post("/api/v1/children/" + childId + "/enrollments", "{\"curriculumId\":\"" + made.curriculumId() + "\"}",
                parent), 201).get("id").asString();
        JsonNode next = json(get("/api/v1/enrollments/" + enrollment + "/next-activity", parent), 200);
        String attempt = json(post("/api/v1/children/" + childId + "/activity-attempts", "{\"activityVersionId\":\""
                + next.get("activityVersionId").asString() + "\",\"learningPathItemId\":\"" + next.get("id").asString() + "\"}", parent), 201)
                .get("id").asString();
        return new Play(parent, childId, attempt);
    }

    private HttpResponse<String> answer(Play play, String versionId, String submitted, boolean hint) throws Exception {
        return post("/api/v1/activity-attempts/" + play.attempt() + "/answers", "{\"questionVersionId\":\"" + versionId
                + "\",\"submittedAnswer\":" + JSON.writeValueAsString(submitted) + ",\"idempotencyKey\":\"" + UUID.randomUUID()
                + "\",\"hintUsed\":" + hint + "}", play.parent());
    }

    private int evidence(Play play, UUID skillId) {
        List<Map<String, Object>> rows = jdbc.queryForList("select evidence_count from learner_skill_states where child_id = ?::uuid "
                + "and skill_id = ?::uuid", UUID.fromString(play.childId()), skillId);
        return rows.isEmpty() ? 0 : ((Number) rows.getFirst().get("evidence_count")).intValue();
    }

    private BigDecimal mastery(Play play, UUID skillId) {
        return jdbc.queryForObject("select mastery_probability from learner_skill_states where child_id = ?::uuid and skill_id = ?::uuid",
                BigDecimal.class, UUID.fromString(play.childId()), skillId);
    }

    private String registerParent() throws Exception {
        return json(post("/api/v1/auth/register", "{\"email\":\"retry-" + UUID.randomUUID() + "@example.com\",\"password\":\"" + PASSWORD
                + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null), 201).get("accessToken").asString();
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

    private HttpResponse<String> send(HttpRequest.Builder builder, String bearer) throws Exception {
        if (bearer != null) builder.header("Authorization", "Bearer " + bearer);
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
