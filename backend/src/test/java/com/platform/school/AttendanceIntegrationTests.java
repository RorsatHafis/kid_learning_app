package com.platform.school;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.PaidSchools;
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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * M12 attendance (owner decisions 2026-11-11) over real HTTP and PostgreSQL: a class's students are the school's own
 * records (no family child involved); the class's teacher and the school's principal take the daily register; a
 * recorded mark changes only with a reason, kept with the old and new values; a future day, a student from elsewhere
 * and a stranger are refused; leaving a class keeps the history; the month adds up.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class AttendanceIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Attendance-Test-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Value("${platform.learning.day-zone:Asia/Phnom_Penh}") private String zone;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SchoolService schoolService;
    @Autowired private SchoolStaffService schoolStaffService;
    @Autowired private SchoolStaffRepository schoolStaffRepository;
    @Autowired private ApplicationContext context;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void theClassTeacherTakesTheRegisterAndAChangedMarkKeepsWhyAndWhatItWas() throws Exception {
        Staff principal = staff(PlatformRole.PRINCIPAL);
        Staff teacher = staff(PlatformRole.TEACHER);
        SchoolClass schoolClass = classTaughtBy(principal, teacher);
        String path = "/api/v1/schools/classes/" + schoolClass.getId();

        // The school's own records, several at once; a name already in the class is left out and said so.
        JsonNode added = json(post(path + "/students", "{\"students\":[{\"name\":\"Sokha Chan\",\"number\":\"S-001\"},{\"name\":\"Dara  Kim\"},"
                + "{\"name\":\"sokha chan\"},{\"name\":\"  \"}]}", teacher.token()), 201);
        assertThat(added.get("added")).hasSize(2);
        assertThat(added.get("alreadyInClass").get(0).asString()).isEqualTo("sokha chan");
        assertThat(jdbc.queryForObject("select count(*) from school_students where school_id = ?", Integer.class, schoolClass.getSchoolId())).isEqualTo(2);
        String sokha = added.get("added").get(0).get("id").asString();
        String dara = added.get("added").get(1).get("id").asString();
        // The school's numbers are its own and unique in it.
        assertThat(post(path + "/students", "{\"students\":[{\"name\":\"Other\",\"number\":\"S-001\"}]}", teacher.token()).statusCode()).isEqualTo(409);

        // Today's register: nobody marked yet; marking is recorded as given.
        JsonNode register = json(get(path + "/attendance", teacher.token()), 200);
        assertThat(register.get("unmarked").asInt()).isEqualTo(2);
        assertThat(register.get("today").asBoolean()).isTrue();
        JsonNode marked = json(put(path + "/attendance", "{\"marks\":[{\"studentId\":\"" + sokha + "\",\"status\":\"PRESENT\"},"
                + "{\"studentId\":\"" + dara + "\",\"status\":\"ABSENT\",\"note\":\"Unwell\"}]}", teacher.token()), 200);
        assertThat(marked.get("counts").get("PRESENT").asInt()).isEqualTo(1);
        assertThat(marked.get("counts").get("ABSENT").asInt()).isEqualTo(1);
        assertThat(marked.get("unmarked").asInt()).isZero();

        // The same marks again change nothing; a different mark needs a reason.
        json(put(path + "/attendance", "{\"marks\":[{\"studentId\":\"" + sokha + "\",\"status\":\"PRESENT\"}]}", teacher.token()), 200);
        JsonNode noReason = json(put(path + "/attendance", "{\"marks\":[{\"studentId\":\"" + dara + "\",\"status\":\"LATE\"}]}", teacher.token()), 400);
        assertThat(noReason.get("detail").asString()).contains("Say why");
        JsonNode corrected = json(put(path + "/attendance", "{\"marks\":[{\"studentId\":\"" + dara + "\",\"status\":\"LATE\"}],"
                + "\"reason\":\"Arrived after the register\"}", principal.token()), 200);
        JsonNode daraRow = corrected.get("rows").valueStream().filter(r -> r.get("student").get("id").asString().equals(dara)).findFirst().orElseThrow();
        assertThat(daraRow.get("status").asString()).isEqualTo("LATE");
        assertThat(daraRow.get("corrected").asBoolean()).isTrue();
        assertThat(jdbc.queryForMap("select old_status, new_status, old_note, reason from attendance_corrections c join attendance_marks m "
                + "on m.id = c.mark_id where m.student_id = ?::uuid", dara))
                .containsEntry("old_status", "ABSENT").containsEntry("new_status", "LATE").containsEntry("old_note", "Unwell")
                .containsEntry("reason", "Arrived after the register");
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action = 'ATTENDANCE_CORRECTED' and resource_id = ?::uuid",
                Integer.class, dara)).isEqualTo(1);
        // The history of a correction can't be rewritten.
        assertThat(jdbc.queryForObject("select count(*) from attendance_corrections", Integer.class)).isPositive();

        // Refused: a day that hasn't come, a status that isn't one, a student from another class.
        LocalDate tomorrow = LocalDate.now(ZoneId.of(zone)).plusDays(1);
        assertThat(get(path + "/attendance?day=" + tomorrow, teacher.token()).statusCode()).isEqualTo(400);
        assertThat(put(path + "/attendance", "{\"marks\":[{\"studentId\":\"" + sokha + "\",\"status\":\"MAYBE\"}]}", teacher.token()).statusCode())
                .isEqualTo(400);
        SchoolClass other = schoolService.createClass(schoolClass.getSchoolId(), "Grade 2");
        assertThat(put("/api/v1/schools/classes/" + other.getId() + "/attendance", "{\"marks\":[{\"studentId\":\"" + sokha
                + "\",\"status\":\"PRESENT\"}]}", principal.token()).statusCode()).isEqualTo(400);

        // Only the class's people: another school's principal, an unassigned teacher, an administrator, a parent - none.
        Staff stranger = staff(PlatformRole.PRINCIPAL);
        schoolService.createSchool("Another School", stranger.id());
        assertThat(get(path + "/attendance", stranger.token()).statusCode()).isIn(403, 404);
        Staff unassigned = staff(PlatformRole.TEACHER);
        assertThat(get(path + "/students", unassigned.token()).statusCode()).isIn(403, 404);
        assertThat(get(path + "/students", staff(PlatformRole.ADMIN).token()).statusCode()).isIn(403, 404);
        assertThat(get(path + "/students", staff(PlatformRole.PARENT).token()).statusCode()).isIn(403, 404);

        // The month adds up.
        JsonNode month = json(get(path + "/attendance/month", teacher.token()), 200);
        assertThat(month.get("daysWithMarks").asInt()).isEqualTo(1);
        JsonNode daraMonth = month.get("rows").valueStream().filter(r -> r.get("student").get("id").asString().equals(dara)).findFirst().orElseThrow();
        assertThat(daraMonth.get("late").asInt()).isEqualTo(1);
        assertThat(daraMonth.get("absent").asInt()).isZero();

        // Leaving the class keeps the record and its history; today's register no longer lists them.
        assertThat(post(path + "/students/" + dara + "/leave", "", principal.token()).statusCode()).isEqualTo(204);
        assertThat(json(get(path + "/students", teacher.token()), 200)).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from attendance_marks where student_id = ?::uuid", Integer.class, dara)).isEqualTo(1);
        assertThat(json(get(path + "/attendance/month", teacher.token()), 200).get("rows")).hasSize(2);
        // No family child is involved anywhere.
        assertThat(jdbc.queryForObject("select count(*) from school_child_access where school_id = ?", Integer.class, schoolClass.getSchoolId())).isZero();
    }

    // ---------------------------------------------------------------------------------------------------------------

    private record Staff(UUID id, String token) {
    }

    private Staff staff(PlatformRole role) throws Exception {
        String email = "att-" + role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        Account account = accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), role));
        JsonNode body = json(post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", null), 200);
        String token = body.get("accessToken").asString();
        return new Staff(account.getId(), role == PlatformRole.ADMIN ? com.platform.TestAdmin.stepUp(port, token, PASSWORD) : token);
    }

    private SchoolClass classTaughtBy(Staff principal, Staff teacher) {
        School school = schoolService.createSchool("Attendance School", principal.id());
        PaidSchools.pay(context, school.getId());
        SchoolClass schoolClass = schoolService.createClass(school.getId(), "Grade 1");
        SchoolStaff membership = SchoolStaff.invite(school.getId(), teacher.id(), principal.id(), Instant.now());
        membership.accept(Instant.now());
        schoolStaffRepository.save(membership);
        schoolStaffService.assign(schoolClass.getId(), teacher.id(), principal.id());
        return schoolClass;
    }

    private static JsonNode json(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> get(String path, String bearer) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).GET(), bearer);
    }

    private HttpResponse<String> post(String path, String body, String bearer) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)), bearer);
    }

    private HttpResponse<String> put(String path, String body, String bearer) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body)), bearer);
    }

    private HttpResponse<String> send(HttpRequest.Builder builder, String bearer) throws Exception {
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
            com.platform.TestAdmin.withStepUp(builder, bearer);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
