package com.platform.security;

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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IDOR/BOLA sweep (CLAUDE.md §10, §12): a resource id is never permission. Every endpoint
 * that takes a child, enrollment or attempt id is called with another family's ids by a
 * parent of a different family, a teacher and a principal with no link to the child. Each
 * must be refused (403/404) and change nothing - and the owning parent's call to the very
 * same URL succeeds, so a mistyped URL can't pass as "refused".
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class CrossFamilyAccessSweepIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Sweep-Test-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    private record Call(String method, String path, String body, int ownerStatus) {
    }

    @Test
    void noOneOutsideTheFamilyCanReadOrChangeAChildsLearning() throws Exception {
        String owner = registerParent();
        String childId = json(post("/api/v1/children", "{\"displayName\":\"Owned Child\",\"dateOfBirth\":\""
                + LocalDate.now().minusYears(6) + "\"}", owner), 201).get("id").asString();
        JsonNode enrollment = json(get("/api/v1/children/" + childId + "/enrollments", owner), 200).get(0);
        String enrollmentId = enrollment.get("enrollmentId").asString();
        JsonNode next = enrollment.get("nextStep");
        String activityVersionId = next.get("activityVersionId").asString();
        String startBody = "{\"activityVersionId\":\"" + activityVersionId + "\",\"learningPathItemId\":\""
                + next.get("learningPathItemId").asString() + "\"}";
        String attemptId = json(post("/api/v1/children/" + childId + "/activity-attempts", startBody, owner), 201).get("id").asString();
        String questionVersionId = json(get("/api/v1/activity-versions/" + activityVersionId + "?childId=" + childId, owner), 200)
                .get("items").get(0).get("questionVersionId").asString();
        String curriculumId = enrollment.get("curriculum").get("id").asString();
        String answerBody = "{\"questionVersionId\":\"" + questionVersionId + "\",\"submittedAnswer\":\"2\",\"idempotencyKey\":\""
                + UUID.randomUUID() + "\"}";

        List<Call> reads = List.of(
                new Call("GET", "/api/v1/children/" + childId, null, 200),
                new Call("GET", "/api/v1/children/" + childId + "/enrollments", null, 200),
                new Call("GET", "/api/v1/children/" + childId + "/skills", null, 200),
                new Call("GET", "/api/v1/children/" + childId + "/activity-history", null, 200),
                new Call("GET", "/api/v1/children/" + childId + "/parent-insights", null, 200),
                new Call("GET", "/api/v1/children/" + childId + "/parent-insights/history", null, 200),
                new Call("GET", "/api/v1/children/" + childId + "/progress", null, 200),
                new Call("GET", "/api/v1/children/" + childId + "/streak", null, 200),
                new Call("GET", "/api/v1/enrollments/" + enrollmentId, null, 200),
                new Call("GET", "/api/v1/enrollments/" + enrollmentId + "/journey", null, 200),
                new Call("GET", "/api/v1/enrollments/" + enrollmentId + "/next-activity", null, 200),
                new Call("GET", "/api/v1/activity-attempts/" + attemptId, null, 200),
                new Call("GET", "/api/v1/activity-attempts/" + attemptId + "/answers", null, 200));
        List<Call> writes = List.of(
                new Call("PATCH", "/api/v1/children/" + childId, "{\"displayName\":\"Taken Over\"}", 200),
                new Call("POST", "/api/v1/children/" + childId + "/enrollments", "{\"curriculumId\":\"" + curriculumId + "\"}", -1),
                new Call("POST", "/api/v1/children/" + childId + "/activity-attempts", startBody, -1),
                new Call("POST", "/api/v1/children/" + childId + "/parent-insights/generate", "", -1),
                new Call("POST", "/api/v1/activity-attempts/" + attemptId + "/answers", answerBody, 201),
                new Call("POST", "/api/v1/activity-attempts/" + attemptId + "/complete", "", -1),
                new Call("POST", "/api/v1/activity-attempts/" + attemptId + "/abandon", "", -1),
                new Call("POST", "/api/v1/activity-attempts/" + attemptId + "/adaptive-next", "", -1),
                new Call("POST", "/api/v1/activity-attempts/" + attemptId + "/smart-review-check", "", -1));

        Map<String, Object> before = snapshot(childId, attemptId);
        for (PlatformRole role : List.of(PlatformRole.PARENT, PlatformRole.TEACHER, PlatformRole.PRINCIPAL)) {
            String outsider = role == PlatformRole.PARENT ? registerParent() : staff(role);
            for (Call call : concat(reads, writes)) {
                HttpResponse<String> refused = send(call, outsider);
                assertThat(refused.statusCode()).as("%s %s %s", role, call.method(), call.path()).isIn(403, 404);
                // A refusal says nothing about the child or their learning.
                JsonNode problem = JSON.readTree(refused.body());
                assertThat(refused.body()).as("%s %s body", role, call.path())
                        .doesNotContain("Owned Child").doesNotContain(questionVersionId)
                        .doesNotContain("Addition").doesNotContain("\"correct\"");
                if (!call.path().contains(childId)) {
                    // Never the child behind someone else's enrollment or attempt.
                    assertThat(refused.body()).as("%s %s body", role, call.path()).doesNotContain(childId);
                }
                // A foreign id is refused exactly like one that doesn't exist, so ids can't be probed.
                Call missing = new Call(call.method(), call.path().replace(childId, UUID.randomUUID().toString())
                        .replace(enrollmentId, UUID.randomUUID().toString()).replace(attemptId, UUID.randomUUID().toString()),
                        call.body(), 0);
                HttpResponse<String> absent = send(missing, outsider);
                assertThat(absent.statusCode()).as("%s nonexistent %s", role, call.path()).isEqualTo(refused.statusCode());
                if (absent.statusCode() == 404) {
                    JsonNode absentProblem = JSON.readTree(absent.body());
                    assertThat(absentProblem.get("detail")).as("%s %s detail", role, call.path()).isEqualTo(problem.get("detail"));
                    assertThat(absentProblem.get("title")).isEqualTo(problem.get("title"));
                }
            }
        }
        assertThat(snapshot(childId, attemptId)).as("nothing changed").isEqualTo(before);

        // The same URLs work for the family (the reads, and a representative write).
        for (Call call : reads) {
            assertThat(send(call, owner).statusCode()).as("owner %s", call.path()).isEqualTo(call.ownerStatus());
        }
        for (Call call : writes) {
            if (call.ownerStatus() > 0) {
                assertThat(send(call, owner).statusCode()).as("owner %s %s", call.method(), call.path()).isEqualTo(call.ownerStatus());
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private Map<String, Object> snapshot(String childId, String attemptId) {
        return jdbc.queryForMap("""
                select c.display_name,
                       (select count(*) from enrollments e where e.child_id = c.id) as enrollments,
                       (select count(*) from activity_attempts a where a.child_id = c.id) as attempts,
                       (select status from activity_attempts a where a.id = ?::uuid) as attempt_status,
                       (select count(*) from answer_records r where r.activity_attempt_id = ?::uuid) as answers,
                       (select count(*) from parent_insights p where p.child_id = c.id) as insights
                from children c where c.id = ?::uuid""", attemptId, attemptId, childId);
    }

    private static List<Call> concat(List<Call> a, List<Call> b) {
        return java.util.stream.Stream.concat(a.stream(), b.stream()).toList();
    }

    private String registerParent() throws Exception {
        return json(post("/api/v1/auth/register", "{\"email\":\"sweep-" + UUID.randomUUID() + "@example.com\",\"password\":\""
                + PASSWORD + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null), 201).get("accessToken").asString();
    }

    private String staff(PlatformRole role) throws Exception {
        String email = "sweep-" + role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), role));
        return json(post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", null), 200)
                .get("accessToken").asString();
    }

    private HttpResponse<String> send(Call call, String token) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + call.path()))
                .header("Authorization", "Bearer " + token);
        if (call.body() == null) {
            req.method(call.method(), HttpRequest.BodyPublishers.noBody());
        } else {
            req.header("Content-Type", "application/json").method(call.method(), HttpRequest.BodyPublishers.ofString(call.body()));
        }
        return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static JsonNode json(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        return send(new Call("GET", path, null, 0), token);
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
