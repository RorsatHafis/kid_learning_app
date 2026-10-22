package com.platform.site;

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
 * M2 over real HTTP against PostgreSQL: the landing page's statements are public and managed only by administrators
 * (a testimonial needs who said it, where permission is recorded and a confirmation that it is genuine); the public
 * demo serves only chosen, published platform activities, checks answers with the real scoring, stores nothing, refuses
 * anything outside the demo, and is rate limited per client.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class PublicDemoAndStatementsIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Demo-Test-Pass-2026";
    /** Seeded platform content (V29): published, two NUMERIC questions each. */
    private static final String RECOGNITION = "00000000-add1-4000-a000-000000000019";
    private static final String WITHIN_FIVE = "00000000-add1-4000-a000-000000000024";

    @Value("${local.server.port}") private int port;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void statementsArePublicManagedByAdministratorsAndTestimonialsMustBeGenuine() throws Exception {
        String admin = staff(PlatformRole.ADMIN);
        String teacher = staff(PlatformRole.TEACHER);
        // Bookeyond's own lines are there for everyone, without punctuation at the end and without notes.
        JsonNode visible = json(send("GET", "/api/v1/site/statements", null, null), 200);
        assertThat(visible.valueStream().map(s -> s.get("textEn").asString())).contains("Learn Today, Build Tomorrow");
        assertThat(visible.valueStream().allMatch(s -> s.get("sourceNote") == null)).isTrue();

        String testimonial = "{\"kind\":\"TESTIMONIAL\",\"textEn\":\"My daughter asks to play the maths path\",\"textKm\":\"កូនស្រីខ្ញុំសុំលេង\","
                + "\"attributionEn\":\"A parent in Phnom Penh\",\"attributionKm\":\"ឪពុកម្តាយនៅភ្នំពេញ\",\"sourceNote\":\"Signed consent form 2026-10-01\","
                + "\"position\":4,\"visible\":true";
        assertThat(send("POST", "/api/v1/admin/site-statements", testimonial + ",\"confirmedGenuine\":false}", admin).statusCode())
                .as("not confirmed as genuine").isEqualTo(400);
        assertThat(send("POST", "/api/v1/admin/site-statements", testimonial.replace(",\"sourceNote\":\"Signed consent form 2026-10-01\"", "")
                + ",\"confirmedGenuine\":true}", admin).statusCode()).as("no source of permission").isEqualTo(400);
        assertThat(send("POST", "/api/v1/admin/site-statements", testimonial + ",\"confirmedGenuine\":true}", teacher).statusCode())
                .as("not an administrator").isEqualTo(403);
        JsonNode created = json(send("POST", "/api/v1/admin/site-statements", testimonial + ",\"confirmedGenuine\":true}", admin), 201);
        String id = created.get("id").asString();
        JsonNode shown = json(send("GET", "/api/v1/site/statements", null, null), 200).valueStream()
                .filter(s -> s.get("id").asString().equals(id)).findFirst().orElseThrow();
        assertThat(shown.get("attributionEn").asString()).isEqualTo("A parent in Phnom Penh");
        assertThat(shown.has("sourceNote")).as("the permission note stays with administrators").isFalse();

        // Hidden, then an edit made over someone else's is refused; deletion is recorded.
        String hide = "{\"kind\":\"BRAND\",\"textEn\":\"Short lessons\",\"textKm\":\"មេរៀនខ្លីៗ\",\"position\":4,\"visible\":false,\"version\":"
                + created.get("version").asLong() + "}";
        json(send("PUT", "/api/v1/admin/site-statements/" + id, hide, admin), 200);
        assertThat(send("PUT", "/api/v1/admin/site-statements/" + id, hide, admin).statusCode()).as("stale version").isEqualTo(409);
        assertThat(json(send("GET", "/api/v1/site/statements", null, null), 200).valueStream()
                .noneMatch(s -> s.get("id").asString().equals(id))).isTrue();
        assertThat(send("DELETE", "/api/v1/admin/site-statements/" + id, null, admin).statusCode()).isEqualTo(204);
        assertThat(jdbc.queryForObject("select count(*) from audit_events where resource_id = ?::uuid and action like 'SITE_STATEMENT_%'",
                Integer.class, id)).isEqualTo(3);
    }

    @Test
    void theDemoServesOnlyChosenPlatformActivitiesChecksAnswersAndStoresNothing() throws Exception {
        String admin = staff(PlatformRole.ADMIN);
        assertThat(send("PUT", "/api/v1/admin/demo-activities", "{\"activityVersionIds\":[\"" + RECOGNITION + "\"]}",
                staff(PlatformRole.TEACHER)).statusCode()).isEqualTo(403);
        assertThat(send("PUT", "/api/v1/admin/demo-activities", "{\"activityVersionIds\":[\"" + RECOGNITION + "\",\"" + WITHIN_FIVE + "\","
                + "\"00000000-add1-4000-a000-00000000002f\",\"00000000-add1-4000-a000-00000000003a\"]}", admin).statusCode())
                .as("more than three").isEqualTo(400);
        assertThat(send("PUT", "/api/v1/admin/demo-activities", "{\"activityVersionIds\":[\"" + UUID.randomUUID() + "\"]}", admin).statusCode())
                .as("unknown or draft").isEqualTo(404);
        json(send("PUT", "/api/v1/admin/demo-activities", "{\"activityVersionIds\":[\"" + RECOGNITION + "\"]}", admin), 200);
        JsonNode candidates = json(send("GET", "/api/v1/admin/demo-activities", null, admin), 200);
        assertThat(candidates.valueStream().filter(c -> c.get("chosen").asBoolean()).map(c -> c.get("activityVersionId").asString()))
                .containsExactly(RECOGNITION);
        assertThat(candidates.get(0).get("activityVersionId").asString()).as("the chosen one first").isEqualTo(RECOGNITION);
        assertThat(candidates.size()).as("a short list however much content there is").isLessThanOrEqualTo(13);
        // Search: by title, case-insensitive; the chosen one stays at the top; % and _ are characters, not wildcards.
        JsonNode within = json(send("GET", "/api/v1/admin/demo-activities?q=within%205", null, admin), 200);
        assertThat(within.valueStream().map(c -> c.get("activityVersionId").asString())).containsExactly(RECOGNITION, WITHIN_FIVE);
        assertThat(json(send("GET", "/api/v1/admin/demo-activities?q=%25%25", null, admin), 200).valueStream()
                .map(c -> c.get("activityVersionId").asString())).containsExactly(RECOGNITION);

        // Anyone, no account: the chosen activity, with no correct answer anywhere in what is sent.
        HttpResponse<String> page = send("GET", "/api/v1/demo/activities", null, null);
        JsonNode demo = json(page, 200);
        assertThat(demo).hasSize(1);
        assertThat(demo.get(0).get("activityVersionId").asString()).isEqualTo(RECOGNITION);
        assertThat(page.body()).doesNotContain("correctAnswer").doesNotContain("\"correct\"");
        JsonNode item = demo.get(0).get("items").get(0);
        String questionVersionId = item.get("questionVersionId").asString();
        String right = jdbc.queryForObject("select correct_answer from question_versions where id = ?::uuid", String.class, questionVersionId);

        int attempts = jdbc.queryForObject("select count(*) from activity_attempts", Integer.class);
        int answers = jdbc.queryForObject("select count(*) from answer_records", Integer.class);
        JsonNode ok = json(check(RECOGNITION, questionVersionId, right), 200);
        assertThat(ok.get("correct").asBoolean()).isTrue();
        JsonNode wrong = json(check(RECOGNITION, questionVersionId, "999"), 200);
        assertThat(wrong.get("correct").asBoolean()).isFalse();
        assertThat(wrong.get("correctAnswer").get("text").asString()).isEqualTo(right.trim());
        assertThat(jdbc.queryForObject("select count(*) from activity_attempts", Integer.class)).as("nothing stored").isEqualTo(attempts);
        assertThat(jdbc.queryForObject("select count(*) from answer_records", Integer.class)).isEqualTo(answers);

        // Outside the demo: another activity's question, or a question of the demo through another activity.
        String otherQuestion = jdbc.queryForObject("select question_version_id::text from activity_items where activity_version_id = ?::uuid "
                + "order by sequence_order limit 1", String.class, WITHIN_FIVE);
        assertThat(check(WITHIN_FIVE, otherQuestion, "1").statusCode()).as("not a demo activity").isEqualTo(404);
        assertThat(check(RECOGNITION, otherQuestion, "1").statusCode()).as("not in the demo activity").isEqualTo(404);

        // Taken off the demo: gone at once.
        json(send("PUT", "/api/v1/admin/demo-activities", "{\"activityVersionIds\":[]}", admin), 200);
        assertThat(json(send("GET", "/api/v1/demo/activities", null, null), 200)).isEmpty();
        assertThat(check(RECOGNITION, questionVersionId, right).statusCode()).isEqualTo(404);
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action = 'PUBLIC_DEMO_CHOSEN'", Integer.class)).isGreaterThanOrEqualTo(2);
    }

    @Test
    void theDemoCheckIsRateLimitedPerClient() throws Exception {
        int refused = 0;
        for (int i = 0; i < 70; i++) {
            HttpResponse<String> r = send("POST", "/api/v1/demo/check", "{\"activityVersionId\":\"" + UUID.randomUUID()
                    + "\",\"questionVersionId\":\"" + UUID.randomUUID() + "\",\"submittedAnswer\":\"1\"}", null, "198.51.100.231");
            if (r.statusCode() == 429) {
                refused++;
            }
        }
        assertThat(refused).as("over 60 a minute from one client").isGreaterThan(0);
        HttpResponse<String> other = send("POST", "/api/v1/demo/check", "{\"activityVersionId\":\"" + UUID.randomUUID()
                + "\",\"questionVersionId\":\"" + UUID.randomUUID() + "\",\"submittedAnswer\":\"1\"}", null, "198.51.100.232");
        assertThat(other.statusCode()).as("another client is unaffected").isEqualTo(404);
    }

    // ------------------------------------------------------------------ helpers

    private HttpResponse<String> check(String activityVersionId, String questionVersionId, String answer) throws Exception {
        return send("POST", "/api/v1/demo/check", "{\"activityVersionId\":\"" + activityVersionId + "\",\"questionVersionId\":\""
                + questionVersionId + "\",\"submittedAnswer\":" + JSON.writeValueAsString(answer) + "}", null);
    }

    private String staff(PlatformRole role) throws Exception {
        String email = "site-" + role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), role));
        return json(send("POST", "/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", null), 200)
                .get("accessToken").asString();
    }

    private static JsonNode json(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> send(String method, String path, String body, String bearer) throws Exception {
        return send(method, path, body, bearer, null);
    }

    private HttpResponse<String> send(String method, String path, String body, String bearer, String forwardedFor) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        if (forwardedFor != null) {
            request.header("X-Forwarded-For", forwardedFor);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

}
