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

    @Test
    void studentsMoveToAnotherClassAsTheSameRecordsWithTheirHistory() throws Exception {
        Staff principal = staff(PlatformRole.PRINCIPAL);
        Staff teacher = staff(PlatformRole.TEACHER);
        SchoolClass grade1 = classTaughtBy(principal, teacher);
        String from = "/api/v1/schools/classes/" + grade1.getId();
        JsonNode added = json(post(from + "/students", "{\"students\":[{\"name\":\"Sokha Chan\",\"number\":\"S-101\"},{\"name\":\"Dara Kim\"}]}",
                teacher.token()), 201);
        String sokha = added.get("added").get(0).get("id").asString();
        String dara = added.get("added").get(1).get("id").asString();
        json(put(from + "/attendance", "{\"marks\":[{\"studentId\":\"" + sokha + "\",\"status\":\"PRESENT\"}]}", teacher.token()), 200);

        SchoolClass grade2 = schoolService.createClass(grade1.getSchoolId(), "Grade 2");
        String to = "/api/v1/schools/classes/" + grade2.getId();
        String both = "{\"fromClassId\":\"" + grade1.getId() + "\",\"studentIds\":[\"" + sokha + "\",\"" + dara + "\"]}";
        // The teacher of Grade 1 only can't place students in a class they don't teach.
        assertThat(post(to + "/students/move", both, teacher.token()).statusCode()).isIn(403, 404);

        // The principal moves them: the same records (number kept, no second record); they leave Grade 1 today.
        JsonNode moved = json(post(to + "/students/move", both, principal.token()), 200);
        assertThat(moved.get("added").valueStream().map(s -> s.get("id").asString())).containsExactlyInAnyOrder(sokha, dara);
        assertThat(moved.get("added").valueStream().filter(s -> s.get("id").asString().equals(sokha)).findFirst().orElseThrow()
                .get("number").asString()).isEqualTo("S-101");
        assertThat(jdbc.queryForObject("select count(*) from school_students where school_id = ?", Integer.class, grade1.getSchoolId())).isEqualTo(2);
        assertThat(json(get(from + "/students", teacher.token()), 200)).isEmpty();
        assertThat(json(get(to + "/students", principal.token()), 200)).hasSize(2);
        // Grade 1's history stays Grade 1's: the mark and the month are still there.
        assertThat(jdbc.queryForObject("select school_class_id from attendance_marks where student_id = ?::uuid", UUID.class, sokha))
                .isEqualTo(grade1.getId());
        assertThat(json(get(from + "/attendance/month", teacher.token()), 200).get("rows")).hasSize(2);
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action = 'SCHOOL_STUDENTS_MOVED' and resource_id = ?",
                Integer.class, grade2.getId())).isEqualTo(1);

        // Today's mark, taken in Grade 1, isn't overwritten from Grade 2; an unmarked student is marked as usual.
        JsonNode taken = json(put(to + "/attendance", "{\"marks\":[{\"studentId\":\"" + sokha + "\",\"status\":\"ABSENT\"}],"
                + "\"reason\":\"Moved class\"}", principal.token()), 409);
        assertThat(taken.get("detail").asString()).contains("already taken in another class");
        json(put(to + "/attendance", "{\"marks\":[{\"studentId\":\"" + dara + "\",\"status\":\"PRESENT\"}]}", principal.token()), 200);

        // Refused: students no longer in the class named, the same class, nobody chosen, another school's class.
        assertThat(post(to + "/students/move", both, principal.token()).statusCode()).isEqualTo(404);
        assertThat(post(to + "/students/move", "{\"fromClassId\":\"" + grade2.getId() + "\",\"studentIds\":[\"" + dara + "\"]}",
                principal.token()).statusCode()).isEqualTo(400);
        assertThat(post(to + "/students/move", "{\"fromClassId\":\"" + grade1.getId() + "\",\"studentIds\":[]}", principal.token())
                .statusCode()).isEqualTo(400);
        Staff stranger = staff(PlatformRole.PRINCIPAL);
        School elsewhere = schoolService.createSchool("Another School", stranger.id());
        PaidSchools.pay(context, elsewhere.getId());
        SchoolClass theirs = schoolService.createClass(elsewhere.getId(), "Theirs");
        assertThat(post("/api/v1/schools/classes/" + theirs.getId() + "/students/move", "{\"fromClassId\":\"" + grade2.getId()
                + "\",\"studentIds\":[\"" + dara + "\"]}", stranger.token()).statusCode()).isIn(403, 404);
        assertThat(json(get(to + "/students", principal.token()), 200)).hasSize(2);

        // From an archived class (last year's): its list stays as it was; the students join the new class.
        jdbc.update("update school_classes set status = 'ARCHIVED' where id = ?", grade2.getId());
        SchoolClass grade3 = schoolService.createClass(grade1.getSchoolId(), "Grade 3");
        JsonNode rolled = json(post("/api/v1/schools/classes/" + grade3.getId() + "/students/move", "{\"fromClassId\":\"" + grade2.getId()
                + "\",\"studentIds\":[\"" + dara + "\",\"" + dara + "\"]}", principal.token()), 200);
        assertThat(rolled.get("added")).hasSize(1);
        assertThat(json(get(to + "/students", principal.token()), 200)).hasSize(2);
        assertThat(json(get("/api/v1/schools/classes/" + grade3.getId() + "/students", principal.token()), 200)).hasSize(1);
        // Already there: said so, not added twice.
        JsonNode again = json(post("/api/v1/schools/classes/" + grade3.getId() + "/students/move", "{\"fromClassId\":\"" + grade2.getId()
                + "\",\"studentIds\":[\"" + dara + "\"]}", principal.token()), 200);
        assertThat(again.get("alreadyInClass").get(0).asString()).isEqualTo("Dara Kim");
        assertThat(again.get("added")).isEmpty();

        // Typing a name already in another class (here: last year's) isn't a new record unless the caller says so:
        // it is held back with where they are, so the same student is brought over instead of split in two.
        SchoolClass grade4 = schoolService.createClass(grade1.getSchoolId(), "Grade 4");
        String four = "/api/v1/schools/classes/" + grade4.getId();
        JsonNode held = json(post(four + "/students", "{\"students\":[{\"name\":\"sokha chan\"},{\"name\":\"Vannak Ly\"}]}", principal.token()), 201);
        assertThat(held.get("added")).hasSize(1);
        assertThat(held.get("atSchool").get(0).get("name").asString()).isEqualTo("sokha chan");
        assertThat(held.get("atSchool").get(0).get("className").asString()).isEqualTo("Grade 2");
        assertThat(held.get("atSchool").get(0).get("classId").asString()).isEqualTo(grade2.getId().toString());
        int records = jdbc.queryForObject("select count(*) from school_students where school_id = ?", Integer.class, grade1.getSchoolId());
        // Confirmed as a different student with the same name: added.
        JsonNode confirmed = json(post(four + "/students", "{\"students\":[{\"name\":\"Sokha Chan\"}],\"sameNameIsNew\":true}",
                principal.token()), 201);
        assertThat(confirmed.get("added")).hasSize(1);
        assertThat(confirmed.get("atSchool")).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from school_students where school_id = ?", Integer.class, grade1.getSchoolId()))
                .isEqualTo(records + 1);
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
