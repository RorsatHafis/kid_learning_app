package com.platform.learning;

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

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * X12 over real HTTP against PostgreSQL: what changed in a child's learning over a window is read
 * from the immutable evidence history with the same rule as today's standing - each skill answered on
 * in the window, where it stood at the window's start and where it stands now, on how many answers.
 * Only the child's family can read it.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class ProgressChangeIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Progress-Test-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void eachSkillAnsweredOnInTheWindowShowsWhereItStoodThenAndStandsNow() throws Exception {
        String parent = registerParent();
        String childId = createChild(parent, "Sokha");
        UUID child = UUID.fromString(childId);
        List<UUID> skills = skills(5);
        Instant now = Instant.now();
        Instant fortyDaysAgo = now.minus(Duration.ofDays(40));
        Instant fortyOneDaysAgo = now.minus(Duration.ofDays(41));
        Instant twoDaysAgo = now.minus(Duration.ofDays(2));

        // Needs practice before the window (3 answers), progressing now (3 more inside it).
        evidence(child, skills.get(0), fortyDaysAgo, "0.2000", "0.2500", "0.3000");
        evidence(child, skills.get(0), twoDaysAgo, "0.5000", "0.6000", "0.7000");
        // First answer inside the window: too early to tell.
        evidence(child, skills.get(1), twoDaysAgo, "0.6000");
        // Answered only before the window: nothing changed, not listed.
        evidence(child, skills.get(2), fortyDaysAgo, "0.4000", "0.5000", "0.6500");
        // Secure before (over two days - D-17: one sitting is never secure), progressing after two wrong answers inside the window.
        evidence(child, skills.get(3), fortyOneDaysAgo, "0.7000", "0.8000");
        evidence(child, skills.get(3), fortyDaysAgo, "0.9000");
        evidence(child, skills.get(3), twoDaysAgo, "0.7000", "0.6200");
        // None before, three inside: a first reading.
        evidence(child, skills.get(4), twoDaysAgo, "0.3000", "0.3500", "0.4000");

        JsonNode progress = json(get("/api/v1/children/" + childId + "/changes?days=28", parent), 200);
        assertThat(progress.get("days").asInt()).isEqualTo(28);
        assertThat(progress.get("answersSince").asInt()).isEqualTo(3 + 1 + 2 + 3);
        Map<UUID, JsonNode> bySkill = new HashMap<>();
        progress.get("changes").forEach(change -> bySkill.put(UUID.fromString(change.get("skillId").asString()), change));
        assertThat(bySkill).doesNotContainKey(skills.get(2));
        assertThat(change(bySkill.get(skills.get(0)))).isEqualTo("MOVED_UP NEEDS_PRACTICE->PROGRESSING 3");
        assertThat(change(bySkill.get(skills.get(1)))).isEqualTo("STARTED NOT_STARTED->TOO_EARLY_TO_TELL 1");
        assertThat(change(bySkill.get(skills.get(3)))).isEqualTo("MOVED_DOWN SECURE->PROGRESSING 2");
        assertThat(change(bySkill.get(skills.get(4)))).isEqualTo("FIRST_READING NOT_STARTED->NEEDS_PRACTICE 3");
        // Listed in the order a parent reads them: up, first readings, started... and what slipped, last.
        List<String> kinds = progress.get("changes").valueStream().map(c -> c.get("kind").asString()).toList();
        assertThat(kinds).containsExactly("MOVED_UP", "FIRST_READING", "STARTED", "MOVED_DOWN");

        // A week: only what happened in it.
        JsonNode week = json(get("/api/v1/children/" + childId + "/changes?days=7", parent), 200);
        assertThat(week.get("changes")).hasSize(4);
        JsonNode quarter = json(get("/api/v1/children/" + childId + "/changes?days=90", parent), 200);
        assertThat(quarter.get("answersSince").asInt()).isEqualTo(18);

        // Another family can't read it; out-of-range windows are refused.
        assertThat(get("/api/v1/children/" + childId + "/changes", registerParent()).statusCode()).isIn(403, 404);
        assertThat(get("/api/v1/children/" + childId + "/changes?days=3", parent).statusCode()).isEqualTo(400);
        assertThat(get("/api/v1/children/" + childId + "/changes?days=365", parent).statusCode()).isEqualTo(400);
    }

    @Test
    void aChildWithNoAnswersInTheWindowHasNothingToReport() throws Exception {
        String parent = registerParent();
        String childId = createChild(parent, "Dara");
        JsonNode progress = json(get("/api/v1/children/" + childId + "/changes", parent), 200);
        assertThat(progress.get("answersSince").asInt()).isZero();
        assertThat(progress.get("changes")).isEmpty();
    }

    // ------------------------------------------------------------------ evidence as the engine records it

    /**
     * {@code count} skills to answer on. The migrations seed only four, and what other test classes add depends on
     * the order they ran in, so the rest are made here under the seeded skills' subject.
     */
    private List<UUID> skills(int count) {
        UUID subjectId = jdbc.queryForObject("select subject_id from skills order by code limit 1", UUID.class);
        Integer existing = jdbc.queryForObject("select count(*) from skills", Integer.class);
        for (int i = existing; i < count; i++) {
            jdbc.update("insert into skills (id, subject_id, code, name) values (?, ?, ?, ?)",
                    UUID.randomUUID(), subjectId, "PROGRESS_TEST_" + UUID.randomUUID().toString().substring(0, 8), "Progress test skill");
        }
        return jdbc.queryForList("select id from skills order by code limit ?", UUID.class, count);
    }

    /** History rows (one per answer, oldest first) and the skill's current state after the last of them. */
    private void evidence(UUID child, UUID skill, Instant at, String... masteryAfterEach) {
        for (int i = 0; i < masteryAfterEach.length; i++) {
            jdbc.update("insert into learner_skill_history (id, child_id, skill_id, mastery_probability, confidence, triggered_by_id,"
                            + " triggered_by_type, occurred_at) values (?, ?, ?, ?, 0.1, ?, 'ACTIVITY_ATTEMPT', ?)",
                    UUID.randomUUID(), child, skill, new BigDecimal(masteryAfterEach[i]), UUID.randomUUID(),
                    Timestamp.from(at.plusSeconds(i)));
        }
        BigDecimal last = new BigDecimal(masteryAfterEach[masteryAfterEach.length - 1]);
        int updated = jdbc.update("update learner_skill_states set evidence_count = evidence_count + ?, mastery_probability = ?,"
                + " last_evidence_at = ? where child_id = ? and skill_id = ?", masteryAfterEach.length, last, Timestamp.from(at), child, skill);
        if (updated == 0) {
            jdbc.update("insert into learner_skill_states (id, child_id, skill_id, mastery_probability, confidence, evidence_count,"
                            + " last_evidence_at) values (?, ?, ?, ?, 0.1, ?, ?)",
                    UUID.randomUUID(), child, skill, last, masteryAfterEach.length, Timestamp.from(at));
        }
    }

    private static String change(JsonNode change) {
        return change.get("kind").asString() + " " + change.get("then").asString() + "->" + change.get("now").asString() + " "
                + change.get("answersSince").asInt();
    }

    // ------------------------------------------------------------------ accounts and HTTP

    private String registerParent() throws Exception {
        return json(post("/api/v1/auth/register", "{\"email\":\"progress-" + UUID.randomUUID() + "@example.com\",\"password\":\""
                + PASSWORD + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null), 201).get("accessToken").asString();
    }

    private String createChild(String parent, String name) throws Exception {
        return json(post("/api/v1/children", "{\"displayName\":\"" + name + "\",\"dateOfBirth\":\"" + LocalDate.now().minusYears(6)
                + "\"}", parent), 201).get("id").asString();
    }

    private static JsonNode json(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> get(String path, String bearer) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body, String bearer) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

}
