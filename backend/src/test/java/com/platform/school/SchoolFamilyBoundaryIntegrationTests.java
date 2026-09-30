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
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The school side of the family boundary, over real HTTP against PostgreSQL.
 *
 * <p>A child belongs to a family. Only the family puts a child in a class, by entering the
 * class's code (PD-01, SchoolAccessIntegrationTests), so no school staff member - teacher,
 * principal or admin - may: knowing a child's id is not permission (CLAUDE.md §10, §15). And a class curriculum may only be one the
 * assigning account is entitled to hand out (TD-12): published platform content, or
 * the account's own.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class SchoolFamilyBoundaryIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "School-Boundary-Pass-2026";

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
    void noSchoolStaffMemberCanPutAFamilyChildIntoAClass() throws Exception {
        Staff principal = staff(PlatformRole.PRINCIPAL);
        Staff teacher = staff(PlatformRole.TEACHER);
        Staff admin = staff(PlatformRole.ADMIN);
        SchoolClass schoolClass = classTaughtBy(principal, teacher);
        String parent = registerParent();
        String childId = createChild(parent);

        for (Staff member : new Staff[] {teacher, principal, admin}) {
            HttpResponse<String> res = post("/api/v1/schools/classes/" + schoolClass.getId() + "/children",
                    "{\"childId\":\"" + childId + "\"}", member.token());
            assertThat(res.statusCode()).as(member.role() + ": " + res.body()).isEqualTo(403);
        }

        assertThat(jdbc.queryForObject("select count(*) from class_enrollments where child_id = ?::uuid",
                Integer.class, childId)).isZero();
        assertThat(json(get("/api/v1/schools/classes/" + schoolClass.getId() + "/children", teacher.token()), 200)
                .isEmpty()).isTrue();
        // The family's own view is untouched.
        assertThat(json(get("/api/v1/children/" + childId, parent), 200).get("id").asString()).isEqualTo(childId);
    }

    @Test
    void refusingDoesNotRevealWhetherAChildExists() throws Exception {
        Staff principal = staff(PlatformRole.PRINCIPAL);
        Staff teacher = staff(PlatformRole.TEACHER);
        SchoolClass schoolClass = classTaughtBy(principal, teacher);
        String realChild = createChild(registerParent());

        int known = post("/api/v1/schools/classes/" + schoolClass.getId() + "/children",
                "{\"childId\":\"" + realChild + "\"}", teacher.token()).statusCode();
        int unknown = post("/api/v1/schools/classes/" + schoolClass.getId() + "/children",
                "{\"childId\":\"" + UUID.randomUUID() + "\"}", teacher.token()).statusCode();

        assertThat(known).isEqualTo(unknown).isEqualTo(403);
    }

    @Test
    void aClassCurriculumMustBePublishedPlatformContentOrTheAssignersOwn() throws Exception {
        Staff principal = staff(PlatformRole.PRINCIPAL);
        Staff teacher = staff(PlatformRole.TEACHER);
        Staff otherTeacher = staff(PlatformRole.TEACHER);
        SchoolClass schoolClass = classTaughtBy(principal, teacher);
        // A class member (with the family's grant, PD-01): the refusal below must not depend on
        // the class being empty.
        String childId = createChild(registerParent());
        jdbc.update("insert into class_enrollments (id, school_class_id, child_id) values (?::uuid, ?::uuid, ?::uuid)",
                UUID.randomUUID().toString(), schoolClass.getId().toString(), childId);
        jdbc.update("""
                insert into school_child_access (id, school_id, child_id, status, granted_by_account_id, granted_at)
                select ?::uuid, ?::uuid, ?::uuid, 'ACTIVE', m.account_id, now()
                from memberships m join children c on c.family_id = m.family_id where c.id = ?::uuid""",
                UUID.randomUUID().toString(), schoolClass.getSchoolId().toString(), childId, childId);

        // Creating a child may auto-enroll it in a platform curriculum; only changes count.
        int enrollmentsBefore = enrollmentCount(childId);
        Curriculum othersCurriculum = publishedCurriculum(otherTeacher.id());
        Curriculum unpublishedPlatform = curriculum(null, false);

        for (Curriculum refused : new Curriculum[] {othersCurriculum, unpublishedPlatform}) {
            HttpResponse<String> res = post("/api/v1/schools/classes/" + schoolClass.getId() + "/curricula/"
                    + refused.getId(), "", teacher.token());
            assertThat(res.statusCode()).as(res.body()).isEqualTo(404);
        }
        assertThat(enrollmentCount(childId)).isEqualTo(enrollmentsBefore);

        Curriculum own = publishedCurriculum(teacher.id());
        assertThat(json(post("/api/v1/schools/classes/" + schoolClass.getId() + "/curricula/" + own.getId(), "",
                teacher.token()), 200).size()).isEqualTo(1);
        assertThat(enrollmentCount(childId)).isEqualTo(enrollmentsBefore + 1);
    }

    private int enrollmentCount(String childId) {
        return jdbc.queryForObject("select count(*) from enrollments where child_id = ?::uuid", Integer.class, childId);
    }

    // ------------------------------------------------------------------ fixture

    private record Staff(UUID id, PlatformRole role, String token) {
    }

    private Staff staff(PlatformRole role) throws Exception {
        String email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        Account account = accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), role));
        JsonNode body = json(post("/api/v1/auth/login",
                "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", null), 200);
        return new Staff(account.getId(), role, body.get("accessToken").asString());
    }

    private SchoolClass classTaughtBy(Staff principal, Staff teacher) {
        School school = schoolService.createSchool("Boundary School", principal.id());
        SchoolClass schoolClass = schoolService.createClass(school.getId(), "Grade 1");
        // Only a school's own staff can be assigned to its classes (V39).
        SchoolStaff membership = SchoolStaff.invite(school.getId(), teacher.id(), principal.id(), java.time.Instant.now());
        membership.accept(java.time.Instant.now());
        schoolStaffRepository.save(membership);
        schoolStaffService.assign(schoolClass.getId(), teacher.id(), principal.id());
        return schoolClass;
    }

    private Curriculum publishedCurriculum(UUID ownerAccountId) {
        return curriculum(ownerAccountId, true);
    }

    private Curriculum curriculum(UUID ownerAccountId, boolean publish) {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        UUID subjectId = subjectRepository.save(Subject.create("SCHOOL_" + suffix, "School " + suffix, null)).getId();
        UUID ageHubId = ageHubRepository.save(AgeHub.create("SCHOOL_" + suffix, "School " + suffix, 90, 99, 99)).getId();
        LearningObjective objective = learningObjectiveService.create(subjectId, "SCHOOL_OBJ_" + suffix, "School " + suffix, null);
        // A published curriculum needs a skill with a playable activity (ContentValidator).
        Skill skill = skillService.createSkill(subjectId, "SCHOOL_" + suffix, "School " + suffix, null);
        learningObjectiveSkillService.link(objective.getId(), skill.getId());
        Question question = questionService.createQuestion(subjectId, QuestionType.NUMERIC);
        var questionDraft = questionService.createDraftVersion(question.getId(), "1 + 1 = ?", "2", 1);
        questionService.tagSkill(questionDraft.getId(), skill.getId());
        var questionVersion = questionService.publish(questionDraft.getId());
        Activity activity = activityService.createActivity(subjectId, null, "PRACTICE", "School " + suffix);
        var activityVersion = activityService.createDraftVersion(activity.getId(), "Add", 1, 60);
        activityService.tagSkill(activityVersion.getId(), skill.getId());
        activityService.addItem(activityVersion.getId(), questionVersion.getId(), 0, java.math.BigDecimal.ONE);
        activityService.publish(activityVersion.getId());
        Curriculum curriculum = ownerAccountId == null
                ? curriculumService.createCurriculum(subjectId, ageHubId, "School " + suffix)
                : curriculumService.createOwnedCurriculum(subjectId, ageHubId, "School " + suffix, ownerAccountId);
        var version = curriculumService.createDraftVersion(curriculum.getId());
        curriculumService.addObjective(version.getId(), objective.getId(), 0);
        if (publish) {
            curriculumService.publish(version.getId());
        }
        return curriculum;
    }

    // ------------------------------------------------------------------ HTTP

    private String registerParent() throws Exception {
        return json(post("/api/v1/auth/register", "{\"email\":\"school-parent-" + UUID.randomUUID()
                + "@example.com\",\"password\":\"" + PASSWORD + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}",
                null), 201).get("accessToken").asString();
    }

    private String createChild(String parentToken) throws Exception {
        String dateOfBirth = LocalDate.now().minusYears(7).toString();
        return json(post("/api/v1/children", "{\"displayName\":\"Family Child\",\"dateOfBirth\":\"" + dateOfBirth + "\"}",
                parentToken), 201).get("id").asString();
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
