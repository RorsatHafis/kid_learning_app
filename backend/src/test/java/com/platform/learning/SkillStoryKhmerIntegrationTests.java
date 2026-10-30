package com.platform.learning;

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
import org.springframework.security.crypto.password.PasswordEncoder;
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
 * LOCALIZATION.md: a skill explained to a family (X19) names its activities and curricula the way the path does - in
 * Khmer for a Khmer reader where their Khmer is written (an activity's when complete), as written otherwise.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class SkillStoryKhmerIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Skill-Story-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AgeHubRepository ageHubRepository;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void aSkillsStoryNamesItsActivitiesAndCurriculaInTheReadersLanguage() throws Exception {
        String admin = admin();
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String subjectId = json(post("/api/v1/studio/subjects", "{\"code\":\"ST_" + suffix + "\",\"name\":\"Story " + suffix + "\"}",
                admin, null), 201).get("id").asString();
        String skillId = json(post("/api/v1/studio/subjects/" + subjectId + "/skills", "{\"code\":\"STS_" + suffix
                + "\",\"name\":\"Counting\",\"khmer\":{\"name\":\"ការរាប់\"}}", admin, null), 201).get("id").asString();
        String objectiveId = json(post("/api/v1/studio/subjects/" + subjectId + "/objectives", "{\"code\":\"STO_" + suffix
                + "\",\"title\":\"Count\"}", admin, null), 201).get("id").asString();
        json(post("/api/v1/studio/objectives/" + objectiveId + "/skills/" + skillId, "", admin, null), 200);
        String questionVersionId = json(post("/api/v1/studio/questions", "{\"subjectId\":\"" + subjectId
                + "\",\"questionType\":\"MULTIPLE_CHOICE\",\"prompt\":\"How many?\",\"difficultyLevel\":1,"
                + "\"options\":[{\"label\":\"three\",\"correct\":true},{\"label\":\"two\",\"correct\":false}],\"skillIds\":[\"" + skillId
                + "\"]}", admin, null), 201).get("versions").get(0).get("id").asString();
        json(post("/api/v1/studio/question-versions/" + questionVersionId + "/publish", "", admin, null), 200);
        String lessonId = json(post("/api/v1/studio/lessons", "{\"subjectId\":\"" + subjectId + "\",\"title\":\"Count\"}", admin, null), 201)
                .get("id").asString();
        String activityVersionId = json(post("/api/v1/studio/lessons/" + lessonId + "/activities",
                "{\"title\":\"Count the ducks\",\"skillIds\":[\"" + skillId + "\"]}", admin, null), 201).get("versions").get(0).get("id").asString();
        json(put("/api/v1/studio/activity-versions/" + activityVersionId + "/items",
                "{\"items\":[{\"questionVersionId\":\"" + questionVersionId + "\"}]}", admin, null), 200);
        // Its Khmer is complete: the name is its only word the English has.
        json(put("/api/v1/studio/activity-versions/" + activityVersionId, "{\"difficultyLevel\":1,\"khmer\":{\"title\":\"រាប់ទា\"}}",
                admin, null), 200);
        json(post("/api/v1/studio/activity-versions/" + activityVersionId + "/publish", "", admin, null), 200);
        String ageHubId = ageHubRepository.save(AgeHub.create("ST_" + suffix, "Story " + suffix, 90, 99, 99)).getId().toString();
        JsonNode curriculum = json(post("/api/v1/studio/curricula", "{\"subjectId\":\"" + subjectId + "\",\"ageHubId\":\"" + ageHubId
                + "\",\"name\":\"Counting " + suffix + "\",\"khmer\":{\"name\":\"ការរាប់ " + suffix + "\"}}", admin, null), 201);
        String curriculumVersion = curriculum.get("versions").get(0).get("id").asString();
        json(put("/api/v1/studio/curriculum-versions/" + curriculumVersion + "/objectives", "{\"objectiveIds\":[\"" + objectiveId + "\"]}",
                admin, null), 200);
        json(post("/api/v1/studio/curriculum-versions/" + curriculumVersion + "/publish", "", admin, null), 200);

        String parent = json(post("/api/v1/auth/register", "{\"email\":\"story-parent-" + UUID.randomUUID() + "@example.com\",\"password\":\""
                + PASSWORD + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null, null), 201).get("accessToken").asString();
        String childId = json(post("/api/v1/children", "{\"displayName\":\"Dara\",\"dateOfBirth\":\"" + LocalDate.now().minusYears(6) + "\"}",
                parent, null), 201).get("id").asString();
        json(post("/api/v1/children/" + childId + "/enrollments", "{\"curriculumId\":\"" + curriculum.get("id").asString() + "\"}",
                parent, null), 201);

        JsonNode km = json(get("/api/v1/children/" + childId + "/skills/" + skillId, parent, "km"), 200);
        JsonNode waitingKm = waiting(km, "រាប់ទា");
        assertThat(waitingKm.get("curriculumName").asString()).isEqualTo("ការរាប់ " + suffix);
        JsonNode en = json(get("/api/v1/children/" + childId + "/skills/" + skillId, parent, "en"), 200);
        JsonNode waitingEn = waiting(en, "Count the ducks");
        assertThat(waitingEn.get("curriculumName").asString()).isEqualTo("Counting " + suffix);
    }

    private static JsonNode waiting(JsonNode story, String title) {
        for (JsonNode step : story.get("waiting")) {
            if (title.equals(step.get("activityTitle").asString())) {
                return step;
            }
        }
        throw new AssertionError("no waiting step named " + title + " in " + story.get("waiting"));
    }

    // ------------------------------------------------------------------ helpers

    private String admin() throws Exception {
        String email = "story-admin-" + UUID.randomUUID() + "@example.com";
        accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), PlatformRole.ADMIN));
        return json(post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", null, null), 200)
                .get("accessToken").asString();
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
