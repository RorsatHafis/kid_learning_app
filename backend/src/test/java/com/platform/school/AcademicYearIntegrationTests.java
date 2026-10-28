package com.platform.school;

import com.platform.PaidSchools;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M12 P1 over real HTTP and PostgreSQL: a principal sets up the school's academic years and terms, makes one current
 * and places classes in a year; the calendar's shared rules hold (dates in order, no overlapping years or terms, terms
 * inside their year, one current year, the current year can't be archived); every change is audited; and only the
 * school's own principal (or an administrator), while the school is paid, can do any of it.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class AcademicYearIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Academic-Year-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private org.springframework.context.ApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void aPrincipalSetsUpYearsAndTermsAndTheCalendarRulesHold() throws Exception {
        User principal = user(PlatformRole.PRINCIPAL);
        String schoolId = paidSchool(principal, "Calendar Primary");
        String base = "/api/v1/schools/" + schoolId + "/academic-years";

        // The first year becomes the current one.
        JsonNode first = json(post(base, year("2025-2026", "2025-11-03", "2026-08-28"), principal), 201);
        assertThat(first.get("current").asBoolean()).isTrue();
        String firstId = first.get("id").asString();

        // Terms inside the year; outside or overlapping are refused.
        String terms = base + "/" + firstId + "/terms";
        assertThat(post(terms, year("Term 1", "2025-11-03", "2026-03-27"), principal).statusCode()).isEqualTo(201);
        assertThat(post(terms, year("Term 2", "2026-04-06", "2026-08-28"), principal).statusCode()).isEqualTo(201);
        assertThat(detail(post(terms, year("Term 3", "2026-03-20", "2026-04-10"), principal), 409))
                .isEqualTo("These dates overlap another term of this year.");
        assertThat(detail(post(terms, year("Summer", "2026-08-01", "2026-09-30"), principal), 409))
                .isEqualTo("A term must fall within its academic year.");
        assertThat(detail(post(terms, year("term 1", "2026-03-28", "2026-04-02"), principal), 409))
                .isEqualTo("This year already has a term with that name.");
        assertThat(detail(post(terms, year("Bad", "2026-02-01", "2026-01-01"), principal), 400))
                .isEqualTo("The end date must be after the start date.");

        // A second year can't overlap the first or reuse its name; a later one is created, not current.
        assertThat(detail(post(base, year("Overlap", "2026-08-01", "2027-06-30"), principal), 409))
                .isEqualTo("These dates overlap another academic year of this school.");
        assertThat(detail(post(base, year("2025-2026", "2030-01-01", "2030-12-31"), principal), 409))
                .isEqualTo("This school already has an academic year with that name.");
        JsonNode second = json(post(base, year("2026-2027", "2026-11-02", "2027-08-27"), principal), 201);
        assertThat(second.get("current").asBoolean()).isFalse();
        String secondId = second.get("id").asString();

        // The year's dates can't shrink past its terms.
        assertThat(detail(put(base + "/" + firstId, year("2025-2026", "2025-12-01", "2026-08-28"), principal), 409))
                .isEqualTo("The year's dates must still contain all of its terms.");

        // Making the second year current leaves exactly one current year.
        assertThat(json(post(base + "/" + secondId + "/current", "", principal), 200).get("current").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from academic_years where school_id = ?::uuid and is_current",
                Integer.class, schoolId)).isEqualTo(1);
        JsonNode listed = json(get(base, principal), 200);
        assertThat(listed).hasSize(2);
        assertThat(listed.get(1).get("terms")).hasSize(2); // newest first: the first year is second

        // The current year can't be archived; the other can, and can't then take classes.
        assertThat(detail(post(base + "/" + secondId + "/archive", "", principal), 409))
                .isEqualTo("The current academic year can't be archived: make another year current first.");
        assertThat(json(post(base + "/" + firstId + "/archive", "", principal), 200).get("archived").asBoolean()).isTrue();

        // Classes are placed in an active year of their own school.
        String classId = json(post("/api/v1/schools/" + schoolId + "/classes", "{\"name\":\"Grade 2A\"}", principal), 201)
                .get("id").asString();
        String place = "/api/v1/schools/" + schoolId + "/classes/" + classId + "/academic-year";
        assertThat(json(put(place, "{\"academicYearId\":\"" + secondId + "\"}", principal), 200).get("academicYearId").asString())
                .isEqualTo(secondId);
        assertThat(detail(put(place, "{\"academicYearId\":\"" + firstId + "\"}", principal), 409))
                .isEqualTo("This academic year is archived.");
        assertThat(json(get("/api/v1/schools/classes/" + classId, principal), 200).has("name")).isTrue();
        assertThat(put(place, "{\"academicYearId\":null}", principal).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select academic_year_id from school_classes where id = ?::uuid", UUID.class, classId)).isNull();

        // Every change is audited.
        for (String action : List.of("ACADEMIC_YEAR_CREATED", "ACADEMIC_TERM_CREATED", "ACADEMIC_YEAR_MADE_CURRENT",
                "ACADEMIC_YEAR_ARCHIVED", "CLASS_ACADEMIC_YEAR_SET")) {
            assertThat(jdbc.queryForObject("select count(*) from audit_events where action = ? and actor_id = ?::uuid",
                    Integer.class, action, UUID.fromString(principal.id()))).as(action).isPositive();
        }
    }

    @Test
    void onlyTheSchoolsOwnPrincipalWhilePaidManagesItsCalendar() throws Exception {
        User principal = user(PlatformRole.PRINCIPAL);
        String schoolId = paidSchool(principal, "Own Calendar School");
        String base = "/api/v1/schools/" + schoolId + "/academic-years";
        String yearId = json(post(base, year("2025-2026", "2025-11-03", "2026-08-28"), principal), 201).get("id").asString();

        User otherPrincipal = user(PlatformRole.PRINCIPAL);
        String otherSchool = paidSchool(otherPrincipal, "Other Calendar School");
        User teacher = user(PlatformRole.TEACHER);
        User parent = user(PlatformRole.PARENT);
        for (User outsider : List.of(otherPrincipal, teacher, parent)) {
            assertThat(get(base, outsider).statusCode()).as(outsider.role() + " reads").isIn(403, 404);
            assertThat(post(base, year("Theirs", "2027-11-01", "2028-08-31"), outsider).statusCode())
                    .as(outsider.role() + " creates").isIn(403, 404);
            assertThat(post(base + "/" + yearId + "/terms", year("T", "2025-11-03", "2025-12-01"), outsider).statusCode())
                    .as(outsider.role() + " adds a term").isIn(403, 404);
        }
        assertThat(jdbc.queryForObject("select count(*) from academic_years where school_id = ?::uuid", Integer.class, schoolId))
                .isEqualTo(1);

        // Another school's class can't be placed in this school's year, even by its own principal through this school.
        String foreignClass = json(post("/api/v1/schools/" + otherSchool + "/classes", "{\"name\":\"Theirs\"}", otherPrincipal), 201)
                .get("id").asString();
        assertThat(put("/api/v1/schools/" + schoolId + "/classes/" + foreignClass + "/academic-year",
                "{\"academicYearId\":\"" + yearId + "\"}", principal).statusCode()).isEqualTo(404);

        // An unpaid school's calendar waits for its payment, like its other services.
        User unpaidPrincipal = user(PlatformRole.PRINCIPAL);
        String unpaid = json(post("/api/v1/schools", "{\"name\":\"Unpaid Calendar School\"}", unpaidPrincipal), 201).get("id").asString();
        assertThat(post("/api/v1/schools/" + unpaid + "/academic-years", year("2025-2026", "2025-11-03", "2026-08-28"),
                unpaidPrincipal).statusCode()).isGreaterThanOrEqualTo(400);
        assertThat(jdbc.queryForObject("select count(*) from academic_years where school_id = ?::uuid", Integer.class, unpaid))
                .isZero();

        // An administrator may manage it (audited, as everything here).
        User admin = user(PlatformRole.ADMIN);
        assertThat(get(base, admin).statusCode()).isEqualTo(200);
    }

    @Test
    void yearEndRolloverCopiesTheClassStructureOnceAndNothingElse() throws Exception {
        User principal = user(PlatformRole.PRINCIPAL);
        String schoolId = paidSchool(principal, "Rollover Primary");
        String base = "/api/v1/schools/" + schoolId + "/academic-years";
        String from = json(post(base, year("2025-2026", "2025-11-03", "2026-08-28"), principal), 201).get("id").asString();
        String to = json(post(base, year("2026-2027", "2026-11-02", "2027-08-27"), principal), 201).get("id").asString();
        for (String name : List.of("Grade 1A", "Grade 2A", "Old Class")) {
            String classId = json(post("/api/v1/schools/" + schoolId + "/classes", "{\"name\":\"" + name + "\"}", principal), 201)
                    .get("id").asString();
            assertThat(put("/api/v1/schools/" + schoolId + "/classes/" + classId + "/academic-year",
                    "{\"academicYearId\":\"" + from + "\"}", principal).statusCode()).isEqualTo(200);
            if (name.equals("Old Class")) {
                jdbc.update("update school_classes set status = 'ARCHIVED' where id = ?::uuid", classId);
            }
        }

        JsonNode copied = json(post(base + "/" + to + "/copy-classes", "{\"fromYearId\":\"" + from + "\"}", principal), 200);
        assertThat(copied.get("created")).extracting(JsonNode::asString).containsExactly("Grade 1A", "Grade 2A");
        assertThat(copied.get("alreadyThere")).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from school_classes where academic_year_id = ?::uuid", Integer.class, to))
                .isEqualTo(2);
        // The new classes start empty: no children, teachers or codes come with them.
        assertThat(jdbc.queryForObject("""
                select count(*) from class_enrollments e join school_classes c on c.id = e.school_class_id
                where c.academic_year_id = ?::uuid""", Integer.class, to)).isZero();
        assertThat(jdbc.queryForObject("""
                select count(*) from teacher_assignments a join school_classes c on c.id = a.school_class_id
                where c.academic_year_id = ?::uuid""", Integer.class, to)).isZero();

        // Again: nothing twice.
        JsonNode again = json(post(base + "/" + to + "/copy-classes", "{\"fromYearId\":\"" + from + "\"}", principal), 200);
        assertThat(again.get("created")).isEmpty();
        assertThat(again.get("alreadyThere")).extracting(JsonNode::asString).containsExactly("Grade 1A", "Grade 2A");
        assertThat(jdbc.queryForObject("select count(*) from school_classes where academic_year_id = ?::uuid", Integer.class, to))
                .isEqualTo(2);

        assertThat(post(base + "/" + to + "/copy-classes", "{\"fromYearId\":\"" + to + "\"}", principal).statusCode()).isEqualTo(400);
        // An archived year takes no classes.
        assertThat(json(post(base + "/" + to + "/current", "", principal), 200).get("current").asBoolean()).isTrue();
        assertThat(post(base + "/" + from + "/archive", "", principal).statusCode()).isEqualTo(200);
        assertThat(detail(post(base + "/" + from + "/copy-classes", "{\"fromYearId\":\"" + to + "\"}", principal), 409))
                .isEqualTo("This academic year is archived.");
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action = 'ACADEMIC_YEAR_CLASSES_COPIED' and actor_id = ?::uuid",
                Integer.class, UUID.fromString(principal.id()))).isEqualTo(2);
    }

    // ------------------------------------------------------------------ helpers

    private static String year(String name, String startsOn, String endsOn) {
        return "{\"name\":\"" + name + "\",\"startsOn\":\"" + startsOn + "\",\"endsOn\":\"" + endsOn + "\"}";
    }

    private record User(String id, String email, PlatformRole role, String token) {
    }

    private User user(PlatformRole role) throws Exception {
        String email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), role));
        JsonNode body = json(post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", null), 200);
        return new User(body.get("accountId").asString(), email, role, body.get("accessToken").asString());
    }

    private String paidSchool(User principal, String name) throws Exception {
        String schoolId = json(post("/api/v1/schools", "{\"name\":\"" + name + "\"}", principal), 201).get("id").asString();
        PaidSchools.pay(context, UUID.fromString(schoolId));
        return schoolId;
    }

    private static JsonNode json(HttpResponse<String> response, int expected) throws Exception {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expected);
        return JSON.readTree(response.body());
    }

    private static String detail(HttpResponse<String> response, int expected) throws Exception {
        return json(response, expected).get("detail").asString();
    }

    private HttpResponse<String> get(String path, User user) throws Exception {
        return http.send(request(path, user).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body, User user) throws Exception {
        return http.send(request(path, user).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> put(String path, String body, User user) throws Exception {
        return http.send(request(path, user).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder request(String path, User user) {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (user != null) {
            req.header("Authorization", "Bearer " + user.token());
        }
        return req;
    }
}
