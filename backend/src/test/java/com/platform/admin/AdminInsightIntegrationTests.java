package com.platform.admin;

import com.platform.TestcontainersConfiguration;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.repository.AccountRepository;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * X15 over real HTTP against PostgreSQL: the administrator's schools directory, learning operations,
 * weekly reports and system status - administrators only, aggregates and operational facts only.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class AdminInsightIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Insight-Test-Pass-2026";
    private static final List<String> READS = List.of("/api/v1/admin/schools", "/api/v1/admin/learning-operations",
            "/api/v1/admin/reports/weekly", "/api/v1/admin/system");

    @Value("${local.server.port}") private int port;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private SchoolService schoolService;
    @Autowired private SchoolStaffService schoolStaffService;
    @Autowired private SchoolStaffRepository schoolStaffRepository;
    @Autowired private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void onlyAnAdministratorReadsOperations() throws Exception {
        for (PlatformRole role : List.of(PlatformRole.TEACHER, PlatformRole.PRINCIPAL)) {
            String token = staff(role).token();
            for (String path : READS) {
                assertThat(get(path, token).statusCode()).as(role + " " + path).isEqualTo(403);
            }
        }
        String parent = registerParent();
        for (String path : READS) {
            assertThat(get(path, parent).statusCode()).as(path).isEqualTo(403);
        }
        assertThat(get("/api/v1/admin/system", null).statusCode()).isEqualTo(401);
    }

    @Test
    void theSchoolsDirectoryShowsWhoRunsEachSchoolAndWhatIsStillToSetUp() throws Exception {
        String admin = staff(PlatformRole.ADMIN).token();
        Staff principal = staff(PlatformRole.PRINCIPAL);
        Staff teacher = staff(PlatformRole.TEACHER);
        String tag = UUID.randomUUID().toString().substring(0, 8);
        School school = schoolService.createSchool("Insight School " + tag, principal.id());
        SchoolClass taught = schoolService.createClass(school.getId(), "Grade 1 " + tag);
        schoolService.createClass(school.getId(), "Grade 2 " + tag);
        SchoolStaff membership = SchoolStaff.invite(school.getId(), teacher.id(), principal.id(), Instant.now());
        membership.accept(Instant.now());
        schoolStaffRepository.save(membership);
        schoolStaffService.assign(taught.getId(), teacher.id(), principal.id());

        JsonNode page = json(get("/api/v1/admin/schools?q=" + tag, admin), 200);
        assertThat(page.get("total").asInt()).isEqualTo(1);
        JsonNode row = page.get("items").get(0);
        assertThat(row.get("name").asString()).isEqualTo("Insight School " + tag);
        assertThat(row.get("principalEmail").asString()).isEqualTo(principal.email());
        assertThat(row.get("classes").asInt()).isEqualTo(2);
        assertThat(row.get("teachers").asInt()).isEqualTo(1);
        assertThat(row.get("classesWithoutTeacher").asInt()).isEqualTo(1);
        assertThat(row.get("classesWithoutCurriculum").asInt()).isEqualTo(2);
        // Found by the principal's address too.
        assertThat(json(get("/api/v1/admin/schools?q=" + principal.email(), admin), 200).get("total").asInt()).isEqualTo(1);

        JsonNode detail = json(get("/api/v1/admin/schools/" + school.getId(), admin), 200);
        assertThat(detail.get("classes")).hasSize(2);
        assertThat(detail.get("classes").get(0).get("teachers").get(0).asString()).isEqualTo(teacher.email());
        assertThat(detail.get("staff").get(0).asString()).isEqualTo(teacher.email());
        assertThat(get("/api/v1/admin/schools/" + UUID.randomUUID(), admin).statusCode()).isEqualTo(404);
        // Operational facts, never children's names or learning.
        assertThat(detail.toString()).doesNotContain("displayName").doesNotContain("mastery");
    }

    @Test
    void learningOperationsReportsTheLoopAndWhereContentLeavesChildrenWithNothingToPractise() throws Exception {
        String admin = staff(PlatformRole.ADMIN).token();
        // A teacher's curriculum (so no family's new child is enrolled in it) relying on a skill nothing in use practises.
        Staff author = staff(PlatformRole.TEACHER);
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String subjectId = json(post("/api/v1/studio/subjects", "{\"code\":\"OPS_" + suffix + "\",\"name\":\"Ops " + suffix + "\"}", admin), 201)
                .get("id").asString();
        String skillId = json(post("/api/v1/studio/subjects/" + subjectId + "/skills", "{\"code\":\"GAP_" + suffix
                + "\",\"name\":\"Gap skill " + suffix + "\"}", admin), 201).get("id").asString();
        jdbc.update("""
                insert into learning_objectives (id, subject_id, code, title, status) values (gen_random_uuid(), ?::uuid, ?, 'Gap', 'ACTIVE')
                """, subjectId, "OBJ_" + suffix);
        String objectiveId = jdbc.queryForObject("select id from learning_objectives where code = ?", String.class, "OBJ_" + suffix);
        jdbc.update("insert into learning_objective_skills (id, learning_objective_id, skill_id) values (gen_random_uuid(), ?::uuid, ?::uuid)",
                objectiveId, skillId);
        String ageHubId = jdbc.queryForObject("select id from age_hubs order by display_order limit 1", String.class);
        String curriculumId = UUID.randomUUID().toString();
        jdbc.update("insert into curriculums (id, subject_id, age_hub_id, name, status, owner_account_id) values "
                + "(?::uuid, ?::uuid, ?::uuid, ?, 'ACTIVE', ?)", curriculumId, subjectId, ageHubId, "Ops curriculum " + suffix, author.id());
        String versionId = UUID.randomUUID().toString();
        jdbc.update("insert into curriculum_versions (id, curriculum_id, version_number, status, published_at) values (?::uuid, ?::uuid, 1, 'PUBLISHED', now())",
                versionId, curriculumId);
        jdbc.update("insert into curriculum_objectives (id, curriculum_version_id, learning_objective_id, display_order) values "
                + "(gen_random_uuid(), ?::uuid, ?::uuid, 0)", versionId, objectiveId);

        JsonNode ops = json(get("/api/v1/admin/learning-operations", admin), 200);
        JsonNode week = ops.get("lastSevenDays");
        assertThat(week.get("placementsBySource").has("ADAPTIVE")).isTrue();
        assertThat(week.get("attemptsCompleted").asLong()).isGreaterThanOrEqualTo(0);
        assertThat(ops.get("coverageGaps").valueStream().anyMatch(g -> g.get("skill").asString().equals("Gap skill " + suffix)
                && g.get("curriculum").asString().equals("Ops curriculum " + suffix))).isTrue();
    }

    @Test
    void weeklyReportsAndSystemStatusAreServedWithoutSecrets() throws Exception {
        String admin = staff(PlatformRole.ADMIN).token();
        String parent = registerParent();
        json(post("/api/v1/children", "{\"displayName\":\"Weekly\",\"dateOfBirth\":\"" + LocalDate.now().minusYears(6) + "\"}", parent), 201);

        JsonNode weeks = json(get("/api/v1/admin/reports/weekly", admin), 200);
        assertThat(weeks).hasSize(12);
        assertThat(weeks.get(11).get("newFamilies").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(LocalDate.parse(weeks.get(11).get("weekOf").asString()).getDayOfWeek()).isEqualTo(java.time.DayOfWeek.MONDAY);

        JsonNode system = json(get("/api/v1/admin/system", admin), 200);
        assertThat(system.get("schemaVersion").asString()).isNotBlank();
        assertThat(system.get("databaseBytes").asLong()).isPositive();
        assertThat(system.get("mediaFreeBytes").asLong()).isPositive();
        assertThat(system.toString()).doesNotContain("test-secret-key").doesNotContain("password");
    }

    // ------------------------------------------------------------------ accounts and HTTP

    private record Staff(UUID id, String email, String token) {
    }

    private Staff staff(PlatformRole role) throws Exception {
        String email = "insight-" + role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        Account account = accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), role));
        return new Staff(account.getId(), email, json(post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                + "\"}", null), 200).get("accessToken").asString());
    }

    private String registerParent() throws Exception {
        return json(post("/api/v1/auth/register", "{\"email\":\"insight-parent-" + UUID.randomUUID() + "@example.com\",\"password\":\""
                + PASSWORD + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null), 201).get("accessToken").asString();
    }

    private static JsonNode json(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> get(String path, String bearer) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body, String bearer) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

}
