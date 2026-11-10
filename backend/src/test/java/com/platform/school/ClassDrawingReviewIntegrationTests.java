package com.platform.school;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.PaidSchools;
import com.platform.TestcontainersConfiguration;
import com.platform.content.entity.Activity;
import com.platform.content.entity.Question;
import com.platform.content.entity.QuestionType;
import com.platform.content.service.ActivityService;
import com.platform.content.service.QuestionService;
import com.platform.curriculum.entity.Curriculum;
import com.platform.curriculum.entity.LearningObjective;
import com.platform.curriculum.entity.Subject;
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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
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

/**
 * Owner-approved 2026-11-10 (V91): a class's teachers review the drawings made in the learning that class assigns -
 * never the family's own learning at home (CLAUDE.md §15) - and see only the school's feedback; the family sees all.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class ClassDrawingReviewIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Class-Drawing-Pass-2026";
    private static final String DRAWN = "{\"strokes\":[[[0.2,0.2],[0.8,0.8]]]}";

    @Value("${local.server.port}") private int port;
    @Autowired private org.springframework.context.ApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private SchoolService schoolService;
    @Autowired private SchoolStaffService schoolStaffService;
    @Autowired private SchoolStaffRepository schoolStaffRepository;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private CurriculumService curriculumService;
    @Autowired private LearningObjectiveService learningObjectiveService;
    @Autowired private SkillService skillService;
    @Autowired private LearningObjectiveSkillService learningObjectiveSkillService;
    @Autowired private QuestionService questionService;
    @Autowired private ActivityService activityService;

    private final HttpClient http = HttpClient.newHttpClient();

    private record Staff(UUID id, String token) {
    }

    @Test
    void aTeacherSeesOnlyTheClasssDrawingsAndOnlyTheSchoolsFeedback() throws Exception {
        Staff principal = staff(PlatformRole.PRINCIPAL);
        Staff teacher = staff(PlatformRole.TEACHER);
        SchoolClass schoolClass = classTaughtBy(principal, teacher);
        String classPath = "/api/v1/schools/classes/" + schoolClass.getId();
        Curriculum assigned = drawingCurriculum("Draw at school");
        Curriculum home = drawingCurriculum("Draw at home");
        json(post(classPath + "/curricula/" + assigned.getId(), "", teacher.token()), 200);
        String code = json(post(classPath + "/join-code", "", teacher.token()), 201).get("code").asString();

        // A family on a plan that includes both, its child learning at home and joining the class.
        String email = "class-drawing-parent-" + UUID.randomUUID() + "@example.com";
        String parent = com.platform.TestPlans.everythingIncluded(jdbc, json(post("/api/v1/auth/register", "{\"email\":\"" + email
                + "\",\"password\":\"" + PASSWORD + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null), 201))
                .get("accessToken").asString();
        String childId = json(post("/api/v1/children", "{\"displayName\":\"Dara\",\"dateOfBirth\":\"" + LocalDate.now().minusYears(7)
                + "\",\"familyChoosesSubjects\":true}", parent), 201).get("id").asString();
        json(post("/api/v1/children/" + childId + "/enrollments", "{\"curriculumId\":\"" + home.getId() + "\"}", parent), 201);
        json(post("/api/v1/children/" + childId + "/schools/join", "{\"code\":\"" + code + "\"}", parent), 200);
        drawIn(parent, childId, assigned.getId());
        drawIn(parent, childId, home.getId());

        // The teacher: the class's drawing only.
        JsonNode seen = json(get(classPath + "/drawings", teacher.token()), 200);
        assertThat(seen).hasSize(1);
        assertThat(seen.get(0).get("activityTitle").asString()).startsWith("Draw at school");
        assertThat(seen.get(0).get("childName").asString()).isEqualTo("Dara");
        String classDrawing = seen.get(0).get("answerId").asString();
        // The family's words on it stay the family's.
        json(post("/api/v1/children/" + childId + "/drawings/" + classDrawing + "/feedback", "{\"body\":\"From home\"}", parent), 200);
        JsonNode reviewed = json(post(classPath + "/drawings/" + classDrawing + "/feedback", "{\"body\":\"Neat line, Dara!\"}", teacher.token()), 200);
        assertThat(reviewed.get("feedback")).hasSize(1);
        assertThat(reviewed.get("feedback").get(0).get("body").asString()).isEqualTo("Neat line, Dara!");
        assertThat(reviewed.get("feedback").get(0).get("side").asString()).isEqualTo("SCHOOL");
        // The home drawing can't be reached through the class.
        String homeDrawing = jdbc.queryForObject("""
                select r.id::text from answer_records r join activity_attempts a on a.id = r.activity_attempt_id
                join activity_versions av on av.id = a.activity_version_id join activities act on act.id = av.activity_id
                where a.child_id = ?::uuid and not r.scored and act.title like 'Draw at home%'
                """, String.class, UUID.fromString(childId));
        assertThat(post(classPath + "/drawings/" + homeDrawing + "/feedback", "{\"body\":\"Hi\"}", teacher.token()).statusCode()).isEqualTo(404);

        // The family sees both drawings and all the feedback.
        JsonNode family = json(get("/api/v1/children/" + childId + "/drawings", parent), 200);
        assertThat(family).hasSize(2);
        assertThat(family.toString()).contains("From home").contains("Neat line, Dara!");

        // A teacher not assigned to the class sees nothing of it.
        Staff other = staff(PlatformRole.TEACHER);
        assertThat(get(classPath + "/drawings", other.token()).statusCode()).isIn(403, 404);
    }

    // ---------------------------------------------------------------------------------------------------------------

    private void drawIn(String parent, String childId, UUID curriculumId) throws Exception {
        String enrollment = null;
        for (JsonNode overview : json(get("/api/v1/children/" + childId + "/enrollments", parent), 200)) {
            if (overview.get("curriculum").get("id").asString().equals(curriculumId.toString())) {
                enrollment = overview.get("enrollmentId").asString();
            }
        }
        assertThat(enrollment).as("on " + curriculumId).isNotNull();
        JsonNode next = json(get("/api/v1/enrollments/" + enrollment + "/next-activity", parent), 200);
        String attempt = json(post("/api/v1/children/" + childId + "/activity-attempts", "{\"activityVersionId\":\""
                + next.get("activityVersionId").asString() + "\",\"learningPathItemId\":\"" + next.get("id").asString() + "\"}", parent), 201)
                .get("id").asString();
        String version = jdbc.queryForObject("select question_version_id::text from activity_items where activity_version_id = ?::uuid",
                String.class, UUID.fromString(next.get("activityVersionId").asString()));
        json(post("/api/v1/activity-attempts/" + attempt + "/answers", "{\"questionVersionId\":\"" + version + "\",\"submittedAnswer\":"
                + JSON.writeValueAsString(DRAWN) + ",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", parent), 201);
        json(post("/api/v1/activity-attempts/" + attempt + "/complete", "", parent), 200);
    }

    private Curriculum drawingCurriculum(String title) {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        UUID subjectId = subjectRepository.save(Subject.create("CDRW_" + suffix, "Class drawing " + suffix, null)).getId();
        LearningObjective objective = learningObjectiveService.create(subjectId, "CDRW_OBJ_" + suffix, "Objective " + suffix, null);
        Skill skill = skillService.createSkill(subjectId, "CDRW_" + suffix, "Writing " + suffix, null);
        learningObjectiveSkillService.link(objective.getId(), skill.getId());
        Question question = questionService.createQuestion(subjectId, QuestionType.DRAWING);
        var draft = questionService.createDraftVersion(question.getId(), "Draw a line.", null, 1);
        questionService.tagSkill(draft.getId(), skill.getId());
        questionService.setDrawingGuide(draft.getId(), "{\"mode\":\"FREE\",\"baseline\":false,\"grid\":true}");
        var published = questionService.publish(draft.getId());
        Activity activity = activityService.createActivity(subjectId, null, "PRACTICE", title + " " + suffix);
        var activityVersion = activityService.createDraftVersion(activity.getId(), "Draw", 1, 60);
        activityService.tagSkill(activityVersion.getId(), skill.getId());
        activityService.addItem(activityVersion.getId(), published.getId(), 0, java.math.BigDecimal.ONE);
        activityService.publish(activityVersion.getId());
        Curriculum curriculum = curriculumService.createCurriculum(subjectId, null, title + " " + suffix, 90, 99, null);
        var version = curriculumService.createDraftVersion(curriculum.getId());
        curriculumService.addObjective(version.getId(), objective.getId(), 0);
        curriculumService.publish(version.getId());
        return curriculum;
    }

    private Staff staff(PlatformRole role) throws Exception {
        String email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        Account account = accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), role));
        JsonNode body = json(post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", null), 200);
        return new Staff(account.getId(), body.get("accessToken").asString());
    }

    private SchoolClass classTaughtBy(Staff principal, Staff teacher) {
        School school = schoolService.createSchool("Drawing School", principal.id());
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

    private HttpResponse<String> send(HttpRequest.Builder builder, String bearer) throws Exception {
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
