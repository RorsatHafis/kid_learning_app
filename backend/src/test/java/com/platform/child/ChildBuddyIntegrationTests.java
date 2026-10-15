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
 * The Learning Buddy a child walks their path with (V58): Momo the otter until the child chooses,
 * kept on the child, chosen only by the child's family, and only from the five buddies.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class ChildBuddyIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Buddy-Test-Lotus-Pond-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void aChildStartsWithMomoAndOnlyItsFamilyChangesTheBuddy() throws Exception {
        String token = register();
        JsonNode child = json(post("/api/v1/children", "{\"displayName\":\"Dara\",\"dateOfBirth\":\""
                + LocalDate.now().minusYears(6) + "\"}", token), 201);
        String childId = child.get("id").asString();
        assertThat(child.get("learningBuddy").asString()).as("Momo the otter until the child chooses").isEqualTo("OTTER");

        assertThat(json(put("/api/v1/children/" + childId + "/buddy", "{\"buddy\":\"ELEPHANT\"}", token), 200)
                .get("learningBuddy").asString()).isEqualTo("ELEPHANT");
        assertThat(json(get("/api/v1/children/" + childId, token), 200).get("learningBuddy").asString())
                .as("kept on the child").isEqualTo("ELEPHANT");
        assertThat(json(get("/api/v1/account/data-export", token), 200).get("children").get(0).get("learningBuddy").asString())
                .as("part of what the family can take with them").isEqualTo("ELEPHANT");

        String stranger = register();
        assertThat(put("/api/v1/children/" + childId + "/buddy", "{\"buddy\":\"FROG\"}", stranger).statusCode())
                .as("another family").isIn(403, 404);
        assertThat(put("/api/v1/children/" + childId + "/buddy", "{\"buddy\":\"DRAGON\"}", token).statusCode())
                .as("not one of the buddies").isEqualTo(400);
        assertThat(put("/api/v1/children/" + childId + "/buddy", "{}", token).statusCode()).isEqualTo(400);
        assertThat(put("/api/v1/children/" + childId + "/buddy", "{\"buddy\":\"FROG\"}", null).statusCode()).isEqualTo(401);
        assertThat(jdbc.queryForObject("select learning_buddy from children where id = ?::uuid", String.class, childId))
                .isEqualTo("ELEPHANT");
    }

    private String register() throws Exception {
        String email = "buddy-" + UUID.randomUUID() + "@example.com";
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

    private HttpResponse<String> put(String path, String body, String token) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body)), token);
    }

    private static JsonNode json(HttpResponse<String> response, int status) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        return JSON.readTree(response.body());
    }

}
