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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
 * Owner requirement 2026-11-10 (V88): a family without a subscription is on the free plan - one child, up to three
 * learning options for it, one active at a time, switching between them - enforced by the server for every request,
 * with nothing deleted when a plan changes. Over real HTTP and PostgreSQL.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class FreePlanIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Free-Plan-Test-Pass-2026";

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
    void aNewFamilyIsOnTheFreePlanWithItsLimitsShown() throws Exception {
        String parent = registerParent("free-" + UUID.randomUUID() + "@example.com");

        JsonNode plan = json(get("/api/v1/family/plan", parent), 200);
        assertThat(plan.get("onDefaultPlan").asBoolean()).isTrue();
        assertThat(plan.get("plan").get("code").asString()).isEqualTo("FAMILY_FREE_2026_11");
        assertThat(plan.get("subscription").isNull()).isTrue();
        assertThat(limit(plan, "CHILDREN_MAX")).isEqualTo(1);
        assertThat(limit(plan, "JOURNEYS_PER_CHILD_MAX")).isEqualTo(3);
        assertThat(limit(plan, "ACTIVE_JOURNEYS_PER_CHILD_MAX")).isEqualTo(1);
    }

    @Test
    void oneChildThreeOptionsOneActiveAndTheFamilySwitchesBetweenThem() throws Exception {
        String parent = registerParent("switch-" + UUID.randomUUID() + "@example.com");
        String childId = json(post("/api/v1/children", child("Dara"), parent), 201).get("id").asString();
        clearAutomaticJourneys(childId);

        // A second child: refused, said plainly, and nothing created.
        JsonNode secondChild = json(post("/api/v1/children", child("Sokha"), parent), 409);
        assertThat(secondChild.get("entitlement").asString()).isEqualTo("CHILDREN_MAX");
        assertThat(secondChild.get("detail").asString()).isEqualTo("Your family's plan includes one child.");
        assertThat(jdbc.queryForObject("select count(*) from children where family_id = (select family_id from children where id = ?::uuid)",
                Integer.class, UUID.fromString(childId))).isEqualTo(1);

        // Three options: the first is learned, the next two wait. A fourth is beyond the plan.
        String enrollments = "/api/v1/children/" + childId + "/enrollments";
        List<String> curricula = List.of(curriculum(), curriculum(), curriculum(), curriculum());
        JsonNode first = json(post(enrollments, choose(curricula.get(0)), parent), 201);
        JsonNode second = json(post(enrollments, choose(curricula.get(1)), parent), 201);
        JsonNode third = json(post(enrollments, choose(curricula.get(2)), parent), 201);
        assertThat(first.get("status").asString()).isEqualTo("ACTIVE");
        assertThat(second.get("status").asString()).isEqualTo("PAUSED");
        assertThat(third.get("status").asString()).isEqualTo("PAUSED");
        JsonNode fourth = json(post(enrollments, choose(curricula.get(3)), parent), 409);
        assertThat(fourth.get("entitlement").asString()).isEqualTo("JOURNEYS_PER_CHILD_MAX");
        assertThat(fourth.get("limit").asInt()).isEqualTo(3);
        // Choosing one again is not a new option (no double counting).
        assertThat(json(post(enrollments, choose(curricula.get(1)), parent), 201).get("id").asString())
                .isEqualTo(second.get("id").asString());

        // A waiting option can't be played, even from an old screen.
        String secondId = second.get("id").asString();
        assertThat(get("/api/v1/enrollments/" + secondId + "/next-activity", parent).statusCode()).isEqualTo(409);

        // Switching: the chosen one is learned, the one before waits - nothing lost.
        JsonNext start = next(first.get("id").asString(), parent);
        String openAttempt = json(post("/api/v1/children/" + childId + "/activity-attempts", start.body(), parent), 201).get("id").asString();
        assertThat(json(post("/api/v1/enrollments/" + secondId + "/switch", "", parent), 200).get("status").asString()).isEqualTo("ACTIVE");
        assertThat(status(first.get("id").asString())).isEqualTo("PAUSED");
        assertThat(status(third.get("id").asString())).isEqualTo("PAUSED");
        assertThat(jdbc.queryForObject("select status from activity_attempts where id = ?::uuid", String.class, UUID.fromString(openAttempt)))
                .as("the open activity waits with its journey").isEqualTo("IN_PROGRESS");
        assertThat(post("/api/v1/children/" + childId + "/activity-attempts", start.body(), parent).statusCode())
                .as("the waiting journey's step can't be started").isEqualTo(409);
        assertThat(json(get("/api/v1/enrollments/" + secondId + "/next-activity", parent), 200).get("id").isNull()).isFalse();
        // A repeated tap changes nothing; a stopped option can't be switched to.
        assertThat(json(post("/api/v1/enrollments/" + secondId + "/switch", "", parent), 200).get("status").asString()).isEqualTo("ACTIVE");
        assertThat(activeCount(childId)).isEqualTo(1);
        json(post("/api/v1/enrollments/" + third.get("id").asString() + "/stop", "", parent), 200);
        assertThat(post("/api/v1/enrollments/" + third.get("id").asString() + "/switch", "", parent).statusCode()).isEqualTo(409);

        // Switching back: the first carries on where it was, its open activity too.
        json(post("/api/v1/enrollments/" + first.get("id").asString() + "/switch", "", parent), 200);
        assertThat(status(secondId)).isEqualTo("PAUSED");
        assertThat(next(first.get("id").asString(), parent).itemId()).isEqualTo(start.itemId());

        // Another family can't see or switch this child's journeys.
        String stranger = registerParent("stranger-" + UUID.randomUUID() + "@example.com");
        assertThat(post("/api/v1/enrollments/" + secondId + "/switch", "", stranger).statusCode()).isEqualTo(404);
        assertThat(status(secondId)).isEqualTo("PAUSED");
    }

    @Test
    void twoSwitchesAtOnceLeaveExactlyOneJourneyActive() throws Exception {
        String parent = registerParent("race-" + UUID.randomUUID() + "@example.com");
        String childId = json(post("/api/v1/children", child("Vanna"), parent), 201).get("id").asString();
        clearAutomaticJourneys(childId);
        String enrollments = "/api/v1/children/" + childId + "/enrollments";
        json(post(enrollments, choose(curriculum()), parent), 201);
        String b = json(post(enrollments, choose(curriculum()), parent), 201).get("id").asString();
        String c = json(post(enrollments, choose(curriculum()), parent), 201).get("id").asString();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Integer>> both = List.of(
                    () -> post("/api/v1/enrollments/" + b + "/switch", "", parent).statusCode(),
                    () -> post("/api/v1/enrollments/" + c + "/switch", "", parent).statusCode());
            List<Integer> answers = new ArrayList<>();
            for (Future<Integer> answer : pool.invokeAll(both)) {
                answers.add(answer.get());
            }
            assertThat(answers).containsOnly(200);
        } finally {
            pool.shutdownNow();
        }
        assertThat(activeCount(childId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from enrollments where child_id = ?::uuid and status = 'PAUSED'",
                Integer.class, UUID.fromString(childId))).isEqualTo(2);
    }

    @Test
    void anUpgradeGivesMoreAndItsEndKeepsEverything() throws Exception {
        String admin = token(PlatformRole.ADMIN);
        String email = "upgrade-" + UUID.randomUUID() + "@example.com";
        String parent = registerParent(email);
        String childId = json(post("/api/v1/children", child("Bopha"), parent), 201).get("id").asString();
        clearAutomaticJourneys(childId);
        String enrollments = "/api/v1/children/" + childId + "/enrollments";
        json(post(enrollments, choose(curriculum()), parent), 201);
        String waiting = json(post(enrollments, choose(curriculum()), parent), 201).get("id").asString();

        // A plan with no limits, granted by the administrator.
        String planId = json(post("/api/v1/admin/plans", "{\"code\":\"OPEN_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()
                + "\",\"name\":\"Open plan\",\"description\":\"For tests\"}", admin), 201).get("plan").get("id").asString();
        json(put("/api/v1/admin/plans/" + planId, "{\"name\":\"Open plan\",\"description\":\"For tests\",\"selfService\":false,"
                + "\"limits\":{\"CHILDREN_MAX\":null,\"JOURNEYS_PER_CHILD_MAX\":null,\"ACTIVE_JOURNEYS_PER_CHILD_MAX\":null}}", admin), 200);
        json(post("/api/v1/admin/plans/" + planId + "/activate", "{}", admin), 200);
        String familyId = json(get("/api/v1/admin/families?parentEmail=" + URLEncoder.encode(email, StandardCharsets.UTF_8), admin), 200)
                .get("familyId").asString();
        json(post("/api/v1/admin/families/" + familyId + "/subscription", "{\"planId\":\"" + planId + "\"}", admin), 200);

        // More children; a waiting journey is learned beside the first; new ones start active.
        String second = json(post("/api/v1/children", child("Kanha"), parent), 201).get("id").asString();
        json(post("/api/v1/enrollments/" + waiting + "/switch", "", parent), 200);
        assertThat(activeCount(childId)).isEqualTo(2);
        assertThat(json(post(enrollments, choose(curriculum()), parent), 201).get("status").asString()).isEqualTo("ACTIVE");
        json(post(enrollments, choose(curriculum()), parent), 201);

        // The grant ends: back on the free plan, and nothing is taken away - both children, every journey as it was.
        json(post("/api/v1/admin/families/" + familyId + "/subscription/end", "{\"reason\":\"Trial over\"}", admin), 200);
        JsonNode plan = json(get("/api/v1/family/plan", parent), 200);
        assertThat(plan.get("onDefaultPlan").asBoolean()).isTrue();
        assertThat(json(get("/api/v1/children", parent), 200).size()).isEqualTo(2);
        assertThat(activeCount(childId)).isEqualTo(4);
        assertThat(json(get("/api/v1/children/" + second, parent), 200).get("id").asString()).isEqualTo(second);
        // ...but adding goes by the free plan again.
        assertThat(json(post("/api/v1/children", child("Rith"), parent), 409).get("entitlement").asString()).isEqualTo("CHILDREN_MAX");
        assertThat(json(post(enrollments, choose(curriculum()), parent), 409).get("entitlement").asString())
                .isEqualTo("JOURNEYS_PER_CHILD_MAX");
    }

    // ---------------------------------------------------------------------------------------------------------------

    private record JsonNext(String itemId, String body) {
    }

    private JsonNext next(String enrollmentId, String parent) throws Exception {
        JsonNode next = json(get("/api/v1/enrollments/" + enrollmentId + "/next-activity", parent), 200);
        return new JsonNext(next.get("id").asString(), "{\"activityVersionId\":\"" + next.get("activityVersionId").asString()
                + "\",\"learningPathItemId\":\"" + next.get("id").asString() + "\"}");
    }

    /** A new child may be started on a path automatically (Battle 2B); these tests choose every path themselves. */
    private void clearAutomaticJourneys(String childId) {
        jdbc.update("update enrollments set status = 'WITHDRAWN' where child_id = ?::uuid", UUID.fromString(childId));
    }

    private int activeCount(String childId) {
        return jdbc.queryForObject("select count(*) from enrollments where child_id = ?::uuid and status = 'ACTIVE'",
                Integer.class, UUID.fromString(childId));
    }

    private String status(String enrollmentId) {
        return jdbc.queryForObject("select status from enrollments where id = ?::uuid", String.class, UUID.fromString(enrollmentId));
    }

    private static Integer limit(JsonNode plan, String key) {
        for (JsonNode entitlement : plan.get("entitlements")) {
            if (entitlement.get("key").asString().equals(key)) {
                return entitlement.get("limit").isNull() ? null : entitlement.get("limit").asInt();
            }
        }
        throw new AssertionError("no " + key + " in " + plan);
    }

    private static String choose(String curriculumId) {
        return "{\"curriculumId\":\"" + curriculumId + "\"}";
    }

    private static String child(String name) {
        return "{\"displayName\":\"" + name + "\",\"dateOfBirth\":\"" + LocalDate.now().minusYears(6) + "\"}";
    }

    /** A published platform curriculum of its own (one activity, one question). */
    private String curriculum() {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        UUID subjectId = subjectRepository.save(Subject.create("FREE_" + suffix, "Free " + suffix, null)).getId();
        UUID ageHubId = ageHubRepository.save(AgeHub.create("FREE_" + suffix, "Free " + suffix, 90, 95, 990)).getId();
        LearningObjective objective = learningObjectiveService.create(subjectId, "FREE_OBJ_" + suffix, "Objective " + suffix, null);
        Skill skill = skillService.createSkill(subjectId, "FREE_" + suffix, "Skill " + suffix, null);
        learningObjectiveSkillService.link(objective.getId(), skill.getId());
        Activity activity = activityService.createActivity(subjectId, null, "PRACTICE", "Free " + suffix);
        var activityVersion = activityService.createDraftVersion(activity.getId(), "Add", 1, 60);
        activityService.tagSkill(activityVersion.getId(), skill.getId());
        Question question = questionService.createQuestion(subjectId, QuestionType.NUMERIC);
        var draft = questionService.createDraftVersion(question.getId(), "1 + 1 = ?", "2", 1);
        questionService.tagSkill(draft.getId(), skill.getId());
        activityService.addItem(activityVersion.getId(), questionService.publish(draft.getId()).getId(), 0, java.math.BigDecimal.ONE);
        activityService.publish(activityVersion.getId());
        Curriculum curriculum = curriculumService.createCurriculum(subjectId, ageHubId, "Free " + suffix);
        var version = curriculumService.createDraftVersion(curriculum.getId());
        curriculumService.addObjective(version.getId(), objective.getId(), 0);
        curriculumService.publish(version.getId());
        return curriculum.getId().toString();
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
