package com.platform.curriculum;

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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
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
 * Owner requirement 2026-11-10 (V90): a curriculum's own age range - set and changed in the Studio, validated, and what
 * family discovery and eligibility use (ages, not school grades). Over real HTTP and PostgreSQL.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class CurriculumAgesIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Curriculum-Ages-Test-Pass-2026";

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
    void aCurriculumIsCreatedWithItsOwnAgesAndBadRangesAreRefused() throws Exception {
        String admin = token(PlatformRole.ADMIN);
        String subjectId = subjectRepository.save(Subject.create("AGES_" + suffix(), "Ages " + suffix(), null)).getId().toString();

        JsonNode created = json(post("/api/v1/studio/curricula", "{\"subjectId\":\"" + subjectId
                + "\",\"name\":\"Counting for 4 to 7\",\"minAge\":4,\"maxAge\":7}", admin), 201);
        assertThat(created.get("minAge").asInt()).isEqualTo(4);
        assertThat(created.get("maxAge").asInt()).isEqualTo(7);
        assertThat(created.get("ageHubId").isNull()).as("no age group needed").isTrue();
        assertThat(json(post("/api/v1/studio/curricula", "{\"subjectId\":\"" + subjectId
                + "\",\"name\":\"Reading 10+\",\"minAge\":10}", admin), 201).get("maxAge").isNull()).as("no upper age").isTrue();

        for (String bad : List.of("\"minAge\":8,\"maxAge\":5", "\"minAge\":19", "\"minAge\":-1", "\"maxAge\":7",
                "\"minAge\":3,\"maxAge\":30")) {
            assertThat(post("/api/v1/studio/curricula", "{\"subjectId\":\"" + subjectId + "\",\"name\":\"Bad\"," + bad + "}", admin)
                    .statusCode()).as(bad).isEqualTo(400);
        }
        // An older client's age group gives its ages.
        AgeHub hub = ageHubRepository.save(AgeHub.create("AGES_" + suffix(), "Ages group", 5, 8, 950));
        JsonNode fromHub = json(post("/api/v1/studio/curricula", "{\"subjectId\":\"" + subjectId + "\",\"name\":\"From a group\","
                + "\"ageHubId\":\"" + hub.getId() + "\"}", admin), 201);
        assertThat(fromHub.get("minAge").asInt()).isEqualTo(5);
        assertThat(fromHub.get("maxAge").asInt()).isEqualTo(8);
    }

    @Test
    void theAgesDecideWhatIsMadeForAChildAndChangingThemIsAuditedAndAdminOnlyForPlatformCurricula() throws Exception {
        String admin = token(PlatformRole.ADMIN);
        Made made = made(5, 7);
        try {
            agesDecide(admin, made);
        } finally {
            // Out of the family catalog again: a curriculum for real children's ages would change other tests' catalogs.
            jdbc.update("update curriculums set status = 'ARCHIVED' where id = ?::uuid", UUID.fromString(made.curriculumId()));
        }
    }

    private void agesDecide(String admin, Made made) throws Exception {
        String parent = registerParent("ages-parent-" + UUID.randomUUID() + "@example.com");
        String childId = json(post("/api/v1/children", "{\"displayName\":\"Dara\",\"dateOfBirth\":\"" + LocalDate.now().minusYears(6)
                + "\",\"familyChoosesSubjects\":true}", parent), 201).get("id").asString();

        JsonNode choice = choice(childId, parent, made.curriculumId());
        assertThat(choice.get("forChildAge").asBoolean()).isTrue();
        assertThat(choice.get("minAge").asInt()).isEqualTo(5);
        assertThat(choice.get("maxAge").asInt()).isEqualTo(7);

        String ages = "/api/v1/studio/curricula/" + made.curriculumId() + "/ages";
        assertThat(put(ages, "{\"minAge\":8,\"maxAge\":10}", token(PlatformRole.TEACHER)).statusCode()).isIn(403, 404);
        assertThat(put(ages, "{\"minAge\":8,\"maxAge\":3}", admin).statusCode()).isEqualTo(400);
        JsonNode changed = json(put(ages, "{\"minAge\":8,\"maxAge\":10}", admin), 200);
        assertThat(changed.get("minAge").asInt()).isEqualTo(8);
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action = 'CURRICULUM_AGES_CHANGED' and resource_id = ?::uuid",
                Integer.class, UUID.fromString(made.curriculumId()))).isEqualTo(1);
        assertThat(choice(childId, parent, made.curriculumId()).get("forChildAge").asBoolean()).as("now for older children").isFalse();

        // A child already on it keeps it; its plan says it's for other ages now.
        json(post("/api/v1/children/" + childId + "/enrollments", "{\"curriculumId\":\"" + made.curriculumId() + "\"}", parent), 201);
        JsonNode fit = json(get("/api/v1/children/" + childId + "/enrollments", parent), 200).get(0).get("ageFit");
        assertThat(fit.get("forChildAge").asBoolean()).isFalse();
        assertThat(fit.get("minAge").asInt()).isEqualTo(8);
    }

    // ---------------------------------------------------------------------------------------------------------------

    private record Made(String subjectId, String curriculumId) {
    }

    private JsonNode choice(String childId, String parent, String curriculumId) throws Exception {
        for (JsonNode c : json(get("/api/v1/children/" + childId + "/curricula", parent), 200)) {
            if (c.get("id").asString().equals(curriculumId)) {
                return c;
            }
        }
        throw new AssertionError("not offered: " + curriculumId);
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /** A published platform curriculum for the ages given (one activity, one question). */
    private Made made(int minAge, Integer maxAge) {
        String suffix = suffix();
        UUID subjectId = subjectRepository.save(Subject.create("AGES_" + suffix, "Ages " + suffix, null)).getId();
        LearningObjective objective = learningObjectiveService.create(subjectId, "AGES_OBJ_" + suffix, "Objective " + suffix, null);
        Skill skill = skillService.createSkill(subjectId, "AGES_" + suffix, "Skill " + suffix, null);
        learningObjectiveSkillService.link(objective.getId(), skill.getId());
        Activity activity = activityService.createActivity(subjectId, null, "PRACTICE", "Ages " + suffix);
        var activityVersion = activityService.createDraftVersion(activity.getId(), "Add", 1, 60);
        activityService.tagSkill(activityVersion.getId(), skill.getId());
        Question question = questionService.createQuestion(subjectId, QuestionType.NUMERIC);
        var draft = questionService.createDraftVersion(question.getId(), "1 + 1 = ?", "2", 1);
        questionService.tagSkill(draft.getId(), skill.getId());
        activityService.addItem(activityVersion.getId(), questionService.publish(draft.getId()).getId(), 0, java.math.BigDecimal.ONE);
        activityService.publish(activityVersion.getId());
        Curriculum curriculum = curriculumService.createCurriculum(subjectId, null, "Ages " + suffix, minAge, maxAge, null);
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
