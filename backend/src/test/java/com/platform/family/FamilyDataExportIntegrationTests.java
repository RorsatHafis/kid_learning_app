package com.platform.family;

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
 * A family can download everything held about them: the account, each child, their paths,
 * skills, activities, every answer as given, and insight history - and nothing of any other
 * family, no answer key, no secrets. Each download is audited.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class FamilyDataExportIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Export-Test-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void aParentDownloadsTheirFamilysLearningAndNothingElse() throws Exception {
        Parent parent = registerParent();
        String childId = createChild(parent.token(), "Sokha");
        String secondChildId = createChild(parent.token(), "Dara");
        Parent stranger = registerParent();
        String strangerChild = createChild(stranger.token(), "Stranger Child");
        playFirstActivity(stranger.token(), strangerChild, "4"); // another family's learning must not appear

        // Sokha plays the first activity: one wrong answer, one right.
        playFirstActivity(parent.token(), childId, null);
        // The family once gave a school access to Sokha and ended it: its own decision, so it is theirs to keep.
        UUID schoolId = UUID.randomUUID();
        jdbc.update("insert into schools (id, name) values (?, 'Export Primary')", schoolId);
        jdbc.update("insert into school_child_access (id, school_id, child_id, status, granted_by_account_id, granted_at, "
                + "revoked_by_account_id, revoked_at) values (?, ?, ?::uuid, 'REVOKED', ?::uuid, now() - interval '3 days', "
                + "?::uuid, now() - interval '1 day')", UUID.randomUUID(), schoolId, childId, parent.id(), parent.id());

        HttpResponse<String> res = get("/api/v1/account/data-export", parent.token());
        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.headers().firstValue("Content-Disposition").orElseThrow())
                .startsWith("attachment").contains("bookeyond-kid-data-").endsWith(".json\"");
        assertThat(res.headers().firstValue("Cache-Control").orElseThrow()).contains("no-store");

        JsonNode export = JSON.readTree(res.body());
        assertThat(export.get("format").asString()).isEqualTo("bookeyond-kid-family-export/1");
        assertThat(export.get("account").get("email").asString()).isEqualTo(parent.email());
        assertThat(export.get("children")).hasSize(2);
        JsonNode sokha = export.get("children").get(0);
        assertThat(sokha.get("id").asString()).isEqualTo(childId);
        assertThat(sokha.get("displayName").asString()).isEqualTo("Sokha");
        assertThat(sokha.get("learningPaths")).hasSize(1);
        assertThat(sokha.get("skills")).isNotEmpty();
        assertThat(sokha.get("completedActivities")).hasSize(1);
        assertThat(sokha.get("completedActivities").get(0).get("title").asString()).isEqualTo("Addition Recognition Practice");

        JsonNode answers = sokha.get("answers");
        assertThat(answers).hasSize(2);
        assertThat(answers.get(0).get("activity").asString()).isEqualTo("Addition Recognition Practice");
        assertThat(answers.get(0).get("question").asString()).isNotBlank();
        assertThat(answers.get(0).get("answer").asString()).isEqualTo("999");
        assertThat(answers.get(0).get("correct").asBoolean()).isFalse();
        assertThat(answers.get(1).get("correct").asBoolean()).isTrue();
        assertThat(export.get("children").get(1).get("id").asString()).isEqualTo(secondChildId);
        assertThat(export.get("children").get(1).get("answers")).isEmpty();

        // The school access the family gave and ended, and the family's plan as the parent sees it.
        JsonNode access = sokha.get("schoolAccess");
        assertThat(access).hasSize(1);
        assertThat(access.get(0).get("schoolName").asString()).isEqualTo("Export Primary");
        assertThat(access.get(0).get("status").asString()).isEqualTo("REVOKED");
        assertThat(access.get(0).get("endedAt").isNull()).isFalse();
        assertThat(access.get(0).get("classNames")).isEmpty();
        assertThat(export.get("children").get(1).get("schoolAccess")).isEmpty();
        assertThat(export.get("plan").get("onDefaultPlan").asBoolean()).isTrue();
        assertThat(export.get("plan").get("plan").get("name").asString()).isNotBlank();

        // Nothing of the other family; no answer key, password or session material.
        assertThat(res.body()).doesNotContain(strangerChild).doesNotContain("Stranger Child").doesNotContain(stranger.email())
                .doesNotContain("\"correctAnswer\"").doesNotContain("passwordHash").doesNotContain(PASSWORD)
                .doesNotContain(parent.refreshToken());

        Integer audited = jdbc.queryForObject(
                "select count(*) from audit_events where action = 'ACCOUNT_DATA_EXPORTED' and actor_id = ?::uuid",
                Integer.class, parent.id());
        assertThat(audited).isEqualTo(1);
    }

    @Test
    void theExportRequiresSigningIn() throws Exception {
        assertThat(get("/api/v1/account/data-export", null).statusCode()).isEqualTo(401);
    }

    // ------------------------------------------------------------------ helpers

    private record Parent(String id, String email, String token, String refreshToken) {
    }

    /** Plays the child's first step: the first answer wrong, the rest right (or all {@code answer}). */
    private void playFirstActivity(String token, String childId, String answer) throws Exception {
        JsonNode next = json(get("/api/v1/children/" + childId + "/enrollments", token), 200).get(0).get("nextStep");
        String attemptId = json(post("/api/v1/children/" + childId + "/activity-attempts",
                "{\"activityVersionId\":\"" + next.get("activityVersionId").asString() + "\",\"learningPathItemId\":\""
                        + next.get("learningPathItemId").asString() + "\"}", token), 201).get("id").asString();
        JsonNode content = json(get("/api/v1/activity-versions/" + next.get("activityVersionId").asString(), token), 200);
        boolean first = true;
        for (JsonNode item : content.get("items")) {
            String given = answer != null ? answer : first ? "999" : correctSum(item.get("prompt").asString());
            first = false;
            json(post("/api/v1/activity-attempts/" + attemptId + "/answers", "{\"questionVersionId\":\""
                    + item.get("questionVersionId").asString() + "\",\"submittedAnswer\":\"" + given
                    + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", token), 201);
        }
        json(post("/api/v1/activity-attempts/" + attemptId + "/complete", "", token), 200);
    }

    /** "1 + 2 = ?" → "3" (the seeded curriculum's first activity asks sums). */
    private static String correctSum(String prompt) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)\\s*\\+\\s*(\\d+)").matcher(prompt);
        assertThat(m.find()).as(prompt).isTrue();
        return String.valueOf(Integer.parseInt(m.group(1)) + Integer.parseInt(m.group(2)));
    }

    private Parent registerParent() throws Exception {
        String email = "export-" + UUID.randomUUID() + "@example.com";
        JsonNode body = json(post("/api/v1/auth/register", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null), 201);
        return new Parent(body.get("accountId").asString(), email, body.get("accessToken").asString(),
                body.get("refreshToken").asString());
    }

    private String createChild(String token, String name) throws Exception {
        return json(post("/api/v1/children", "{\"displayName\":\"" + name + "\",\"dateOfBirth\":\""
                + LocalDate.now().minusYears(6) + "\"}", token), 201).get("id").asString();
    }

    private static JsonNode json(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
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
