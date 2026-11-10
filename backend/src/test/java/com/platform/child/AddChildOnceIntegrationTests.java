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
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F-IDEM-1: a child is added once. The same request sent again under its Idempotency-Key header - a retry after a lost
 * response, a second tap - answers with the child the first one added, not a second child taking another place of the
 * family's plan; a new key adds a new child; the key reused for a different child is refused; without a key, as before.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class AddChildOnceIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Add-Child-Once-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void theSameChildSentAgainIsTheChildAlreadyAdded() throws Exception {
        String parent = parent();
        String key = UUID.randomUUID().toString();
        Add body = child("Dara", LocalDate.now().minusYears(6), key);

        String first = json(add(body, parent), 201).get("id").asString();
        String again = json(add(body, parent), 201).get("id").asString();
        assertThat(again).isEqualTo(first);
        assertThat(children(parent)).isEqualTo(1);

        // The key reused for a different child: refused, nothing added.
        assertThat(add(child("Nita", LocalDate.now().minusYears(8), key), parent).statusCode()).isEqualTo(409);
        assertThat(children(parent)).isEqualTo(1);

        // Another child, another key: added.
        json(add(child("Nita", LocalDate.now().minusYears(8), UUID.randomUUID().toString()), parent), 201);
        assertThat(children(parent)).isEqualTo(2);
    }

    @Test
    void twoTapsAtOnceAddOneChild() throws Exception {
        String parent = parent();
        Add body = child("Sok", LocalDate.now().minusYears(5), UUID.randomUUID().toString());
        CompletableFuture<HttpResponse<String>> a = http.sendAsync(request(body, parent), HttpResponse.BodyHandlers.ofString());
        CompletableFuture<HttpResponse<String>> b = http.sendAsync(request(body, parent), HttpResponse.BodyHandlers.ofString());
        // One adds the child; the other is the same child, or "still being added" (409) - never a second child, never a 500.
        assertThat(java.util.List.of(a.get().statusCode(), b.get().statusCode())).allSatisfy(s -> assertThat(s).isIn(201, 409));
        assertThat(children(parent)).isEqualTo(1);
    }

    @Test
    void anOverlongKeyIsRefusedAndWithoutAKeyEachRequestAddsAChildAsBefore() throws Exception {
        String parent = parent();
        String body = "{\"displayName\":\"Lina\",\"dateOfBirth\":\"" + LocalDate.now().minusYears(7) + "\"}";
        assertThat(add(new Add(body, "k".repeat(101)), parent).statusCode()).isEqualTo(400);
        assertThat(children(parent)).isZero();
        json(post("/api/v1/children", body, parent), 201);
        json(post("/api/v1/children", body, parent), 201);
        assertThat(children(parent)).isEqualTo(2);
    }

    /** A child to add, and the key it is sent under (the Idempotency-Key header). */
    private record Add(String body, String key) {
    }

    private static Add child(String name, LocalDate born, String key) {
        return new Add("{\"displayName\":\"" + name + "\",\"dateOfBirth\":\"" + born + "\",\"familyChoosesSubjects\":true}", key);
    }

    private int children(String parent) throws Exception {
        return json(get("/api/v1/children", parent), 200).size();
    }

    private String parent() throws Exception {
        String email = "add-once-" + UUID.randomUUID() + "@example.com";
        String token = com.platform.TestPlans.everythingIncluded(jdbc, json(post("/api/v1/auth/register", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null), 201)).get("accessToken").asString();
        jdbc.update("update accounts set email_verified_at = now() where normalized_email = ?", email);
        return token;
    }

    private static JsonNode json(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return JSON.readTree(response.body());
    }

    private HttpRequest request(Add child, String bearer) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/children")).header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + bearer).header("Idempotency-Key", child.key())
                .POST(HttpRequest.BodyPublishers.ofString(child.body())).build();
    }

    private HttpResponse<String> add(Add child, String bearer) throws Exception {
        return http.send(request(child, bearer), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String bearer) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("Authorization", "Bearer " + bearer)
                .GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body, String bearer) throws Exception {
        HttpRequest.Builder r = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (bearer != null) {
            r.header("Authorization", "Bearer " + bearer);
        }
        return http.send(r.build(), HttpResponse.BodyHandlers.ofString());
    }
}
