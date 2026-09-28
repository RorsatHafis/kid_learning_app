package com.platform.learning;

import com.platform.TestcontainersConfiguration;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The parent's and child's read endpoints must not issue more SQL as a child's history
 * grows (no N+1 per attempt, answer or step). Counts statements per request after one
 * completed activity and again after most of the path, and requires them to stay the same.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Tag("integration")
class ReadQueryScalingIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Value("${local.server.port}") private int port;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void readStatementsDoNotGrowWithTheChildsHistory() throws Exception {
        String token = json(post("/api/v1/auth/register", "{\"email\":\"scaling-" + UUID.randomUUID()
                + "@example.com\",\"password\":\"Scaling-Test-Pass-2026\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null), 201)
                .get("accessToken").asString();
        String childId = json(post("/api/v1/children", "{\"displayName\":\"Scaling Child\",\"dateOfBirth\":\""
                + LocalDate.now().minusYears(6) + "\"}", token), 201).get("id").asString();
        String enrollmentId = json(get("/api/v1/children/" + childId + "/enrollments", token), 200).get(0).get("enrollmentId").asString();

        playNext(token, childId);
        Map<String, Long> afterOne = measure(token, childId, enrollmentId);
        int played = 1;
        while (played < 8 && playNext(token, childId)) {
            played++;
        }
        Map<String, Long> afterMany = measure(token, childId, enrollmentId);
        System.out.println("SQL statements per request after 1 and " + played + " activities: " + afterOne + " -> " + afterMany);

        assertThat(played).isGreaterThanOrEqualTo(5);
        int activities = played;
        afterMany.forEach((endpoint, statements) ->
                // The same number of statements however long the history (a couple of statements of
                // slack for Hibernate's own bookkeeping); growth here means a query per attempt or answer.
                assertThat(statements).as("%s: %s after 1 activity, %s after %s", endpoint, afterOne.get(endpoint), statements, activities)
                        .isLessThanOrEqualTo(afterOne.get(endpoint) + 2L));
    }

    private Map<String, Long> measure(String token, String childId, String enrollmentId) throws Exception {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String path : List.of("/api/v1/children/" + childId + "/enrollments", "/api/v1/enrollments/" + enrollmentId + "/journey",
                "/api/v1/children/" + childId + "/skills", "/api/v1/children/" + childId + "/activity-history",
                "/api/v1/children/" + childId + "/parent-insights", "/api/v1/account/data-export")) {
            Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
            stats.clear();
            json(get(path, token), 200);
            counts.put(path.replaceAll("[0-9a-f-]{36}", "{id}"), stats.getPrepareStatementCount());
        }
        return counts;
    }

    /** Plays the next step with every answer right; false when the path has nothing next. */
    private boolean playNext(String token, String childId) throws Exception {
        JsonNode next = json(get("/api/v1/children/" + childId + "/enrollments", token), 200).get(0).get("nextStep");
        if (next == null || next.isNull()) {
            return false;
        }
        String attemptId = json(post("/api/v1/children/" + childId + "/activity-attempts",
                "{\"activityVersionId\":\"" + next.get("activityVersionId").asString() + "\",\"learningPathItemId\":\""
                        + next.get("learningPathItemId").asString() + "\"}", token), 201).get("id").asString();
        JsonNode content = json(get("/api/v1/activity-versions/" + next.get("activityVersionId").asString(), token), 200);
        for (JsonNode item : content.get("items")) {
            json(post("/api/v1/activity-attempts/" + attemptId + "/answers", "{\"questionVersionId\":\""
                    + item.get("questionVersionId").asString() + "\",\"submittedAnswer\":\"" + sum(item.get("prompt").asString())
                    + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", token), 201);
        }
        json(post("/api/v1/activity-attempts/" + attemptId + "/complete", "", token), 200);
        return true;
    }

    private static String sum(String prompt) {
        Matcher m = Pattern.compile("(\\d+)\\s*\\+\\s*(\\d+)").matcher(prompt);
        return m.find() ? String.valueOf(Integer.parseInt(m.group(1)) + Integer.parseInt(m.group(2))) : "1";
    }

    private static JsonNode json(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body, String token) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

}
