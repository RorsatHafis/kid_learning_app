package com.platform.school;

import com.platform.PaidSchools;
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
import org.springframework.context.ApplicationContext;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A skill's follow-up (X10) lists the questions the class finds hardest: of the class's members who answered each, how
 * many got it wrong at least once - members only (a child outside the class, answering the same question, doesn't
 * count), children not answers, and only for the class's staff.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class ClassHardestQuestionsIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Hardest-Questions-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private SchoolService schoolService;
    @Autowired private SchoolStaffRepository schoolStaffRepository;
    @Autowired private SchoolStaffService schoolStaffService;
    @Autowired private ApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private AgeHubRepository ageHubRepository;
    @Autowired private LearningObjectiveService learningObjectiveService;
    @Autowired private SkillService skillService;
    @Autowired private LearningObjectiveSkillService learningObjectiveSkillService;
    @Autowired private QuestionService questionService;
    @Autowired private ActivityService activityService;
    @Autowired private CurriculumService curriculumService;

    private final HttpClient http = HttpClient.newHttpClient();

    private record Staff(UUID id, String token) {
    }

    private record Parent(UUID id, String token) {
    }

    private record Offered(Curriculum curriculum, UUID skillId) {
    }

    @Test
    void theClassesHardestQuestionsCountItsMembersOnly() throws Exception {
        Staff principal = staff(PlatformRole.PRINCIPAL);
        Staff teacher = staff(PlatformRole.TEACHER);
        SchoolClass schoolClass = classTaughtBy(principal, teacher);
        String classPath = "/api/v1/schools/classes/" + schoolClass.getId();
        Offered offered = publishedCurriculum();
        UUID curriculumId = offered.curriculum().getId();
        json(post(classPath + "/curricula/" + curriculumId, "", teacher.token()), 200);
        String code = json(post(classPath + "/join-code", "", teacher.token()), 201).get("code").asString();

        // Two members: one answers wrong, one right. Nothing answered yet: no hardest questions.
        Parent parent = parent();
        confirmEmail(parent);
        String wrongChild = createChild(parent.token(), "Dara");
        String rightChild = createChild(parent.token(), "Nita");
        for (String child : new String[]{wrongChild, rightChild}) {
            json(post("/api/v1/children/" + child + "/schools/join", "{\"code\":\"" + code + "\"}", parent.token()), 200);
        }
        String skillPath = classPath + "/skills/" + offered.skillId();
        assertThat(json(get(skillPath, teacher.token()), 200).get("mostMissed")).isEmpty();
        playNext(parent.token(), wrongChild, curriculumId, "3");
        playNext(parent.token(), rightChild, curriculumId, "2");

        // A child outside the class answers the same question wrong too - not one of the class's.
        Parent other = parent();
        confirmEmail(other);
        String outsider = createChild(other.token(), "Sok");
        json(post("/api/v1/children/" + outsider + "/enrollments", "{\"curriculumId\":\"" + curriculumId + "\"}", other.token()), 201);
        playNext(other.token(), outsider, curriculumId, "5");

        JsonNode missed = json(get(skillPath, teacher.token()), 200).get("mostMissed");
        assertThat(missed).hasSize(1);
        assertThat(missed.get(0).get("prompt").asString()).isEqualTo("1 + 1 = ?");
        assertThat(missed.get(0).get("answered").asInt()).as("members who answered").isEqualTo(2);
        assertThat(missed.get(0).get("wrong").asInt()).as("members who got it wrong").isEqualTo(1);

        // The same child wrong again counts once (children, not answers).
        playAgain(parent.token(), wrongChild, curriculumId, "4");
        assertThat(json(get(skillPath, teacher.token()), 200).get("mostMissed").get(0).get("wrong").asInt()).isEqualTo(1);

        // The class's principal sees it too; a teacher of another class doesn't.
        assertThat(json(get(skillPath, principal.token()), 200).get("mostMissed")).hasSize(1);
        Staff stranger = staff(PlatformRole.TEACHER);
        assertThat(get(skillPath, stranger.token()).statusCode()).isIn(403, 404);
    }

    // ------------------------------------------------------------------ fixtures (as SchoolAccessIntegrationTests)

    private Staff staff(PlatformRole role) throws Exception {
        String email = "hardest-" + role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        Account account = accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), role));
        return new Staff(account.getId(), json(post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                + "\"}", null), 200).get("accessToken").asString());
    }

    private SchoolClass classTaughtBy(Staff principal, Staff teacher) {
        School school = schoolService.createSchool("Hardest School", principal.id());
        PaidSchools.pay(context, school.getId());
        SchoolClass schoolClass = schoolService.createClass(school.getId(), "Grade 1");
        SchoolStaff membership = SchoolStaff.invite(school.getId(), teacher.id(), principal.id(), Instant.now());
        membership.accept(Instant.now());
        schoolStaffRepository.save(membership);
        schoolStaffService.assign(schoolClass.getId(), teacher.id(), principal.id());
        return schoolClass;
    }

    private Parent parent() throws Exception {
        String email = "hardest-parent-" + UUID.randomUUID() + "@example.com";
        String token = json(post("/api/v1/auth/register", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null), 201).get("accessToken").asString();
        UUID id = jdbc.queryForObject("select id from accounts where normalized_email = ?", UUID.class, email);
        return new Parent(id, token);
    }

    private void confirmEmail(Parent parent) {
        jdbc.update("update accounts set email_verified_at = now() where id = ?", parent.id());
    }

    private String createChild(String parentToken, String name) throws Exception {
        return json(post("/api/v1/children", "{\"displayName\":\"" + name + "\",\"dateOfBirth\":\"" + LocalDate.now().minusYears(7)
                + "\"}", parentToken), 201).get("id").asString();
    }

    private Offered publishedCurriculum() {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        UUID subjectId = subjectRepository.save(Subject.create("HARD_" + suffix, "Hard " + suffix, null)).getId();
        UUID ageHubId = ageHubRepository.save(AgeHub.create("HARD_" + suffix, "Hard " + suffix, 90, 99, 99)).getId();
        LearningObjective objective = learningObjectiveService.create(subjectId, "HARD_OBJ_" + suffix, "Objective " + suffix, null);
        Skill skill = skillService.createSkill(subjectId, "HARD_" + suffix, "Skill " + suffix, null);
        learningObjectiveSkillService.link(objective.getId(), skill.getId());
        Question question = questionService.createQuestion(subjectId, QuestionType.NUMERIC);
        var questionDraft = questionService.createDraftVersion(question.getId(), "1 + 1 = ?", "2", 1);
        questionService.tagSkill(questionDraft.getId(), skill.getId());
        var questionVersion = questionService.publish(questionDraft.getId());
        Activity activity = activityService.createActivity(subjectId, null, "PRACTICE", "Hard " + suffix);
        var activityVersion = activityService.createDraftVersion(activity.getId(), "Add", 1, 60);
        activityService.tagSkill(activityVersion.getId(), skill.getId());
        activityService.addItem(activityVersion.getId(), questionVersion.getId(), 0, java.math.BigDecimal.ONE);
        activityService.publish(activityVersion.getId());
        Curriculum curriculum = curriculumService.createCurriculum(subjectId, ageHubId, "Hard " + suffix);
        var version = curriculumService.createDraftVersion(curriculum.getId());
        curriculumService.addObjective(version.getId(), objective.getId(), 0);
        curriculumService.publish(version.getId());
        return new Offered(curriculum, skill.getId());
    }

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
        play(token, childId, next.get("activityVersionId").asString(),
                ",\"learningPathItemId\":\"" + next.get("learningPathItemId").asString() + "\"", answer);
    }

    /** The same activity again as free practice (off the path): the child answers the question once more. */
    private void playAgain(String token, String childId, UUID curriculumId, String answer) throws Exception {
        String activityVersionId = jdbc.queryForObject("""
                select a.activity_version_id from activity_attempts a where a.child_id = ?::uuid order by a.started_at limit 1
                """, String.class, childId);
        play(token, childId, activityVersionId, "", answer);
    }

    private void play(String token, String childId, String activityVersionId, String pathPart, String answer) throws Exception {
        String attemptId = json(post("/api/v1/children/" + childId + "/activity-attempts", "{\"activityVersionId\":\""
                + activityVersionId + "\"" + pathPart + "}", token), 201).get("id").asString();
        JsonNode content = json(get("/api/v1/activity-versions/" + activityVersionId + "?childId=" + childId, token), 200);
        for (JsonNode item : content.get("items")) {
            json(post("/api/v1/activity-attempts/" + attemptId + "/answers", "{\"questionVersionId\":\""
                    + item.get("questionVersionId").asString() + "\",\"submittedAnswer\":\"" + answer + "\",\"idempotencyKey\":\""
                    + UUID.randomUUID() + "\"}", token), 201);
        }
        json(post("/api/v1/activity-attempts/" + attemptId + "/complete", "", token), 200);
    }

    // ------------------------------------------------------------------ HTTP

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
