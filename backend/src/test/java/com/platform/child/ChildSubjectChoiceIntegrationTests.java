package com.platform.child;

import com.platform.TestcontainersConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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
 * A family that chooses its child's subjects (the parent's setup flow): the child is created with no
 * learning path chosen for it, even when the catalog has one for the child's age, and then follows
 * exactly the curricula the family enrols it in - through the same enrolment rules as ever.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class ChildSubjectChoiceIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Subject-Test-Lotus-Pond-2026";

    @Value("${local.server.port}") private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void aFamilyThatChoosesGetsNothingChosenForItAndThenExactlyWhatItChose() throws Exception {
        String token = register();
        JsonNode child = json(post("/api/v1/children", "{\"displayName\":\"Sokha\",\"dateOfBirth\":\""
                + LocalDate.now().minusYears(6) + "\",\"familyChoosesSubjects\":true}", token), 201);
        String childId = child.get("id").asString();

        assertThat(json(get("/api/v1/children/" + childId + "/enrollments", token), 200).size())
                .as("no path is started for a family that chooses").isZero();

        JsonNode catalog = json(get("/api/v1/children/" + childId + "/curricula", token), 200);
        List<String> forAge = new ArrayList<>();
        catalog.forEach(c -> {
            if (c.get("forChildAge").asBoolean()) {
                forAge.add(c.get("id").asString());
            }
        });
        assertThat(forAge).as("the seeded catalog offers this age something to choose").isNotEmpty();

        String chosen = forAge.getFirst();
        json(post("/api/v1/children/" + childId + "/enrollments", "{\"curriculumId\":\"" + chosen + "\"}", token), 201);
        JsonNode plan = json(get("/api/v1/children/" + childId + "/enrollments", token), 200);
        assertThat(plan.size()).isEqualTo(1);
        assertThat(plan.get(0).get("curriculum").get("id").asString()).isEqualTo(chosen);

        // Choosing the same subject again changes nothing.
        json(post("/api/v1/children/" + childId + "/enrollments", "{\"curriculumId\":\"" + chosen + "\"}", token), 201);
        assertThat(json(get("/api/v1/children/" + childId + "/enrollments", token), 200).size()).isEqualTo(1);

        // Another family can neither see this child's plan nor add to it.
        String stranger = register();
        assertThat(get("/api/v1/children/" + childId + "/enrollments", stranger).statusCode()).isIn(403, 404);
        assertThat(post("/api/v1/children/" + childId + "/enrollments", "{\"curriculumId\":\"" + chosen + "\"}", stranger)
                .statusCode()).isIn(403, 404);
    }

    @Test
    void theFieldIsOptionalAndFalseMeansWhatItAlwaysMeant() throws Exception {
        String token = register();
        // Absent or false: created as before (Battle 2B decides whether a path is started).
        assertThat(post("/api/v1/children", "{\"displayName\":\"Dara\",\"dateOfBirth\":\""
                + LocalDate.now().minusYears(6) + "\"}", token).statusCode()).isEqualTo(201);
        assertThat(post("/api/v1/children", "{\"displayName\":\"Vanna\",\"dateOfBirth\":\""
                + LocalDate.now().minusYears(6) + "\",\"familyChoosesSubjects\":false}", token).statusCode()).isEqualTo(201);
    }

    private String register() throws Exception {
        String email = "subjects-" + UUID.randomUUID() + "@example.com";
        return json(post("/api/v1/auth/register", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null), 201).get("accessToken").asString();
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private HttpResponse<String> send(HttpRequest.Builder request, String token) throws Exception {
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).GET(), token);
    }

    private HttpResponse<String> post(String path, String body, String token) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)), token);
    }

    private static JsonNode json(HttpResponse<String> response, int status) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        return JSON.readTree(response.body());
    }

}
