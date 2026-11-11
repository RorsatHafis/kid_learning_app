package com.platform.school;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.PaidSchools;
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
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * M12 (owner decision 2026-11-11) over real HTTP and PostgreSQL: a school's student record is connected to a family's
 * child only with the parent's explicit consent. The school can ask only about a child in the class whose family
 * shares them with it; the parent connects or declines; either side disconnects; withdrawing the school's access ends
 * the link; nothing of the family's is copied into the school's record.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class StudentLinkIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Student-Link-Test-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SchoolService schoolService;
    @Autowired private SchoolStaffService schoolStaffService;
    @Autowired private SchoolStaffRepository schoolStaffRepository;
    @Autowired private ApplicationContext context;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void aRecordIsConnectedToAChildOnlyWithTheParentsConsentAndEndsWithTheSchoolsAccess() throws Exception {
        String principal = staff(PlatformRole.PRINCIPAL);
        UUID principalId = lastId;
        String teacher = staff(PlatformRole.TEACHER);
        UUID teacherId = lastId;
        School school = schoolService.createSchool("Link School", principalId);
        PaidSchools.pay(context, school.getId());
        SchoolClass schoolClass = schoolService.createClass(school.getId(), "Grade 1");
        SchoolStaff membership = SchoolStaff.invite(school.getId(), teacherId, principalId, Instant.now());
        membership.accept(Instant.now());
        schoolStaffRepository.save(membership);
        schoolStaffService.assign(schoolClass.getId(), teacherId, principalId);
        String classPath = "/api/v1/schools/classes/" + schoolClass.getId();

        String student = json(post(classPath + "/students", "{\"students\":[{\"name\":\"Dara Kim\"}]}", teacher), 201)
                .get("added").get(0).get("id").asString();
        // Nobody to ask about yet: no child of a sharing family is in the class.
        assertThat(json(get(classPath + "/student-links", teacher), 200).get("candidates")).isEmpty();

        // A family shares its child with the school by joining the class.
        String code = json(post(classPath + "/join-code", "", teacher), 201).get("code").asString();
        String parent = registerParent();
        String childId = json(post("/api/v1/children", "{\"displayName\":\"Dara\",\"dateOfBirth\":\"" + LocalDate.now().minusYears(7)
                + "\",\"familyChoosesSubjects\":true}", parent), 201).get("id").asString();
        json(post("/api/v1/children/" + childId + "/schools/join", "{\"code\":\"" + code + "\"}", parent), 200);
        JsonNode links = json(get(classPath + "/student-links", teacher), 200);
        assertThat(links.get("candidates").get(0).get("childId").asString()).isEqualTo(childId);

        // A child who isn't in the class can't be named.
        String otherParent = registerParent();
        String otherChild = json(post("/api/v1/children", "{\"displayName\":\"Other\",\"dateOfBirth\":\"" + LocalDate.now().minusYears(7)
                + "\",\"familyChoosesSubjects\":true}", otherParent), 201).get("id").asString();
        assertThat(post(classPath + "/students/" + student + "/link", "{\"childId\":\"" + otherChild + "\"}", teacher).statusCode()).isEqualTo(400);

        // The school asks; nothing is connected until the parent says so.
        String linkPath = classPath + "/students/" + student + "/link";
        assertThat(json(post(linkPath, "{\"childId\":\"" + childId + "\"}", teacher), 200).get("links").get(0).get("status").asString())
                .isEqualTo("PENDING");
        assertThat(post(linkPath, "{\"childId\":\"" + childId + "\"}", teacher).statusCode()).as("one request at a time").isEqualTo(409);
        JsonNode asked = json(get("/api/v1/children/" + childId + "/school-links", parent), 200);
        assertThat(asked.get(0).get("schoolName").asString()).isEqualTo("Link School");
        assertThat(asked.get(0).get("studentName").asString()).isEqualTo("Dara Kim");
        String linkId = asked.get(0).get("id").asString();
        // Only the child's family sees or answers it.
        assertThat(get("/api/v1/children/" + childId + "/school-links", otherParent).statusCode()).isIn(403, 404);
        assertThat(post("/api/v1/children/" + childId + "/school-links/" + linkId + "/connect", "", otherParent).statusCode()).isIn(403, 404);

        // The parent's consent connects them; who and when is kept.
        assertThat(json(post("/api/v1/children/" + childId + "/school-links/" + linkId + "/connect", "", parent), 200).get(0).get("status").asString())
                .isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("select decided_by is not null and decided_at is not null from school_student_links where id = ?::uuid",
                Boolean.class, linkId)).isTrue();
        JsonNode connected = json(get(classPath + "/student-links", teacher), 200);
        assertThat(connected.get("links").get(0).get("childName").asString()).isEqualTo("Dara");
        // Nothing of the family's is copied into the school's record.
        assertThat(jdbc.queryForObject("select full_name from school_students where id = ?::uuid", String.class, student)).isEqualTo("Dara Kim");

        // The family disconnects; asked again, the family can decline.
        json(delete("/api/v1/children/" + childId + "/school-links/" + linkId, parent), 200);
        json(post(linkPath, "{\"childId\":\"" + childId + "\"}", teacher), 200);
        String second = json(get("/api/v1/children/" + childId + "/school-links", parent), 200).get(0).get("id").asString();
        assertThat(json(post("/api/v1/children/" + childId + "/school-links/" + second + "/decline", "", parent), 200)).isEmpty();

        // Connected again, then the family withdraws the school's access: the link ends with it.
        json(post(linkPath, "{\"childId\":\"" + childId + "\"}", teacher), 200);
        String third = json(get("/api/v1/children/" + childId + "/school-links", parent), 200).get(0).get("id").asString();
        json(post("/api/v1/children/" + childId + "/school-links/" + third + "/connect", "", parent), 200);
        assertThat(delete("/api/v1/children/" + childId + "/schools/" + school.getId(), parent).statusCode()).isBetween(200, 204);
        assertThat(jdbc.queryForObject("select status from school_student_links where id = ?::uuid", String.class, third)).isEqualTo("ENDED");
        assertThat(json(get(classPath + "/student-links", teacher), 200).get("links")).isEmpty();
    }

    // ---------------------------------------------------------------------------------------------------------------

    private UUID lastId;

    private String staff(PlatformRole role) throws Exception {
        String email = "link-" + role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        Account account = accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), role));
        lastId = account.getId();
        return json(post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", null), 200)
                .get("accessToken").asString();
    }

    private String registerParent() throws Exception {
        return com.platform.TestPlans.everythingIncluded(jdbc, json(post("/api/v1/auth/register", "{\"email\":\"link-parent-" + UUID.randomUUID()
                + "@example.com\",\"password\":\"" + PASSWORD + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null), 201))
                .get("accessToken").asString();
    }

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

    private HttpResponse<String> send(HttpRequest.Builder builder, String bearer) throws Exception {
        if (bearer != null) builder.header("Authorization", "Bearer " + bearer);
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
