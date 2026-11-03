package com.platform.security;

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
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every endpoint, read from the API's own description (springdoc), refuses an account with no relationship to the
 * resources named in its address: a real family's child (joined to a paid school's class, an activity played), the
 * class, the school, the curriculum, and a teacher's own Studio content - asked for by a brand-new parent. Reads must
 * answer 401/403/404; writes (given a body shaped by the endpoint's schema, so they reach the permission check rather
 * than stop at validation) must not succeed. And the endpoints without an id that a parent may open are exactly the
 * family's own and the public ones listed here - so an endpoint added later without its guard fails this test.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class AuthorizationSweepIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Authorization-Sweep-Pass-2026";
    private static final Pattern PARAM = Pattern.compile("\\{(\\w+)}");

    /** What any signed-in parent may open without naming a resource: their own account and family, or public data. */
    private static final Set<String> A_PARENTS_OWN_OR_PUBLIC = Set.of(
            "/api/v1/account", "/api/v1/account/second-factor", "/api/v1/account/phone", "/api/v1/account/devices",
            "/api/v1/account/data-export", "/api/v1/children", "/api/v1/caregiving", "/api/v1/family/plan",
            "/api/v1/family/payments", "/api/v1/family/learning-day", "/api/v1/payments/methods", "/api/v1/curricula",
            "/api/v1/site/statements", "/api/v1/site/copy", "/api/v1/demo/activities", "/api/v1/auth/legal");

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

    @Test
    void noEndpointServesAnotherAccountsResources() throws Exception {
        Map<String, String> ids = othersResources();
        String stranger = parentToken();
        JsonNode docs = json(get("/v3/api-docs", null), 200);

        List<String> readable = new ArrayList<>();
        List<String> writable = new ArrayList<>();
        List<String> untested = new ArrayList<>();
        int reads = 0;
        int writes = 0;
        for (Iterator<Map.Entry<String, JsonNode>> paths = docs.get("paths").properties().iterator(); paths.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = paths.next();
            String path = entry.getKey();
            List<String> names = new ArrayList<>();
            Matcher m = PARAM.matcher(path);
            while (m.find()) {
                names.add(m.group(1));
            }
            if (names.isEmpty()) {
                continue;
            }
            if (names.stream().anyMatch(n -> !ids.containsKey(n))) {
                untested.add(path);
                continue;
            }
            String url = path;
            for (String n : names) {
                url = url.replace("{" + n + "}", ids.get(n));
            }
            JsonNode ops = entry.getValue();
            if (ops.has("get")) {
                String query = requiredQuery(ops.get("get"), ids);
                HttpResponse<String> r = get(url + query, stranger);
                reads++;
                if (r.statusCode() != 401 && r.statusCode() != 403 && r.statusCode() != 404) {
                    readable.add(r.statusCode() + " GET " + url + query);
                }
            }
            for (String method : List.of("post", "put", "patch")) {
                if (!ops.has(method)) {
                    continue;
                }
                HttpResponse<String> r = send(method.toUpperCase(), url, bodyFor(docs, ops.get(method), ids), stranger);
                writes++;
                if (r.statusCode() >= 200 && r.statusCode() < 300) {
                    writable.add(r.statusCode() + " " + method.toUpperCase() + " " + url);
                }
            }
        }

        assertThat(readable).as("reads another account's resources answered").isEmpty();
        assertThat(writable).as("writes to another account's resources succeeded").isEmpty();
        // The sweep has to reach the endpoints that matter - a renamed parameter must not quietly skip them.
        assertThat(reads).as("reads swept").isGreaterThanOrEqualTo(50);
        assertThat(writes).as("writes swept").isGreaterThanOrEqualTo(90);
        // What is left needs objects this fixture doesn't make (payments, caregivers, device codes, reviews, media,
        // organizations, plan requests): their guards are covered by their own suites.
        assertThat(untested).as("endpoints whose ids the sweep can't fill").hasSizeLessThan(30);
    }

    @Test
    void whatAParentMayOpenWithoutAnIdIsTheirOwnOrPublic() throws Exception {
        String parent = parentToken();
        JsonNode docs = json(get("/v3/api-docs", null), 200);
        Set<String> opened = new TreeSet<>();
        for (Iterator<Map.Entry<String, JsonNode>> paths = docs.get("paths").properties().iterator(); paths.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = paths.next();
            String path = entry.getKey();
            if (path.contains("{") || !entry.getValue().has("get") || !path.startsWith("/api/")) {
                continue;
            }
            String query = requiredQuery(entry.getValue().get("get"), Map.of());
            HttpResponse<String> r = get(path + query, parent);
            if (r.statusCode() >= 200 && r.statusCode() < 300) {
                opened.add(path);
            }
        }
        assertThat(opened).as("a parent opens only their own or public data").isSubsetOf(A_PARENTS_OWN_OR_PUBLIC);
        assertThat(opened).as("and the family's own pages do open").contains("/api/v1/account", "/api/v1/children");
    }

    // ------------------------------------------------------------------ the resources of others

    /** A real family, school, class, curriculum and teacher's content; their ids by the names the API uses. */
    private Map<String, String> othersResources() throws Exception {
        Map<String, String> ids = new HashMap<>();
        Staff principal = staff(PlatformRole.PRINCIPAL);
        Staff teacher = staff(PlatformRole.TEACHER);
        School school = schoolService.createSchool("Sweep School", principal.id());
        PaidSchools.pay(context, school.getId());
        SchoolClass schoolClass = schoolService.createClass(school.getId(), "Grade 2");
        SchoolStaff membership = SchoolStaff.invite(school.getId(), teacher.id(), principal.id(), Instant.now());
        membership.accept(Instant.now());
        schoolStaffRepository.save(membership);
        schoolStaffService.assign(schoolClass.getId(), teacher.id(), principal.id());
        Offered offered = publishedCurriculum();
        String classPath = "/api/v1/schools/classes/" + schoolClass.getId();
        json(post(classPath + "/curricula/" + offered.curriculum().getId(), "", teacher.token()), 200);
        String code = json(post(classPath + "/join-code", "", teacher.token()), 201).get("code").asString();

        String family = parentToken();
        UUID familyId = jdbc.queryForObject("select m.family_id from memberships m join accounts a on a.id = m.account_id"
                + " order by a.created_at desc limit 1", UUID.class);
        jdbc.update("update accounts set email_verified_at = now() where id in (select account_id from memberships where family_id = ?)",
                familyId);
        String childId = json(post("/api/v1/children", "{\"displayName\":\"Sweep\",\"dateOfBirth\":\"" + LocalDate.now().minusYears(7)
                + "\"}", family), 201).get("id").asString();
        json(post("/api/v1/children/" + childId + "/schools/join", "{\"code\":\"" + code + "\"}", family), 200);
        JsonNode journey = null;
        for (JsonNode overview : json(get("/api/v1/children/" + childId + "/enrollments", family), 200)) {
            journey = overview;
        }
        JsonNode next = journey.get("nextStep");
        String attemptId = json(post("/api/v1/children/" + childId + "/activity-attempts", "{\"activityVersionId\":\""
                + next.get("activityVersionId").asString() + "\",\"learningPathItemId\":\"" + next.get("learningPathItemId").asString()
                + "\"}", family), 201).get("id").asString();

        // The teacher's own Studio content.
        String lessonId = json(post("/api/v1/studio/lessons", "{\"subjectId\":\"" + offered.subjectId() + "\",\"title\":\"Mine\"}",
                teacher.token()), 201).get("id").asString();
        JsonNode activity = json(post("/api/v1/studio/lessons/" + lessonId + "/activities", "{\"title\":\"Mine\",\"skillIds\":[\""
                + offered.skillId() + "\"]}", teacher.token()), 201);
        JsonNode question = json(post("/api/v1/studio/activity-versions/" + activity.get("versions").get(0).get("id").asString()
                + "/questions", "{\"subjectId\":\"" + offered.subjectId() + "\",\"questionType\":\"NUMERIC\",\"prompt\":\"2 + 2 = ?\","
                + "\"correctAnswer\":\"4\",\"skillIds\":[]}", teacher.token()), 201);
        String questionVersion = question.get("versions").get(0).get("items").get(0).get("questionVersionId").asString();
        String questionId = jdbc.queryForObject("select question_id from question_versions where id = ?::uuid", String.class, questionVersion);

        // A school year and term, the staff membership, the objective, and what admin pages name.
        String yearId = json(post("/api/v1/schools/" + school.getId() + "/academic-years", "{\"name\":\"2026-27\",\"startsOn\":\""
                + LocalDate.now().minusMonths(1) + "\",\"endsOn\":\"" + LocalDate.now().plusMonths(9) + "\"}", principal.token()), 201)
                .get("id").asString();
        String termId = json(post("/api/v1/schools/" + school.getId() + "/academic-years/" + yearId + "/terms", "{\"name\":\"Term 1\","
                + "\"startsOn\":\"" + LocalDate.now().minusMonths(1) + "\",\"endsOn\":\"" + LocalDate.now().plusMonths(2) + "\"}",
                principal.token()), 201).at("/terms/0/id").asString();
        ids.put("yearId", yearId);
        ids.put("termId", termId);
        ids.put("membershipId", membership.getId().toString());
        ids.put("teacherAccountId", teacher.id().toString());
        ids.put("principalId", principal.id().toString());
        ids.put("sourceSchoolId", school.getId().toString());
        ids.put("objectiveId", jdbc.queryForObject("select learning_objective_id::text from learning_objective_skills where skill_id = ?",
                String.class, offered.skillId()));
        ids.put("prerequisiteSkillId", offered.skillId().toString());
        ids.put("familyId", familyId.toString());
        ids.put("action", "archive");
        jdbc.query("select id::text from subscription_plans limit 1", rs -> {
            ids.put("planId", rs.getString(1));
        });
        jdbc.query("select id::text from site_statements limit 1", rs -> {
            ids.put("id", rs.getString(1));
        });
        ids.put("childId", childId);
        ids.put("classId", schoolClass.getId().toString());
        ids.put("schoolId", school.getId().toString());
        ids.put("curriculumId", offered.curriculum().getId().toString());
        ids.put("subjectId", offered.subjectId().toString());
        ids.put("skillId", offered.skillId().toString());
        ids.put("enrollmentId", journey.get("enrollmentId").asString());
        ids.put("attemptId", attemptId);
        ids.put("activityAttemptId", attemptId);
        ids.put("activityId", activity.get("id").asString());
        ids.put("activityVersionId", activity.get("versions").get(0).get("id").asString());
        ids.put("versionId", questionVersion);
        ids.put("questionVersionId", questionVersion);
        ids.put("questionId", questionId);
        ids.put("lessonId", lessonId);
        ids.put("accountId", teacher.id().toString());
        ids.put("kind", "QUESTION");
        return ids;
    }

    private record Staff(UUID id, String token) {
    }

    private record Offered(Curriculum curriculum, UUID subjectId, UUID skillId) {
    }

    private Staff staff(PlatformRole role) throws Exception {
        String email = "sweep-" + role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        Account account = accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), role));
        return new Staff(account.getId(), json(post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                + "\"}", null), 200).get("accessToken").asString());
    }

    private String parentToken() throws Exception {
        String email = "sweep-parent-" + UUID.randomUUID() + "@example.com";
        return json(post("/api/v1/auth/register", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null), 201).get("accessToken").asString();
    }

    private Offered publishedCurriculum() {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        UUID subjectId = subjectRepository.save(Subject.create("SWP_" + suffix, "Sweep " + suffix, null)).getId();
        UUID ageHubId = ageHubRepository.save(AgeHub.create("SWP_" + suffix, "Sweep " + suffix, 90, 99, 99)).getId();
        LearningObjective objective = learningObjectiveService.create(subjectId, "SWP_OBJ_" + suffix, "Objective " + suffix, null);
        Skill skill = skillService.createSkill(subjectId, "SWP_" + suffix, "Skill " + suffix, null);
        learningObjectiveSkillService.link(objective.getId(), skill.getId());
        Question question = questionService.createQuestion(subjectId, QuestionType.NUMERIC);
        var questionDraft = questionService.createDraftVersion(question.getId(), "1 + 1 = ?", "2", 1);
        questionService.tagSkill(questionDraft.getId(), skill.getId());
        var questionVersion = questionService.publish(questionDraft.getId());
        Activity activity = activityService.createActivity(subjectId, null, "PRACTICE", "Sweep " + suffix);
        var activityVersion = activityService.createDraftVersion(activity.getId(), "Add", 1, 60);
        activityService.tagSkill(activityVersion.getId(), skill.getId());
        activityService.addItem(activityVersion.getId(), questionVersion.getId(), 0, java.math.BigDecimal.ONE);
        activityService.publish(activityVersion.getId());
        Curriculum curriculum = curriculumService.createCurriculum(subjectId, ageHubId, "Sweep " + suffix);
        var version = curriculumService.createDraftVersion(curriculum.getId());
        curriculumService.addObjective(version.getId(), objective.getId(), 0);
        curriculumService.publish(version.getId());
        return new Offered(curriculum, subjectId, skill.getId());
    }

    // ------------------------------------------------------------------ requests shaped by the API's description

    private static String requiredQuery(JsonNode op, Map<String, String> ids) {
        List<String> parts = new ArrayList<>();
        for (JsonNode p : op.path("parameters")) {
            if ("query".equals(p.path("in").asString()) && p.path("required").asBoolean()) {
                String name = p.get("name").asString();
                parts.add(name + "=" + ids.getOrDefault(name, "00000000-0000-0000-0000-000000000000"));
            }
        }
        return parts.isEmpty() ? "" : "?" + String.join("&", parts);
    }

    private static String bodyFor(JsonNode docs, JsonNode op, Map<String, String> ids) {
        JsonNode schema = op.path("requestBody").path("content").path("application/json").path("schema");
        return schema.isMissingNode() ? "{}" : String.valueOf(example(docs, schema, "", ids, 0));
    }

    /** A value of the schema's shape: every property filled, enums by their first value, ids from the sweep's own. */
    private static Object example(JsonNode docs, JsonNode schema, String name, Map<String, String> ids, int depth) {
        while (schema.has("$ref")) {
            String ref = schema.get("$ref").asString();
            schema = docs.get("components").get("schemas").get(ref.substring(ref.lastIndexOf('/') + 1));
        }
        if (depth > 4) {
            return null;
        }
        if (schema.has("enum")) {
            return JSON.writeValueAsString(schema.get("enum").get(0).asString());
        }
        String type = schema.path("type").asString("");
        switch (type) {
            case "object" -> {
                ObjectNode o = JSON.createObjectNode();
                for (Iterator<Map.Entry<String, JsonNode>> props = schema.path("properties").properties().iterator(); props.hasNext(); ) {
                    Map.Entry<String, JsonNode> p = props.next();
                    Object v = example(docs, p.getValue(), p.getKey(), ids, depth + 1);
                    o.set(p.getKey(), v == null ? JSON.nullNode() : JSON.readTree(String.valueOf(v)));
                }
                return o;
            }
            case "array" -> {
                return schema.path("minItems").asInt(0) > 0 ? "[" + example(docs, schema.get("items"), name, ids, depth + 1) + "]" : "[]";
            }
            case "integer", "number" -> {
                return Math.max(schema.path("minimum").asInt(1), 1);
            }
            case "boolean" -> {
                return false;
            }
            case "string" -> {
                String format = schema.path("format").asString("");
                String value = switch (format) {
                    case "uuid" -> ids.getOrDefault(name, ids.getOrDefault(name.replaceAll("s$", ""), ids.get("childId")));
                    case "date" -> "2019-01-01";
                    case "date-time" -> "2026-12-01T00:00:00Z";
                    default -> name.toLowerCase().contains("email") ? "sweep-target@example.com" : "Sweep " + name;
                };
                return JSON.writeValueAsString(value);
            }
            default -> {
                return null;
            }
        }
    }

    // ------------------------------------------------------------------ HTTP

    private static JsonNode json(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> get(String path, String bearer) throws Exception {
        return send("GET", path, null, bearer);
    }

    private HttpResponse<String> post(String path, String body, String bearer) throws Exception {
        return send("POST", path, body, bearer);
    }

    private HttpResponse<String> send(String method, String path, String body, String bearer) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
