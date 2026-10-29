package com.platform.content;

import com.platform.TestcontainersConfiguration;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Studio: duplicating a question an author may edit - a new question whose first draft is the original's latest
 * version (words, choices, help, difficulty, skills, translations), owned like the original; the original untouched.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class QuestionDuplicateIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Duplicate-Test-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void aTeacherDuplicatesTheirQuestionIntoANewDraftWithEverythingAndTheOriginalStaysAsItWas() throws Exception {
        Staff admin = staff(PlatformRole.ADMIN);
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String subjectId = json(post("/api/v1/studio/subjects", "{\"code\":\"DUP_" + suffix + "\",\"name\":\"Dup " + suffix + "\"}",
                admin), 201).get("id").asString();
        String skillId = json(post("/api/v1/studio/subjects/" + subjectId + "/skills", "{\"code\":\"BIG_" + suffix
                + "\",\"name\":\"Compare numbers\",\"description\":\"Says which is bigger.\"}", admin), 201).get("id").asString();
        Staff teacher = staff(PlatformRole.TEACHER);
        JsonNode original = json(post("/api/v1/studio/questions", "{\"subjectId\":\"" + subjectId + "\",\"questionType\":\"MULTIPLE_CHOICE\","
                + "\"prompt\":\"Which is bigger?\",\"difficultyLevel\":2,\"options\":[{\"label\":\"7\",\"correct\":true},"
                + "{\"label\":\"4\",\"correct\":false}],\"skillIds\":[\"" + skillId + "\"],\"hint\":\"Count up from the smaller one.\","
                + "\"explanation\":\"7 comes after 4 when we count.\"}", teacher), 201);
        String originalId = original.get("id").asString();
        String originalVersion = original.get("versions").get(0).get("id").asString();
        jdbc.update("insert into content_localizations (id, content_type, content_id, locale, field_name, localized_text) "
                + "values (?, 'QUESTION_VERSION', ?::uuid, 'km', 'prompt', 'តើលេខមួយណាធំជាង?')", UUID.randomUUID(), originalVersion);

        JsonNode copy = json(post("/api/v1/studio/questions/" + originalId + "/duplicate", "", teacher), 201);
        String copyId = copy.get("id").asString();
        assertThat(copyId).isNotEqualTo(originalId);
        assertThat(copy.get("subjectId").asString()).isEqualTo(subjectId);
        assertThat(copy.get("questionType").asString()).isEqualTo("MULTIPLE_CHOICE");
        assertThat(copy.get("versions")).hasSize(1);
        JsonNode draft = copy.get("versions").get(0);
        assertThat(draft.get("status").asString()).isEqualTo("DRAFT");
        assertThat(draft.get("versionNumber").asInt()).isEqualTo(1);
        assertThat(draft.get("prompt").asString()).isEqualTo("Which is bigger?");
        assertThat(draft.get("difficultyLevel").asInt()).isEqualTo(2);
        assertThat(draft.get("hint").asString()).isEqualTo("Count up from the smaller one.");
        assertThat(draft.get("explanation").asString()).isEqualTo("7 comes after 4 when we count.");
        List<String> choices = new ArrayList<>();
        draft.get("options").forEach(o -> choices.add(o.get("label").asString() + ":" + o.get("correct").asBoolean()));
        assertThat(choices).containsExactlyInAnyOrder("7:true", "4:false");
        assertThat(draft.get("skillIds").get(0).asString()).isEqualTo(skillId);
        assertThat(jdbc.queryForObject("select localized_text from content_localizations where content_type = 'QUESTION_VERSION' "
                + "and content_id = ?::uuid and locale = 'km' and field_name = 'prompt'", String.class,
                UUID.fromString(draft.get("id").asString()))).isEqualTo("តើលេខមួយណាធំជាង?");
        assertThat(jdbc.queryForObject("select owner_account_id from questions where id = ?::uuid", UUID.class, UUID.fromString(copyId)))
                .isEqualTo(teacher.id());

        // Changing the copy leaves the original as it was.
        json(put("/api/v1/studio/question-versions/" + draft.get("id").asString(), "{\"prompt\":\"Which is bigger: 6 or 3?\","
                + "\"difficultyLevel\":2,\"options\":[{\"label\":\"6\",\"correct\":true},{\"label\":\"3\",\"correct\":false}],"
                + "\"hint\":\"Count up from 3.\",\"explanation\":\"6 comes after 3.\"}", teacher), 200);
        JsonNode originalNow = json(get("/api/v1/studio/questions/" + originalId, teacher), 200);
        assertThat(originalNow.get("versions")).hasSize(1);
        assertThat(originalNow.get("versions").get(0).get("prompt").asString()).isEqualTo("Which is bigger?");

        // A published original: its published version is copied, and the copy is still a draft.
        json(post("/api/v1/studio/question-versions/" + originalVersion + "/publish", "", teacher), 200);
        JsonNode fromPublished = json(post("/api/v1/studio/questions/" + originalId + "/duplicate", "", teacher), 201);
        assertThat(fromPublished.get("versions").get(0).get("status").asString()).isEqualTo("DRAFT");
        assertThat(fromPublished.get("versions").get(0).get("prompt").asString()).isEqualTo("Which is bigger?");

        // The bank shows a reused question's published wording, which a newer draft doesn't change.
        String newer = json(post("/api/v1/studio/questions/" + originalId + "/draft", "", teacher), 200).get("versions").get(1).get("id").asString();
        json(put("/api/v1/studio/question-versions/" + newer, "{\"prompt\":\"Which is the bigger number?\",\"difficultyLevel\":2,"
                + "\"options\":[{\"label\":\"7\",\"correct\":true},{\"label\":\"4\",\"correct\":false}]}", teacher), 200);
        JsonNode bank = json(get("/api/v1/studio/questions?subjectId=" + subjectId + "&live=true", teacher), 200);
        JsonNode listed = null;
        for (JsonNode q : bank.get("items")) {
            if (q.get("id").asString().equals(originalId)) {
                listed = q;
            }
        }
        assertThat(listed).isNotNull();
        assertThat(listed.get("latestPrompt").asString()).isEqualTo("Which is the bigger number?");
        assertThat(listed.get("publishedPrompt").asString()).isEqualTo("Which is bigger?");
        assertThat(listed.get("publishedVersionId").asString()).isEqualTo(originalVersion);

        // An administrator's copy of a teacher's question stays the teacher's (its media are the teacher's files).
        JsonNode adminCopy = json(post("/api/v1/studio/questions/" + originalId + "/duplicate", "", admin), 201);
        assertThat(jdbc.queryForObject("select owner_account_id from questions where id = ?::uuid", UUID.class,
                UUID.fromString(adminCopy.get("id").asString()))).isEqualTo(teacher.id());

        // Someone who may not edit the question can't copy it: another teacher, a parent; nor an unknown id.
        Staff otherTeacher = staff(PlatformRole.TEACHER);
        assertThat(post("/api/v1/studio/questions/" + originalId + "/duplicate", "", otherTeacher).statusCode()).isIn(403, 404);
        assertThat(post("/api/v1/studio/questions/" + originalId + "/duplicate", "", staff(PlatformRole.PARENT)).statusCode()).isEqualTo(403);
        assertThat(post("/api/v1/studio/questions/" + UUID.randomUUID() + "/duplicate", "", teacher).statusCode()).isEqualTo(404);
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action = 'QUESTION_DUPLICATED' "
                + "and metadata ->> 'sourceQuestionId' = ?", Integer.class, originalId)).isEqualTo(3);
    }

    /** An activity's copy: the same lesson, settings, introduction and questions (reused, at their current version). */
    @Test
    void aTeacherCopiesAnActivityIntoTheSameLessonWithItsSettingsAndQuestionsAtTheirCurrentVersion() throws Exception {
        Staff admin = staff(PlatformRole.ADMIN);
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String subjectId = json(post("/api/v1/studio/subjects", "{\"code\":\"ACT_" + suffix + "\",\"name\":\"Act " + suffix + "\"}",
                admin), 201).get("id").asString();
        String skillId = json(post("/api/v1/studio/subjects/" + subjectId + "/skills", "{\"code\":\"ADD_" + suffix
                + "\",\"name\":\"Add within 10\",\"description\":\"Adds small numbers.\"}", admin), 201).get("id").asString();
        Staff teacher = staff(PlatformRole.TEACHER);
        String first = question(teacher, subjectId, skillId, "2 + 3 = ?", "5");
        String second = question(teacher, subjectId, skillId, "4 + 4 = ?", "8");
        String firstV1 = json(get("/api/v1/studio/questions/" + first, teacher), 200).get("versions").get(0).get("id").asString();
        String secondV1 = json(get("/api/v1/studio/questions/" + second, teacher), 200).get("versions").get(0).get("id").asString();
        json(post("/api/v1/studio/question-versions/" + firstV1 + "/publish", "", teacher), 200);
        json(post("/api/v1/studio/question-versions/" + secondV1 + "/publish", "", teacher), 200);
        String lessonId = json(post("/api/v1/studio/lessons", "{\"subjectId\":\"" + subjectId + "\",\"title\":\"Adding\"}", teacher), 201)
                .get("id").asString();
        JsonNode activity = json(post("/api/v1/studio/lessons/" + lessonId + "/activities",
                "{\"title\":\"Adding practice A\",\"skillIds\":[\"" + skillId + "\"]}", teacher), 201);
        String activityId = activity.get("id").asString();
        String versionId = activity.get("versions").get(0).get("id").asString();
        json(put("/api/v1/studio/activity-versions/" + versionId, "{\"instructions\":\"Add the numbers.\",\"difficultyLevel\":2,"
                + "\"estimatedDurationSeconds\":120}", teacher), 200);
        json(put("/api/v1/studio/activity-versions/" + versionId + "/introduction", "{\"text\":\"Adding puts two groups together.\"}",
                teacher), 200);
        json(put("/api/v1/studio/activity-versions/" + versionId + "/items", "{\"items\":[{\"questionVersionId\":\"" + firstV1
                + "\",\"points\":2},{\"questionVersionId\":\"" + secondV1 + "\"}]}", teacher), 200);
        // The first question gets a newer published version after the activity was built.
        String firstV2 = json(post("/api/v1/studio/questions/" + first + "/draft", "", teacher), 200).get("versions").get(1).get("id").asString();
        json(put("/api/v1/studio/question-versions/" + firstV2, "{\"prompt\":\"2 + 3 = ? (count on)\",\"correctAnswer\":\"5\","
                + "\"difficultyLevel\":1}", teacher), 200);
        json(post("/api/v1/studio/question-versions/" + firstV2 + "/publish", "", teacher), 200);

        JsonNode copy = json(post("/api/v1/studio/activities/" + activityId + "/duplicate", "{\"title\":\"Adding practice B\"}", teacher), 201);
        assertThat(copy.get("id").asString()).isNotEqualTo(activityId);
        assertThat(copy.get("title").asString()).isEqualTo("Adding practice B");
        assertThat(copy.get("lessonId").asString()).isEqualTo(lessonId);
        JsonNode draft = copy.get("versions").get(0);
        assertThat(copy.get("versions")).hasSize(1);
        assertThat(draft.get("status").asString()).isEqualTo("DRAFT");
        assertThat(draft.get("instructions").asString()).isEqualTo("Add the numbers.");
        assertThat(draft.get("difficultyLevel").asInt()).isEqualTo(2);
        assertThat(draft.get("estimatedDurationSeconds").asInt()).isEqualTo(120);
        assertThat(draft.get("skillIds").get(0).asString()).isEqualTo(skillId);
        assertThat(draft.get("introduction").toString()).contains("Adding puts two groups together.");
        JsonNode items = draft.get("items");
        assertThat(items).hasSize(2);
        assertThat(items.get(0).get("questionVersionId").asString()).as("taken at its newer version").isEqualTo(firstV2);
        assertThat(items.get(0).get("points").asDouble()).isEqualTo(2.0);
        assertThat(items.get(1).get("questionVersionId").asString()).isEqualTo(secondV1);
        // Questions are reused, not copied; the original activity is unchanged.
        assertThat(jdbc.queryForObject("select count(*) from questions where subject_id = ?::uuid", Integer.class,
                UUID.fromString(subjectId))).isEqualTo(2);
        JsonNode originalNow = json(get("/api/v1/studio/activities/" + activityId, teacher), 200);
        assertThat(originalNow.get("title").asString()).isEqualTo("Adding practice A");
        assertThat(originalNow.get("versions").get(0).get("items").get(0).get("questionVersionId").asString()).isEqualTo(firstV1);

        // Refusals: no title, another teacher, a parent; audited with how many questions moved to a newer version.
        json(post("/api/v1/studio/activities/" + activityId + "/duplicate", "{\"title\":\" \"}", teacher), 400);
        assertThat(post("/api/v1/studio/activities/" + activityId + "/duplicate", "{\"title\":\"X\"}", staff(PlatformRole.TEACHER))
                .statusCode()).isIn(403, 404);
        assertThat(post("/api/v1/studio/activities/" + activityId + "/duplicate", "{\"title\":\"X\"}", staff(PlatformRole.PARENT))
                .statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("select metadata ->> 'atNewerVersion' from audit_events where action = 'ACTIVITY_DUPLICATED' "
                + "and resource_id = ?::uuid", String.class, UUID.fromString(copy.get("id").asString()))).isEqualTo("1");
    }

    private String question(Staff author, String subjectId, String skillId, String prompt, String answer) throws Exception {
        return json(post("/api/v1/studio/questions", "{\"subjectId\":\"" + subjectId + "\",\"questionType\":\"NUMERIC\",\"prompt\":\""
                + prompt + "\",\"correctAnswer\":\"" + answer + "\",\"difficultyLevel\":1,\"skillIds\":[\"" + skillId + "\"]}", author), 201)
                .get("id").asString();
    }

    // ------------------------------------------------------------------ accounts and HTTP

    private record Staff(UUID id, String token) {
    }

    private Staff staff(PlatformRole role) throws Exception {
        String email = "duplicate-" + role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        Account account = accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), role));
        return new Staff(account.getId(), json(post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\""
                + PASSWORD + "\"}", null), 200).get("accessToken").asString());
    }

    private static JsonNode json(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> get(String path, Staff staff) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).GET(), staff);
    }

    private HttpResponse<String> post(String path, String body, Staff staff) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)), staff);
    }

    private HttpResponse<String> put(String path, String body, Staff staff) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body)), staff);
    }

    private HttpResponse<String> send(HttpRequest.Builder request, Staff staff) throws Exception {
        if (staff != null) {
            request.header("Authorization", "Bearer " + staff.token());
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
