package com.platform.school;

import com.platform.PaidSchools;
import com.platform.TestcontainersConfiguration;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.repository.AccountRepository;
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
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M12 B2 (docs/SCHOOL-MANAGEMENT.md §8.2): an organization's overview - each branch's size and payment state and
 * the totals, for its owner only, counts only.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class OrganizationOverviewIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Overview-Test-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private ApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void theOwnerSeesEachBranchesSizeAndPaymentAndTotalsThatCountEachChildAndTeacherOnce() throws Exception {
        User owner = user(PlatformRole.PRINCIPAL);
        String north = json(post("/api/v1/schools", "{\"name\":\"Overview North\"}", owner), 201).get("id").asString();
        PaidSchools.pay(context, UUID.fromString(north));
        String organizationId = json(get("/api/v1/organizations/mine", owner), 200).get(0).get("id").asString();
        String south = json(post("/api/v1/organizations/" + organizationId + "/branches", "{\"name\":\"Overview South\"}", owner), 201)
                .get("id").asString();
        String east = json(post("/api/v1/organizations/" + organizationId + "/branches", "{\"name\":\"Overview East\"}", owner), 201)
                .get("id").asString();

        // North: two classes, a current year, one teacher who also works in East; South: unpaid, one child shared with North.
        json(post("/api/v1/schools/" + north + "/classes", "{\"name\":\"Grade 1\"}", owner), 201);
        json(post("/api/v1/schools/" + north + "/classes", "{\"name\":\"Grade 2\"}", owner), 201);
        json(post("/api/v1/schools/" + north + "/academic-years", "{\"name\":\"2026-2027\",\"startsOn\":\"2026-11-02\","
                + "\"endsOn\":\"2027-08-27\"}", owner), 201);
        User teacher = user(PlatformRole.TEACHER);
        json(post("/api/v1/schools/" + north + "/staff", "{\"email\":\"" + teacher.email() + "\"}", owner), 201);
        PaidSchools.pay(context, UUID.fromString(east));
        json(post("/api/v1/schools/" + east + "/staff", "{\"email\":\"" + teacher.email() + "\"}", owner), 201);
        User parent = parent();
        String dara = child(parent, "Dara");
        String sokha = child(parent, "Sokha");
        // Arranged directly: how a child comes to a school (a family's class code) is covered by SchoolAccessIntegrationTests.
        grantAccess(north, dara, parent);
        grantAccess(north, sokha, parent);
        grantAccess(south, dara, parent);

        JsonNode overview = json(get("/api/v1/organizations/" + organizationId + "/overview", owner), 200);
        assertThat(overview.get("branchesInUse").asInt()).isEqualTo(3);
        assertThat(overview.get("branchesPaid").asInt()).isEqualTo(2);
        assertThat(overview.get("children").asLong()).as("Dara is in two branches, counted once").isEqualTo(2);
        assertThat(overview.get("teachers").asLong()).as("one teacher in two branches").isEqualTo(1);
        assertThat(overview.get("classes").asInt()).isEqualTo(2);
        JsonNode northRow = branch(overview, north);
        assertThat(northRow.get("classes").asInt()).isEqualTo(2);
        assertThat(northRow.get("teachers").asInt()).isEqualTo(1);
        assertThat(northRow.get("children").asInt()).isEqualTo(2);
        assertThat(northRow.get("paid").asBoolean()).isTrue();
        assertThat(northRow.get("paidUntil").isNull()).isFalse();
        assertThat(northRow.get("currentYear").asString()).isEqualTo("2026-2027");
        assertThat(northRow.get("runByOwner").asBoolean()).isTrue();
        JsonNode southRow = branch(overview, south);
        assertThat(southRow.get("paid").asBoolean()).isFalse();
        assertThat(southRow.get("children").asInt()).isEqualTo(1);
        assertThat(southRow.get("currentYear").isNull()).isTrue();
        // Counts only: no child, parent or teacher is named.
        assertThat(overview.toString()).doesNotContain("Dara").doesNotContain("Sokha").doesNotContain(teacher.email())
                .doesNotContain(parent.email()).doesNotContain(dara);

        // A branch's own principal, a teacher, a parent and another principal don't see the organization's overview.
        User branchPrincipal = user(PlatformRole.PRINCIPAL);
        json(put("/api/v1/organizations/" + organizationId + "/branches/" + south + "/principal",
                "{\"email\":\"" + branchPrincipal.email() + "\"}", owner), 200);
        assertThat(branch(json(get("/api/v1/organizations/" + organizationId + "/overview", owner), 200), south).get("runByOwner")
                .asBoolean()).isFalse();
        for (User other : new User[] {branchPrincipal, teacher, parent, user(PlatformRole.PRINCIPAL)}) {
            assertThat(get("/api/v1/organizations/" + organizationId + "/overview", other).statusCode()).as(other.role().name())
                    .isEqualTo(404);
        }
        assertThat(get("/api/v1/organizations/" + organizationId + "/overview", null).statusCode()).isEqualTo(401);
        // A platform administrator sees the same counts (oversight of a customer, still no child named).
        assertThat(json(get("/api/v1/organizations/" + organizationId + "/overview", user(PlatformRole.ADMIN)), 200)
                .get("children").asLong()).isEqualTo(2);

        // An archived branch is listed but no longer counted.
        jdbc.update("update schools set status = 'ARCHIVED' where id = ?::uuid", UUID.fromString(south));
        JsonNode afterArchive = json(get("/api/v1/organizations/" + organizationId + "/overview", owner), 200);
        assertThat(afterArchive.get("branchesInUse").asInt()).isEqualTo(2);
        assertThat(branch(afterArchive, south).get("archived").asBoolean()).isTrue();
        assertThat(afterArchive.get("children").asLong()).isEqualTo(2);
    }

    /** An administrator reads one school's invoices and payments only (the school's admin page lists them). */
    @Test
    void anAdministratorListsOneSchoolsPaymentsOnly() throws Exception {
        User principal = user(PlatformRole.PRINCIPAL);
        String first = json(post("/api/v1/schools", "{\"name\":\"Payments One\"}", principal), 201).get("id").asString();
        String second = json(post("/api/v1/schools", "{\"name\":\"Payments Two\"}", principal), 201).get("id").asString();
        PaidSchools.pay(context, UUID.fromString(first));
        PaidSchools.pay(context, UUID.fromString(first));
        PaidSchools.pay(context, UUID.fromString(second));
        String admin = com.platform.TestAdmin.stepUp(port, user(PlatformRole.ADMIN).token(), PASSWORD);
        User adminUser = new User(null, null, PlatformRole.ADMIN, admin);

        JsonNode firstOnly = json(get("/api/v1/admin/payments?schoolId=" + first, adminUser), 200);
        assertThat(firstOnly.get("total").asLong()).isEqualTo(2);
        firstOnly.get("items").forEach(order -> assertThat(order.get("schoolId").asString()).isEqualTo(first));
        assertThat(json(get("/api/v1/admin/payments?schoolId=" + second + "&status=SUCCEEDED", adminUser), 200).get("total").asLong())
                .isEqualTo(1);
        assertThat(json(get("/api/v1/admin/payments?schoolId=" + second + "&status=PENDING", adminUser), 200).get("total").asLong())
                .isZero();
        assertThat(json(get("/api/v1/admin/payments?schoolId=" + UUID.randomUUID(), adminUser), 200).get("total").asLong()).isZero();
        // Only an administrator lists payments.
        assertThat(get("/api/v1/admin/payments?schoolId=" + first, principal).statusCode()).isEqualTo(403);
    }

    // ------------------------------------------------------------------ helpers

    private record User(String id, String email, PlatformRole role, String token) {
    }

    private static JsonNode branch(JsonNode overview, String schoolId) {
        for (JsonNode b : overview.get("branches")) {
            if (b.get("schoolId").asString().equals(schoolId)) {
                return b;
            }
        }
        throw new AssertionError("branch " + schoolId + " not in " + overview);
    }

    private void grantAccess(String schoolId, String childId, User grantedBy) {
        jdbc.update("insert into school_child_access (id, school_id, child_id, status, granted_by_account_id, granted_at) "
                + "values (?, ?::uuid, ?::uuid, 'ACTIVE', ?::uuid, now())", UUID.randomUUID(), schoolId, childId, grantedBy.id());
    }

    private User parent() throws Exception {
        String email = "overview-parent-" + UUID.randomUUID() + "@example.com";
        JsonNode body = json(post("/api/v1/auth/register", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\","
                + "\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null), 201);
        return new User(body.get("accountId").asString(), email, PlatformRole.PARENT, body.get("accessToken").asString());
    }

    private String child(User parent, String name) throws Exception {
        return json(post("/api/v1/children", "{\"displayName\":\"" + name + "\",\"dateOfBirth\":\"" + LocalDate.now().minusYears(7) + "\"}",
                parent), 201).get("id").asString();
    }

    private User user(PlatformRole role) throws Exception {
        String email = role.name().toLowerCase() + "-overview-" + UUID.randomUUID() + "@example.com";
        accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), role));
        JsonNode body = json(post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", null), 200);
        return new User(body.get("accountId").asString(), email, role, body.get("accessToken").asString());
    }

    private static JsonNode json(HttpResponse<String> response, int expected) throws Exception {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expected);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> get(String path, User user) throws Exception {
        return http.send(request(path, user).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body, User user) throws Exception {
        return http.send(request(path, user).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> put(String path, String body, User user) throws Exception {
        return http.send(request(path, user).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder request(String path, User user) {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (user != null) {
            req.header("Authorization", "Bearer " + user.token());
        }
        return req;
    }
}
