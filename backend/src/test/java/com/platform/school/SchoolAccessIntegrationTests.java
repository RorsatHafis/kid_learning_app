package com.platform.school;

import com.platform.TestcontainersConfiguration;
import com.platform.content.entity.Activity;
import com.platform.content.entity.Question;
import com.platform.content.entity.QuestionType;
import com.platform.content.service.ActivityService;
import com.platform.content.service.QuestionService;
import com.platform.curriculum.entity.AgeHub;
import com.platform.curriculum.entity.Curriculum;
import com.platform.curriculum.entity.LearningObjective;
import com.platform.curriculum.entity.Subject;
import com.platform.curriculum.repository.AgeHubRepository;
import com.platform.curriculum.repository.SubjectRepository;
import com.platform.curriculum.service.CurriculumService;
import com.platform.curriculum.service.LearningObjectiveService;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.repository.AccountRepository;
import com.platform.knowledge.entity.Skill;
import com.platform.knowledge.service.LearningObjectiveSkillService;
import com.platform.knowledge.service.SkillService;
import com.platform.school.entity.School;
import com.platform.school.entity.SchoolClass;
import com.platform.school.entity.SchoolStaff;
import com.platform.school.repository.SchoolStaffRepository;
import com.platform.school.service.SchoolService;
import com.platform.school.service.SchoolStaffService;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PD-01 over real HTTP against PostgreSQL (PLATFORM-EXPANSION.md §4): a family joins a class with its
 * code, the school sees only the learning the class assigned, and the family ends that access at any
 * time without the child losing anything. Only the class's teachers and principal work with its
 * children; an administrator, another teacher and another family can't.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class SchoolAccessIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "School-Access-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private SchoolService schoolService;
    @Autowired private SchoolStaffService schoolStaffService;
    @Autowired private SchoolStaffRepository schoolStaffRepository;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private AgeHubRepository ageHubRepository;
    @Autowired private CurriculumService curriculumService;
    @Autowired private LearningObjectiveService learningObjectiveService;
    @Autowired private SkillService skillService;
    @Autowired private LearningObjectiveSkillService learningObjectiveSkillService;
    @Autowired private QuestionService questionService;
    @Autowired private ActivityService activityService;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void aFamilyJoinsWithTheCodeTheSchoolSeesOnlyWhatItAssignedAndTheFamilyEndsIt() throws Exception {
        Staff principal = staff(PlatformRole.PRINCIPAL);
        Staff teacher = staff(PlatformRole.TEACHER);
        SchoolClass schoolClass = classTaughtBy(principal, teacher);
        String classPath = "/api/v1/schools/classes/" + schoolClass.getId();
        Curriculum assigned = publishedCurriculum(null, "Assigned maths");
        Curriculum homeChoice = publishedCurriculum(null, "Home reading");

        // The class assigns a curriculum before anyone has joined: recorded, nobody enrolled.
        assertThat(json(post(classPath + "/curricula/" + assigned.getId(), "", teacher.token()), 200).size()).isZero();
        String code = json(post(classPath + "/join-code", "", teacher.token()), 201).get("code").asString();
        assertThat(code).matches("[A-HJKMNP-Z2-9]{8}");
        assertThat(json(get(classPath + "/join-code", principal.token()), 200).get("code").asString()).isEqualTo(code);

        Parent parent = parent();
        String childId = createChild(parent.token(), "Dara");
        json(post("/api/v1/children/" + childId + "/enrollments", "{\"curriculumId\":\"" + homeChoice.getId() + "\"}",
                parent.token()), 201);
        String codeBody = "{\"code\":\"" + code.substring(0, 4).toLowerCase() + "-" + code.substring(4) + "\"}";

        // Joining needs a confirmed email address (PD-22); previewing doesn't, and changes nothing.
        JsonNode preview = json(post("/api/v1/children/" + childId + "/schools/preview", codeBody, parent.token()), 200);
        assertThat(preview.get("schoolName").asString()).isEqualTo("Access School");
        assertThat(preview.get("className").asString()).isEqualTo("Grade 1");
        assertThat(preview.get("assignedCurricula").get(0).asString()).isEqualTo(assigned.getName());
        assertThat(preview.get("alreadyInClass").asBoolean()).isFalse();
        assertThat(post("/api/v1/children/" + childId + "/schools/join", codeBody, parent.token()).statusCode()).isEqualTo(409);
        assertThat(count("select count(*) from school_child_access where child_id = ?::uuid", childId)).isZero();
        confirmEmail(parent);

        int enrollmentsBefore = count("select count(*) from enrollments where child_id = ?::uuid", childId);
        JsonNode joined = json(post("/api/v1/children/" + childId + "/schools/join", codeBody, parent.token()), 200);
        assertThat(joined.get("alreadyInClass").asBoolean()).isTrue();
        // The class's curriculum started; the grant and the membership are recorded and audited.
        assertThat(count("select count(*) from enrollments where child_id = ?::uuid", childId)).isEqualTo(enrollmentsBefore + 1);
        assertThat(count("select count(*) from school_child_access where child_id = ?::uuid and status = 'ACTIVE'", childId)).isEqualTo(1);
        assertThat(audited("SCHOOL_ACCESS_GRANTED", parent.id())).isEqualTo(1);
        assertThat(audited("CLASS_JOINED", parent.id())).isEqualTo(1);
        // Joining a class the child is already in (a double tap, a retry) changes and records nothing.
        assertThat(json(post("/api/v1/children/" + childId + "/schools/join", codeBody, parent.token()), 200)
                .get("alreadyInClass").asBoolean()).isTrue();
        assertThat(audited("CLASS_JOINED", parent.id())).isEqualTo(1);
        assertThat(audited("SCHOOL_ACCESS_GRANTED", parent.id())).isEqualTo(1);
        assertThat(count("select count(*) from enrollments where child_id = ?::uuid", childId)).isEqualTo(enrollmentsBefore + 1);

        // The class's staff see the child and the assigned learning, and nothing the family chose.
        assertThat(json(get(classPath + "/children", teacher.token()), 200).get(0).get("displayName").asString()).isEqualTo("Dara");
        for (Staff staffMember : List.of(teacher, principal)) {
            HttpResponse<String> learning = get(classPath + "/learning", staffMember.token());
            JsonNode body = json(learning, 200);
            JsonNode journeys = body.get("members").get(0).get("journeys");
            assertThat(journeys).hasSize(1);
            assertThat(journeys.get(0).get("curriculumId").asString()).isEqualTo(assigned.getId().toString());
            assertThat(journeys.get(0).get("started").asBoolean()).isTrue();
            assertThat(learning.body()).doesNotContain(homeChoice.getName()).doesNotContain("dateOfBirth");
            assertThat(body.get("skills").get(0).get("objectives").get(0).get("skills").get(0).get("notStarted").asInt()).isEqualTo(1);
        }
        HttpResponse<String> detail = get(classPath + "/children/" + childId + "/learning", teacher.token());
        assertThat(json(detail, 200).get("skills")).hasSize(1);
        assertThat(detail.body()).doesNotContain(homeChoice.getName());

        // Nobody else: not an administrator, not another teacher, not a parent, not another family.
        Staff admin = staff(PlatformRole.ADMIN);
        Staff otherTeacher = staff(PlatformRole.TEACHER);
        Parent stranger = parent();
        for (String token : List.of(admin.token(), otherTeacher.token(), stranger.token(), parent.token())) {
            for (String path : List.of(classPath + "/children", classPath + "/learning", classPath + "/join-code",
                    classPath + "/children/" + childId + "/learning")) {
                assertThat(get(path, token).statusCode()).as(path).isIn(403, 404);
            }
            assertThat(delete(classPath + "/children/" + childId, token).statusCode()).isIn(403, 404);
        }
        for (String path : List.of("/api/v1/children/" + childId + "/schools")) {
            assertThat(get(path, stranger.token()).statusCode()).isEqualTo(404);
            assertThat(get(path, teacher.token()).statusCode()).isEqualTo(404);
        }
        assertThat(post("/api/v1/children/" + childId + "/schools/preview", codeBody, stranger.token()).statusCode()).isEqualTo(404);
        UUID schoolId = schoolClass.getSchoolId();
        assertThat(delete("/api/v1/children/" + childId + "/schools/" + schoolId, teacher.token()).statusCode()).isEqualTo(404);
        assertThat(delete("/api/v1/children/" + childId + "/schools/" + schoolId, stranger.token()).statusCode()).isEqualTo(404);

        // The family sees the school, then ends its access: the class loses the child at once, the
        // child keeps every journey, and the history of the grant stays.
        JsonNode schools = json(get("/api/v1/children/" + childId + "/schools", parent.token()), 200);
        assertThat(schools.get(0).get("schoolName").asString()).isEqualTo("Access School");
        assertThat(schools.get(0).get("classNames").get(0).asString()).isEqualTo("Grade 1");
        assertThat(delete("/api/v1/children/" + childId + "/schools/" + schoolId, parent.token()).statusCode()).isEqualTo(204);
        assertThat(json(get(classPath + "/children", teacher.token()), 200)).isEmpty();
        assertThat(json(get(classPath + "/learning", teacher.token()), 200).get("members")).isEmpty();
        assertThat(get(classPath + "/children/" + childId + "/learning", teacher.token()).statusCode()).isEqualTo(404);
        assertThat(count("select count(*) from enrollments where child_id = ?::uuid", childId)).isEqualTo(enrollmentsBefore + 1);
        assertThat(count("select count(*) from class_enrollments where child_id = ?::uuid and status = 'REMOVED'", childId)).isEqualTo(1);
        assertThat(json(get("/api/v1/children/" + childId + "/schools", parent.token()), 200)).isEmpty();
        assertThat(audited("SCHOOL_ACCESS_REVOKED", parent.id())).isEqualTo(1);
        assertThat(delete("/api/v1/children/" + childId + "/schools/" + schoolId, parent.token()).statusCode()).isEqualTo(404);

        // Joining again is a new grant: the old one stays as history.
        json(post("/api/v1/children/" + childId + "/schools/join", codeBody, parent.token()), 200);
        assertThat(count("select count(*) from school_child_access where child_id = ?::uuid", childId)).isEqualTo(2);
        assertThat(json(get(classPath + "/children", teacher.token()), 200)).hasSize(1);
    }

    @Test
    void codesAreTheClassStaffsAndStopWorkingWhenReplacedRevokedOrExpired() throws Exception {
        Staff principal = staff(PlatformRole.PRINCIPAL);
        Staff teacher = staff(PlatformRole.TEACHER);
        SchoolClass schoolClass = classTaughtBy(principal, teacher);
        String classPath = "/api/v1/schools/classes/" + schoolClass.getId();
        Parent parent = parent();
        String childId = createChild(parent.token(), "Sophea");
        String preview = "/api/v1/children/" + childId + "/schools/preview";

        for (String token : List.of(staff(PlatformRole.ADMIN).token(), staff(PlatformRole.TEACHER).token(), parent.token())) {
            assertThat(post(classPath + "/join-code", "", token).statusCode()).isIn(403, 404);
        }
        assertThat(get(classPath + "/join-code", teacher.token()).statusCode()).isEqualTo(204);

        String first = json(post(classPath + "/join-code", "", teacher.token()), 201).get("code").asString();
        String second = json(post(classPath + "/join-code", "", principal.token()), 201).get("code").asString();
        assertThat(post(preview, codeBody(first), parent.token()).statusCode()).isEqualTo(404);
        assertThat(post(preview, codeBody(second), parent.token()).statusCode()).isEqualTo(200);

        assertThat(delete(classPath + "/join-code", teacher.token()).statusCode()).isEqualTo(204);
        assertThat(post(preview, codeBody(second), parent.token()).statusCode()).isEqualTo(404);
        assertThat(get(classPath + "/join-code", teacher.token()).statusCode()).isEqualTo(204);

        String third = json(post(classPath + "/join-code", "", teacher.token()), 201).get("code").asString();
        jdbc.update("update class_join_codes set created_at = now() - interval '15 days', expires_at = now() - interval '1 day' where code = ?", third);
        assertThat(post(preview, codeBody(third), parent.token()).statusCode()).isEqualTo(404);
        assertThat(json(get(classPath + "/join-code", teacher.token()), 200).get("expired").asBoolean()).isTrue();

        assertThat(post(preview, codeBody("NOPE0000"), parent.token()).statusCode()).isEqualTo(404);
        assertThat(post(preview, codeBody(""), parent.token()).statusCode()).isEqualTo(400);
        assertThat(audited("CLASS_JOIN_CODE_ISSUED", teacher.id())).isEqualTo(2);
    }

    @Test
    void theClassCanLetAChildGoAndItsAssignmentsDecideWhatItSees() throws Exception {
        Staff principal = staff(PlatformRole.PRINCIPAL);
        Staff teacher = staff(PlatformRole.TEACHER);
        SchoolClass schoolClass = classTaughtBy(principal, teacher);
        String classPath = "/api/v1/schools/classes/" + schoolClass.getId();
        String code = json(post(classPath + "/join-code", "", teacher.token()), 201).get("code").asString();
        Parent parent = parent();
        confirmEmail(parent);
        String childId = createChild(parent.token(), "Nita");
        json(post("/api/v1/children/" + childId + "/schools/join", codeBody(code), parent.token()), 200);

        // A curriculum assigned after joining starts for the member; unassigning hides it from the
        // school but leaves the child's journey in place.
        Curriculum later = publishedCurriculum(null, "Later shapes");
        int before = count("select count(*) from enrollments where child_id = ?::uuid", childId);
        assertThat(json(post(classPath + "/curricula/" + later.getId(), "", teacher.token()), 200)).hasSize(1);
        assertThat(count("select count(*) from enrollments where child_id = ?::uuid", childId)).isEqualTo(before + 1);
        assertThat(json(get(classPath + "/curricula", teacher.token()), 200)).hasSize(1);
        assertThat(delete(classPath + "/curricula/" + later.getId(), teacher.token()).statusCode()).isEqualTo(204);
        assertThat(json(get(classPath + "/learning", teacher.token()), 200).get("members").get(0).get("journeys")).isEmpty();
        assertThat(count("select count(*) from enrollments where child_id = ?::uuid", childId)).isEqualTo(before + 1);
        assertThat(delete(classPath + "/curricula/" + later.getId(), teacher.token()).statusCode()).isEqualTo(404);

        // A class row from before PD-01, with no family grant, is not a member of anything.
        String legacyChild = createChild(parent().token(), "Legacy");
        jdbc.update("insert into class_enrollments (id, school_class_id, child_id) values (?::uuid, ?::uuid, ?::uuid)",
                UUID.randomUUID().toString(), schoolClass.getId().toString(), legacyChild);
        assertThat(json(get(classPath + "/children", teacher.token()), 200)).hasSize(1);
        assertThat(get(classPath + "/children/" + legacyChild + "/learning", teacher.token()).statusCode()).isEqualTo(404);

        // The class lets the child go; the family's grant to the school is still the family's.
        assertThat(delete(classPath + "/children/" + childId, teacher.token()).statusCode()).isEqualTo(204);
        assertThat(json(get(classPath + "/children", teacher.token()), 200)).isEmpty();
        JsonNode schools = json(get("/api/v1/children/" + childId + "/schools", parent.token()), 200);
        assertThat(schools).hasSize(1);
        assertThat(schools.get(0).get("classNames")).isEmpty();
        assertThat(audited("CLASS_MEMBER_REMOVED", teacher.id())).isEqualTo(1);
        assertThat(delete(classPath + "/children/" + childId, teacher.token()).statusCode()).isEqualTo(404);
    }

    @Test
    void codeLookupsAreRateLimitedPerAccount() throws Exception {
        Parent parent = parent();
        String childId = createChild(parent.token(), "Rotha");
        String preview = "/api/v1/children/" + childId + "/schools/preview";
        for (int i = 0; i < 10; i++) {
            assertThat(post(preview, codeBody("ABCDEFGH"), parent.token()).statusCode()).isEqualTo(404);
        }
        HttpResponse<String> limited = post(preview, codeBody("ABCDEFGH"), parent.token());
        assertThat(limited.statusCode()).isEqualTo(429);
        assertThat(limited.headers().firstValue("Retry-After")).isPresent();
        // Another family has its own budget.
        Parent other = parent();
        assertThat(post("/api/v1/children/" + createChild(other.token(), "Kosal") + "/schools/preview", codeBody("ABCDEFGH"),
                other.token()).statusCode()).isEqualTo(404);
    }

    // ------------------------------------------------------------------ fixture

    private record Staff(UUID id, PlatformRole role, String token) {
    }

    private record Parent(UUID id, String token) {
    }

    private Staff staff(PlatformRole role) throws Exception {
        String email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        Account account = accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), role));
        JsonNode body = json(post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", null), 200);
        return new Staff(account.getId(), role, body.get("accessToken").asString());
    }

    private SchoolClass classTaughtBy(Staff principal, Staff teacher) {
        School school = schoolService.createSchool("Access School", principal.id());
        SchoolClass schoolClass = schoolService.createClass(school.getId(), "Grade 1");
        SchoolStaff membership = SchoolStaff.invite(school.getId(), teacher.id(), principal.id(), Instant.now());
        membership.accept(Instant.now());
        schoolStaffRepository.save(membership);
        schoolStaffService.assign(schoolClass.getId(), teacher.id(), principal.id());
        return schoolClass;
    }

    private Parent parent() throws Exception {
        String email = "access-parent-" + UUID.randomUUID() + "@example.com";
        String token = json(post("/api/v1/auth/register", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null), 201).get("accessToken").asString();
        UUID id = jdbc.queryForObject("select id from accounts where normalized_email = ?", UUID.class, email);
        return new Parent(id, token);
    }

    /** The email-confirmation flow is tested on its own (W4.3); here only its outcome matters. */
    private void confirmEmail(Parent parent) {
        jdbc.update("update accounts set email_verified_at = now() where id = ?", parent.id());
    }

    private String createChild(String parentToken, String name) throws Exception {
        String dateOfBirth = LocalDate.now().minusYears(7).toString();
        return json(post("/api/v1/children", "{\"displayName\":\"" + name + "\",\"dateOfBirth\":\"" + dateOfBirth + "\"}",
                parentToken), 201).get("id").asString();
    }

    private Curriculum publishedCurriculum(UUID ownerAccountId, String name) {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        UUID subjectId = subjectRepository.save(Subject.create("ACCESS_" + suffix, "Access " + suffix, null)).getId();
        UUID ageHubId = ageHubRepository.save(AgeHub.create("ACCESS_" + suffix, "Access " + suffix, 90, 99, 99)).getId();
        LearningObjective objective = learningObjectiveService.create(subjectId, "ACCESS_OBJ_" + suffix, "Objective " + suffix, null);
        Skill skill = skillService.createSkill(subjectId, "ACCESS_" + suffix, "Skill " + suffix, null);
        learningObjectiveSkillService.link(objective.getId(), skill.getId());
        Question question = questionService.createQuestion(subjectId, QuestionType.NUMERIC);
        var questionDraft = questionService.createDraftVersion(question.getId(), "1 + 1 = ?", "2", 1);
        questionService.tagSkill(questionDraft.getId(), skill.getId());
        var questionVersion = questionService.publish(questionDraft.getId());
        Activity activity = activityService.createActivity(subjectId, null, "PRACTICE", "Access " + suffix);
        var activityVersion = activityService.createDraftVersion(activity.getId(), "Add", 1, 60);
        activityService.tagSkill(activityVersion.getId(), skill.getId());
        activityService.addItem(activityVersion.getId(), questionVersion.getId(), 0, java.math.BigDecimal.ONE);
        activityService.publish(activityVersion.getId());
        Curriculum curriculum = ownerAccountId == null
                ? curriculumService.createCurriculum(subjectId, ageHubId, name + " " + suffix)
                : curriculumService.createOwnedCurriculum(subjectId, ageHubId, name + " " + suffix, ownerAccountId);
        var version = curriculumService.createDraftVersion(curriculum.getId());
        curriculumService.addObjective(version.getId(), objective.getId(), 0);
        curriculumService.publish(version.getId());
        return curriculum;
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private int audited(String action, UUID actorId) {
        return count("select count(*) from audit_events where action = ? and actor_id = ?", action, actorId);
    }

    private static String codeBody(String code) {
        return "{\"code\":\"" + code + "\"}";
    }

    // ------------------------------------------------------------------ HTTP

    private static JsonNode json(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> get(String path, String bearer) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).GET(), bearer);
    }

    private HttpResponse<String> delete(String path, String bearer) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).DELETE(), bearer);
    }

    private HttpResponse<String> post(String path, String body, String bearer) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)), bearer);
    }

    private HttpResponse<String> send(HttpRequest.Builder request, String bearer) throws Exception {
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
