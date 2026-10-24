package com.platform.child;

import com.platform.TestcontainersConfiguration;
import org.junit.jupiter.api.Assumptions;
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
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M3 over real HTTP against PostgreSQL: a parent sets a child's learning day; the time used is the server's own (from
 * attempt and answer timestamps); once it is used a new activity doesn't start, but the one already open finishes and
 * its answers are taken; a parent can give a little more time (recorded, capped); another family can do none of it.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class DailyLearningTimeIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Daily-Time-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private com.platform.identity.repository.AccountRepository accountRepository;
    @Autowired private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void aUsedLearningDayStopsNewActivitiesButNeverTheOpenOneAndAParentCanGiveMore() throws Exception {
        // The backdated time below must stay inside today's learning day (Cambodia by default).
        Assumptions.assumeTrue(LocalTime.now(ZoneId.of("Asia/Phnom_Penh")).isAfter(LocalTime.of(0, 30)), "too close to midnight");
        String parent = registerParent();
        String child = createChild(parent);
        JsonNode today = json(send("GET", "/api/v1/children/" + child + "/today", null, parent), 200);
        assertThat(today.get("limitMinutes").isNull()).as("off until a parent sets it").isTrue();
        assertThat(send("PUT", "/api/v1/children/" + child + "/daily-limit", "{\"minutes\":7}", parent).statusCode()).isEqualTo(400);
        assertThat(json(send("PUT", "/api/v1/children/" + child + "/daily-limit", "{\"minutes\":15}", parent), 200).get("minutes").asInt())
                .isEqualTo(15);

        // One finished activity, and the next one opened (not finished).
        JsonNode steps = json(send("GET", "/api/v1/children/" + child + "/enrollments", null, parent), 200).get(0).get("nextStep");
        String first = start(parent, child, steps.get("activityVersionId").asString(), steps.get("learningPathItemId").asString(), 201);
        answerAll(parent, child, first, steps.get("activityVersionId").asString());
        json(send("POST", "/api/v1/activity-attempts/" + first + "/complete", "", parent), 200);
        JsonNode open = json(send("GET", "/api/v1/children/" + child + "/enrollments", null, parent), 200).get(0).get("nextStep");
        String openAttempt = start(parent, child, open.get("activityVersionId").asString(), open.get("learningPathItemId").asString(), 201);

        // The finished one took 16 minutes (server timestamps): the 15-minute day is used.
        jdbc.update("update activity_attempts set started_at = started_at - interval '16 minutes' where id = ?::uuid", first);
        today = json(send("GET", "/api/v1/children/" + child + "/today", null, parent), 200);
        assertThat(today.get("usedMinutes").asInt()).isGreaterThanOrEqualTo(15);
        assertThat(today.get("reached").asBoolean()).isTrue();
        assertThat(today.get("remainingMinutes").asInt()).isZero();

        // A new activity doesn't start - said as the day being used, with its figures.
        String other = otherPublishedVersion(steps.get("activityVersionId").asString(), open.get("activityVersionId").asString());
        HttpResponse<String> refused = send("POST", "/api/v1/children/" + child + "/activity-attempts",
                "{\"activityVersionId\":\"" + other + "\"}", parent);
        assertThat(refused.statusCode()).isEqualTo(409);
        JsonNode problem = JSON.readTree(refused.body());
        assertThat(problem.get("dailyLimitReached").asBoolean()).isTrue();
        assertThat(problem.get("limitMinutes").asInt()).isEqualTo(15);

        // The open activity carries on: resuming it and answering it both work.
        assertThat(start(parent, child, open.get("activityVersionId").asString(), open.get("learningPathItemId").asString(), null))
                .isEqualTo(openAttempt);
        answerAll(parent, child, openAttempt, open.get("activityVersionId").asString());

        // A little more time: a choice of lengths, recorded; then a new activity starts again.
        assertThat(send("POST", "/api/v1/children/" + child + "/today/extensions", "{\"minutes\":20}", parent).statusCode()).isEqualTo(400);
        JsonNode more = json(send("POST", "/api/v1/children/" + child + "/today/extensions", "{\"minutes\":15}", parent), 200);
        assertThat(more.get("extraMinutes").asInt()).isEqualTo(15);
        assertThat(more.get("reached").asBoolean()).isFalse();
        start(parent, child, other, null, 201);
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action = 'CHILD_DAILY_TIME_EXTENDED' and resource_id = ?::uuid",
                Integer.class, child)).isEqualTo(1);
        // At most 2 hours extra in one day.
        for (int i = 0; i < 3; i++) {
            json(send("POST", "/api/v1/children/" + child + "/today/extensions", "{\"minutes\":30}", parent), 200);
        }
        assertThat(send("POST", "/api/v1/children/" + child + "/today/extensions", "{\"minutes\":30}", parent).statusCode()).isEqualTo(409);

        // Another family can't read or change any of it.
        String stranger = registerParent();
        assertThat(send("GET", "/api/v1/children/" + child + "/today", null, stranger).statusCode()).isIn(403, 404);
        assertThat(send("PUT", "/api/v1/children/" + child + "/daily-limit", "{\"minutes\":120}", stranger).statusCode()).isIn(403, 404);
        assertThat(send("POST", "/api/v1/children/" + child + "/today/extensions", "{\"minutes\":15}", stranger).statusCode()).isIn(403, 404);

        // Turned off: no limit at all.
        json(send("PUT", "/api/v1/children/" + child + "/daily-limit", "{\"minutes\":null}", parent), 200);
        assertThat(json(send("GET", "/api/v1/children/" + child + "/today", null, parent), 200).get("reached").asBoolean()).isFalse();
    }

    @Test
    void withoutALimitNothingIsEverRefused() throws Exception {
        String parent = registerParent();
        String child = createChild(parent);
        assertThat(send("POST", "/api/v1/children/" + child + "/today/extensions", "{\"minutes\":15}", parent).statusCode())
                .as("nothing to extend").isEqualTo(409);
        JsonNode steps = json(send("GET", "/api/v1/children/" + child + "/enrollments", null, parent), 200).get(0).get("nextStep");
        String attempt = start(parent, child, steps.get("activityVersionId").asString(), steps.get("learningPathItemId").asString(), 201);
        jdbc.update("update activity_attempts set started_at = started_at - interval '5 hours' where id = ?::uuid", attempt);
        assertThat(json(send("GET", "/api/v1/children/" + child + "/today", null, parent), 200).get("reached").asBoolean()).isFalse();
    }

    @Test
    void aFamilysLearningDayFollowsTheTimeZoneAParentChose() throws Exception {
        String parent = registerParent();
        String child = createChild(parent);
        JsonNode day = json(send("GET", "/api/v1/family/learning-day", null, parent), 200);
        assertThat(day.get("zone").asString()).isEqualTo("Asia/Phnom_Penh");
        assertThat(day.get("familyChoice").asBoolean()).isFalse();

        assertThat(send("PUT", "/api/v1/family/learning-day", "{\"zone\":\"Mars/Olympus\"}", parent).statusCode()).isEqualTo(400);
        ZoneId newYork = ZoneId.of("America/New_York");
        Assumptions.assumeTrue(java.time.LocalTime.now(newYork).isAfter(LocalTime.of(1, 0)), "too close to midnight in New York");
        day = json(send("PUT", "/api/v1/family/learning-day", "{\"zone\":\"America/New_York\"}", parent), 200);
        assertThat(day.get("zone").asString()).isEqualTo("America/New_York");
        assertThat(day.get("familyChoice").asBoolean()).isTrue();

        // The day, and the time counted in it, start at midnight in New York.
        JsonNode today = json(send("GET", "/api/v1/children/" + child + "/today", null, parent), 200);
        assertThat(today.get("zone").asString()).isEqualTo("America/New_York");
        assertThat(today.get("day").asString()).isEqualTo(LocalDate.now(newYork).toString());
        JsonNode steps = json(send("GET", "/api/v1/children/" + child + "/enrollments", null, parent), 200).get(0).get("nextStep");
        String attempt = start(parent, child, steps.get("activityVersionId").asString(), steps.get("learningPathItemId").asString(), 201);
        java.time.Instant newYorkMidnight = LocalDate.now(newYork).atStartOfDay(newYork).toInstant();
        jdbc.update("update activity_attempts set started_at = ?, completed_at = ?, status = 'COMPLETED' where id = ?::uuid",
                java.sql.Timestamp.from(newYorkMidnight.minusSeconds(3600)), java.sql.Timestamp.from(newYorkMidnight.minusSeconds(1800)), attempt);
        assertThat(json(send("GET", "/api/v1/children/" + child + "/today", null, parent), 200).get("usedMinutes").asInt())
                .as("learning before midnight in New York belongs to yesterday").isZero();
        jdbc.update("update activity_attempts set started_at = ?, completed_at = ? where id = ?::uuid",
                java.sql.Timestamp.from(newYorkMidnight.plusSeconds(60)), java.sql.Timestamp.from(newYorkMidnight.plusSeconds(60 + 20 * 60)), attempt);
        assertThat(json(send("GET", "/api/v1/children/" + child + "/today", null, parent), 200).get("usedMinutes").asInt()).isEqualTo(20);

        // In the family's data export, and audited; back to the platform's day on request.
        assertThat(json(send("GET", "/api/v1/account/data-export", null, parent), 200).get("learningDayZone").asString())
                .isEqualTo("America/New_York");
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action = 'FAMILY_LEARNING_DAY_ZONE_CHANGED' "
                + "and resource_id = (select family_id from children where id = ?::uuid)", Integer.class, child)).isEqualTo(1);
        assertThat(json(send("PUT", "/api/v1/family/learning-day", "{\"zone\":null}", parent), 200).get("familyChoice").asBoolean()).isFalse();
        assertThat(json(send("GET", "/api/v1/children/" + child + "/today", null, parent), 200).get("zone").asString())
                .isEqualTo("Asia/Phnom_Penh");
    }

    @Test
    void someoneInNoFamilyHasNoLearningDayToSet() throws Exception {
        String email = "daily-teacher-" + UUID.randomUUID() + "@example.com";
        accountRepository.saveAndFlush(com.platform.identity.entity.Account.register(email, passwordEncoder.encode(PASSWORD),
                com.platform.identity.entity.PlatformRole.TEACHER));
        String teacher = json(send("POST", "/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", null), 200)
                .get("accessToken").asString();
        assertThat(send("GET", "/api/v1/family/learning-day", null, teacher).statusCode()).isEqualTo(404);
        assertThat(send("PUT", "/api/v1/family/learning-day", "{\"zone\":\"Asia/Bangkok\"}", teacher).statusCode()).isEqualTo(404);
    }

    // ------------------------------------------------------------------ helpers

    private String start(String parent, String child, String activityVersionId, String pathItemId, Integer expected) throws Exception {
        HttpResponse<String> r = send("POST", "/api/v1/children/" + child + "/activity-attempts", "{\"activityVersionId\":\"" + activityVersionId + "\""
                + (pathItemId == null ? "" : ",\"learningPathItemId\":\"" + pathItemId + "\"") + "}", parent);
        if (expected != null) {
            assertThat(r.statusCode()).as(r.body()).isEqualTo(expected);
        } else {
            assertThat(r.statusCode()).as(r.body()).isIn(200, 201);
        }
        return JSON.readTree(r.body()).get("id").asString();
    }

    private void answerAll(String parent, String child, String attemptId, String activityVersionId) throws Exception {
        JsonNode content = json(send("GET", "/api/v1/activity-versions/" + activityVersionId + "?childId=" + child, null, parent), 200);
        for (JsonNode item : content.get("items")) {
            HttpResponse<String> r = send("POST", "/api/v1/activity-attempts/" + attemptId + "/answers", "{\"questionVersionId\":\""
                    + item.get("questionVersionId").asString() + "\",\"submittedAnswer\":\"3\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}",
                    parent);
            assertThat(r.statusCode()).as("answers are never refused: " + r.body()).isEqualTo(201);
        }
    }

    private String otherPublishedVersion(String... not) {
        return jdbc.queryForObject("select av.id::text from activity_versions av join activities a on a.id = av.activity_id "
                + "left join lessons l on l.id = a.lesson_id where av.status = 'PUBLISHED' and l.owner_account_id is null "
                + "and av.id::text not in (?, ?) and av.id::text like '00000000-add1-%' order by av.id limit 1", String.class, not[0], not[1]);
    }

    private String registerParent() throws Exception {
        String email = "daily-" + UUID.randomUUID() + "@example.com";
        JsonNode body = json(send("POST", "/api/v1/auth/register", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null), 201);
        return body.get("accessToken").asString();
    }

    private String createChild(String parent) throws Exception {
        return json(send("POST", "/api/v1/children", "{\"displayName\":\"Dara\",\"dateOfBirth\":\"" + LocalDate.now().minusYears(6) + "\"}", parent),
                201).get("id").asString();
    }

    private static JsonNode json(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> send(String method, String path, String body, String bearer) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

}
