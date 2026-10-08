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
    void theStaffOverviewSummarisesOnlyTheirOwnClassesFromWhatTheClassSees() throws Exception {
        Staff principal = staff(PlatformRole.PRINCIPAL);
        Staff teacher = staff(PlatformRole.TEACHER);
        SchoolClass taught = classTaughtBy(principal, teacher);
        SchoolClass untaught = schoolService.createClass(taught.getSchoolId(), "Grade 2");
        String classPath = "/api/v1/schools/classes/" + taught.getId();
        Curriculum assigned = publishedCurriculum(null, "Overview maths");
        Curriculum homeChoice = publishedCurriculum(null, "Overview reading");
        json(post(classPath + "/curricula/" + assigned.getId(), "", teacher.token()), 200);

        // Before anyone joins, the principal sees the school's setup gaps.
        JsonNode school = json(get("/api/v1/schools/overview", principal.token()), 200).get("schools").get(0);
        assertThat(school.get("classes").asInt()).isEqualTo(2);
        assertThat(school.get("classesWithoutTeacher").asInt()).isEqualTo(1);
        assertThat(school.get("classesWithoutCurriculum").asInt()).isEqualTo(1);
        assertThat(school.get("classesWithoutOpenCode").asInt()).isEqualTo(2);
        assertThat(school.get("childrenWithAccess").asInt()).isZero();

        String code = json(post(classPath + "/join-code", "", teacher.token()), 201).get("code").asString();
        Parent parent = parent();
        String childId = createChild(parent.token(), "Sophea");
        json(post("/api/v1/children/" + childId + "/enrollments", "{\"curriculumId\":\"" + homeChoice.getId() + "\"}",
                parent.token()), 201);
        confirmEmail(parent);
        json(post("/api/v1/children/" + childId + "/schools/join", codeBody(code), parent.token()), 200);

        // The teacher: only the class they teach, counted from grant-backed members; no names, no family choices.
        HttpResponse<String> mine = get("/api/v1/schools/overview", teacher.token());
        JsonNode teacherView = json(mine, 200);
        assertThat(teacherView.get("schools")).isEmpty();
        assertThat(teacherView.get("classes")).hasSize(1);
        JsonNode row = teacherView.get("classes").get(0);
        assertThat(row.get("classId").asString()).isEqualTo(taught.getId().toString());
        assertThat(row.get("children").asInt()).isEqualTo(1);
        assertThat(row.get("teachers").asInt()).isEqualTo(1);
        assertThat(row.get("joinCode").get("open").asBoolean()).isTrue();
        assertThat(row.get("curricula").get(0).get("curriculumId").asString()).isEqualTo(assigned.getId().toString());
        assertThat(row.get("attention").get("childrenNotStarted").asInt()).isEqualTo(1);
        assertThat(row.get("attention").get("childrenNeedingPractice").asInt()).isZero();
        assertThat(mine.body()).doesNotContain("Sophea").doesNotContain(homeChoice.getName()).doesNotContain(childId);

        // The principal: every class of the school, and the children the school now reaches.
        JsonNode principalView = json(get("/api/v1/schools/overview", principal.token()), 200);
        assertThat(principalView.get("classes")).hasSize(2);
        assertThat(principalView.get("schools").get(0).get("childrenWithAccess").asInt()).isEqualTo(1);
        assertThat(principalView.get("schools").get(0).get("classesWithoutOpenCode").asInt()).isEqualTo(1);
        JsonNode second = principalView.get("classes").get(1);
        assertThat(second.get("classId").asString()).isEqualTo(untaught.getId().toString());
        assertThat(second.get("attention").get("childrenNotStarted").asInt()).isZero();

        // Ending access removes the child from the counts at once.
        assertThat(delete("/api/v1/children/" + childId + "/schools/" + taught.getSchoolId(), parent.token()).statusCode()).isEqualTo(204);
        JsonNode after = json(get("/api/v1/schools/overview", principal.token()), 200);
        assertThat(after.get("schools").get(0).get("childrenWithAccess").asInt()).isZero();
        assertThat(after.get("classes").get(0).get("children").asInt()).isZero();

        // Another teacher has nothing; an administrator and a parent have no class overview at all.
        assertThat(json(get("/api/v1/schools/overview", staff(PlatformRole.TEACHER).token()), 200).get("classes")).isEmpty();
        assertThat(get("/api/v1/schools/overview", staff(PlatformRole.ADMIN).token()).statusCode()).isEqualTo(403);
        assertThat(get("/api/v1/schools/overview", parent.token()).statusCode()).isEqualTo(403);
        assertThat(get("/api/v1/schools/overview", null).statusCode()).isEqualTo(401);
    }

    @Test
    void aTeacherFollowsUpASkillWithPracticeTheEngineServesAndEndedAccessWithdraws() throws Exception {
        Staff principal = staff(PlatformRole.PRINCIPAL);
        Staff teacher = staff(PlatformRole.TEACHER);
        SchoolClass schoolClass = classTaughtBy(principal, teacher);
        String classPath = "/api/v1/schools/classes/" + schoolClass.getId();
        Curriculum assigned = publishedCurriculum(null, "Follow-up maths");
        json(post(classPath + "/curricula/" + assigned.getId(), "", teacher.token()), 200);
        String code = json(post(classPath + "/join-code", "", teacher.token()), 201).get("code").asString();

        Parent familyA = parent();
        String childA = createChild(familyA.token(), "Arun");
        Parent familyB = parent();
        String childB = createChild(familyB.token(), "Bopha");
        for (Parent family : List.of(familyA, familyB)) {
            confirmEmail(family);
        }
        json(post("/api/v1/children/" + childA + "/schools/join", codeBody(code), familyA.token()), 200);
        json(post("/api/v1/children/" + childB + "/schools/join", codeBody(code), familyB.token()), 200);

        String skillId = json(get(classPath + "/learning", teacher.token()), 200)
                .get("skills").get(0).get("objectives").get(0).get("skills").get(0).get("skillId").asString();
        String extraActivity = extraPracticeActivity(UUID.fromString(skillId));
        String skillPath = classPath + "/skills/" + skillId;

        // Who stands where, on what evidence, and what could be placed.
        JsonNode view = json(get(skillPath, teacher.token()), 200);
        assertThat(view.get("children")).hasSize(2);
        assertThat(view.get("children").get(0).get("standing").asString()).isEqualTo("NOT_STARTED");
        assertThat(view.get("children").get(0).get("latestPractice").isNull()).isTrue();
        List<String> options = new ArrayList<>();
        view.get("practice").forEach(option -> options.add(option.get("activityId").asString()));
        assertThat(options).contains(extraActivity);
        assertThat(json(get(skillPath, principal.token()), 200).get("children")).hasSize(2);

        // Refused, without a trace: no class role, another teacher, a parent, a stranger child, a
        // skill the class doesn't teach, an activity that doesn't practise it, nobody chosen.
        String body = "{\"activityId\":\"" + extraActivity + "\",\"childIds\":[\"" + childA + "\"]}";
        Staff otherTeacher = staff(PlatformRole.TEACHER);
        for (String token : List.of(staff(PlatformRole.ADMIN).token(), otherTeacher.token(), familyA.token())) {
            assertThat(get(skillPath, token).statusCode()).isIn(403, 404);
            assertThat(post(skillPath + "/practice", body, token).statusCode()).isIn(403, 404);
        }
        String stranger = createChild(parent().token(), "Stranger");
        assertThat(post(skillPath + "/practice", "{\"activityId\":\"" + extraActivity + "\",\"childIds\":[\"" + stranger + "\"]}",
                teacher.token()).statusCode()).isEqualTo(404);
        assertThat(get(classPath + "/skills/" + UUID.randomUUID(), teacher.token()).statusCode()).isEqualTo(404);
        assertThat(post(skillPath + "/practice", "{\"activityId\":\"" + UUID.randomUUID() + "\",\"childIds\":[\"" + childA + "\"]}",
                teacher.token()).statusCode()).isEqualTo(400);
        assertThat(post(skillPath + "/practice", "{\"activityId\":\"" + extraActivity + "\",\"childIds\":[]}", teacher.token())
                .statusCode()).isEqualTo(400);
        assertThat(count("select count(*) from learning_path_items where assigned_class_id = ?", schoolClass.getId())).isZero();

        // Placed once; a retry changes nothing.
        JsonNode result = json(post(skillPath + "/practice", body, teacher.token()), 200);
        assertThat(result.get("assigned").get(0).asString()).isEqualTo(childA);
        assertThat(json(post(skillPath + "/practice", body, teacher.token()), 200).get("alreadyWaiting").get(0).asString()).isEqualTo(childA);
        assertThat(count("select count(*) from learning_path_items where assigned_class_id = ? and source = 'TEACHER_ASSIGNED' "
                + "and assigned_skill_id = ?::uuid and assigned_by_account_id = ?", schoolClass.getId(), skillId, teacher.id())).isEqualTo(1);
        assertThat(audited("CLASS_PRACTICE_ASSIGNED", teacher.id())).isEqualTo(1);

        // The engine serves it before the rest of the curriculum, and the family sees who asked and why.
        JsonNode next = journeyIn(familyA.token(), childA, assigned.getId()).get("nextStep");
        assertThat(next.get("source").asString()).isEqualTo("TEACHER_ASSIGNED");
        assertThat(next.get("explanation").get("assignedBy").asString()).isEqualTo("Grade 1 · Access School");
        assertThat(next.get("explanation").get("skill").get("id").asString()).isEqualTo(skillId);

        // Played: the class sees it done, and the evidence it produced.
        playNext(familyA.token(), childA, assigned.getId(), "4");
        JsonNode after = json(get(skillPath, teacher.token()), 200);
        JsonNode arun = null;
        for (JsonNode child : after.get("children")) {
            if (child.get("childId").asString().equals(childA)) {
                arun = child;
            }
        }
        assertThat(arun.get("latestPractice").get("status").asString()).isEqualTo("COMPLETED");
        // TD-25: the family summary counts in the database what the child did - one activity, one step.
        JsonNode summary = json(get("/api/v1/children/" + childA + "/progress", familyA.token()), 200);
        assertThat(summary.get("completedActivities").asLong()).isEqualTo(1);
        assertThat(summary.get("completedPathItems").asLong()).isEqualTo(1);
        assertThat(summary.get("totalPathItems").asLong()).isGreaterThan(1);
        // X12: where Arun stood when it was placed - no answers yet - against where he stands now.
        assertThat(arun.get("latestPractice").get("standingWhenAssigned").asString()).isEqualTo("NOT_STARTED");
        assertThat(arun.get("scoredAnswers").asInt()).isPositive();

        // Placed for both again; one family ends the school's access, the class lets the other go:
        // their waiting practice is withdrawn, finished practice stays.
        JsonNode both = json(post(skillPath + "/practice", "{\"activityId\":\"" + extraActivity + "\",\"childIds\":[\"" + childA
                + "\",\"" + childB + "\"]}", teacher.token()), 200);
        assertThat(both.get("assigned")).hasSize(2);
        assertThat(delete("/api/v1/children/" + childB + "/schools/" + schoolClass.getSchoolId(), familyB.token()).statusCode()).isEqualTo(204);
        assertThat(delete(classPath + "/children/" + childA, teacher.token()).statusCode()).isEqualTo(204);
        assertThat(count("select count(*) from learning_path_items where assigned_class_id = ? and status = 'PENDING'", schoolClass.getId()))
                .isZero();
        assertThat(count("select count(*) from learning_path_items where assigned_class_id = ? and status = 'SKIPPED'", schoolClass.getId()))
                .isEqualTo(2);
        assertThat(count("select count(*) from learning_path_items where assigned_class_id = ? and status = 'COMPLETED'", schoolClass.getId()))
                .isEqualTo(1);
        assertThat(journeyIn(familyB.token(), childB, assigned.getId()).get("nextStep").get("source").asString())
                .isNotEqualTo("TEACHER_ASSIGNED");
    }

    @Test
    void theClassTrendShowsWhereTheClassStoodEachWeekFromTheEvidenceAndOnlyToItsStaff() throws Exception {
        Staff principal = staff(PlatformRole.PRINCIPAL);
        Staff teacher = staff(PlatformRole.TEACHER);
        SchoolClass taught = classTaughtBy(principal, teacher);
        String classPath = "/api/v1/schools/classes/" + taught.getId();
        Curriculum assigned = publishedCurriculum(null, "Trend maths");
        json(post(classPath + "/curricula/" + assigned.getId(), "", teacher.token()), 200);
        String code = json(post(classPath + "/join-code", "", teacher.token()), 201).get("code").asString();
        Parent parent = parent();
        String childId = createChild(parent.token(), "Dara");
        confirmEmail(parent);
        json(post("/api/v1/children/" + childId + "/schools/join", codeBody(code), parent.token()), 200);
        UUID child = UUID.fromString(childId);
        UUID skill = jdbc.queryForObject("""
                select los.skill_id from curriculum_versions v
                join curriculum_objectives co on co.curriculum_version_id = v.id
                join learning_objective_skills los on los.learning_objective_id = co.learning_objective_id
                where v.curriculum_id = ?""", UUID.class, assigned.getId());

        // In the class for 40 days (the row is re-made: its timestamps are kept by a trigger). Three answers
        // 20 days ago leave the skill needing practice; two 3 days ago make it secure.
        Instant now = Instant.now();
        jdbc.update("delete from class_enrollments where child_id = ?", child);
        jdbc.update("insert into class_enrollments (id, school_class_id, child_id, status, created_at, updated_at) values (?, ?, ?, 'ACTIVE', ?, ?)",
                UUID.randomUUID(), taught.getId(), child, java.sql.Timestamp.from(now.minus(java.time.Duration.ofDays(40))),
                java.sql.Timestamp.from(now.minus(java.time.Duration.ofDays(40))));
        history(child, skill, now.minus(java.time.Duration.ofDays(20)), "0.40", "0.45", "0.50");
        history(child, skill, now.minus(java.time.Duration.ofDays(3)), "0.85", "0.90");

        HttpResponse<String> response = get(classPath + "/trend?weeks=4", teacher.token());
        JsonNode trend = json(response, 200);
        assertThat(trend.get("skills").asInt()).isEqualTo(1);
        JsonNode points = trend.get("points");
        assertThat(points).hasSize(5);
        assertThat(points.get(0).get("children").asInt()).isEqualTo(1);
        assertThat(points.get(0).get("notStarted").asInt()).isEqualTo(1);
        assertThat(points.get(1).get("notStarted").asInt()).isEqualTo(1);
        assertThat(points.get(2).get("needsPractice").asInt()).isEqualTo(1);
        assertThat(points.get(3).get("needsPractice").asInt()).isEqualTo(1);
        assertThat(points.get(4).get("secure").asInt()).isEqualTo(1);
        JsonNode answers = trend.get("answers");
        assertThat(answers).hasSize(4);
        assertThat(answers.get(1).get("answers").asInt()).isEqualTo(3);
        assertThat(answers.get(1).get("childrenAnswering").asInt()).isEqualTo(1);
        assertThat(answers.get(3).get("answers").asInt()).isEqualTo(2);
        assertThat(trend.get("movement").get("becameSecure").asInt()).isEqualTo(1);
        assertThat(trend.get("bySkill").get(0).get("skillId").asString()).isEqualTo(skill.toString());
        // Counts only: no child is named.
        assertThat(response.body()).doesNotContain("Dara").doesNotContain(childId);
        // The principal sees it too; another teacher, an administrator and the family do not.
        assertThat(get(classPath + "/trend", principal.token()).statusCode()).isEqualTo(200);
        assertThat(get(classPath + "/trend", staff(PlatformRole.TEACHER).token()).statusCode()).isEqualTo(403);
        assertThat(get(classPath + "/trend", staff(PlatformRole.ADMIN).token()).statusCode()).isEqualTo(403);
        assertThat(get(classPath + "/trend", parent.token()).statusCode()).isEqualTo(403);
        assertThat(get(classPath + "/trend?weeks=2", teacher.token()).statusCode()).isEqualTo(400);

        // A child who joins today counts only from today: the earlier weeks are the class as it was.
        Parent other = parent();
        String newcomer = createChild(other.token(), "Bopha");
        confirmEmail(other);
        json(post("/api/v1/children/" + newcomer + "/schools/join", codeBody(code), other.token()), 200);
        JsonNode later = json(get(classPath + "/trend?weeks=4", teacher.token()), 200).get("points");
        assertThat(later.get(3).get("children").asInt()).isEqualTo(1);
        assertThat(later.get(4).get("children").asInt()).isEqualTo(2);
        assertThat(later.get(4).get("notStarted").asInt()).isEqualTo(1);
    }

    /** Evidence rows (one per answer, oldest first) as the mastery engine records them; history is append-only. */
    private void history(UUID child, UUID skill, Instant at, String... masteryAfterEach) {
        for (int i = 0; i < masteryAfterEach.length; i++) {
            jdbc.update("insert into learner_skill_history (id, child_id, skill_id, mastery_probability, confidence, triggered_by_id,"
                            + " triggered_by_type, occurred_at) values (?, ?, ?, ?, 0.1, ?, 'ACTIVITY_ATTEMPT', ?)",
                    UUID.randomUUID(), child, skill, new java.math.BigDecimal(masteryAfterEach[i]), UUID.randomUUID(),
                    java.sql.Timestamp.from(at.plusSeconds(i)));
        }
    }

    @Test
    void thePrincipalRenamesAndArchivesAClassAndArchiveMeansNoNewUsesWhileChildrenKeepEverything() throws Exception {
        Staff principal = staff(PlatformRole.PRINCIPAL);
        Staff teacher = staff(PlatformRole.TEACHER);
        SchoolClass taught = classTaughtBy(principal, teacher);
        String classPath = "/api/v1/schools/classes/" + taught.getId();
        Curriculum assigned = publishedCurriculum(null, "Lifecycle maths");
        Curriculum another = publishedCurriculum(null, "Lifecycle reading");
        json(post(classPath + "/curricula/" + assigned.getId(), "", teacher.token()), 200);
        String code = json(post(classPath + "/join-code", "", teacher.token()), 201).get("code").asString();
        Parent parent = parent();
        String childId = createChild(parent.token(), "Sokha");
        confirmEmail(parent);
        json(post("/api/v1/children/" + childId + "/schools/join", codeBody(code), parent.token()), 200);

        // Only the school's principal shapes its classes and its name.
        assertThat(put(classPath, "{\"name\":\"Teacher's name\"}", teacher.token()).statusCode()).isEqualTo(403);
        assertThat(put(classPath, "{\"name\":\"Elsewhere\"}", staff(PlatformRole.PRINCIPAL).token()).statusCode()).isEqualTo(403);
        assertThat(post(classPath + "/archive", "", teacher.token()).statusCode()).isEqualTo(403);
        assertThat(put(classPath, "{\"name\":\" \"}", principal.token()).statusCode()).isEqualTo(400);
        assertThat(json(put(classPath, "{\"name\":\"  Grade 1 Lotus \"}", principal.token()), 200).get("name").asString())
                .isEqualTo("Grade 1 Lotus");
        assertThat(json(put("/api/v1/schools/" + taught.getSchoolId(), "{\"name\":\"Riverside Primary\"}", principal.token()), 200)
                .get("name").asString()).isEqualTo("Riverside Primary");
        assertThat(put("/api/v1/schools/" + taught.getSchoolId(), "{\"name\":\"Mine now\"}", teacher.token()).statusCode()).isEqualTo(403);
        assertThat(audited("CLASS_UPDATED", principal.id())).isEqualTo(1);
        assertThat(audited("SCHOOL_UPDATED", principal.id())).isEqualTo(1);

        // Archive: the open code stops at once and nothing new can be added.
        JsonNode archived = json(post(classPath + "/archive", "", principal.token()), 200);
        assertThat(archived.get("archived").asBoolean()).isTrue();
        assertThat(get(classPath + "/join-code", teacher.token()).statusCode()).isEqualTo(204);
        Parent latecomer = parent();
        String lateChild = createChild(latecomer.token(), "Late");
        confirmEmail(latecomer);
        assertThat(post("/api/v1/children/" + lateChild + "/schools/join", codeBody(code), latecomer.token()).statusCode()).isEqualTo(404);
        assertThat(post(classPath + "/join-code", "", teacher.token()).statusCode()).isEqualTo(409);
        assertThat(post(classPath + "/curricula/" + another.getId(), "", teacher.token()).statusCode()).isEqualTo(409);
        assertThat(post(classPath + "/teachers", "{\"teacherAccountId\":\"" + teacher.id() + "\"}", principal.token()).statusCode())
                .isEqualTo(409);
        UUID skill = jdbc.queryForObject("select los.skill_id from curriculum_versions v join curriculum_objectives co on co.curriculum_version_id = v.id"
                + " join learning_objective_skills los on los.learning_objective_id = co.learning_objective_id where v.curriculum_id = ?",
                UUID.class, assigned.getId());
        assertThat(post(classPath + "/skills/" + skill + "/practice", "{\"activityId\":\"" + extraPracticeActivity(skill)
                + "\",\"childIds\":[\"" + childId + "\"]}", teacher.token()).statusCode()).isEqualTo(409);
        assertThat(post(classPath + "/archive", "", principal.token()).statusCode()).isEqualTo(409);

        // ...while the child keeps everything: the class, the school's access, the journey; staff can still read it.
        JsonNode learning = json(get(classPath + "/learning", teacher.token()), 200);
        assertThat(learning.get("members")).hasSize(1);
        assertThat(journeyIn(parent.token(), childId, assigned.getId())).isNotNull();
        assertThat(json(get("/api/v1/schools/classes/" + taught.getId(), principal.token()), 200).get("archived").asBoolean()).isTrue();
        // The school's setup no longer counts it as a gap; the class list says it is archived.
        JsonNode overview = json(get("/api/v1/schools/overview", principal.token()), 200);
        assertThat(overview.get("schools").get(0).get("classes").asInt()).isZero();
        assertThat(overview.get("classes").get(0).get("archived").asBoolean()).isTrue();
        assertThat(audited("CLASS_ARCHIVED", principal.id())).isEqualTo(1);

        // Restore: usable again, with a fresh code.
        assertThat(json(post(classPath + "/restore", "", principal.token()), 200).get("archived").asBoolean()).isFalse();
        assertThat(post(classPath + "/restore", "", principal.token()).statusCode()).isEqualTo(409);
        json(post(classPath + "/join-code", "", teacher.token()), 201);
        assertThat(audited("CLASS_RESTORED", principal.id())).isEqualTo(1);
    }

    @Test
    void thePrincipalSeesLearningAcrossTheSchoolClassByClassAndNoOneElseDoes() throws Exception {
        Staff principal = staff(PlatformRole.PRINCIPAL);
        Staff teacher = staff(PlatformRole.TEACHER);
        SchoolClass taught = classTaughtBy(principal, teacher);
        SchoolClass quiet = schoolService.createClass(taught.getSchoolId(), "Grade 2 Quiet");
        SchoolClass old = schoolService.createClass(taught.getSchoolId(), "Grade 0 Old");
        json(post("/api/v1/schools/classes/" + old.getId() + "/archive", "", principal.token()), 200);
        String classPath = "/api/v1/schools/classes/" + taught.getId();
        Curriculum assigned = publishedCurriculum(null, "School learning maths");
        json(post(classPath + "/curricula/" + assigned.getId(), "", teacher.token()), 200);
        String code = json(post(classPath + "/join-code", "", teacher.token()), 201).get("code").asString();
        Parent parent = parent();
        String childId = createChild(parent.token(), "Chenda");
        confirmEmail(parent);
        json(post("/api/v1/children/" + childId + "/schools/join", codeBody(code), parent.token()), 200);
        UUID child = UUID.fromString(childId);
        UUID skill = jdbc.queryForObject("""
                select los.skill_id from curriculum_versions v
                join curriculum_objectives co on co.curriculum_version_id = v.id
                join learning_objective_skills los on los.learning_objective_id = co.learning_objective_id
                where v.curriculum_id = ?""", UUID.class, assigned.getId());
        // In the class for three weeks; three answers two days ago make the skill secure.
        java.time.Instant now = java.time.Instant.now();
        jdbc.update("delete from class_enrollments where child_id = ?", child);
        jdbc.update("insert into class_enrollments (id, school_class_id, child_id, status, created_at, updated_at) values (?, ?, ?, 'ACTIVE', ?, ?)",
                UUID.randomUUID(), taught.getId(), child, java.sql.Timestamp.from(now.minus(java.time.Duration.ofDays(21))),
                java.sql.Timestamp.from(now.minus(java.time.Duration.ofDays(21))));
        history(child, skill, now.minus(java.time.Duration.ofDays(2)), "0.85", "0.90", "0.95");

        String path = "/api/v1/schools/" + taught.getSchoolId() + "/learning";
        HttpResponse<String> response = get(path, principal.token());
        JsonNode school = json(response, 200);
        assertThat(school.get("weeks").asInt()).isEqualTo(4);
        // Active classes only, by name.
        assertThat(school.get("classes").valueStream().map(c -> c.get("className").asString()).toList())
                .containsExactly("Grade 1", "Grade 2 Quiet");
        JsonNode row = school.get("classes").get(0);
        assertThat(row.get("children").asInt()).isEqualTo(1);
        assertThat(row.get("answeringThisWeek").asInt()).isEqualTo(1);
        assertThat(row.get("answersThisWeek").asInt()).isEqualTo(3);
        assertThat(row.get("now").get("secure").asInt()).isEqualTo(1);
        assertThat(row.get("movement").get("becameSecure").asInt()).isEqualTo(1);
        assertThat(school.get("classes").get(1).get("children").asInt()).isZero();
        assertThat(response.body()).doesNotContain("Chenda").doesNotContain(childId);

        // Not the class's teacher, another principal, an administrator or a family.
        assertThat(get(path, teacher.token()).statusCode()).isEqualTo(403);
        assertThat(get(path, staff(PlatformRole.PRINCIPAL).token()).statusCode()).isEqualTo(403);
        assertThat(get(path, staff(PlatformRole.ADMIN).token()).statusCode()).isEqualTo(403);
        assertThat(get(path, parent.token()).statusCode()).isEqualTo(403);
    }

    @Test
    void aTeachersOwnPublishedCurriculumCanBeAssignedToTheirClassButNoOneElsesCan() throws Exception {
        Staff principal = staff(PlatformRole.PRINCIPAL);
        Staff teacher = staff(PlatformRole.TEACHER);
        Staff otherTeacher = staff(PlatformRole.TEACHER);
        SchoolClass taught = classTaughtBy(principal, teacher);
        Curriculum mine = publishedCurriculum(teacher.id(), "My own maths");
        Curriculum theirs = publishedCurriculum(otherTeacher.id(), "Their maths");
        Curriculum platform = publishedCurriculum(null, "Platform maths");

        JsonNode assignable = json(get("/api/v1/schools/curricula/assignable", teacher.token()), 200);
        java.util.Map<String, Boolean> offered = new java.util.HashMap<>();
        assignable.forEach(c -> offered.put(c.get("id").asString(), c.get("own").asBoolean()));
        assertThat(offered).containsEntry(mine.getId().toString(), true).containsEntry(platform.getId().toString(), false)
                .doesNotContainKey(theirs.getId().toString());
        // Their own come first.
        assertThat(assignable.get(0).get("own").asBoolean()).isTrue();

        String classPath = "/api/v1/schools/classes/" + taught.getId();
        json(post(classPath + "/curricula/" + mine.getId(), "", teacher.token()), 200);
        assertThat(post(classPath + "/curricula/" + theirs.getId(), "", teacher.token()).statusCode()).isEqualTo(404);
        // A child who joins starts the teacher's curriculum with the class.
        String code = json(post(classPath + "/join-code", "", teacher.token()), 201).get("code").asString();
        Parent parent = parent();
        String childId = createChild(parent.token(), "Vanna");
        confirmEmail(parent);
        json(post("/api/v1/children/" + childId + "/schools/join", codeBody(code), parent.token()), 200);
        assertThat(journeyIn(parent.token(), childId, mine.getId()).get("totalSteps").asInt()).isPositive();

        // Families have no assignable list.
        assertThat(get("/api/v1/schools/curricula/assignable", parent.token()).statusCode()).isEqualTo(403);
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

    /** A second published activity practising the skill: what a teacher can place beyond the curriculum's own steps. */
    private String extraPracticeActivity(UUID skillId) {
        UUID subjectId = jdbc.queryForObject("select subject_id from skills where id = ?", UUID.class, skillId);
        Question question = questionService.createQuestion(subjectId, QuestionType.NUMERIC);
        var questionDraft = questionService.createDraftVersion(question.getId(), "2 + 2 = ?", "4", 1);
        questionService.tagSkill(questionDraft.getId(), skillId);
        var questionVersion = questionService.publish(questionDraft.getId());
        Activity activity = activityService.createActivity(subjectId, null, "PRACTICE", "More practice " + UUID.randomUUID());
        var draft = activityService.createDraftVersion(activity.getId(), "Add more", 1, 60);
        activityService.tagSkill(draft.getId(), skillId);
        activityService.addItem(draft.getId(), questionVersion.getId(), 0, java.math.BigDecimal.ONE);
        activityService.publish(draft.getId());
        return activity.getId().toString();
    }

    /** The child's journey in one curriculum (a child may also follow others, e.g. one started by age). */
    private JsonNode journeyIn(String token, String childId, UUID curriculumId) throws Exception {
        for (JsonNode overview : json(get("/api/v1/children/" + childId + "/enrollments", token), 200)) {
            if (overview.get("curriculum").get("id").asString().equals(curriculumId.toString())) {
                return overview;
            }
        }
        throw new AssertionError("no journey in " + curriculumId);
    }

    /** The child plays their next step in one curriculum, answering every question with {@code answer}. */
    private void playNext(String token, String childId, UUID curriculumId, String answer) throws Exception {
        JsonNode next = journeyIn(token, childId, curriculumId).get("nextStep");
        String attemptId = json(post("/api/v1/children/" + childId + "/activity-attempts", "{\"activityVersionId\":\""
                + next.get("activityVersionId").asString() + "\",\"learningPathItemId\":\"" + next.get("learningPathItemId").asString()
                + "\"}", token), 201).get("id").asString();
        JsonNode content = json(get("/api/v1/activity-versions/" + next.get("activityVersionId").asString() + "?childId=" + childId, token), 200);
        for (JsonNode item : content.get("items")) {
            json(post("/api/v1/activity-attempts/" + attemptId + "/answers", "{\"questionVersionId\":\""
                    + item.get("questionVersionId").asString() + "\",\"submittedAnswer\":\"" + answer + "\",\"idempotencyKey\":\""
                    + UUID.randomUUID() + "\"}", token), 201);
        }
        json(post("/api/v1/activity-attempts/" + attemptId + "/complete", "", token), 200);
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

    private HttpResponse<String> put(String path, String body, String bearer) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body)), bearer);
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
