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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Y42 over real HTTP against PostgreSQL: a quiz is written inside its activity. A new question is created
 * and added in one step - for the activity's subject and skills, only by someone who may write the activity.
 * The activity and its draft questions are then published together or not at all: one refused question
 * leaves everything as it was. A superseded version still can't be added.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class ActivityBuilderIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Builder-Test-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AgeHubRepository ageHubRepository;
    @Autowired private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void aQuestionWrittenInAnActivityIsCreatedAndAddedInOneStepForTheActivitysSubjectAndSkills() throws Exception {
        Staff teacher = staff(PlatformRole.TEACHER);
        Staff otherTeacher = staff(PlatformRole.TEACHER);
        Catalog catalog = catalog(staff(PlatformRole.ADMIN).token());
        String activityVersion = newActivity(teacher.token(), catalog);
        int questionsBefore = questionCount(catalog.subjectId());

        // Another teacher can't write into it - and no question is left behind.
        assertThat(post("/api/v1/studio/activity-versions/" + activityVersion + "/questions",
                numeric("2 + 2 = ?", "4"), otherTeacher.token()).statusCode()).isEqualTo(403);
        assertThat(questionCount(catalog.subjectId())).isEqualTo(questionsBefore);

        // The request's subject is ignored: the question belongs to the activity's subject, tagged with its skills.
        JsonNode activity = json(post("/api/v1/studio/activity-versions/" + activityVersion + "/questions",
                numeric("2 + 2 = ?", "4"), teacher.token()), 201);
        JsonNode item = activity.get("versions").get(0).get("items").get(0);
        assertThat(item.get("questionVersionStatus").asString()).isEqualTo("DRAFT");
        assertThat(item.get("questionSkillIds").get(0).asString()).isEqualTo(catalog.skillId());
        assertThat(questionCount(catalog.subjectId())).isEqualTo(questionsBefore + 1);
        JsonNode second = json(post("/api/v1/studio/activity-versions/" + activityVersion + "/questions",
                numeric("3 + 1 = ?", "4"), teacher.token()), 201);
        assertThat(second.get("versions").get(0).get("items")).hasSize(2);
        assertThat(second.get("versions").get(0).get("items").get(1).get("prompt").asString()).isEqualTo("3 + 1 = ?");
    }

    @Test
    void anActivityAndItsDraftQuestionsArePublishedTogetherOrNotAtAll() throws Exception {
        Staff teacher = staff(PlatformRole.TEACHER);
        Catalog catalog = catalog(staff(PlatformRole.ADMIN).token());
        String activityVersion = newActivity(teacher.token(), catalog);
        json(post("/api/v1/studio/activity-versions/" + activityVersion + "/questions", numeric("2 + 2 = ?", "4"), teacher.token()), 201);
        // A number question without its answer can't be published.
        JsonNode withBroken = json(post("/api/v1/studio/activity-versions/" + activityVersion + "/questions",
                numeric("5 + 5 = ?", null), teacher.token()), 201);
        String broken = withBroken.get("versions").get(0).get("items").get(1).get("questionVersionId").asString();

        HttpResponse<String> refused = post("/api/v1/studio/activity-versions/" + activityVersion + "/publish-with-questions", "",
                teacher.token());
        assertThat(refused.statusCode()).as(refused.body()).isIn(409, 422);
        // Nothing was published: not the good question, not the activity.
        assertThat(jdbc.queryForObject("select count(*) from question_versions qv join activity_items ai on ai.question_version_id = qv.id"
                + " where ai.activity_version_id = ?::uuid and qv.status = 'PUBLISHED'", Integer.class, activityVersion)).isZero();
        assertThat(jdbc.queryForObject("select status from activity_versions where id = ?::uuid", String.class, activityVersion))
                .isEqualTo("DRAFT");

        // Fixed, it all goes out in one step.
        json(put("/api/v1/studio/question-versions/" + broken, "{\"prompt\":\"5 + 5 = ?\",\"correctAnswer\":\"10\"}", teacher.token()), 200);
        JsonNode published = json(post("/api/v1/studio/activity-versions/" + activityVersion + "/publish-with-questions", "",
                teacher.token()), 200);
        assertThat(published.get("versions").get(0).get("status").asString()).isEqualTo("PUBLISHED");
        published.get("versions").get(0).get("items")
                .forEach(i -> assertThat(i.get("questionVersionStatus").asString()).isEqualTo("PUBLISHED"));
        // And it is frozen like anything published.
        assertThat(post("/api/v1/studio/activity-versions/" + activityVersion + "/questions", numeric("1 + 1 = ?", "2"),
                teacher.token()).statusCode()).isEqualTo(409);
    }

    @Test
    void aSupersededQuestionVersionStillCantBeAdded() throws Exception {
        Staff admin = staff(PlatformRole.ADMIN);
        Catalog catalog = catalog(admin.token());
        JsonNode question = json(post("/api/v1/studio/questions", "{\"subjectId\":\"" + catalog.subjectId()
                + "\",\"questionType\":\"NUMERIC\",\"prompt\":\"1 + 2 = ?\",\"correctAnswer\":\"3\",\"difficultyLevel\":1,\"skillIds\":[\""
                + catalog.skillId() + "\"]}", admin.token()), 201);
        String v1 = question.get("versions").get(0).get("id").asString();
        json(post("/api/v1/studio/question-versions/" + v1 + "/publish", "", admin.token()), 200);
        String v2 = json(post("/api/v1/studio/questions/" + question.get("id").asString() + "/draft", "", admin.token()), 200)
                .get("versions").get(1).get("id").asString();
        json(post("/api/v1/studio/question-versions/" + v2 + "/publish", "", admin.token()), 200);
        String activityVersion = newActivity(admin.token(), catalog);
        assertThat(put("/api/v1/studio/activity-versions/" + activityVersion + "/items",
                "{\"items\":[{\"questionVersionId\":\"" + v1 + "\"}]}", admin.token()).statusCode()).isEqualTo(409);
    }

    private String newActivity(String token, Catalog catalog) throws Exception {
        String lessonId = json(post("/api/v1/studio/lessons", "{\"subjectId\":\"" + catalog.subjectId() + "\",\"title\":\"Quiz\"}",
                token), 201).get("id").asString();
        return json(post("/api/v1/studio/lessons/" + lessonId + "/activities",
                "{\"title\":\"My quiz\",\"skillIds\":[\"" + catalog.skillId() + "\"]}", token), 201)
                .get("versions").get(0).get("id").asString();
    }

    /** A number question; its subject is any id - the activity's subject is the one used; no skills - the activity's are. */
    private static String numeric(String prompt, String answer) {
        return "{\"subjectId\":\"" + UUID.randomUUID() + "\",\"questionType\":\"NUMERIC\",\"prompt\":\"" + prompt + "\","
                + "\"correctAnswer\":" + (answer == null ? "null" : "\"" + answer + "\"") + ",\"difficultyLevel\":1,\"skillIds\":[]}";
    }

    private int questionCount(String subjectId) {
        return jdbc.queryForObject("select count(*) from questions where subject_id = ?::uuid", Integer.class, subjectId);
    }

    private record Catalog(String suffix, String subjectId, String ageHubId, String skillId, String objectiveId) {
    }

    private Catalog catalog(String admin) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String subjectId = json(post("/api/v1/studio/subjects", "{\"code\":\"BUILD_" + suffix + "\",\"name\":\"Build "
                + suffix + "\"}", admin), 201).get("id").asString();
        String skillId = json(post("/api/v1/studio/subjects/" + subjectId + "/skills", "{\"code\":\"COUNT_" + suffix
                + "\",\"name\":\"Count to 10\",\"description\":\"Counts objects.\"}", admin), 201).get("id").asString();
        String objectiveId = json(post("/api/v1/studio/subjects/" + subjectId + "/objectives", "{\"code\":\"OBJ_" + suffix
                + "\",\"title\":\"Counting\"}", admin), 201).get("id").asString();
        json(post("/api/v1/studio/objectives/" + objectiveId + "/skills/" + skillId, "", admin), 200);
        String ageHubId = ageHubRepository.save(AgeHub.create("BUILD_" + suffix, "Build " + suffix, 90, 99, 99)).getId().toString();
        return new Catalog(suffix, subjectId, ageHubId, skillId, objectiveId);
    }

    private record Played(String parent, String activityVersionId, String attemptId, String childId) {
    }

    /** A published activity holding the questions, on a published curriculum a family's child is enrolled in, attempt started. */
    private Played playableActivity(String admin, Catalog catalog, List<String> questionVersionIds) throws Exception {
        String lessonId = json(post("/api/v1/studio/lessons", "{\"subjectId\":\"" + catalog.subjectId() + "\",\"title\":\"Stars\"}",
                admin), 201).get("id").asString();
        JsonNode activity = json(post("/api/v1/studio/lessons/" + lessonId + "/activities",
                "{\"title\":\"Count stars\",\"skillIds\":[\"" + catalog.skillId() + "\"]}", admin), 201);
        String activityVersionId = activity.get("versions").get(0).get("id").asString();
        json(put("/api/v1/studio/activity-versions/" + activityVersionId + "/items", "{\"items\":[" + String.join(",",
                questionVersionIds.stream().map(id -> "{\"questionVersionId\":\"" + id + "\"}").toList()) + "]}", admin), 200);
        json(post("/api/v1/studio/activity-versions/" + activityVersionId + "/publish", "", admin), 200);
        JsonNode curriculum = json(post("/api/v1/studio/curricula", "{\"subjectId\":\"" + catalog.subjectId()
                + "\",\"ageHubId\":\"" + catalog.ageHubId() + "\",\"name\":\"Stars " + catalog.suffix() + "\"}", admin), 201);
        String curriculumVersionId = curriculum.get("versions").get(0).get("id").asString();
        json(put("/api/v1/studio/curriculum-versions/" + curriculumVersionId + "/objectives",
                "{\"objectiveIds\":[\"" + catalog.objectiveId() + "\"]}", admin), 200);
        json(post("/api/v1/studio/curriculum-versions/" + curriculumVersionId + "/publish", "", admin), 200);

        String parent = json(post("/api/v1/auth/register", "{\"email\":\"builder-parent-" + UUID.randomUUID()
                + "@example.com\",\"password\":\"" + PASSWORD + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}",
                null), 201).get("accessToken").asString();
        String childId = json(post("/api/v1/children", "{\"displayName\":\"Build Child\",\"dateOfBirth\":\""
                + LocalDate.now().minusYears(6) + "\"}", parent), 201).get("id").asString();
        String enrollmentId = json(post("/api/v1/children/" + childId + "/enrollments",
                "{\"curriculumId\":\"" + curriculum.get("id").asString() + "\"}", parent), 201).get("id").asString();
        JsonNode step = json(get("/api/v1/enrollments/" + enrollmentId + "/journey", parent), 200).get("steps").get(0);
        String attemptId = json(post("/api/v1/children/" + childId + "/activity-attempts", "{\"activityVersionId\":\""
                + step.get("activityVersionId").asString() + "\",\"learningPathItemId\":\""
                + step.get("learningPathItemId").asString() + "\"}", parent), 201).get("id").asString();
        return new Played(parent, step.get("activityVersionId").asString(), attemptId, childId);
    }

    private static JsonNode itemFor(JsonNode content, String questionVersionId) {
        for (JsonNode item : content.get("items")) {
            if (item.get("questionVersionId").asString().equals(questionVersionId)) {
                return item;
            }
        }
        throw new AssertionError("no item for " + questionVersionId);
    }

    private static String answer(String questionVersionId, String submittedAnswerJson) {
        return "{\"questionVersionId\":\"" + questionVersionId + "\",\"submittedAnswer\":" + submittedAnswerJson
                + ",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}";
    }

    private static String firstVersion(JsonNode detail) {
        return detail.get("versions").get(0).get("id").asString();
    }

    // ------------------------------------------------------------------ accounts and HTTP

    private record Staff(UUID id, String token) {
    }

    private Staff staff(PlatformRole role) throws Exception {
        String email = "builder-" + role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        Account account = accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), role));
        return new Staff(account.getId(), json(post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\""
                + PASSWORD + "\"}", null), 200).get("accessToken").asString());
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
