package com.platform.content;

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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Content Studio over real HTTP against PostgreSQL: an administrator authors a
 * curriculum from nothing, publishing refuses anything a child couldn't learn from,
 * and what gets published is actually playable and produces evidence for a family's
 * child. Authors only see and change their own content; the catalog is ADMIN-only.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class ContentStudioIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Studio-Test-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AgeHubRepository ageHubRepository;
    @Autowired private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void anAdministratorAuthorsACurriculumThatAChildCanLearnFrom() throws Exception {
        String admin = staff(PlatformRole.ADMIN);
        Catalog catalog = catalog(admin);

        // A curriculum with no activity yet can't be published: an enrolled child would have nothing to do.
        JsonNode curriculum = json(post("/api/v1/studio/curricula", "{\"subjectId\":\"" + catalog.subjectId()
                + "\",\"ageHubId\":\"" + catalog.ageHubId() + "\",\"name\":\"Counting " + catalog.suffix() + "\"}", admin), 201);
        String curriculumVersionId = curriculum.get("versions").get(0).get("id").asString();
        json(put("/api/v1/studio/curriculum-versions/" + curriculumVersionId + "/objectives",
                "{\"objectiveIds\":[\"" + catalog.objectiveId() + "\"]}", admin), 200);
        assertThat(codes(json(get("/api/v1/studio/curriculum-versions/" + curriculumVersionId + "/validation", admin), 200)
                .get("errors"))).containsExactly("NO_PLAYABLE_ACTIVITY");
        HttpResponse<String> refused = post("/api/v1/studio/curriculum-versions/" + curriculumVersionId + "/publish", "", admin);
        assertThat(refused.statusCode()).isEqualTo(409);
        assertThat(codes(JSON.readTree(refused.body()).get("validation").get("errors"))).containsExactly("NO_PLAYABLE_ACTIVITY");

        // A multiple-choice question: drafted, checked, published.
        JsonNode question = json(post("/api/v1/studio/questions", "{\"subjectId\":\"" + catalog.subjectId()
                + "\",\"questionType\":\"MULTIPLE_CHOICE\",\"prompt\":\"How many apples? 🍎🍎🍎\",\"difficultyLevel\":2,"
                + "\"options\":[{\"label\":\"2\",\"correct\":false},{\"label\":\"3\",\"correct\":true},{\"label\":\"4\",\"correct\":false}],"
                + "\"skillIds\":[\"" + catalog.skillId() + "\"]}", admin), 201);
        String questionVersionId = question.get("versions").get(0).get("id").asString();
        assertThat(json(get("/api/v1/studio/question-versions/" + questionVersionId + "/validation", admin), 200)
                .get("errors")).isEmpty();
        JsonNode preview = json(get("/api/v1/studio/question-versions/" + questionVersionId + "/preview", admin), 200);
        assertThat(preview.get("options")).hasSize(3);
        assertThat(preview.toString()).doesNotContain("correct");
        json(post("/api/v1/studio/question-versions/" + questionVersionId + "/publish", "", admin), 200);

        // An activity in a lesson, holding that question.
        String lessonId = json(post("/api/v1/studio/lessons", "{\"subjectId\":\"" + catalog.subjectId()
                + "\",\"title\":\"Counting to 5\"}", admin), 201).get("id").asString();
        JsonNode activity = json(post("/api/v1/studio/lessons/" + lessonId + "/activities",
                "{\"title\":\"Count the fruit\",\"skillIds\":[\"" + catalog.skillId() + "\"]}", admin), 201);
        String activityId = activity.get("id").asString();
        String activityVersionId = activity.get("versions").get(0).get("id").asString();
        json(put("/api/v1/studio/activity-versions/" + activityVersionId,
                "{\"instructions\":\"Count carefully\",\"difficultyLevel\":2,\"estimatedDurationSeconds\":90}", admin), 200);
        json(put("/api/v1/studio/activity-versions/" + activityVersionId + "/items",
                "{\"items\":[{\"questionVersionId\":\"" + questionVersionId + "\"}]}", admin), 200);
        JsonNode activityReport = json(get("/api/v1/studio/activity-versions/" + activityVersionId + "/validation", admin), 200);
        assertThat(activityReport.get("errors")).isEmpty();
        assertThat(codes(activityReport.get("warnings"))).containsExactly("NOT_ON_ANY_PATH");
        assertThat(json(get("/api/v1/studio/activity-versions/" + activityVersionId + "/preview", admin), 200)
                .get("items")).hasSize(1);
        json(post("/api/v1/studio/activity-versions/" + activityVersionId + "/publish", "", admin), 200);

        // Now the curriculum is publishable, and audited.
        json(post("/api/v1/studio/curriculum-versions/" + curriculumVersionId + "/publish", "", admin), 200);
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action = 'CONTENT_PUBLISHED' and resource_id in (?::uuid, ?::uuid, ?::uuid)",
                Integer.class, curriculumVersionId, activityVersionId, questionVersionId)).isEqualTo(3);

        // A family finds it, enrolls a child, and the child's answer is evidence for the authored skill.
        String parent = registerParent();
        String childId = json(post("/api/v1/children", "{\"displayName\":\"Studio Child\",\"dateOfBirth\":\""
                + LocalDate.now().minusYears(6) + "\"}", parent), 201).get("id").asString();
        assertThat(json(get("/api/v1/curricula", parent), 200).toString()).contains(curriculum.get("id").asString());
        String enrollmentId = json(post("/api/v1/children/" + childId + "/enrollments",
                "{\"curriculumId\":\"" + curriculum.get("id").asString() + "\"}", parent), 201).get("id").asString();
        JsonNode journey = json(get("/api/v1/enrollments/" + enrollmentId + "/journey", parent), 200);
        JsonNode step = journey.get("steps").get(0);
        assertThat(step.get("title").asString()).isEqualTo("Count the fruit");

        String attemptId = json(post("/api/v1/children/" + childId + "/activity-attempts", "{\"activityVersionId\":\""
                + step.get("activityVersionId").asString() + "\",\"learningPathItemId\":\""
                + step.get("learningPathItemId").asString() + "\"}", parent), 201).get("id").asString();
        JsonNode content = json(get("/api/v1/activity-versions/" + step.get("activityVersionId").asString(), parent), 200);
        String correctOptionId = null;
        for (JsonNode option : content.get("items").get(0).get("options")) {
            if (option.get("label").asString().equals("3")) {
                correctOptionId = option.get("id").asString();
            }
        }
        JsonNode answer = json(post("/api/v1/activity-attempts/" + attemptId + "/answers", "{\"questionVersionId\":\""
                + questionVersionId + "\",\"submittedAnswer\":\"" + correctOptionId + "\",\"idempotencyKey\":\""
                + UUID.randomUUID() + "\"}", parent), 201);
        assertThat(answer.get("correct").asBoolean()).isTrue();
        json(post("/api/v1/activity-attempts/" + attemptId + "/complete", "", parent), 200);

        // The child was also auto-enrolled in the seeded Maths curriculum; find this one.
        JsonNode skill = null;
        for (JsonNode enrollment : json(get("/api/v1/children/" + childId + "/skills", parent), 200)) {
            if (enrollment.get("enrollmentId").asString().equals(enrollmentId)) {
                skill = enrollment.get("objectives").get(0).get("skills").get(0);
            }
        }
        assertThat(skill).isNotNull();
        assertThat(skill.get("skillId").asString()).isEqualTo(catalog.skillId());
        assertThat(skill.get("scoredAnswers").asInt()).isEqualTo(1);
        assertThat(skill.get("correctAnswers").asInt()).isEqualTo(1);
    }

    /** L9: a matching question from authoring to a scored answer, and the pairing never reaches the child. */
    @Test
    void aMatchingQuestionIsAuthoredPlayedAndScoredWithoutRevealingThePairs() throws Exception {
        String admin = staff(PlatformRole.ADMIN);
        Catalog catalog = catalog(admin);

        JsonNode question = json(post("/api/v1/studio/questions", "{\"subjectId\":\"" + catalog.subjectId()
                + "\",\"questionType\":\"MATCHING\",\"prompt\":\"Match each number to its dots\",\"difficultyLevel\":1,"
                + "\"pairs\":[{\"left\":\"1\",\"right\":\"•\"}],\"skillIds\":[\"" + catalog.skillId() + "\"]}", admin), 201);
        String questionId = question.get("id").asString();
        String versionId = firstVersion(question);
        assertThat(codes(json(get("/api/v1/studio/question-versions/" + versionId + "/validation", admin), 200).get("errors")))
                .containsExactly("MATCHING_PAIR_COUNT");
        json(put("/api/v1/studio/question-versions/" + versionId, "{\"prompt\":\"Match each number to its dots\","
                + "\"pairs\":[{\"left\":\"1\",\"right\":\"•\"},{\"left\":\"2\",\"right\":\"•\"}]}", admin), 200);
        assertThat(codes(json(get("/api/v1/studio/question-versions/" + versionId + "/validation", admin), 200).get("errors")))
                .containsExactly("MATCHING_DUPLICATE_ITEM");
        JsonNode draft = json(put("/api/v1/studio/question-versions/" + versionId, "{\"prompt\":\"Match each number to its dots\","
                + "\"pairs\":[{\"left\":\"1\",\"right\":\"•\"},{\"left\":\"2\",\"right\":\"••\"},{\"left\":\"3\",\"right\":\"•••\"}]}", admin), 200);
        JsonNode pairs = draft.get("versions").get(0).get("pairs");
        assertThat(pairs).hasSize(3);
        assertThat(json(get("/api/v1/studio/question-versions/" + versionId + "/validation", admin), 200).get("errors")).isEmpty();
        JsonNode preview = json(get("/api/v1/studio/question-versions/" + versionId + "/preview", admin), 200);
        assertThat(preview.get("options")).hasSize(6);
        assertThat(preview.toString()).doesNotContain("matchPair").doesNotContain("correct");
        json(post("/api/v1/studio/question-versions/" + versionId + "/publish", "", admin), 200);

        String lessonId = json(post("/api/v1/studio/lessons", "{\"subjectId\":\"" + catalog.subjectId()
                + "\",\"title\":\"Dots\"}", admin), 201).get("id").asString();
        JsonNode activity = json(post("/api/v1/studio/lessons/" + lessonId + "/activities",
                "{\"title\":\"Match the dots\",\"skillIds\":[\"" + catalog.skillId() + "\"]}", admin), 201);
        String activityVersionId = firstVersion(activity);
        json(put("/api/v1/studio/activity-versions/" + activityVersionId + "/items",
                "{\"items\":[{\"questionVersionId\":\"" + versionId + "\"}]}", admin), 200);
        json(post("/api/v1/studio/activity-versions/" + activityVersionId + "/publish", "", admin), 200);
        JsonNode curriculum = json(post("/api/v1/studio/curricula", "{\"subjectId\":\"" + catalog.subjectId()
                + "\",\"ageHubId\":\"" + catalog.ageHubId() + "\",\"name\":\"Dots " + catalog.suffix() + "\"}", admin), 201);
        json(put("/api/v1/studio/curriculum-versions/" + firstVersion(curriculum) + "/objectives",
                "{\"objectiveIds\":[\"" + catalog.objectiveId() + "\"]}", admin), 200);
        json(post("/api/v1/studio/curriculum-versions/" + firstVersion(curriculum) + "/publish", "", admin), 200);

        // What a child's device receives: two columns, no pairing, the right column not in pair order.
        String parent = registerParent();
        String childId = json(post("/api/v1/children", "{\"displayName\":\"Matcher\",\"dateOfBirth\":\""
                + LocalDate.now().minusYears(6) + "\"}", parent), 201).get("id").asString();
        String enrollmentId = json(post("/api/v1/children/" + childId + "/enrollments",
                "{\"curriculumId\":\"" + curriculum.get("id").asString() + "\"}", parent), 201).get("id").asString();
        JsonNode step = json(get("/api/v1/enrollments/" + enrollmentId + "/journey", parent), 200).get("steps").get(0);
        JsonNode item = json(get("/api/v1/activity-versions/" + activityVersionId, parent), 200).get("items").get(0);
        assertThat(item.get("questionType").asString()).isEqualTo("MATCHING");
        assertThat(item.toString()).doesNotContain("matchPair").doesNotContain("correct");
        List<String> sides = new ArrayList<>();
        item.get("options").forEach(option -> sides.add(option.get("matchSide").asString()));
        assertThat(sides).containsExactly("LEFT", "LEFT", "LEFT", "RIGHT", "RIGHT", "RIGHT");

        String attemptId = json(post("/api/v1/children/" + childId + "/activity-attempts", "{\"activityVersionId\":\""
                + activityVersionId + "\",\"learningPathItemId\":\"" + step.get("learningPathItemId").asString() + "\"}", parent), 201)
                .get("id").asString();
        // An incomplete pairing is refused and is not evidence.
        String partial = "{\\\"" + pairs.get(0).get("leftId").asString() + "\\\":\\\"" + pairs.get(0).get("rightId").asString() + "\\\"}";
        assertThat(post("/api/v1/activity-attempts/" + attemptId + "/answers", "{\"questionVersionId\":\"" + versionId
                + "\",\"submittedAnswer\":\"" + partial + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", parent).statusCode())
                .isEqualTo(422);
        StringBuilder all = new StringBuilder("{");
        for (JsonNode pair : pairs) {
            all.append(all.length() > 1 ? "," : "").append("\\\"").append(pair.get("leftId").asString()).append("\\\":\\\"")
                    .append(pair.get("rightId").asString()).append("\\\"");
        }
        all.append("}");
        JsonNode answer = json(post("/api/v1/activity-attempts/" + attemptId + "/answers", "{\"questionVersionId\":\""
                + versionId + "\",\"submittedAnswer\":\"" + all + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", parent), 201);
        assertThat(answer.get("correct").asBoolean()).isTrue();
        assertThat(answer.get("correctAnswer").get("matches")).hasSize(3);
        assertThat(answer.get("correctAnswer").get("text").asString()).isEqualTo("1 → •, 2 → ••, 3 → •••");
        json(post("/api/v1/activity-attempts/" + attemptId + "/complete", "", parent), 200);
        assertThat(jdbc.queryForObject("select count(*) from answer_records where activity_attempt_id = ?::uuid", Integer.class,
                UUID.fromString(attemptId))).as("the refused pairing left no evidence").isEqualTo(1);

        // Changing the pairs means a new version; the published one keeps its pairing.
        JsonNode opened = json(post("/api/v1/studio/questions/" + questionId + "/draft", "", admin), 200);
        assertThat(opened.get("versions").get(1).get("pairs")).hasSize(3);
    }

    @Test
    void publishingRefusesContentThatWouldTeachThePlatformNothing() throws Exception {
        String admin = staff(PlatformRole.ADMIN);
        Catalog catalog = catalog(admin);
        String otherSkillId = json(post("/api/v1/studio/subjects/" + catalog.subjectId() + "/skills",
                "{\"code\":\"OTHER_" + catalog.suffix() + "\",\"name\":\"Other skill\"}", admin), 201).get("id").asString();

        // No skill: answers would record no evidence.
        JsonNode untagged = json(post("/api/v1/studio/questions", numeric(catalog.subjectId(), "2 + 2 = ?", "4", List.of()), admin), 201);
        assertThat(publishErrors(post("/api/v1/studio/question-versions/" + firstVersion(untagged) + "/publish", "", admin)))
                .containsExactly("QUESTION_NO_SKILL");

        // Two identical choices a child couldn't tell apart.
        JsonNode duplicate = json(post("/api/v1/studio/questions", "{\"subjectId\":\"" + catalog.subjectId()
                + "\",\"questionType\":\"MULTIPLE_CHOICE\",\"prompt\":\"Pick one\",\"options\":[{\"label\":\"Cat\",\"correct\":true},"
                + "{\"label\":\" cat \",\"correct\":false}],\"skillIds\":[\"" + catalog.skillId() + "\"]}", admin), 201);
        assertThat(publishErrors(post("/api/v1/studio/question-versions/" + firstVersion(duplicate) + "/publish", "", admin)))
                .containsExactly("CHOICES_DUPLICATE");

        // An activity placed for a skill none of its questions evidences.
        JsonNode good = json(post("/api/v1/studio/questions", numeric(catalog.subjectId(), "1 + 1 = ?", "2",
                List.of(catalog.skillId())), admin), 201);
        json(post("/api/v1/studio/question-versions/" + firstVersion(good) + "/publish", "", admin), 200);
        String lessonId = json(post("/api/v1/studio/lessons", "{\"subjectId\":\"" + catalog.subjectId()
                + "\",\"title\":\"Mixed\"}", admin), 201).get("id").asString();
        JsonNode activity = json(post("/api/v1/studio/lessons/" + lessonId + "/activities",
                "{\"title\":\"Misfiled\",\"skillIds\":[\"" + otherSkillId + "\"]}", admin), 201);
        json(put("/api/v1/studio/activity-versions/" + firstVersion(activity) + "/items",
                "{\"items\":[{\"questionVersionId\":\"" + firstVersion(good) + "\"}]}", admin), 200);
        assertThat(publishErrors(post("/api/v1/studio/activity-versions/" + firstVersion(activity) + "/publish", "", admin)))
                .containsExactly("SKILL_WITHOUT_EVIDENCE");
        // An empty activity can't be published either.
        JsonNode empty = json(post("/api/v1/studio/lessons/" + lessonId + "/activities",
                "{\"title\":\"Empty\",\"skillIds\":[\"" + catalog.skillId() + "\"]}", admin), 201);
        assertThat(publishErrors(post("/api/v1/studio/activity-versions/" + firstVersion(empty) + "/publish", "", admin)))
                .contains("NO_QUESTIONS");
        // A draft question can't go into an activity.
        assertThat(put("/api/v1/studio/activity-versions/" + firstVersion(empty) + "/items",
                "{\"items\":[{\"questionVersionId\":\"" + firstVersion(untagged) + "\"}]}", admin).statusCode()).isEqualTo(409);
    }

    @Test
    void publishedContentIsChangedOnlyThroughANewDraft() throws Exception {
        String admin = staff(PlatformRole.ADMIN);
        Catalog catalog = catalog(admin);
        JsonNode question = json(post("/api/v1/studio/questions", numeric(catalog.subjectId(), "3 + 1 = ?", "4",
                List.of(catalog.skillId())), admin), 201);
        String v1 = firstVersion(question);
        json(post("/api/v1/studio/question-versions/" + v1 + "/publish", "", admin), 200);

        // The published version is immutable.
        assertThat(put("/api/v1/studio/question-versions/" + v1, "{\"prompt\":\"changed\",\"correctAnswer\":\"5\"}", admin)
                .statusCode()).isEqualTo(409);

        // A new draft starts as a copy; opening it twice gives the same draft.
        String questionId = question.get("id").asString();
        JsonNode opened = json(post("/api/v1/studio/questions/" + questionId + "/draft", "", admin), 200);
        assertThat(json(post("/api/v1/studio/questions/" + questionId + "/draft", "", admin), 200).get("versions")).hasSize(2);
        JsonNode v2 = opened.get("versions").get(1);
        assertThat(v2.get("status").asString()).isEqualTo("DRAFT");
        assertThat(v2.get("prompt").asString()).isEqualTo("3 + 1 = ?");
        json(put("/api/v1/studio/question-versions/" + v2.get("id").asString(),
                "{\"prompt\":\"3 + 2 = ?\",\"correctAnswer\":\"5\",\"difficultyLevel\":3}", admin), 200);
        JsonNode republished = json(post("/api/v1/studio/question-versions/" + v2.get("id").asString() + "/publish", "", admin), 200);
        assertThat(republished.get("versions").get(0).get("status").asString()).isEqualTo("RETIRED");
        assertThat(republished.get("versions").get(1).get("status").asString()).isEqualTo("PUBLISHED");
        assertThat(republished.get("versions").get(0).get("prompt").asString()).isEqualTo("3 + 1 = ?");

        // An activity's next draft re-pins its questions to their current published versions.
        String lessonId = json(post("/api/v1/studio/lessons", "{\"subjectId\":\"" + catalog.subjectId()
                + "\",\"title\":\"Adding\"}", admin), 201).get("id").asString();
        JsonNode activity = json(post("/api/v1/studio/lessons/" + lessonId + "/activities",
                "{\"title\":\"Add one more\",\"skillIds\":[\"" + catalog.skillId() + "\"]}", admin), 201);
        String activityId = activity.get("id").asString();
        json(put("/api/v1/studio/activity-versions/" + firstVersion(activity) + "/items",
                "{\"items\":[{\"questionVersionId\":\"" + v2.get("id").asString() + "\"}]}", admin), 200);
        json(post("/api/v1/studio/activity-versions/" + firstVersion(activity) + "/publish", "", admin), 200);
        JsonNode v3 = json(post("/api/v1/studio/questions/" + questionId + "/draft", "", admin), 200).get("versions").get(2);
        json(post("/api/v1/studio/question-versions/" + v3.get("id").asString() + "/publish", "", admin), 200);

        JsonNode nextDraft = json(post("/api/v1/studio/activities/" + activityId + "/draft", "", admin), 200)
                .get("versions").get(1);
        assertThat(nextDraft.get("status").asString()).isEqualTo("DRAFT");
        assertThat(nextDraft.get("items").get(0).get("questionVersionId").asString()).isEqualTo(v3.get("id").asString());
    }

    @Test
    void authorsSeeAndChangeOnlyTheirOwnContentAndOnlyAdminsChangeTheCatalog() throws Exception {
        String admin = staff(PlatformRole.ADMIN);
        String teacher = staff(PlatformRole.TEACHER);
        Catalog catalog = catalog(admin);
        String platformLesson = json(post("/api/v1/studio/lessons", "{\"subjectId\":\"" + catalog.subjectId()
                + "\",\"title\":\"Platform lesson\"}", admin), 201).get("id").asString();
        JsonNode platformActivity = json(post("/api/v1/studio/lessons/" + platformLesson + "/activities",
                "{\"title\":\"Platform activity\",\"skillIds\":[\"" + catalog.skillId() + "\"]}", admin), 201);
        JsonNode platformQuestion = json(post("/api/v1/studio/questions", numeric(catalog.subjectId(), "5 + 5 = ?", "10",
                List.of(catalog.skillId())), admin), 201);
        json(post("/api/v1/studio/question-versions/" + firstVersion(platformQuestion) + "/publish", "", admin), 200);

        // The teacher's own work.
        String teacherLesson = json(post("/api/v1/studio/lessons", "{\"subjectId\":\"" + catalog.subjectId()
                + "\",\"title\":\"My lesson\"}", teacher), 201).get("id").asString();
        JsonNode teacherActivity = json(post("/api/v1/studio/lessons/" + teacherLesson + "/activities",
                "{\"title\":\"My activity\",\"skillIds\":[\"" + catalog.skillId() + "\"]}", teacher), 201);
        JsonNode teacherQuestion = json(post("/api/v1/studio/questions", numeric(catalog.subjectId(), "6 + 1 = ?", "7",
                List.of(catalog.skillId())), teacher), 201);
        json(post("/api/v1/studio/question-versions/" + firstVersion(teacherQuestion) + "/publish", "", teacher), 200);

        // Lists are scoped to the caller.
        assertThat(get("/api/v1/studio/lessons", teacher).body()).contains("My lesson").doesNotContain("Platform lesson");
        assertThat(get("/api/v1/studio/lessons", admin).body()).contains("Platform lesson").doesNotContain("My lesson");
        assertThat(get("/api/v1/studio/questions?subjectId=" + catalog.subjectId(), teacher).body())
                .contains("6 + 1").doesNotContain("5 + 5");

        // The teacher can't read or change platform content, or the catalog.
        assertThat(get("/api/v1/studio/activities/" + platformActivity.get("id").asString(), teacher).statusCode()).isEqualTo(403);
        assertThat(put("/api/v1/studio/activity-versions/" + firstVersion(platformActivity) + "/items",
                "{\"items\":[]}", teacher).statusCode()).isEqualTo(403);
        assertThat(post("/api/v1/studio/subjects/" + catalog.subjectId() + "/skills",
                "{\"code\":\"T_" + catalog.suffix() + "\",\"name\":\"Teacher skill\"}", teacher).statusCode()).isEqualTo(403);
        assertThat(post("/api/v1/studio/subjects", "{\"code\":\"T_" + catalog.suffix() + "\",\"name\":\"Teacher subject\"}",
                teacher).statusCode()).isEqualTo(403);

        // Platform questions may be used in the teacher's activity; the teacher's may not go into platform content.
        assertThat(put("/api/v1/studio/activity-versions/" + firstVersion(teacherActivity) + "/items",
                "{\"items\":[{\"questionVersionId\":\"" + firstVersion(platformQuestion) + "\"}]}", teacher).statusCode())
                .isEqualTo(200);
        assertThat(put("/api/v1/studio/activity-versions/" + firstVersion(platformActivity) + "/items",
                "{\"items\":[{\"questionVersionId\":\"" + firstVersion(teacherQuestion) + "\"}]}", admin).statusCode())
                .isEqualTo(404);

        // Families and anonymous callers have no studio at all.
        String parent = registerParent();
        assertThat(get("/api/v1/studio/lessons", parent).statusCode()).isEqualTo(403);
        assertThat(get("/api/v1/studio/lessons", null).statusCode()).isEqualTo(401);
    }

    // ------------------------------------------------------------------ fixture

    private record Catalog(String suffix, String subjectId, String ageHubId, String skillId, String objectiveId) {
    }

    /** A new subject with one skill in one objective, authored through the Studio; an age hub no test child falls into. */
    private Catalog catalog(String admin) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String subjectId = json(post("/api/v1/studio/subjects", "{\"code\":\"STUDIO_" + suffix + "\",\"name\":\"Studio "
                + suffix + "\"}", admin), 201).get("id").asString();
        String skillId = json(post("/api/v1/studio/subjects/" + subjectId + "/skills", "{\"code\":\"COUNT_" + suffix
                + "\",\"name\":\"Count to 5\",\"description\":\"Counts up to five objects.\"}", admin), 201).get("id").asString();
        String objectiveId = json(post("/api/v1/studio/subjects/" + subjectId + "/objectives", "{\"code\":\"OBJ_" + suffix
                + "\",\"title\":\"Counting\"}", admin), 201).get("id").asString();
        JsonNode linked = json(post("/api/v1/studio/objectives/" + objectiveId + "/skills/" + skillId, "", admin), 200);
        assertThat(linked.get("skillIds").get(0).asString()).isEqualTo(skillId);
        String ageHubId = ageHubRepository.save(AgeHub.create("STUDIO_" + suffix, "Studio " + suffix, 90, 99, 99)).getId().toString();
        return new Catalog(suffix, subjectId, ageHubId, skillId, objectiveId);
    }

    private static String numeric(String subjectId, String prompt, String answer, List<String> skillIds) {
        return "{\"subjectId\":\"" + subjectId + "\",\"questionType\":\"NUMERIC\",\"prompt\":\"" + prompt
                + "\",\"correctAnswer\":\"" + answer + "\",\"difficultyLevel\":1,\"skillIds\":["
                + String.join(",", skillIds.stream().map(id -> "\"" + id + "\"").toList()) + "]}";
    }

    private static String firstVersion(JsonNode detail) {
        return detail.get("versions").get(0).get("id").asString();
    }

    private static List<String> publishErrors(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(409);
        return codes(JSON.readTree(response.body()).get("validation").get("errors"));
    }

    private static List<String> codes(JsonNode issues) {
        List<String> codes = new ArrayList<>();
        issues.forEach(issue -> codes.add(issue.get("code").asString()));
        return codes;
    }

    private String staff(PlatformRole role) throws Exception {
        String email = "studio-" + role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), role));
        return json(post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", null), 200)
                .get("accessToken").asString();
    }

    private String registerParent() throws Exception {
        return json(post("/api/v1/auth/register", "{\"email\":\"studio-parent-" + UUID.randomUUID()
                + "@example.com\",\"password\":\"" + PASSWORD + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}",
                null), 201).get("accessToken").asString();
    }

    // ------------------------------------------------------------------ HTTP

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
