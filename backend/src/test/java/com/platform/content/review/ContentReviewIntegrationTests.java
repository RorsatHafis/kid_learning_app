package com.platform.content.review;

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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PD-20's mechanism (Y14) with review switched on: platform content is published only by a second
 * administrator approving a review of the draft as it is; a teacher's own content is not reviewed.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "platform.content-review.required=true")
@Tag("integration")
class ContentReviewIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Review-Test-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void platformContentIsPublishedOnlyByASecondAdministratorApprovingTheDraftAsItIs() throws Exception {
        String author = staff(PlatformRole.ADMIN);
        String reviewer = staff(PlatformRole.ADMIN);
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String subjectId = json(post("/api/v1/studio/subjects", "{\"code\":\"REVIEW_" + suffix + "\",\"name\":\"Review " + suffix
                + "\"}", author), 201).get("id").asString();
        String skillId = json(post("/api/v1/studio/subjects/" + subjectId + "/skills", "{\"code\":\"RV_" + suffix
                + "\",\"name\":\"Reviewed skill\"}", author), 201).get("id").asString();
        String versionId = json(post("/api/v1/studio/questions", "{\"subjectId\":\"" + subjectId
                + "\",\"questionType\":\"MULTIPLE_CHOICE\",\"prompt\":\"Which is three? " + suffix + "\",\"difficultyLevel\":2,"
                + "\"options\":[{\"label\":\"2\",\"correct\":false},{\"label\":\"3\",\"correct\":true}],"
                + "\"skillIds\":[\"" + skillId + "\"]}", author), 201).get("versions").get(0).get("id").asString();
        String reviewPath = "/api/v1/studio/question-versions/" + versionId + "/review";

        assertThat(json(get("/api/v1/studio/review-settings", author), 200).get("required").asBoolean()).isTrue();
        HttpResponse<String> direct = post("/api/v1/studio/question-versions/" + versionId + "/publish", "", author);
        assertThat(direct.statusCode()).as("publishing alone is refused").isEqualTo(409);
        assertThat(direct.body()).contains("second administrator");
        JsonNode state = json(get(reviewPath, author), 200);
        assertThat(state.get("required").asBoolean()).isTrue();
        assertThat(state.get("review").isNull()).isTrue();

        // Asked; the author can't approve their own request.
        JsonNode asked = json(post(reviewPath, "", author), 200);
        assertThat(asked.get("review").get("status").asString()).isEqualTo("OPEN");
        assertThat(asked.get("review").get("mine").asBoolean()).isTrue();
        String firstId = asked.get("review").get("id").asString();
        assertThat(post("/api/v1/studio/reviews/" + firstId + "/approve", "", author).statusCode()).isEqualTo(409);
        assertThat(json(get("/api/v1/studio/reviews?size=100", reviewer), 200).get("items").valueStream()
                .anyMatch(row -> row.get("id").asString().equals(firstId))).isTrue();

        // Edited after asking: the reviewer can't approve a draft they didn't see; asking again replaces the request.
        json(put("/api/v1/studio/question-versions/" + versionId, "{\"prompt\":\"Which one is three? " + suffix
                + "\",\"difficultyLevel\":2,\"options\":[{\"label\":\"2\",\"correct\":false},{\"label\":\"3\",\"correct\":true}]}", author), 200);
        assertThat(json(get(reviewPath, reviewer), 200).get("review").get("changedSince").asBoolean()).isTrue();
        assertThat(post("/api/v1/studio/reviews/" + firstId + "/approve", "", reviewer).statusCode()).isEqualTo(409);
        String secondId = json(post(reviewPath, "", author), 200).get("review").get("id").asString();
        assertThat(secondId).isNotEqualTo(firstId);
        assertThat(jdbc.queryForObject("select status from content_reviews where id = ?::uuid", String.class, firstId))
                .isEqualTo("WITHDRAWN");

        // Changes asked, with a note the author reads; asked again; approved, which publishes.
        JsonNode changes = json(post("/api/v1/studio/reviews/" + secondId + "/request-changes",
                "{\"note\":\"Add a third choice.\"}", reviewer), 200);
        assertThat(changes.get("review").get("status").asString()).isEqualTo("CHANGES_REQUESTED");
        assertThat(json(get(reviewPath, author), 200).get("review").get("note").asString()).isEqualTo("Add a third choice.");
        String thirdId = json(post(reviewPath, "", author), 200).get("review").get("id").asString();
        JsonNode approved = json(post("/api/v1/studio/reviews/" + thirdId + "/approve", "", reviewer), 200);
        assertThat(approved.get("review").get("status").asString()).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("select status from question_versions where id = ?::uuid", String.class, versionId))
                .isEqualTo("PUBLISHED");
        assertThat(post("/api/v1/studio/reviews/" + thirdId + "/approve", "", reviewer).statusCode()).as("answered").isEqualTo(409);
        assertThat(jdbc.queryForObject("select count(*) from audit_events where resource_type = 'CONTENT_REVIEW' and resource_id in "
                + "(?::uuid, ?::uuid, ?::uuid)", Integer.class, firstId, secondId, thirdId)).isGreaterThanOrEqualTo(5);
    }

    @Test
    void aTeachersOwnContentIsNotReviewedAndTeachersDontReview() throws Exception {
        String admin = staff(PlatformRole.ADMIN);
        String teacher = staff(PlatformRole.TEACHER);
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String subjectId = json(post("/api/v1/studio/subjects", "{\"code\":\"TREVIEW_" + suffix + "\",\"name\":\"Teacher review "
                + suffix + "\"}", admin), 201).get("id").asString();
        String skillId = json(post("/api/v1/studio/subjects/" + subjectId + "/skills", "{\"code\":\"TRV_" + suffix
                + "\",\"name\":\"Teacher skill\"}", admin), 201).get("id").asString();
        String versionId = json(post("/api/v1/studio/questions", "{\"subjectId\":\"" + subjectId
                + "\",\"questionType\":\"NUMERIC\",\"prompt\":\"2 + 2 = ? " + suffix + "\",\"correctAnswer\":\"4\",\"difficultyLevel\":1,"
                + "\"skillIds\":[\"" + skillId + "\"]}", teacher), 201).get("versions").get(0).get("id").asString();

        assertThat(json(get("/api/v1/studio/question-versions/" + versionId + "/review", teacher), 200).get("required").asBoolean())
                .isFalse();
        assertThat(post("/api/v1/studio/question-versions/" + versionId + "/publish", "", teacher).statusCode()).isEqualTo(200);
        assertThat(get("/api/v1/studio/reviews", teacher).statusCode()).isEqualTo(403);
        assertThat(post("/api/v1/studio/reviews/" + UUID.randomUUID() + "/approve", "", teacher).statusCode()).isEqualTo(403);
        assertThat(get("/api/v1/studio/question-versions/" + versionId + "/review", staff(PlatformRole.TEACHER)).statusCode())
                .as("another teacher's draft").isIn(403, 404);
    }

    // ------------------------------------------------------------------

    private String staff(PlatformRole role) throws Exception {
        String email = "review-" + role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), role));
        return json(post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", null), 200)
                .get("accessToken").asString();
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
        return send(path, "POST", body, token);
    }

    private HttpResponse<String> put(String path, String body, String token) throws Exception {
        return send(path, "PUT", body, token);
    }

    private HttpResponse<String> send(String path, String method, String body, String token) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
        if (token != null) req.header("Authorization", "Bearer " + token);
        return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

}
