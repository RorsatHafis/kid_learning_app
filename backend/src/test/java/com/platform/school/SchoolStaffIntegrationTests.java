package com.platform.school;

import com.icegreen.greenmail.util.GreenMail;
import com.platform.TestcontainersConfiguration;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.repository.AccountRepository;
import com.platform.curriculum.entity.Subject;
import com.platform.curriculum.repository.SubjectRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A school's teachers over real HTTP, PostgreSQL and SMTP: a principal invites by email, a
 * new teacher chooses their own password from the emailed link, accepts in the app, and is
 * assigned to a class; school staff and class assignment stay distinct relationships. Also
 * the boundaries: other principals, teachers and parents; other schools' teachers;
 * someone else's invitation; accounts that can't be teachers; removal ending access.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class SchoolStaffIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "School-Staff-Pass-2026";
    private static final Pattern SETUP_LINK = Pattern.compile("http://localhost:5173/set-up-account#token=([A-Za-z0-9_-]+)");

    @Value("${local.server.port}") private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private GreenMail greenMail;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private SubjectRepository subjectRepository;

    private final HttpClient http = HttpClient.newHttpClient();

    // ------------------------------------------------------------------ the whole journey

    @Test
    void aNewTeacherIsInvitedChoosesTheirOwnPasswordJoinsAndIsAssignedToAClass() throws Exception {
        User principal = user(PlatformRole.PRINCIPAL);
        String schoolId = createSchool(principal, "Sunrise Primary");
        String classId = createClass(principal, schoolId, "Grade 1A");
        String email = "new-teacher-" + UUID.randomUUID() + "@example.com";

        JsonNode invited = json(post("/api/v1/schools/" + schoolId + "/staff", "{\"email\":\"" + email + "\"}", principal), 201);
        assertThat(invited.get("status").asString()).isEqualTo("INVITED");
        String teacherId = invited.get("accountId").asString();

        // Nobody knows the new account's password: it cannot sign in until its owner chooses one.
        assertThat(loginStatus(email, PASSWORD)).isEqualTo(401);
        String token = setupToken(email, 1).getFirst();
        assertThat(post("/api/v1/auth/account-setup/confirm",
                "{\"token\":\"" + token + "\",\"newPassword\":\"" + PASSWORD + "\"}", null).statusCode()).isEqualTo(204);
        assertThat(post("/api/v1/auth/account-setup/confirm",
                "{\"token\":\"" + token + "\",\"newPassword\":\"Another-Pass-2026\"}", null).statusCode()).as("single use").isEqualTo(400);
        User teacher = login(email, PlatformRole.TEACHER);
        assertThat(teacher.id()).isEqualTo(teacherId);

        // Invited is not staff: the principal cannot assign them yet.
        assertThat(assign(principal, classId, teacherId).statusCode()).isEqualTo(404);

        JsonNode invitations = json(get("/api/v1/schools/invitations/mine", teacher), 200);
        assertThat(invitations).hasSize(1);
        assertThat(invitations.get(0).get("schoolName").asString()).isEqualTo("Sunrise Primary");
        assertThat(post("/api/v1/schools/invitations/" + invitations.get(0).get("id").asString() + "/accept", "", teacher)
                .statusCode()).isEqualTo(204);
        assertThat(json(get("/api/v1/schools/invitations/mine", teacher), 200)).isEmpty();

        assertThat(assign(principal, classId, teacherId).statusCode()).isEqualTo(201);
        JsonNode myClasses = json(get("/api/v1/schools/classes/mine", teacher), 200);
        assertThat(myClasses).hasSize(1);
        JsonNode schoolClass = json(get("/api/v1/schools/classes/" + classId, teacher), 200);
        assertThat(schoolClass.get("teachers").get(0).get("email").asString()).isEqualTo(email);
        JsonNode classes = json(get("/api/v1/schools/" + schoolId + "/classes", principal), 200);
        assertThat(classes.get(0).get("teachers")).hasSize(1);

        // Unassigning ends the teacher's access to that class.
        assertThat(delete("/api/v1/schools/classes/" + classId + "/teachers/" + teacherId, principal).statusCode()).isEqualTo(204);
        assertThat(get("/api/v1/schools/classes/" + classId, teacher).statusCode()).isEqualTo(403);
        assertThat(json(get("/api/v1/schools/classes/mine", teacher), 200)).isEmpty();

        assertThat(auditCount(principal.id(), "SCHOOL_STAFF_INVITED")).isEqualTo(1);
        assertThat(auditCount(teacherId, "SCHOOL_STAFF_JOINED")).isEqualTo(1);
        assertThat(auditCount(teacherId, "ACCOUNT_SETUP_COMPLETED")).isEqualTo(1);
    }

    @Test
    void anExistingTeacherIsToldToSignInAndCanDeclineThenBeInvitedAgain() throws Exception {
        User principal = user(PlatformRole.PRINCIPAL);
        String schoolId = createSchool(principal, "Riverside School");
        User teacher = user(PlatformRole.TEACHER);

        json(post("/api/v1/schools/" + schoolId + "/staff", "{\"email\":\"" + teacher.email() + "\"}", principal), 201);

        MimeMessage invite = awaitMessages(teacher.email(), 1).getFirst();
        String body = (String) invite.getContent();
        assertThat(body).contains("http://localhost:5173/login").doesNotContain("#token=");
        String invitationId = json(get("/api/v1/schools/invitations/mine", teacher), 200).get(0).get("id").asString();

        assertThat(post("/api/v1/schools/invitations/" + invitationId + "/decline", "", teacher).statusCode()).isEqualTo(204);
        assertThat(post("/api/v1/schools/invitations/" + invitationId + "/accept", "", teacher).statusCode())
                .as("a declined invitation is closed").isEqualTo(409);
        assertThat(staffStatus(schoolId, teacher.id())).isEqualTo("DECLINED");

        json(post("/api/v1/schools/" + schoolId + "/staff", "{\"email\":\"" + teacher.email() + "\"}", principal), 201);
        assertThat(staffStatus(schoolId, teacher.id())).isEqualTo("INVITED");
        assertThat(jdbc.queryForObject("select count(*) from school_staff where school_id = ?::uuid", Integer.class,
                UUID.fromString(schoolId))).as("one row per person per school").isEqualTo(1);
    }

    // ------------------------------------------------------------------ refusals

    @Test
    void onlyTeachersCanBeInvitedAndNobodyTwice() throws Exception {
        User principal = user(PlatformRole.PRINCIPAL);
        String schoolId = createSchool(principal, "Hillside School");
        User parent = user(PlatformRole.PARENT);
        User otherPrincipal = user(PlatformRole.PRINCIPAL);
        User teacher = user(PlatformRole.TEACHER);

        assertThat(post("/api/v1/schools/" + schoolId + "/staff", "{\"email\":\"" + parent.email() + "\"}", principal)
                .statusCode()).isEqualTo(409);
        assertThat(post("/api/v1/schools/" + schoolId + "/staff", "{\"email\":\"" + otherPrincipal.email() + "\"}", principal)
                .statusCode()).isEqualTo(409);
        assertThat(accountRepository.findById(UUID.fromString(parent.id())).orElseThrow().getPlatformRole())
                .isEqualTo(PlatformRole.PARENT);

        assertThat(post("/api/v1/schools/" + schoolId + "/staff", "{\"email\":\"" + teacher.email() + "\"}", principal)
                .statusCode()).isEqualTo(201);
        HttpResponse<String> again = post("/api/v1/schools/" + schoolId + "/staff", "{\"email\":\"" + teacher.email() + "\"}", principal);
        assertThat(again.statusCode()).as("invited again within a minute").isEqualTo(429);

        accept(teacher, schoolId);
        jdbc.update("update school_staff set invited_at = invited_at - interval '2 minutes' where school_id = ?::uuid",
                UUID.fromString(schoolId));
        assertThat(post("/api/v1/schools/" + schoolId + "/staff", "{\"email\":\"" + teacher.email() + "\"}", principal)
                .statusCode()).as("already on the staff").isEqualTo(409);
        assertThat(post("/api/v1/schools/" + schoolId + "/staff", "{\"email\":\"not-an-email\"}", principal)
                .statusCode()).isEqualTo(400);
    }

    @Test
    void nobodyElseCanManageTheSchoolsStaff() throws Exception {
        User principal = user(PlatformRole.PRINCIPAL);
        String schoolId = createSchool(principal, "Lakeside School");
        User otherPrincipal = user(PlatformRole.PRINCIPAL);
        User teacher = user(PlatformRole.TEACHER);
        User parent = user(PlatformRole.PARENT);
        String invite = "{\"email\":\"someone-" + UUID.randomUUID() + "@example.com\"}";

        for (User outsider : List.of(otherPrincipal, teacher, parent)) {
            assertThat(get("/api/v1/schools/" + schoolId + "/staff", outsider).statusCode()).as(outsider.role().name()).isEqualTo(403);
            assertThat(post("/api/v1/schools/" + schoolId + "/staff", invite, outsider).statusCode()).as(outsider.role().name()).isEqualTo(403);
            assertThat(get("/api/v1/schools/" + schoolId + "/classes", outsider).statusCode()).as(outsider.role().name()).isEqualTo(403);
        }
        String membershipId = json(post("/api/v1/schools/" + schoolId + "/staff",
                "{\"email\":\"" + teacher.email() + "\"}", principal), 201).get("id").asString();
        assertThat(delete("/api/v1/schools/" + schoolId + "/staff/" + membershipId, otherPrincipal).statusCode()).isEqualTo(403);
        // A membership of this school cannot be removed through another school's address.
        String otherSchoolId = createSchool(otherPrincipal, "Other School");
        assertThat(delete("/api/v1/schools/" + otherSchoolId + "/staff/" + membershipId, otherPrincipal).statusCode()).isEqualTo(404);
        assertThat(staffStatus(schoolId, teacher.id())).isEqualTo("INVITED");
        assertThat(get("/api/v1/schools/invitations/mine", principal).statusCode()).isEqualTo(403);
    }

    @Test
    void anInvitationCanOnlyBeAnsweredByTheTeacherItWasSentTo() throws Exception {
        User principal = user(PlatformRole.PRINCIPAL);
        String schoolId = createSchool(principal, "Meadow School");
        User invited = user(PlatformRole.TEACHER);
        User otherTeacher = user(PlatformRole.TEACHER);
        String invitationId = json(post("/api/v1/schools/" + schoolId + "/staff",
                "{\"email\":\"" + invited.email() + "\"}", principal), 201).get("id").asString();

        assertThat(post("/api/v1/schools/invitations/" + invitationId + "/accept", "", otherTeacher).statusCode()).isEqualTo(404);
        assertThat(post("/api/v1/schools/invitations/" + invitationId + "/accept", "", principal).statusCode()).isEqualTo(403);
        assertThat(staffStatus(schoolId, invited.id())).isEqualTo("INVITED");
        assertThat(staffStatus(schoolId, otherTeacher.id())).isNull();
    }

    @Test
    void aTeacherFromAnotherSchoolCannotBeAssigned() throws Exception {
        User principal = user(PlatformRole.PRINCIPAL);
        String schoolId = createSchool(principal, "North School");
        String classId = createClass(principal, schoolId, "Grade 2");
        User otherPrincipal = user(PlatformRole.PRINCIPAL);
        String otherSchoolId = createSchool(otherPrincipal, "South School");
        User theirTeacher = user(PlatformRole.TEACHER);
        json(post("/api/v1/schools/" + otherSchoolId + "/staff", "{\"email\":\"" + theirTeacher.email() + "\"}", otherPrincipal), 201);
        accept(theirTeacher, otherSchoolId);

        assertThat(assign(principal, classId, theirTeacher.id()).statusCode()).isEqualTo(404);
        assertThat(assign(otherPrincipal, classId, theirTeacher.id()).statusCode()).as("not their class").isEqualTo(403);
        assertThat(json(get("/api/v1/schools/classes/mine", theirTeacher), 200)).isEmpty();
    }

    @Test
    void removingATeacherFromTheStaffEndsTheirClassesThereOnly() throws Exception {
        User principalA = user(PlatformRole.PRINCIPAL);
        String schoolA = createSchool(principalA, "School A");
        String classA = createClass(principalA, schoolA, "A1");
        User principalB = user(PlatformRole.PRINCIPAL);
        String schoolB = createSchool(principalB, "School B");
        String classB = createClass(principalB, schoolB, "B1");
        User teacher = user(PlatformRole.TEACHER);
        String membershipA = json(post("/api/v1/schools/" + schoolA + "/staff", "{\"email\":\"" + teacher.email() + "\"}", principalA), 201)
                .get("id").asString();
        accept(teacher, schoolA);
        json(post("/api/v1/schools/" + schoolB + "/staff", "{\"email\":\"" + teacher.email() + "\"}", principalB), 201);
        accept(teacher, schoolB);
        assertThat(assign(principalA, classA, teacher.id()).statusCode()).isEqualTo(201);
        assertThat(assign(principalB, classB, teacher.id()).statusCode()).isEqualTo(201);

        assertThat(delete("/api/v1/schools/" + schoolA + "/staff/" + membershipA, principalA).statusCode()).isEqualTo(204);

        assertThat(get("/api/v1/schools/classes/" + classA, teacher).statusCode()).isEqualTo(403);
        assertThat(get("/api/v1/schools/classes/" + classB, teacher).statusCode()).isEqualTo(200);
        assertThat(staffStatus(schoolA, teacher.id())).isEqualTo("REMOVED");
        assertThat(assign(principalA, classA, teacher.id()).statusCode()).as("no longer staff").isEqualTo(404);
    }

    @Test
    void aTeacherWhoNeverChoseAPasswordIsSentAFreshSetupLinkWhenInvitedAgain() throws Exception {
        User principal = user(PlatformRole.PRINCIPAL);
        String schoolId = createSchool(principal, "Garden School");
        String email = "slow-teacher-" + UUID.randomUUID() + "@example.com";
        String membershipId = json(post("/api/v1/schools/" + schoolId + "/staff", "{\"email\":\"" + email + "\"}", principal), 201)
                .get("id").asString();
        String first = setupToken(email, 1).getFirst();

        // Withdrawn, then invited again after the one-minute gap.
        assertThat(delete("/api/v1/schools/" + schoolId + "/staff/" + membershipId, principal).statusCode()).isEqualTo(204);
        jdbc.update("update school_staff set invited_at = invited_at - interval '2 minutes' where id = ?::uuid", UUID.fromString(membershipId));
        jdbc.update("update account_action_tokens set issued_at = issued_at - interval '2 minutes', expires_at = expires_at - interval '2 minutes' "
                + "where account_id = (select id from accounts where normalized_email = ?)", email);
        json(post("/api/v1/schools/" + schoolId + "/staff", "{\"email\":\"" + email + "\"}", principal), 201);

        List<String> tokens = setupToken(email, 2);
        assertThat(tokens).hasSize(2);
        assertThat(post("/api/v1/auth/account-setup/confirm",
                "{\"token\":\"" + first + "\",\"newPassword\":\"" + PASSWORD + "\"}", null).statusCode()).as("replaced").isEqualTo(400);
        assertThat(post("/api/v1/auth/account-setup/confirm",
                "{\"token\":\"" + tokens.get(1) + "\",\"newPassword\":\"" + PASSWORD + "\"}", null).statusCode()).isEqualTo(204);
        assertThat(loginStatus(email, PASSWORD)).isEqualTo(200);
    }

    @Test
    void aPrincipalCanEditOnlyTheirOwnTeachersContent() throws Exception {
        User principal = user(PlatformRole.PRINCIPAL);
        String schoolId = createSchool(principal, "Content School");
        User teacher = user(PlatformRole.TEACHER);
        json(post("/api/v1/schools/" + schoolId + "/staff", "{\"email\":\"" + teacher.email() + "\"}", principal), 201);
        User otherPrincipal = user(PlatformRole.PRINCIPAL);
        createSchool(otherPrincipal, "Elsewhere School");
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        UUID subjectId = subjectRepository.save(Subject.create("STAFF_" + suffix, "Staff " + suffix, null)).getId();
        String questionId = json(post("/api/v1/teacher/questions",
                "{\"subjectId\":\"" + subjectId + "\",\"questionType\":\"NUMERIC\"}", teacher), 200).get("id").asString();
        String draft = "{\"prompt\":\"2 + 2\",\"correctAnswer\":\"4\",\"difficultyLevel\":1}";

        // Invited is not yet staff: the principal has no say over the teacher's work.
        assertThat(post("/api/v1/teacher/questions/" + questionId + "/versions", draft, principal).statusCode()).isEqualTo(403);
        accept(teacher, schoolId);
        assertThat(post("/api/v1/teacher/questions/" + questionId + "/versions", draft, principal).statusCode()).isEqualTo(200);
        // Another school's principal never could.
        assertThat(post("/api/v1/teacher/questions/" + questionId + "/versions", draft, otherPrincipal).statusCode()).isEqualTo(403);
    }

    // ------------------------------------------------------------------ helpers

    private record User(String id, String email, PlatformRole role, String token) {
    }

    private User user(PlatformRole role) throws Exception {
        String email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), role));
        return login(email, role);
    }

    private User login(String email, PlatformRole role) throws Exception {
        JsonNode body = json(post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", null), 200);
        return new User(body.get("accountId").asString(), email, role, body.get("accessToken").asString());
    }

    private String createSchool(User principal, String name) throws Exception {
        return json(post("/api/v1/schools", "{\"name\":\"" + name + "\"}", principal), 201).get("id").asString();
    }

    private String createClass(User principal, String schoolId, String name) throws Exception {
        return json(post("/api/v1/schools/" + schoolId + "/classes", "{\"name\":\"" + name + "\"}", principal), 201).get("id").asString();
    }

    private void accept(User teacher, String schoolId) throws Exception {
        for (JsonNode invitation : json(get("/api/v1/schools/invitations/mine", teacher), 200)) {
            if (invitation.get("schoolId").asString().equals(schoolId)) {
                assertThat(post("/api/v1/schools/invitations/" + invitation.get("id").asString() + "/accept", "", teacher)
                        .statusCode()).isEqualTo(204);
            }
        }
    }

    private HttpResponse<String> assign(User principal, String classId, String teacherId) throws Exception {
        return post("/api/v1/schools/classes/" + classId + "/teachers", "{\"teacherAccountId\":\"" + teacherId + "\"}", principal);
    }

    private String staffStatus(String schoolId, String accountId) {
        List<String> rows = jdbc.queryForList("select status from school_staff where school_id = ?::uuid and account_id = ?::uuid",
                String.class, UUID.fromString(schoolId), UUID.fromString(accountId));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private int loginStatus(String email, String password) throws Exception {
        return post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}", null).statusCode();
    }

    private int auditCount(String actorId, String action) {
        return jdbc.queryForObject("select count(*) from audit_events where action = ? and actor_id = ?::uuid",
                Integer.class, action, UUID.fromString(actorId));
    }

    private List<String> setupToken(String email, int atLeast) throws Exception {
        List<String> tokens = new ArrayList<>();
        for (MimeMessage message : awaitMessages(email, atLeast)) {
            String body = (String) message.getContent();
            assertThat(body.lines()).as("no collapsed line continuations").noneMatch(line -> line.matches(".*\\S {2,}\\S.*"));
            Matcher matcher = SETUP_LINK.matcher(body);
            if (matcher.find()) {
                tokens.add(matcher.group(1));
            }
        }
        return tokens;
    }

    private List<MimeMessage> awaitMessages(String to, int atLeast) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (true) {
            List<MimeMessage> messages = new ArrayList<>();
            for (MimeMessage message : greenMail.getReceivedMessages()) {
                if (message.getRecipients(Message.RecipientType.TO)[0].toString().equalsIgnoreCase(to)) {
                    messages.add(message);
                }
            }
            if (messages.size() >= atLeast || System.currentTimeMillis() > deadline) {
                return messages;
            }
            Thread.sleep(50);
        }
    }

    private static JsonNode json(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> get(String path, User user) throws Exception {
        return http.send(request(path, user).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> delete(String path, User user) throws Exception {
        return http.send(request(path, user).DELETE().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body, User user) throws Exception {
        return http.send(request(path, user).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder request(String path, User user) {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (user != null) {
            req.header("Authorization", "Bearer " + user.token());
        }
        return req;
    }

}
