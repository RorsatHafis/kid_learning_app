package com.platform.subscription;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
 * Owner requirement 2026-11-10 (V89): a subject is assigned to the family plans that include it, in the Content Studio
 * (administrators). A family whose plan doesn't include it neither sees nor starts, switches to or plays it; nothing
 * learned is lost when a plan stops including it. Over real HTTP and PostgreSQL.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class SubjectPlansIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Subject-Plans-Test-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private AgeHubRepository ageHubRepository;
    @Autowired private LearningObjectiveService learningObjectiveService;
    @Autowired private SkillService skillService;
    @Autowired private LearningObjectiveSkillService learningObjectiveSkillService;
    @Autowired private ActivityService activityService;
    @Autowired private QuestionService questionService;
    @Autowired private CurriculumService curriculumService;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void aNewSubjectIsInEveryPlanAndOnlyAnAdministratorChoosesItsPlans() throws Exception {
        String admin = token(PlatformRole.ADMIN);
        Made made = made();
        String path = "/api/v1/studio/subjects/" + made.subjectId() + "/plans";

        JsonNode view = json(get(path, admin), 200);
        assertThat(view.get("everyPlan").asBoolean()).isTrue();
        assertThat(view.get("planIds")).isEmpty();
        List<String> ids = new ArrayList<>();
        view.get("plans").forEach(plan -> ids.add(plan.get("id").asString()));
        assertThat(view.get("plans").get(0).get("code").asString()).as("the default plan first").isEqualTo("FAMILY_FREE_2026_11");
        for (String id : ids) {
            assertThat(jdbc.queryForObject("select audience from subscription_plans where id = ?::uuid", String.class, UUID.fromString(id)))
                    .isEqualTo("FAMILY");
        }

        assertThat(get(path, token(PlatformRole.TEACHER)).statusCode()).isEqualTo(403);
        assertThat(put(path, "{\"everyPlan\":true}", token(PlatformRole.TEACHER)).statusCode()).isEqualTo(403);
        assertThat(get(path, registerParent("subject-parent-" + UUID.randomUUID() + "@example.com")).statusCode()).isEqualTo(403);

        // Nothing chosen is refused; a school plan isn't a family plan.
        assertThat(put(path, "{\"everyPlan\":false,\"planIds\":[]}", admin).statusCode()).isEqualTo(400);
        String schoolPlan = jdbc.queryForObject("select id::text from subscription_plans where audience = 'SCHOOL' limit 1", String.class);
        assertThat(put(path, "{\"everyPlan\":false,\"planIds\":[\"" + schoolPlan + "\"]}", admin).statusCode()).isEqualTo(400);
        assertThat(json(get(path, admin), 200).get("everyPlan").asBoolean()).as("unchanged").isTrue();
    }

    @Test
    void aFamilyOnlySeesAndStartsTheSubjectsItsPlanIncludesAndKeepsItsLearningWhenThatChanges() throws Exception {
        String admin = token(PlatformRole.ADMIN);
        String plus = activePlan(admin, "PLUS");
        Made made = made();
        json(put("/api/v1/studio/subjects/" + made.subjectId() + "/plans", "{\"everyPlan\":false,\"planIds\":[\"" + plus + "\"]}",
                admin), 200);
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action = 'CATALOG_SUBJECT_PLANS_CHANGED' "
                + "and resource_id = ?::uuid", Integer.class, UUID.fromString(made.subjectId()))).isEqualTo(1);

        String email = "subject-family-" + UUID.randomUUID() + "@example.com";
        String parent = registerParent(email);
        String childId = json(post("/api/v1/children", child("Dara"), parent), 201).get("id").asString();
        jdbc.update("update enrollments set status = 'WITHDRAWN' where child_id = ?::uuid", UUID.fromString(childId));

        // On the free plan: not offered, and a direct request is refused.
        assertThat(catalogIds(childId, parent)).doesNotContain(made.curriculumId());
        JsonNode refused = json(post("/api/v1/children/" + childId + "/enrollments", choose(made.curriculumId()), parent), 409);
        assertThat(refused.get("subjectNotInPlan").asBoolean()).isTrue();
        assertThat(refused.get("detail").asString()).isEqualTo("This subject isn't part of your family's plan.");
        assertThat(jdbc.queryForObject("select count(*) from enrollments where child_id = ?::uuid and status <> 'WITHDRAWN'",
                Integer.class, UUID.fromString(childId))).isZero();

        // On a plan that includes it: offered, started, played.
        String familyId = json(get("/api/v1/admin/families?parentEmail=" + URLEncoder.encode(email, StandardCharsets.UTF_8), admin), 200)
                .get("familyId").asString();
        json(post("/api/v1/admin/families/" + familyId + "/subscription", "{\"planId\":\"" + plus + "\"}", admin), 200);
        assertThat(catalogIds(childId, parent)).contains(made.curriculumId());
        String enrollment = json(post("/api/v1/children/" + childId + "/enrollments", choose(made.curriculumId()), parent), 201)
                .get("id").asString();
        assertThat(get("/api/v1/enrollments/" + enrollment + "/next-activity", parent).statusCode()).isEqualTo(200);
        assertThat(overview(childId, parent, enrollment).get("inPlan").asBoolean()).isTrue();

        // The plan ends: the journey is kept, marked, and can't be played until a plan includes it again.
        json(post("/api/v1/admin/families/" + familyId + "/subscription/end", "{\"reason\":\"Over\"}", admin), 200);
        assertThat(overview(childId, parent, enrollment).get("inPlan").asBoolean()).isFalse();
        assertThat(get("/api/v1/enrollments/" + enrollment + "/next-activity", parent).statusCode()).isEqualTo(409);
        assertThat(jdbc.queryForObject("select status from enrollments where id = ?::uuid", String.class, UUID.fromString(enrollment)))
                .isEqualTo("ACTIVE");
        json(post("/api/v1/admin/families/" + familyId + "/subscription", "{\"planId\":\"" + plus + "\"}", admin), 200);
        assertThat(get("/api/v1/enrollments/" + enrollment + "/next-activity", parent).statusCode()).isEqualTo(200);
    }

    @Test
    void aPlansNewVersionIncludesTheSameSubjects() throws Exception {
        String admin = token(PlatformRole.ADMIN);
        String plus = activePlan(admin, "VERS");
        Made made = made();
        json(put("/api/v1/studio/subjects/" + made.subjectId() + "/plans", "{\"everyPlan\":false,\"planIds\":[\"" + plus + "\"]}",
                admin), 200);
        String copy = json(post("/api/v1/admin/plans/" + plus + "/duplicate", "{\"code\":\"VERS2_" + suffix() + "\",\"name\":\"Next\"}",
                admin), 201).get("plan").get("id").asString();
        List<String> planIds = new ArrayList<>();
        json(get("/api/v1/studio/subjects/" + made.subjectId() + "/plans", admin), 200).get("planIds")
                .forEach(id -> planIds.add(id.asString()));
        assertThat(planIds).containsExactlyInAnyOrder(plus, copy);
    }

    // ---------------------------------------------------------------------------------------------------------------

    private record Made(String subjectId, String curriculumId) {
    }

    private List<String> catalogIds(String childId, String parent) throws Exception {
        List<String> ids = new ArrayList<>();
        json(get("/api/v1/children/" + childId + "/curricula", parent), 200).forEach(c -> ids.add(c.get("id").asString()));
        return ids;
    }

    private JsonNode overview(String childId, String parent, String enrollmentId) throws Exception {
        for (JsonNode overview : json(get("/api/v1/children/" + childId + "/enrollments", parent), 200)) {
            if (overview.get("enrollmentId").asString().equals(enrollmentId)) {
                return overview;
            }
        }
        throw new AssertionError("no journey " + enrollmentId);
    }

    private String activePlan(String admin, String prefix) throws Exception {
        String planId = json(post("/api/v1/admin/plans", "{\"code\":\"" + prefix + "_" + suffix()
                + "\",\"name\":\"Family plus\",\"description\":\"For tests\"}", admin), 201).get("plan").get("id").asString();
        json(put("/api/v1/admin/plans/" + planId, "{\"name\":\"Family plus\",\"description\":\"For tests\",\"selfService\":false,"
                + "\"limits\":{\"CHILDREN_MAX\":null,\"JOURNEYS_PER_CHILD_MAX\":null,\"ACTIVE_JOURNEYS_PER_CHILD_MAX\":null}}", admin), 200);
        json(post("/api/v1/admin/plans/" + planId + "/activate", "{}", admin), 200);
        return planId;
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private static String choose(String curriculumId) {
        return "{\"curriculumId\":\"" + curriculumId + "\"}";
    }

    private static String child(String name) {
        return "{\"displayName\":\"" + name + "\",\"dateOfBirth\":\"" + LocalDate.now().minusYears(6) + "\"}";
    }

    /** A published platform curriculum of its own subject (one activity, one question). */
    private Made made() {
        String suffix = suffix();
        UUID subjectId = subjectRepository.save(Subject.create("SUBPLAN_" + suffix, "Subject " + suffix, null)).getId();
        UUID ageHubId = ageHubRepository.save(AgeHub.create("SUBPLAN_" + suffix, "Subject " + suffix, 90, 95, 990)).getId();
        LearningObjective objective = learningObjectiveService.create(subjectId, "SUBPLAN_OBJ_" + suffix, "Objective " + suffix, null);
        Skill skill = skillService.createSkill(subjectId, "SUBPLAN_" + suffix, "Skill " + suffix, null);
        learningObjectiveSkillService.link(objective.getId(), skill.getId());
        Activity activity = activityService.createActivity(subjectId, null, "PRACTICE", "Subject " + suffix);
        var activityVersion = activityService.createDraftVersion(activity.getId(), "Add", 1, 60);
        activityService.tagSkill(activityVersion.getId(), skill.getId());
        Question question = questionService.createQuestion(subjectId, QuestionType.NUMERIC);
        var draft = questionService.createDraftVersion(question.getId(), "1 + 1 = ?", "2", 1);
        questionService.tagSkill(draft.getId(), skill.getId());
        activityService.addItem(activityVersion.getId(), questionService.publish(draft.getId()).getId(), 0, java.math.BigDecimal.ONE);
        activityService.publish(activityVersion.getId());
        Curriculum curriculum = curriculumService.createCurriculum(subjectId, ageHubId, "Subject " + suffix);
        var version = curriculumService.createDraftVersion(curriculum.getId());
        curriculumService.addObjective(version.getId(), objective.getId(), 0);
        curriculumService.publish(version.getId());
        return new Made(subjectId.toString(), curriculum.getId().toString());
    }

    private String registerParent(String email) throws Exception {
        return json(post("/api/v1/auth/register", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\","
                + "\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null), 201).get("accessToken").asString();
    }

    private String token(PlatformRole role) throws Exception {
        String email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), role));
        String token = json(post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", null), 200)
                .get("accessToken").asString();
        return role == PlatformRole.ADMIN ? com.platform.TestAdmin.stepUp(port, token, PASSWORD) : token;
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
