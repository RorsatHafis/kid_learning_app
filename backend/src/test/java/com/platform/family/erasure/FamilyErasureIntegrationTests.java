package com.platform.family.erasure;

import com.icegreen.greenmail.util.GreenMail;
import com.platform.TestcontainersConfiguration;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.repository.AccountRepository;
import com.platform.school.entity.School;
import com.platform.school.entity.SchoolClass;
import com.platform.school.entity.SchoolStaff;
import com.platform.school.repository.SchoolStaffRepository;
import com.platform.school.service.SchoolService;
import com.platform.school.service.SchoolStaffService;
import jakarta.mail.Message;
import jakarta.mail.internet.MimeMessage;
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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deleting a child or a family account (Y3) over real HTTP, PostgreSQL and SMTP: the
 * password confirms the request, a child leaves learning and schools at once and can be
 * kept until the date, an account keeps working until the date, and then the sweep erases
 * every row about them - evidence included - and nothing of anyone else. Audit events stay.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class FamilyErasureIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Erasure-Test-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private GreenMail greenMail;
    @Autowired private FamilyErasureService erasure;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private SchoolService schoolService;
    @Autowired private SchoolStaffService schoolStaffService;
    @Autowired private SchoolStaffRepository schoolStaffRepository;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void theErasureCoversEveryTableThatHoldsRowsAboutAChild() {
        List<String> closure = jdbc.queryForList("""
                with recursive fk as (
                    select conrelid::regclass::text as t, confrelid::regclass::text as r from pg_constraint where contype = 'f'),
                closure(t) as (select 'children'::text union select fk.t from fk join closure c on fk.r = c.t)
                select t from closure
                """, String.class);
        assertThat(new HashSet<>(closure)).as("a new table about children must be added to the erasure")
                .isEqualTo(FamilyErasureService.CHILD_TABLES);
    }

    @Test
    void aDeletedChildLeavesLearningAndSchoolsAtOnceCanBeKeptAndIsThenErasedCompletely() throws Exception {
        erasure.eraseDue(); // anything an earlier run left due
        Parent parent = registerParent();
        String sokha = createChild(parent, "Sokha");
        String dara = createChild(parent, "Dara");
        Parent stranger = registerParent();
        String strangerChild = createChild(stranger, "Stranger");
        playFirstActivity(parent, sokha);
        playFirstActivity(stranger, strangerChild);
        String classPath = classWithCode();
        json(post("/api/v1/children/" + sokha + "/schools/join", "{\"code\":\"" + codeOf(classPath) + "\"}", parent.token()), 200);
        List<UUID> sokhasAttempts = jdbc.queryForList("select id from activity_attempts where child_id = ?::uuid", UUID.class, sokha);
        assertThat(sokhasAttempts).isNotEmpty();
        long strangerAnswers = answersOf(strangerChild);

        // The password confirms it; a wrong one changes nothing.
        assertThat(deleteChild(parent, sokha, "Not-The-Password-2026").statusCode()).isEqualTo(422);
        assertThat(get("/api/v1/children/" + sokha, parent.token()).statusCode()).isEqualTo(200);
        assertThat(deleteChild(stranger, sokha, PASSWORD).statusCode()).as("another family").isEqualTo(404);

        JsonNode scheduled = json(deleteChild(parent, sokha, PASSWORD), 202);
        Instant eraseAfter = Instant.parse(scheduled.get("eraseAfter").asString());
        assertThat(eraseAfter).isBetween(Instant.now().plus(Duration.ofDays(7)).minusSeconds(60),
                Instant.now().plus(Duration.ofDays(7)).plusSeconds(60));

        // Out of learning and schools now; still listed to the family, with the date.
        assertThat(get("/api/v1/children/" + sokha, parent.token()).statusCode()).isEqualTo(404);
        assertThat(get("/api/v1/children/" + sokha + "/enrollments", parent.token()).statusCode()).isEqualTo(404);
        JsonNode listed = childNamed(json(get("/api/v1/children", parent.token()), 200), "Sokha");
        assertThat(listed.get("status").asString()).isEqualTo("DELETION_PENDING");
        assertThat(listed.get("eraseAfter").asString()).isNotBlank();
        assertThat(jdbc.queryForObject("select status from school_child_access where child_id = ?::uuid", String.class, sokha))
                .isEqualTo("REVOKED");
        assertThat(waitForEmail(parent.email(), "Sokha will be deleted")).contains("Keep Sokha");

        // Kept: back in learning; the school's access does not come back by itself.
        JsonNode kept = json(delete("/api/v1/children/" + sokha + "/deletion", parent.token()), 200);
        assertThat(kept.get("status").asString()).isEqualTo("ACTIVE");
        assertThat(kept.get("eraseAfter").isNull()).isTrue();
        assertThat(get("/api/v1/children/" + sokha + "/enrollments", parent.token()).statusCode()).isEqualTo(200);
        assertThat(delete("/api/v1/children/" + sokha + "/deletion", parent.token()).statusCode()).isEqualTo(409);

        // Asked again, and the date passes.
        json(deleteChild(parent, sokha, PASSWORD), 202);
        assertThat(erasure.eraseDue()).as("nothing is due yet").isZero();
        jdbc.update("update children set erase_after = now() - interval '1 minute' where id = ?::uuid", sokha);
        assertThat(erasure.eraseDue()).isEqualTo(1);

        for (String table : FamilyErasureService.CHILD_TABLES) {
            String column = table.equals("children") ? "id" : "child_id";
            boolean hasColumn = Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from information_schema.columns "
                    + "where table_name = ? and column_name = ?)", Boolean.class, table, column));
            if (hasColumn) {
                assertThat(jdbc.queryForObject("select count(*) from " + table + " where " + column + " = ?::uuid", Long.class, sokha))
                        .as(table).isZero();
            }
        }
        assertThat(jdbc.queryForObject("select count(*) from answer_records where activity_attempt_id = any(?)", Long.class,
                (Object) sokhasAttempts.toArray(UUID[]::new))).isZero();
        // Nobody else's learning moved: the sibling and the other family are untouched.
        assertThat(get("/api/v1/children/" + dara + "/enrollments", parent.token()).statusCode()).isEqualTo(200);
        assertThat(answersOf(strangerChild)).isEqualTo(strangerAnswers);
        // The record that it happened stays, with no name in it.
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action = 'CHILD_ERASED' and resource_id = ?::uuid",
                Long.class, sokha)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_events where resource_id = ?::uuid and metadata::text like '%Sokha%'",
                Long.class, sokha)).isZero();
        assertThat(waitForEmail(parent.email(), "Sokha has been deleted")).contains("can't be undone");
        assertThat(delete("/api/v1/children/" + sokha + "/deletion", parent.token()).statusCode()).isEqualTo(404);
        assertThat(erasure.eraseDue()).as("done once").isZero();
    }

    @Test
    void aDeletedAccountKeepsWorkingUntilTheDateThenIsErasedWithItsFamily() throws Exception {
        erasure.eraseDue(); // anything an earlier run left due
        Parent parent = registerParent();
        String child = createChild(parent, "Bopha");
        playFirstActivity(parent, child);
        Parent stranger = registerParent();
        String strangerChild = createChild(stranger, "Stranger");
        playFirstActivity(stranger, strangerChild);
        String familyId = jdbc.queryForObject("select family_id::text from memberships where account_id = ?::uuid", String.class,
                parent.id());

        assertThat(post("/api/v1/account/deletion", "{\"currentPassword\":\"wrong-password-2026\"}", parent.token()).statusCode())
                .isEqualTo(422);
        JsonNode scheduled = json(post("/api/v1/account/deletion", "{\"currentPassword\":\"" + PASSWORD + "\"}", parent.token()), 202);
        assertThat(json(get("/api/v1/account", parent.token()), 200).get("eraseAfter").asString())
                .isEqualTo(scheduled.get("eraseAfter").asString());
        assertThat(post("/api/v1/account/deletion", "{\"currentPassword\":\"" + PASSWORD + "\"}", parent.token()).statusCode())
                .as("already asked").isEqualTo(409);
        // Everything keeps working until the date.
        assertThat(get("/api/v1/children/" + child + "/enrollments", parent.token()).statusCode()).isEqualTo(200);
        assertThat(waitForEmail(parent.email(), "Your Bookeyond Kid account will be deleted")).contains("Keep my account");

        assertThat(delete("/api/v1/account/deletion", parent.token()).statusCode()).isEqualTo(204);
        assertThat(json(get("/api/v1/account", parent.token()), 200).get("eraseAfter").isNull()).isTrue();
        assertThat(delete("/api/v1/account/deletion", parent.token()).statusCode()).isEqualTo(409);

        json(post("/api/v1/account/deletion", "{\"currentPassword\":\"" + PASSWORD + "\"}", parent.token()), 202);
        jdbc.update("update accounts set erase_after = now() - interval '1 minute' where id = ?::uuid", parent.id());
        assertThat(erasure.eraseDue()).isEqualTo(1);

        assertThat(count("accounts", "id", parent.id())).isZero();
        assertThat(count("families", "id", familyId)).isZero();
        assertThat(count("memberships", "family_id", familyId)).isZero();
        assertThat(count("children", "id", child)).isZero();
        assertThat(count("activity_attempts", "child_id", child)).isZero();
        assertThat(count("account_sessions", "account_id", parent.id())).isZero();
        assertThat(post("/api/v1/auth/login", "{\"email\":\"" + parent.email() + "\",\"password\":\"" + PASSWORD + "\"}", null)
                .statusCode()).isEqualTo(401);
        assertThat(post("/api/v1/auth/refresh", "{\"refreshToken\":\"" + parent.refreshToken() + "\"}", null).statusCode())
                .isEqualTo(401);
        assertThat(count("activity_attempts", "child_id", strangerChild)).isPositive();
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action = 'ACCOUNT_ERASED' and resource_id = ?::uuid",
                Long.class, parent.id())).isEqualTo(1);
        assertThat(waitForEmail(parent.email(), "has been deleted")).contains("can't be undone");
        // The address is free again.
        assertThat(post("/api/v1/auth/register", "{\"email\":\"" + parent.email() + "\",\"password\":\"" + PASSWORD
                + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null).statusCode()).isEqualTo(201);
    }

    @Test
    void onlyAFamilyAccountDeletesItselfHere() throws Exception {
        Account teacher = accountRepository.saveAndFlush(Account.register("erasure-teacher-" + UUID.randomUUID() + "@example.com",
                passwordEncoder.encode(PASSWORD), PlatformRole.TEACHER));
        String token = json(post("/api/v1/auth/login", "{\"email\":\"" + teacher.getEmail() + "\",\"password\":\"" + PASSWORD
                + "\"}", null), 200).get("accessToken").asString();

        assertThat(post("/api/v1/account/deletion", "{\"currentPassword\":\"" + PASSWORD + "\"}", token).statusCode()).isEqualTo(403);
        assertThat(post("/api/v1/account/deletion", "{\"currentPassword\":\"" + PASSWORD + "\"}", null).statusCode()).isEqualTo(401);
    }

    // ------------------------------------------------------------------ helpers

    private record Parent(String id, String email, String token, String refreshToken) {
    }

    private Parent registerParent() throws Exception {
        String email = "erasure-" + UUID.randomUUID() + "@example.com";
        JsonNode body = json(post("/api/v1/auth/register", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null), 201);
        jdbc.update("update accounts set email_verified_at = now() where normalized_email = ?", email);
        return new Parent(body.get("accountId").asString(), email, body.get("accessToken").asString(),
                body.get("refreshToken").asString());
    }

    private String createChild(Parent parent, String name) throws Exception {
        return json(post("/api/v1/children", "{\"displayName\":\"" + name + "\",\"dateOfBirth\":\""
                + LocalDate.now().minusYears(6) + "\"}", parent.token()), 201).get("id").asString();
    }

    private static JsonNode childNamed(JsonNode children, String name) {
        for (JsonNode child : children) {
            if (child.get("displayName").asString().equals(name)) return child;
        }
        throw new AssertionError("no child " + name);
    }

    /** Plays the child's first step, answering every question (wrong answers are evidence too). */
    private void playFirstActivity(Parent parent, String childId) throws Exception {
        JsonNode next = json(get("/api/v1/children/" + childId + "/enrollments", parent.token()), 200).get(0).get("nextStep");
        String attemptId = json(post("/api/v1/children/" + childId + "/activity-attempts",
                "{\"activityVersionId\":\"" + next.get("activityVersionId").asString() + "\",\"learningPathItemId\":\""
                        + next.get("learningPathItemId").asString() + "\"}", parent.token()), 201).get("id").asString();
        JsonNode content = json(get("/api/v1/activity-versions/" + next.get("activityVersionId").asString(), parent.token()), 200);
        for (JsonNode item : content.get("items")) {
            json(post("/api/v1/activity-attempts/" + attemptId + "/answers", "{\"questionVersionId\":\""
                    + item.get("questionVersionId").asString() + "\",\"submittedAnswer\":\"999\",\"idempotencyKey\":\""
                    + UUID.randomUUID() + "\"}", parent.token()), 201);
        }
        json(post("/api/v1/activity-attempts/" + attemptId + "/complete", "", parent.token()), 200);
    }

    /** A school class with the platform's Maths curriculum and an open join code; returns its API path. */
    private String classWithCode() throws Exception {
        UUID principalId = staff(PlatformRole.PRINCIPAL);
        UUID teacherId = staff(PlatformRole.TEACHER);
        School school = schoolService.createSchool("Erasure School " + UUID.randomUUID(), principalId);
        SchoolClass schoolClass = schoolService.createClass(school.getId(), "Grade 1");
        SchoolStaff membership = SchoolStaff.invite(school.getId(), teacherId, principalId, Instant.now());
        membership.accept(Instant.now());
        schoolStaffRepository.save(membership);
        schoolStaffService.assign(schoolClass.getId(), teacherId, principalId);
        teacherToken = login(teacherId);
        String classPath = "/api/v1/schools/classes/" + schoolClass.getId();
        json(post(classPath + "/join-code", "", teacherToken), 201);
        return classPath;
    }

    private String teacherToken;

    private String codeOf(String classPath) throws Exception {
        return json(get(classPath + "/join-code", teacherToken), 200).get("code").asString();
    }

    private UUID staff(PlatformRole role) {
        return accountRepository.saveAndFlush(Account.register("erasure-" + role.name().toLowerCase() + "-" + UUID.randomUUID()
                + "@example.com", passwordEncoder.encode(PASSWORD), role)).getId();
    }

    private String login(UUID accountId) throws Exception {
        String email = accountRepository.findById(accountId).orElseThrow().getEmail();
        return json(post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", null), 200)
                .get("accessToken").asString();
    }

    private long answersOf(String childId) {
        return jdbc.queryForObject("select count(*) from answer_records r join activity_attempts a on a.id = r.activity_attempt_id "
                + "where a.child_id = ?::uuid", Long.class, childId);
    }

    private long count(String table, String column, String id) {
        return jdbc.queryForObject("select count(*) from " + table + " where " + column + " = ?::uuid", Long.class, id);
    }

    private HttpResponse<String> deleteChild(Parent parent, String childId, String password) throws Exception {
        return post("/api/v1/children/" + childId + "/deletion", "{\"currentPassword\":\"" + password + "\"}", parent.token());
    }

    /** The body of the newest email to {@code to} whose subject contains {@code subject}. */
    private String waitForEmail(String to, String subject) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (true) {
            MimeMessage found = null;
            for (MimeMessage message : greenMail.getReceivedMessages()) {
                if (message.getRecipients(Message.RecipientType.TO)[0].toString().equalsIgnoreCase(to)
                        && message.getSubject().contains(subject)) {
                    found = message;
                }
            }
            if (found != null) return (String) found.getContent();
            assertThat(System.currentTimeMillis()).as("email '%s' to %s", subject, to).isLessThan(deadline);
            Thread.sleep(50);
        }
    }

    private static JsonNode json(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return response.body().isEmpty() ? null : JSON.readTree(response.body());
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (token != null) req.header("Authorization", "Bearer " + token);
        return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> delete(String path, String token) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).DELETE();
        if (token != null) req.header("Authorization", "Bearer " + token);
        return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body, String token) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) req.header("Authorization", "Bearer " + token);
        return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

}
