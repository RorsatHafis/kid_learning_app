package com.platform.learning;

import com.platform.TestcontainersConfiguration;
import com.platform.curriculum.entity.AgeHub;
import com.platform.curriculum.repository.AgeHubRepository;
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
 * A family path's fit with the child's age now, over real HTTP: a path for another age group says so and names the
 * published platform path in the same subject for the child's age; once the child is on that one, it fits and the
 * other no longer suggests it. Nothing changes on its own. (The age groups are for ages 110-119, which no other test
 * uses, so the age rule finds exactly these; each run has its own subject.)
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class AgeFitIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Age-Fit-Test-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private AgeHubRepository ageHubRepository;
    @Autowired private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void aPathForAnotherAgeSaysSoAndNamesThePathForTheChildsAge() throws Exception {
        int base = 110;
        String tag = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        UUID younger = hub("FIT_YOUNGER", base, base + 4);
        UUID older = hub("FIT_OLDER", base + 5, base + 9);
        UUID subject = UUID.randomUUID();
        jdbc.update("insert into subjects (id, code, name, status) values (?, ?, ?, 'ACTIVE')", subject, "FIT_" + tag, "Fit " + tag);
        UUID youngerPath = curriculum(subject, younger, "Fit Younger " + tag);

        String parent = json(send("POST", "/api/v1/auth/register", "{\"email\":\"fit-" + UUID.randomUUID() + "@example.com\",\"password\":\""
                + PASSWORD + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null), 201).get("accessToken").asString();
        String child = json(send("POST", "/api/v1/children", "{\"displayName\":\"Dara\",\"dateOfBirth\":\""
                + LocalDate.now().minusYears(base + 6).minusDays(1) + "\"}", parent), 201).get("id").asString();
        // The path for the child's age is published after the child was added, so automatic enrolment (which takes the
        // only path for a new child's age) hasn't already put the child on it.
        UUID olderPath = curriculum(subject, older, "Fit Older " + tag);
        json(send("POST", "/api/v1/children/" + child + "/enrollments", "{\"curriculumId\":\"" + youngerPath + "\"}", parent), 201);

        JsonNode fit = pathFit(parent, child, youngerPath);
        assertThat(fit.get("forChildAge").asBoolean()).isFalse();
        assertThat(fit.get("childAge").asInt()).isEqualTo(base + 6);
        assertThat(fit.get("minAge").asInt()).isEqualTo(base);
        assertThat(fit.get("maxAge").asInt()).isEqualTo(base + 4);
        assertThat(fit.get("suggestedCurriculumId").asString()).as(fit.toString()).isEqualTo(olderPath.toString());
        assertThat(fit.get("suggestedCurriculumName").asString()).isEqualTo("Fit Older " + tag);

        // The family chooses the path for the child's age: it fits; the other stays, no longer suggesting it.
        json(send("POST", "/api/v1/children/" + child + "/enrollments", "{\"curriculumId\":\"" + olderPath + "\"}", parent), 201);
        assertThat(pathFit(parent, child, olderPath).get("forChildAge").asBoolean()).isTrue();
        JsonNode stillYounger = pathFit(parent, child, youngerPath);
        assertThat(stillYounger.get("forChildAge").asBoolean()).isFalse();
        assertThat(stillYounger.get("suggestedCurriculumId").isNull()).isTrue();
    }

    // ------------------------------------------------------------------ helpers

    /** Age groups for ages no other test uses (born after 1900, as a child's date of birth must be), made once. */
    private UUID hub(String code, int min, int max) {
        return ageHubRepository.findAll().stream().filter(h -> h.getCode().equals(code)).findFirst()
                .orElseGet(() -> ageHubRepository.save(AgeHub.create(code, code, min, max, 900 + min))).getId();
    }

    /** A platform curriculum with a published (empty) version, as the seed data writes them. */
    private UUID curriculum(UUID subject, UUID ageHub, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into curriculums (id, subject_id, age_hub_id, name, status) values (?, ?, ?, ?, 'ACTIVE')", id, subject, ageHub, name);
        jdbc.update("insert into curriculum_versions (id, curriculum_id, version_number, status, published_at) values (?, ?, 1, 'PUBLISHED', now())",
                UUID.randomUUID(), id);
        return id;
    }

    private JsonNode pathFit(String parent, String child, UUID curriculumId) throws Exception {
        for (JsonNode enrollment : json(send("GET", "/api/v1/children/" + child + "/enrollments", null, parent), 200)) {
            if (enrollment.get("curriculum").get("id").asString().equals(curriculumId.toString())) {
                return enrollment.get("ageFit");
            }
        }
        throw new AssertionError("not enrolled in " + curriculumId);
    }

    private static JsonNode json(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> send(String method, String path, String body, String bearer) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

}
