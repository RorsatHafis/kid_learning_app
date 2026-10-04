package com.platform.school;

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
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A class's reads must not issue more SQL as the class grows (no query per child): the staff
 * overview, the class's learning and its trend are measured with two children and again with six.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Tag("integration")
class SchoolReadScalingIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "School-Scaling-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private SchoolService schoolService;
    @Autowired private SchoolStaffService schoolStaffService;
    @Autowired private SchoolStaffRepository schoolStaffRepository;
    @Autowired private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void classReadsDoNotGrowWithTheClass() throws Exception {
        UUID principalId = account(PlatformRole.PRINCIPAL);
        UUID teacherId = account(PlatformRole.TEACHER);
        String teacher = token(teacherId);
        School school = schoolService.createSchool("Scaling School", principalId);
        SchoolClass schoolClass = schoolService.createClass(school.getId(), "Grade 1");
        SchoolStaff membership = SchoolStaff.invite(school.getId(), teacherId, principalId, Instant.now());
        membership.accept(Instant.now());
        schoolStaffRepository.save(membership);
        schoolStaffService.assign(schoolClass.getId(), teacherId, principalId);
        String classPath = "/api/v1/schools/classes/" + schoolClass.getId();
        UUID maths = jdbc.queryForObject("""
                select c.id from curriculums c join subjects s on s.id = c.subject_id
                where s.code = 'MATHS' and c.owner_account_id is null and c.status = 'ACTIVE'
                  and exists (select 1 from curriculum_versions v where v.curriculum_id = c.id and v.status = 'PUBLISHED')
                order by c.created_at limit 1
                """, UUID.class);
        json(post(classPath + "/curricula/" + maths, "", teacher), 200);
        String code = json(post(classPath + "/join-code", "", teacher), 201).get("code").asString();

        join(code, 2);
        Map<String, Long> small = measure(teacher, classPath);
        join(code, 4);
        Map<String, Long> large = measure(teacher, classPath);
        System.out.println("SQL statements per request with 2 and 6 children: " + small + " -> " + large);

        large.forEach((endpoint, statements) -> assertThat(statements)
                .as("%s: %s with 2 children, %s with 6", endpoint, small.get(endpoint), statements)
                .isLessThanOrEqualTo(small.get(endpoint) + 2L));
    }

    private Map<String, Long> measure(String token, String classPath) throws Exception {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String path : List.of("/api/v1/schools/overview", classPath + "/learning", classPath + "/trend")) {
            Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
            stats.clear();
            json(get(path, token), 200);
            counts.put(path.replaceAll("[0-9a-f-]{36}", "{id}"), stats.getPrepareStatementCount());
        }
        return counts;
    }

    /** {@code n} families each join the class with one child (a confirmed email, as PD-22 requires). */
    private void join(String code, int n) throws Exception {
        for (int i = 0; i < n; i++) {
            String email = "scaling-parent-" + UUID.randomUUID() + "@example.com";
            String parent = json(post("/api/v1/auth/register", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                    + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null), 201).get("accessToken").asString();
            jdbc.update("update accounts set email_verified_at = now() where normalized_email = ?", email);
            String childId = json(post("/api/v1/children", "{\"displayName\":\"Child " + i + "\",\"dateOfBirth\":\""
                    + LocalDate.now().minusYears(6) + "\"}", parent), 201).get("id").asString();
            json(post("/api/v1/children/" + childId + "/schools/join", "{\"code\":\"" + code + "\"}", parent), 200);
        }
    }

    private UUID account(PlatformRole role) {
        String email = "scaling-" + role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        Account account = accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), role));
        return account.getId();
    }

    private String token(UUID accountId) throws Exception {
        String email = accountRepository.findById(accountId).orElseThrow().getEmail();
        return json(post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", null), 200)
                .get("accessToken").asString();
    }

    private static JsonNode json(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body, String token) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

}
