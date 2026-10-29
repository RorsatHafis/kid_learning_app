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
 * LOCALIZATION.md "Content in two languages": a question's English and Khmer are saved together and independently,
 * kept through new drafts and copies, previewed per language, and a child reading Khmer gets the question - and its
 * explanation and right answer - in Khmer when the Khmer is complete, in English otherwise.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class BilingualQuestionIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Bilingual-Test-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AgeHubRepository ageHubRepository;
    @Autowired private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    private static final String KHMER = ",\"khmer\":{\"prompt\":\"តើលេខមួយណាធំជាង?\",\"hint\":\"រាប់ពី 4 ឡើងទៅ។\","
            + "\"explanation\":\"7 មកក្រោយ 4 ពេលយើងរាប់។\",\"options\":[\"ប្រាំពីរ\",\"បួន\"]}";

    @Test
    void bothLanguagesAreSavedApartKeptThroughCopiesAndAChildReadingKhmerGetsKhmer() throws Exception {
        String admin = staff(PlatformRole.ADMIN);
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String subjectId = json(post("/api/v1/studio/subjects", "{\"code\":\"KM_" + suffix + "\",\"name\":\"Bilingual " + suffix + "\"}",
                admin, null), 201).get("id").asString();
        String skillId = json(post("/api/v1/studio/subjects/" + subjectId + "/skills", "{\"code\":\"BIG_" + suffix
                + "\",\"name\":\"Compare\",\"description\":\"Which is bigger.\"}", admin, null), 201).get("id").asString();
        String objectiveId = json(post("/api/v1/studio/subjects/" + subjectId + "/objectives", "{\"code\":\"OBJ_" + suffix
                + "\",\"title\":\"Comparing\"}", admin, null), 201).get("id").asString();
        json(post("/api/v1/studio/objectives/" + objectiveId + "/skills/" + skillId, "", admin, null), 200);
        String ageHubId = ageHubRepository.save(AgeHub.create("KM_" + suffix, "Bilingual " + suffix, 90, 99, 99)).getId().toString();

        // English and Khmer in one save.
        JsonNode created = json(post("/api/v1/studio/questions", "{\"subjectId\":\"" + subjectId + "\",\"questionType\":\"MULTIPLE_CHOICE\","
                + "\"prompt\":\"Which is bigger?\",\"difficultyLevel\":1,\"hint\":\"Count up from 4.\",\"explanation\":\"7 comes after 4.\","
                + "\"options\":[{\"label\":\"seven\",\"correct\":true},{\"label\":\"four\",\"correct\":false}],\"skillIds\":[\"" + skillId + "\"]"
                + KHMER + "}", admin, null), 201);
        String questionId = created.get("id").asString();
        JsonNode version = created.get("versions").get(0);
        String versionId = version.get("id").asString();
        assertThat(version.get("prompt").asString()).isEqualTo("Which is bigger?");
        JsonNode km = version.get("khmer");
        assertThat(km.get("prompt").asString()).isEqualTo("តើលេខមួយណាធំជាង?");
        assertThat(km.get("hint").asString()).isEqualTo("រាប់ពី 4 ឡើងទៅ។");
        assertThat(km.get("explanation").asString()).isEqualTo("7 មកក្រោយ 4 ពេលយើងរាប់។");
        assertThat(km.get("complete").asBoolean()).isTrue();
        assertThat(km.get("translatable").asBoolean()).isTrue();
        String sevenId = version.get("options").get(0).get("id").asString();
        assertThat(km.get("options").get(sevenId).asString()).isEqualTo("ប្រាំពីរ");

        // Editing the English with the Khmer as it is keeps both; the Khmer never replaces the English.
        JsonNode revised = json(put("/api/v1/studio/question-versions/" + versionId, "{\"prompt\":\"Which number is bigger?\","
                + "\"difficultyLevel\":1,\"hint\":\"Count up from 4.\",\"explanation\":\"7 comes after 4.\","
                + "\"options\":[{\"label\":\"seven\",\"correct\":true},{\"label\":\"four\",\"correct\":false}]" + KHMER + "}", admin, null), 200)
                .get("versions").get(0);
        assertThat(revised.get("prompt").asString()).isEqualTo("Which number is bigger?");
        assertThat(revised.get("khmer").get("prompt").asString()).isEqualTo("តើលេខមួយណាធំជាង?");
        assertThat(revised.get("khmer").get("complete").asBoolean()).isTrue();
        assertThat(revised.get("khmer").get("options").get(revised.get("options").get(1).get("id").asString()).asString()).isEqualTo("បួន");

        // Preview per language: Khmer when asked for and complete; English as written otherwise.
        JsonNode previewKm = json(get("/api/v1/studio/question-versions/" + versionId + "/preview?language=km", admin, null), 200);
        assertThat(previewKm.get("language").asString()).isEqualTo("km");
        assertThat(previewKm.get("prompt").asString()).isEqualTo("តើលេខមួយណាធំជាង?");
        assertThat(previewKm.get("hint").asString()).isEqualTo("រាប់ពី 4 ឡើងទៅ។");
        assertThat(labels(previewKm)).containsExactly("ប្រាំពីរ", "បួន");
        JsonNode previewEn = json(get("/api/v1/studio/question-versions/" + versionId + "/preview?language=en", admin, null), 200);
        assertThat(previewEn.get("language").isNull()).isTrue();
        assertThat(previewEn.get("prompt").asString()).isEqualTo("Which number is bigger?");
        assertThat(labels(previewEn)).containsExactly("seven", "four");
        assertThat(codes(json(get("/api/v1/studio/question-versions/" + versionId + "/validation", admin, null), 200).get("warnings")))
                .doesNotContain("KHMER_INCOMPLETE");

        // Publish, then a new draft carries the Khmer to its new options.
        json(post("/api/v1/studio/question-versions/" + versionId + "/publish", "", admin, null), 200);
        JsonNode opened = json(post("/api/v1/studio/questions/" + questionId + "/draft", "", admin, null), 200).get("versions").get(1);
        assertThat(opened.get("khmer").get("prompt").asString()).isEqualTo("តើលេខមួយណាធំជាង?");
        assertThat(opened.get("khmer").get("options").get(opened.get("options").get(0).get("id").asString()).asString()).isEqualTo("ប្រាំពីរ");
        assertThat(opened.get("khmer").get("complete").asBoolean()).isTrue();
        // ...and so does a copy of the question.
        JsonNode copy = json(post("/api/v1/studio/questions/" + questionId + "/duplicate", "", admin, null), 201).get("versions").get(0);
        assertThat(copy.get("khmer").get("options").get(copy.get("options").get(1).get("id").asString()).asString()).isEqualTo("បួន");

        // A Khmer missing a choice: the draft is incomplete, says so, and is previewed in English.
        String openedId = opened.get("id").asString();
        JsonNode partial = json(put("/api/v1/studio/question-versions/" + openedId, "{\"prompt\":\"Which number is bigger?\","
                + "\"difficultyLevel\":1,\"hint\":\"Count up from 4.\",\"explanation\":\"7 comes after 4.\","
                + "\"options\":[{\"label\":\"seven\",\"correct\":true},{\"label\":\"four\",\"correct\":false}],"
                + "\"khmer\":{\"prompt\":\"តើលេខមួយណាធំជាង?\",\"hint\":\"រាប់ពី 4 ឡើងទៅ។\",\"explanation\":\"7 មកក្រោយ 4។\","
                + "\"options\":[\"ប្រាំពីរ\",\" \"]}}", admin, null), 200).get("versions").get(1);
        assertThat(partial.get("khmer").get("complete").asBoolean()).isFalse();
        assertThat(codes(json(get("/api/v1/studio/question-versions/" + openedId + "/validation", admin, null), 200).get("warnings")))
                .contains("KHMER_INCOMPLETE");
        assertThat(json(get("/api/v1/studio/question-versions/" + openedId + "/preview?language=km", admin, null), 200)
                .get("language").isNull()).isTrue();

        // A short answer is scored on the author's words: it keeps no Khmer.
        JsonNode word = json(post("/api/v1/studio/questions", "{\"subjectId\":\"" + subjectId + "\",\"questionType\":\"SHORT_ANSWER\","
                + "\"prompt\":\"What says meow?\",\"correctAnswer\":\"cat\",\"difficultyLevel\":1,\"skillIds\":[\"" + skillId + "\"],"
                + "\"khmer\":{\"prompt\":\"តើអ្វីនិយាយថា មេអ៊ូ?\"}}", admin, null), 201).get("versions").get(0).get("khmer");
        assertThat(word.get("translatable").asBoolean()).isFalse();
        assertThat(word.get("prompt").isNull()).isTrue();

        // A child: the published version (complete Khmer) in an activity, on a curriculum.
        String lessonId = json(post("/api/v1/studio/lessons", "{\"subjectId\":\"" + subjectId + "\",\"title\":\"Bigger\"}", admin, null), 201)
                .get("id").asString();
        JsonNode activity = json(post("/api/v1/studio/lessons/" + lessonId + "/activities",
                "{\"title\":\"Which is bigger\",\"skillIds\":[\"" + skillId + "\"]}", admin, null), 201);
        String activityVersionId = activity.get("versions").get(0).get("id").asString();
        json(put("/api/v1/studio/activity-versions/" + activityVersionId + "/items",
                "{\"items\":[{\"questionVersionId\":\"" + versionId + "\"}]}", admin, null), 200);
        // The activity's own words in both languages: details (name, instructions) and its introduction, saved apart.
        JsonNode details = json(put("/api/v1/studio/activity-versions/" + activityVersionId, "{\"instructions\":\"Choose the bigger number.\","
                + "\"difficultyLevel\":1,\"khmer\":{\"title\":\"តើមួយណាធំជាង\",\"instructions\":\"ជ្រើសលេខដែលធំជាង។\"}}", admin, null), 200);
        JsonNode activityKm = details.get("versions").get(0).get("khmer");
        assertThat(activityKm.get("title").asString()).isEqualTo("តើមួយណាធំជាង");
        assertThat(activityKm.get("complete").asBoolean()).isTrue();
        JsonNode withIntro = json(put("/api/v1/studio/activity-versions/" + activityVersionId + "/introduction",
                "{\"text\":\"Bigger means more.\",\"khmer\":{\"text\":\"ធំជាង មានន័យថាច្រើនជាង។\"}}", admin, null), 200);
        assertThat(withIntro.get("versions").get(0).get("khmer").get("introduction").asString()).isEqualTo("ធំជាង មានន័យថាច្រើនជាង។");
        assertThat(withIntro.get("versions").get(0).get("khmer").get("instructions").asString()).as("the other save left it").isEqualTo("ជ្រើសលេខដែលធំជាង។");
        assertThat(withIntro.get("versions").get(0).get("instructions").asString()).isEqualTo("Choose the bigger number.");
        json(post("/api/v1/studio/activity-versions/" + activityVersionId + "/publish", "", admin, null), 200);
        JsonNode curriculum = json(post("/api/v1/studio/curricula", "{\"subjectId\":\"" + subjectId + "\",\"ageHubId\":\"" + ageHubId
                + "\",\"name\":\"Bilingual " + suffix + "\"}", admin, null), 201);
        String curriculumVersion = curriculum.get("versions").get(0).get("id").asString();
        json(put("/api/v1/studio/curriculum-versions/" + curriculumVersion + "/objectives", "{\"objectiveIds\":[\"" + objectiveId + "\"]}",
                admin, null), 200);
        json(post("/api/v1/studio/curriculum-versions/" + curriculumVersion + "/publish", "", admin, null), 200);
        String parent = registerParent();
        String childId = json(post("/api/v1/children", "{\"displayName\":\"Dara\",\"dateOfBirth\":\"" + LocalDate.now().minusYears(6) + "\"}",
                parent, null), 201).get("id").asString();
        String enrollmentId = json(post("/api/v1/children/" + childId + "/enrollments",
                "{\"curriculumId\":\"" + curriculum.get("id").asString() + "\"}", parent, null), 201).get("id").asString();
        JsonNode step = json(get("/api/v1/enrollments/" + enrollmentId + "/journey", parent, null), 200).get("steps").get(0);

        JsonNode contentKm = json(get("/api/v1/activity-versions/" + activityVersionId + "?childId=" + childId, parent, "km"), 200);
        assertThat(contentKm.get("title").asString()).isEqualTo("តើមួយណាធំជាង");
        assertThat(contentKm.get("instructions").asString()).isEqualTo("ជ្រើសលេខដែលធំជាង។");
        assertThat(contentKm.get("introduction").get("text").asString()).isEqualTo("ធំជាង មានន័យថាច្រើនជាង។");
        JsonNode contentEn = json(get("/api/v1/activity-versions/" + activityVersionId + "?childId=" + childId, parent, "en"), 200);
        assertThat(contentEn.get("title").asString()).isEqualTo("Which is bigger");
        assertThat(contentEn.get("introduction").get("text").asString()).isEqualTo("Bigger means more.");
        JsonNode itemKm = contentKm.get("items").get(0);
        // The child's path names the activity in the same language.
        assertThat(json(get("/api/v1/enrollments/" + enrollmentId + "/journey", parent, "km"), 200).get("steps").get(0).get("title")
                .asString()).isEqualTo("តើមួយណាធំជាង");
        assertThat(json(get("/api/v1/enrollments/" + enrollmentId + "/journey", parent, "en"), 200).get("steps").get(0).get("title")
                .asString()).isEqualTo("Which is bigger");
        assertThat(itemKm.get("language").asString()).isEqualTo("km");
        assertThat(itemKm.get("prompt").asString()).isEqualTo("តើលេខមួយណាធំជាង?");
        assertThat(labels(itemKm)).containsExactly("ប្រាំពីរ", "បួន");
        assertThat(itemKm.toString()).doesNotContain("correct");
        JsonNode itemEn = json(get("/api/v1/activity-versions/" + activityVersionId + "?childId=" + childId, parent, "en"), 200)
                .get("items").get(0);
        assertThat(itemEn.get("language").isNull()).isTrue();
        assertThat(itemEn.get("prompt").asString()).isEqualTo("Which number is bigger?");

        // Answering wrong in Khmer: the explanation and the right answer come in Khmer; the score is the same.
        String attemptId = json(post("/api/v1/children/" + childId + "/activity-attempts", "{\"activityVersionId\":\"" + activityVersionId
                + "\",\"learningPathItemId\":\"" + step.get("learningPathItemId").asString() + "\"}", parent, "km"), 201).get("id").asString();
        String fourId = itemKm.get("options").get(1).get("id").asString();
        JsonNode answer = json(post("/api/v1/activity-attempts/" + attemptId + "/answers", "{\"questionVersionId\":\"" + versionId
                + "\",\"submittedAnswer\":\"" + fourId + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", parent, "km"), 201);
        assertThat(answer.get("correct").asBoolean()).isFalse();
        assertThat(answer.get("explanation").asString()).isEqualTo("7 មកក្រោយ 4 ពេលយើងរាប់។");
        assertThat(answer.get("correctAnswer").get("text").asString()).isEqualTo("ប្រាំពីរ");
        assertThat(jdbc.queryForObject("select count(*) from content_localizations where content_id = ?::uuid and locale = 'km'",
                Integer.class, UUID.fromString(versionId))).as("prompt, hint, explanation and two choices").isEqualTo(5);

        // A new version of the activity keeps its Khmer.
        String activityId = activity.get("id").asString();
        JsonNode next = json(post("/api/v1/studio/activities/" + activityId + "/draft", "", admin, null), 200).get("versions").get(1);
        assertThat(next.get("khmer").get("title").asString()).isEqualTo("តើមួយណាធំជាង");
        assertThat(next.get("khmer").get("introduction").asString()).isEqualTo("ធំជាង មានន័យថាច្រើនជាង។");
    }

    // ------------------------------------------------------------------ helpers

    private static List<String> labels(JsonNode item) {
        List<String> labels = new ArrayList<>();
        item.get("options").forEach(o -> labels.add(o.get("label").asString()));
        return labels;
    }

    private static List<String> codes(JsonNode issues) {
        List<String> codes = new ArrayList<>();
        issues.forEach(i -> codes.add(i.get("code").asString()));
        return codes;
    }

    private String staff(PlatformRole role) throws Exception {
        String email = "bilingual-" + role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), role));
        return json(post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", null, null), 200)
                .get("accessToken").asString();
    }

    private String registerParent() throws Exception {
        return json(post("/api/v1/auth/register", "{\"email\":\"bilingual-parent-" + UUID.randomUUID() + "@example.com\",\"password\":\""
                + PASSWORD + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null, null), 201).get("accessToken").asString();
    }

    private static JsonNode json(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> get(String path, String bearer, String language) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).GET(), bearer, language);
    }

    private HttpResponse<String> post(String path, String body, String bearer, String language) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)), bearer, language);
    }

    private HttpResponse<String> put(String path, String body, String bearer, String language) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body)), bearer, language);
    }

    private HttpResponse<String> send(HttpRequest.Builder request, String bearer, String language) throws Exception {
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        if (language != null) {
            request.header("Accept-Language", language);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
