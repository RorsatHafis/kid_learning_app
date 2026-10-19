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
 * Y41 over real HTTP against PostgreSQL: an author's hint and explanation are part of a question's
 * version - written on a draft, frozen once published, carried to the next draft, written only by
 * someone who may write the question. A child gets the hint with the question and the explanation
 * only with an answer they have given; a hint that says the answer can't be published; and a
 * question without either works exactly as before.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class QuestionGuidanceIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Guidance-Test-Pass-2026";
    private static final String HINT = "Touch each star as you count it.";
    private static final String EXPLANATION = "There are three stars: one, two, three.";

    @Value("${local.server.port}") private int port;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AgeHubRepository ageHubRepository;
    @Autowired private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void aHintAndAnExplanationAreAuthoredOnADraftFrozenWhenPublishedAndCarriedToTheNextDraft() throws Exception {
        Staff teacher = staff(PlatformRole.TEACHER);
        Staff otherTeacher = staff(PlatformRole.TEACHER);
        Catalog catalog = catalog(staff(PlatformRole.ADMIN).token());

        JsonNode question = json(post("/api/v1/studio/questions", numeric(catalog, "How many stars? ★★★", "3", HINT, EXPLANATION),
                teacher.token()), 201);
        JsonNode first = question.get("versions").get(0);
        assertThat(first.get("hint").asString()).isEqualTo(HINT);
        assertThat(first.get("explanation").asString()).isEqualTo(EXPLANATION);
        String versionId = first.get("id").asString();

        // Another teacher can't write it; a blank one removes it; stray space is trimmed.
        assertThat(put("/api/v1/studio/question-versions/" + versionId, revise("3", "x", "y"), otherTeacher.token()).statusCode())
                .isEqualTo(403);
        JsonNode revised = json(put("/api/v1/studio/question-versions/" + versionId, revise("3", "  " + HINT + "  ", " "),
                teacher.token()), 200).get("versions").get(0);
        assertThat(revised.get("hint").asString()).isEqualTo(HINT);
        assertThat(revised.get("explanation").isNull()).isTrue();
        json(put("/api/v1/studio/question-versions/" + versionId, revise("3", HINT, EXPLANATION), teacher.token()), 200);

        // The author previews it as the child will get it: with the hint.
        JsonNode preview = json(get("/api/v1/studio/question-versions/" + versionId + "/preview", teacher.token()), 200);
        assertThat(preview.get("hint").asString()).isEqualTo(HINT);

        // Published: frozen.
        json(post("/api/v1/studio/question-versions/" + versionId + "/publish", "", teacher.token()), 200);
        assertThat(put("/api/v1/studio/question-versions/" + versionId, revise("3", "changed", "changed"), teacher.token()).statusCode())
                .isEqualTo(409);
        assertThat(jdbc.queryForObject("select hint from question_versions where id = ?::uuid", String.class, versionId)).isEqualTo(HINT);

        // The next draft starts with both; changing it leaves the published version as it was.
        JsonNode draft = json(post("/api/v1/studio/questions/" + question.get("id").asString() + "/draft", "", teacher.token()), 200)
                .get("versions").get(1);
        assertThat(draft.get("hint").asString()).isEqualTo(HINT);
        assertThat(draft.get("explanation").asString()).isEqualTo(EXPLANATION);
        json(put("/api/v1/studio/question-versions/" + draft.get("id").asString(), revise("3", "Count slowly.", EXPLANATION),
                teacher.token()), 200);
        assertThat(jdbc.queryForObject("select hint from question_versions where id = ?::uuid", String.class, versionId)).isEqualTo(HINT);

        // Too long is refused, and nothing changes.
        assertThat(put("/api/v1/studio/question-versions/" + draft.get("id").asString(), revise("3", "a".repeat(301), null),
                teacher.token()).statusCode()).isEqualTo(400);
    }

    @Test
    void aHintThatSaysTheAnswerCantBePublished() throws Exception {
        Staff admin = staff(PlatformRole.ADMIN);
        Catalog catalog = catalog(admin.token());

        String says = firstVersion(json(post("/api/v1/studio/questions", numeric(catalog, "2 + 5 = ?", "7", "It is 7.", null),
                admin.token()), 201));
        assertThat(post("/api/v1/studio/question-versions/" + says + "/publish", "", admin.token()).body()).contains("HINT_GIVES_ANSWER");
        // ...in Khmer digits too.
        String khmer = firstVersion(json(post("/api/v1/studio/questions", numeric(catalog, "2 + 5 = ?", "7", "ចម្លើយគឺ ៧", null),
                admin.token()), 201));
        assertThat(post("/api/v1/studio/question-versions/" + khmer + "/publish", "", admin.token()).body()).contains("HINT_GIVES_ANSWER");
        // A hint with other numbers in it is fine: only the answer itself is held back.
        String fine = firstVersion(json(post("/api/v1/studio/questions", numeric(catalog, "2 + 5 = ?", "7", "Start at 2 and count on 5.", null),
                admin.token()), 201));
        json(post("/api/v1/studio/question-versions/" + fine + "/publish", "", admin.token()), 200);

        // A choice's words in the hint: a warning to check, not a refusal.
        String choice = firstVersion(json(post("/api/v1/studio/questions", choice(catalog, "Which one barks?", "dog",
                "Think which dog sound you know."), admin.token()), 201));
        JsonNode report = json(get("/api/v1/studio/question-versions/" + choice + "/validation", admin.token()), 200);
        assertThat(report.get("errors")).isEmpty();
        assertThat(report.get("warnings").toString()).contains("HINT_NAMES_CHOICE");
        json(post("/api/v1/studio/question-versions/" + choice + "/publish", "", admin.token()), 200);
    }

    @Test
    void aChildGetsTheHintWithTheQuestionAndTheExplanationOnlyWithTheirAnswer() throws Exception {
        Staff admin = staff(PlatformRole.ADMIN);
        Catalog catalog = catalog(admin.token());
        String guided = firstVersion(json(post("/api/v1/studio/questions", numeric(catalog, "How many stars? ★★★", "3", HINT, EXPLANATION),
                admin.token()), 201));
        String plain = firstVersion(json(post("/api/v1/studio/questions", numeric(catalog, "1 + 1 = ?", "2", null, null),
                admin.token()), 201));
        for (String id : List.of(guided, plain)) {
            json(post("/api/v1/studio/question-versions/" + id + "/publish", "", admin.token()), 200);
        }
        Played played = playableActivity(admin.token(), catalog, List.of(guided, plain));

        JsonNode content = json(get("/api/v1/activity-versions/" + played.activityVersionId() + "?childId=" + played.childId(),
                played.parent()), 200);
        assertThat(itemFor(content, guided).get("hint").asString()).isEqualTo(HINT);
        assertThat(itemFor(content, plain).get("hint").isNull()).isTrue();
        // The explanation isn't in what the child gets before answering.
        assertThat(content.toString()).doesNotContain(EXPLANATION).doesNotContain("explanation");

        // A wrong answer: right answer and explanation come back with it.
        JsonNode wrong = json(post("/api/v1/activity-attempts/" + played.attemptId() + "/answers", answer(guided, "\"4\""),
                played.parent()), 201);
        assertThat(wrong.get("correct").asBoolean()).isFalse();
        assertThat(wrong.get("correctAnswer").get("text").asString()).isEqualTo("3");
        assertThat(wrong.get("explanation").asString()).isEqualTo(EXPLANATION);
        // A question without one answers as before.
        JsonNode right = json(post("/api/v1/activity-attempts/" + played.attemptId() + "/answers", answer(plain, "\"2\""),
                played.parent()), 201);
        assertThat(right.get("correct").asBoolean()).isTrue();
        assertThat(right.get("explanation").isNull()).isTrue();
        // Reading the answers back (resuming) gives the same.
        JsonNode answers = json(get("/api/v1/activity-attempts/" + played.attemptId() + "/answers", played.parent()), 200);
        assertThat(answers.toString()).contains(EXPLANATION);
    }

    // ------------------------------------------------------------------ content

    private static String numeric(Catalog catalog, String prompt, String answer, String hint, String explanation) {
        return "{\"subjectId\":\"" + catalog.subjectId() + "\",\"questionType\":\"NUMERIC\",\"prompt\":\"" + prompt + "\","
                + "\"correctAnswer\":\"" + answer + "\",\"difficultyLevel\":1,\"skillIds\":[\"" + catalog.skillId() + "\"],"
                + "\"hint\":" + quoteOrNull(hint) + ",\"explanation\":" + quoteOrNull(explanation) + "}";
    }

    private static String choice(Catalog catalog, String prompt, String right, String hint) {
        return "{\"subjectId\":\"" + catalog.subjectId() + "\",\"questionType\":\"MULTIPLE_CHOICE\",\"prompt\":\"" + prompt + "\","
                + "\"difficultyLevel\":1,\"options\":[{\"label\":\"" + right + "\",\"correct\":true},{\"label\":\"cat\",\"correct\":false}],"
                + "\"skillIds\":[\"" + catalog.skillId() + "\"],\"hint\":" + quoteOrNull(hint) + "}";
    }

    private static String revise(String answer, String hint, String explanation) {
        return "{\"prompt\":\"How many stars? ★★★\",\"correctAnswer\":\"" + answer + "\",\"difficultyLevel\":1,"
                + "\"hint\":" + quoteOrNull(hint) + ",\"explanation\":" + quoteOrNull(explanation) + "}";
    }

    private static String quoteOrNull(String text) {
        return text == null ? "null" : JSON.writeValueAsString(text);
    }

    private record Catalog(String suffix, String subjectId, String ageHubId, String skillId, String objectiveId) {
    }

    private Catalog catalog(String admin) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String subjectId = json(post("/api/v1/studio/subjects", "{\"code\":\"GUIDE_" + suffix + "\",\"name\":\"Guide "
                + suffix + "\"}", admin), 201).get("id").asString();
        String skillId = json(post("/api/v1/studio/subjects/" + subjectId + "/skills", "{\"code\":\"COUNT_" + suffix
                + "\",\"name\":\"Count to 10\",\"description\":\"Counts objects.\"}", admin), 201).get("id").asString();
        String objectiveId = json(post("/api/v1/studio/subjects/" + subjectId + "/objectives", "{\"code\":\"OBJ_" + suffix
                + "\",\"title\":\"Counting\"}", admin), 201).get("id").asString();
        json(post("/api/v1/studio/objectives/" + objectiveId + "/skills/" + skillId, "", admin), 200);
        String ageHubId = ageHubRepository.save(AgeHub.create("GUIDE_" + suffix, "Guide " + suffix, 90, 99, 99)).getId().toString();
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

        String parent = json(post("/api/v1/auth/register", "{\"email\":\"guide-parent-" + UUID.randomUUID()
                + "@example.com\",\"password\":\"" + PASSWORD + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}",
                null), 201).get("accessToken").asString();
        String childId = json(post("/api/v1/children", "{\"displayName\":\"Guide Child\",\"dateOfBirth\":\""
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
        String email = "guide-" + role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
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
