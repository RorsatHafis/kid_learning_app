package com.platform.child;

import com.platform.TestcontainersConfiguration;
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
 * M10b over real HTTP against PostgreSQL: a parent invites a trusted caregiver for one child. Only the invited address,
 * confirmed, accepts. "See progress" reads the child's learning as the parent's pages show it and nothing else;
 * "open the child's space" also plays; settings, time, subjects, devices, other caregivers, the sibling and the
 * family's lists stay out of reach; the family changes or ends it at once; a family has at most two caregivers.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class CaregiverIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Caregiver-Test-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    private record Person(String email, String token) {
    }

    @Test
    void aCaregiverSeesOrHelpsWithOneChildAndNothingElse() throws Exception {
        Person parent = register(true);
        String child = createChild(parent, "Dara");
        String sibling = createChild(parent, "Sokha");
        Person grandma = register(false);

        assertThat(send("GET", "/api/v1/children/" + child, null, grandma).statusCode()).as("not before").isEqualTo(404);
        assertThat(send("POST", "/api/v1/children/" + child + "/caregivers", invite(parent.email(), "VIEW_PROGRESS"), parent).statusCode())
                .as("yourself").isEqualTo(400);
        assertThat(send("POST", "/api/v1/children/" + child + "/caregivers", invite(grandma.email(), "EVERYTHING"), parent).statusCode())
                .isEqualTo(400);
        assertThat(send("POST", "/api/v1/children/" + child + "/caregivers", invite(grandma.email(), "VIEW_PROGRESS"), grandma)
                .statusCode()).as("not her family").isEqualTo(404);
        String invitation = json(send("POST", "/api/v1/children/" + child + "/caregivers",
                invite(grandma.email().toUpperCase(), "VIEW_PROGRESS"), parent), 201).get("id").asString();
        assertThat(send("POST", "/api/v1/children/" + child + "/caregivers", invite(grandma.email(), "OPEN_SPACE"), parent).statusCode())
                .as("already invited").isEqualTo(409);

        // Only the invited address, confirmed, accepts.
        JsonNode caring = json(send("GET", "/api/v1/caregiving", null, grandma), 200);
        assertThat(caring.get(0).get("childName").asString()).isEqualTo("Dara");
        assertThat(caring.get(0).get("status").asString()).isEqualTo("INVITED");
        Person stranger = register(true);
        assertThat(send("POST", "/api/v1/caregiving/" + invitation + "/accept", null, stranger).statusCode()).isEqualTo(404);
        assertThat(send("POST", "/api/v1/caregiving/" + invitation + "/accept", null, grandma).statusCode()).as("confirm first").isEqualTo(409);
        jdbc.update("update accounts set email_verified_at = now() where normalized_email = ?", grandma.email());
        assertThat(json(send("POST", "/api/v1/caregiving/" + invitation + "/accept", null, grandma), 200).get("status").asString())
                .isEqualTo("ACTIVE");

        // See progress: the reads a parent's pages show, without the family's details.
        JsonNode seen = json(send("GET", "/api/v1/children/" + child, null, grandma), 200);
        assertThat(seen.get("access").asString()).isEqualTo("VIEW_PROGRESS");
        assertThat(seen.get("dateOfBirth").isNull()).isTrue();
        String enrollmentId = json(send("GET", "/api/v1/children/" + child + "/enrollments", null, grandma), 200).get(0)
                .get("enrollmentId").asString();
        for (String read : new String[] {
                "/api/v1/enrollments/" + enrollmentId + "/journey", "/api/v1/children/" + child + "/skills",
                "/api/v1/children/" + child + "/changes?days=28", "/api/v1/children/" + child + "/activity-history",
                "/api/v1/children/" + child + "/parent-insights", "/api/v1/children/" + child + "/progress"}) {
            assertThat(send("GET", read, null, grandma).statusCode()).as(read).isEqualTo(200);
        }
        String[][] refused = {
                {"POST", "/api/v1/children/" + child + "/activity-attempts"},
                {"GET", "/api/v1/children/" + child + "/today"},
                {"PUT", "/api/v1/children/" + child + "/buddy"},
                {"GET", "/api/v1/enrollments/" + enrollmentId + "/next-activity"},
                {"PATCH", "/api/v1/children/" + child},
                {"GET", "/api/v1/children/" + child + "/daily-limit"},
                {"POST", "/api/v1/children/" + child + "/today/extensions"},
                {"PUT", "/api/v1/children/" + child + "/break-reminder"},
                {"POST", "/api/v1/children/" + child + "/enrollments"},
                {"POST", "/api/v1/enrollments/" + enrollmentId + "/stop"},
                {"POST", "/api/v1/children/" + child + "/parent-insights/generate"},
                {"GET", "/api/v1/children/" + child + "/caregivers"},
                {"POST", "/api/v1/children/" + child + "/device-codes"},
                {"GET", "/api/v1/children/" + child + "/streak"},
                {"GET", "/api/v1/children/" + sibling},
                {"GET", "/api/v1/children/" + sibling + "/skills"},
        };
        for (String[] call : refused) {
            assertThat(send(call[0], call[1], call[0].equals("GET") ? null : "{}", grandma).statusCode()).as(call[0] + " " + call[1])
                    .isIn(400, 404);
        }
        // Starting a real activity (a valid request) is refused too: seeing progress isn't playing.
        JsonNode parentsNext = json(send("GET", "/api/v1/children/" + child + "/enrollments", null, parent), 200).get(0).get("nextStep");
        assertThat(send("POST", "/api/v1/children/" + child + "/activity-attempts", "{\"activityVersionId\":\""
                + parentsNext.get("activityVersionId").asString() + "\",\"learningPathItemId\":\""
                + parentsNext.get("learningPathItemId").asString() + "\"}", grandma).statusCode()).isEqualTo(404);
        assertThat(json(send("GET", "/api/v1/children", null, grandma), 200).valueStream()
                .noneMatch(c -> c.get("id").asString().equals(child))).as("not one of her family's children").isTrue();

        // Open the child's space: she plays a whole activity with Dara.
        json(send("PUT", "/api/v1/children/" + child + "/caregivers/" + invitation, "{\"scope\":\"OPEN_SPACE\"}", parent), 200);
        assertThat(send("GET", "/api/v1/children/" + child + "/today", null, grandma).statusCode()).isEqualTo(200);
        JsonNode next = json(send("GET", "/api/v1/enrollments/" + enrollmentId + "/next-activity", null, grandma), 200);
        String version = next.get("activityVersionId").asString();
        JsonNode content = json(send("GET", "/api/v1/activity-versions/" + version + "?childId=" + child, null, grandma), 200);
        String attempt = json(send("POST", "/api/v1/children/" + child + "/activity-attempts", "{\"activityVersionId\":\"" + version
                + "\",\"learningPathItemId\":\"" + next.get("id").asString() + "\"}", grandma), 201).get("id").asString();
        for (JsonNode item : content.get("items")) {
            json(send("POST", "/api/v1/activity-attempts/" + attempt + "/answers", "{\"questionVersionId\":\""
                    + item.get("questionVersionId").asString() + "\",\"submittedAnswer\":\"3\",\"idempotencyKey\":\"" + UUID.randomUUID()
                    + "\"}", grandma), 201);
        }
        json(send("POST", "/api/v1/activity-attempts/" + attempt + "/complete", "", grandma), 200);
        assertThat(send("GET", "/api/v1/children/" + child + "/daily-limit", null, grandma).statusCode()).as("still not settings").isEqualTo(404);

        // At most two people per family; the family ends one at once.
        Person nanny = register(true);
        json(send("POST", "/api/v1/children/" + sibling + "/caregivers", invite(nanny.email(), "VIEW_PROGRESS"), parent), 201);
        assertThat(send("POST", "/api/v1/children/" + sibling + "/caregivers", invite(register(true).email(), "VIEW_PROGRESS"), parent)
                .statusCode()).isEqualTo(409);
        assertThat(send("POST", "/api/v1/children/" + sibling + "/caregivers", invite(grandma.email(), "VIEW_PROGRESS"), parent)
                .statusCode()).as("the same person for another child takes no new place").isEqualTo(201);
        assertThat(send("DELETE", "/api/v1/children/" + child + "/caregivers/" + invitation, null, parent).statusCode()).isEqualTo(204);
        assertThat(send("GET", "/api/v1/children/" + child, null, grandma).statusCode()).isEqualTo(404);
        assertThat(send("GET", "/api/v1/activity-attempts/" + attempt, null, grandma).statusCode()).isEqualTo(404);
        assertThat(json(send("GET", "/api/v1/caregiving", null, grandma), 200).valueStream()
                .noneMatch(c -> c.get("childId").asString().equals(child))).isTrue();

        // The family's export lists the child's caregivers; hers says nothing of Dara.
        assertThat(send("GET", "/api/v1/account/data-export", null, parent).body()).contains(grandma.email());
        assertThat(send("GET", "/api/v1/account/data-export", null, grandma).body()).doesNotContain(child);
        assertThat(jdbc.queryForList("select action from audit_events where resource_id = ?::uuid and action like 'CAREGIVER_%'",
                String.class, child)).contains("CAREGIVER_INVITED", "CAREGIVER_ACCEPTED", "CAREGIVER_SCOPE_CHANGED", "CAREGIVER_REMOVED");
    }

    // ------------------------------------------------------------------ helpers

    private static String invite(String email, String scope) {
        return "{\"email\":\"" + email + "\",\"scope\":\"" + scope + "\"}";
    }

    private Person register(boolean confirmed) throws Exception {
        String email = "care-" + UUID.randomUUID() + "@example.com";
        String token = json(send("POST", "/api/v1/auth/register", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null), 201).get("accessToken").asString();
        if (confirmed) {
            jdbc.update("update accounts set email_verified_at = now() where normalized_email = ?", email);
        }
        return new Person(email, token);
    }

    private String createChild(Person parent, String name) throws Exception {
        return json(send("POST", "/api/v1/children", "{\"displayName\":\"" + name + "\",\"dateOfBirth\":\"" + LocalDate.now().minusYears(6)
                + "\"}", parent), 201).get("id").asString();
    }

    private static JsonNode json(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> send(String method, String path, String body, Person as) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        if (as != null) {
            request.header("Authorization", "Bearer " + as.token());
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

}
