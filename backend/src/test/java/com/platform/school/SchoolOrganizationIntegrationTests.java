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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M12 B0 over real HTTP and PostgreSQL: every school is a branch of an organization (a school on its own is its
 * organization's one branch, including schools written by older paths); the owner adds branches (a retried request
 * makes no second one), each waiting for its own payment; a sibling branch's calendar can be reused; nothing crosses
 * organizations, and only the owner adds branches.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class SchoolOrganizationIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "School-Organization-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private org.springframework.context.ApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void everySchoolIsABranchIncludingSchoolsWrittenWithoutAnOrganization() throws Exception {
        User principal = user(PlatformRole.PRINCIPAL);
        JsonNode school = json(post("/api/v1/schools", "{\"name\":\"Single Branch Primary\"}", principal), 201);
        String organizationId = school.get("organizationId").asString();
        JsonNode mine = json(get("/api/v1/organizations/mine", principal), 200);
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).get("id").asString()).isEqualTo(organizationId);
        assertThat(mine.get(0).get("owner").asBoolean()).isTrue();
        assertThat(mine.get(0).get("branches")).hasSize(1);

        // An older code path (or raw SQL) inserting a school without an organization: it gets its own.
        UUID legacy = UUID.randomUUID();
        jdbc.update("insert into schools (id, name) values (?, 'Legacy Insert School')", legacy);
        assertThat(jdbc.queryForObject("select organization_id from schools where id = ?", UUID.class, legacy)).isEqualTo(legacy);
        assertThat(jdbc.queryForObject("select name from school_organizations where id = ?", String.class, legacy))
                .isEqualTo("Legacy Insert School");
    }

    @Test
    void theOwnerAddsBranchesThatShareTheOrganizationButKeepTheirOwnPaymentAndCalendar() throws Exception {
        User owner = user(PlatformRole.PRINCIPAL);
        String first = json(post("/api/v1/schools", "{\"name\":\"Riverside Group - North\"}", owner), 201).get("id").asString();
        PaidSchools.pay(context, UUID.fromString(first));
        String organizationId = json(get("/api/v1/organizations/mine", owner), 200).get(0).get("id").asString();

        JsonNode branch = json(post("/api/v1/organizations/" + organizationId + "/branches", "{\"name\":\"Riverside Group - South\"}", owner), 201);
        String second = branch.get("id").asString();
        assertThat(branch.get("principalAccountId").asString()).isEqualTo(owner.id());
        // A retried request makes no second branch.
        assertThat(json(post("/api/v1/organizations/" + organizationId + "/branches", "{\"name\":\"riverside group - south\"}", owner), 201)
                .get("id").asString()).isEqualTo(second);
        assertThat(json(get("/api/v1/organizations/mine", owner), 200).get(0).get("branches")).hasSize(2);
        assertThat(jdbc.queryForObject("select organization_id::text from schools where id = ?::uuid", String.class, second))
                .isEqualTo(organizationId);

        // The new branch waits for its own payment, like any school.
        String calendar = "/api/v1/schools/" + first + "/academic-years";
        String yearId = json(post(calendar, "{\"name\":\"2025-2026\",\"startsOn\":\"2025-11-03\",\"endsOn\":\"2026-08-28\"}", owner), 201)
                .get("id").asString();
        assertThat(post(calendar + "/" + yearId + "/terms", "{\"name\":\"Term 1\",\"startsOn\":\"2025-11-03\",\"endsOn\":\"2026-03-27\"}", owner)
                .statusCode()).isEqualTo(201);
        String copy = "/api/v1/schools/" + second + "/academic-years/copy-from/" + first;
        assertThat(post(copy, "", owner).statusCode()).isGreaterThanOrEqualTo(400);
        PaidSchools.pay(context, UUID.fromString(second));

        // Reusing the sibling's calendar: years and terms, once.
        JsonNode copied = json(post(copy, "", owner), 200);
        assertThat(copied.get("yearsAdded")).extracting(JsonNode::asString).containsExactly("2025-2026");
        JsonNode years = json(get("/api/v1/schools/" + second + "/academic-years", owner), 200);
        assertThat(years).hasSize(1);
        assertThat(years.get(0).get("terms")).hasSize(1);
        assertThat(json(post(copy, "", owner), 200).get("yearsAlreadyThere")).extracting(JsonNode::asString).containsExactly("2025-2026");

        // Nothing crosses organizations; only the owner adds branches.
        User other = user(PlatformRole.PRINCIPAL);
        String elsewhere = json(post("/api/v1/schools", "{\"name\":\"Another Group School\"}", other), 201).get("id").asString();
        PaidSchools.pay(context, UUID.fromString(elsewhere));
        assertThat(post("/api/v1/schools/" + elsewhere + "/academic-years/copy-from/" + first, "", other).statusCode()).isIn(403, 404);
        assertThat(post("/api/v1/organizations/" + organizationId + "/branches", "{\"name\":\"Not Theirs\"}", other).statusCode()).isEqualTo(404);
        assertThat(post("/api/v1/organizations/" + organizationId + "/branches", "{\"name\":\"Not Theirs\"}", user(PlatformRole.TEACHER))
                .statusCode()).isIn(403, 404);
        assertThat(post("/api/v1/organizations/" + organizationId + "/branches", "{\"name\":\"Not Theirs\"}", user(PlatformRole.PARENT))
                .statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("select count(*) from schools where organization_id = ?::uuid", Integer.class, organizationId))
                .isEqualTo(2);
        for (String action : List.of("SCHOOL_BRANCH_CREATED", "ACADEMIC_CALENDAR_COPIED")) {
            assertThat(jdbc.queryForObject("select count(*) from audit_events where action = ? and actor_id = ?::uuid", Integer.class,
                    action, UUID.fromString(owner.id()))).as(action).isPositive();
        }
    }

    @Test
    void theOwnerAppointsABranchPrincipalWhoRunsThatBranchOnly() throws Exception {
        User owner = user(PlatformRole.PRINCIPAL);
        String north = json(post("/api/v1/schools", "{\"name\":\"Lotus Group - North\"}", owner), 201).get("id").asString();
        PaidSchools.pay(context, UUID.fromString(north));
        String organizationId = json(get("/api/v1/organizations/mine", owner), 200).get(0).get("id").asString();
        String south = json(post("/api/v1/organizations/" + organizationId + "/branches", "{\"name\":\"Lotus Group - South\"}", owner), 201)
                .get("id").asString();
        PaidSchools.pay(context, UUID.fromString(south));
        String appoint = "/api/v1/organizations/" + organizationId + "/branches/" + south + "/principal";

        // Only an existing, active principal account can be appointed.
        User teacher = user(PlatformRole.TEACHER);
        assertThat(json(put(appoint, "{\"email\":\"" + teacher.email() + "\"}", owner), 409).get("detail").asString())
                .isEqualTo("That account isn't an active principal account.");
        assertThat(json(put(appoint, "{\"email\":\"nobody-" + UUID.randomUUID() + "@example.com\"}", owner), 409).get("detail").asString())
                .isEqualTo("There is no principal account with that email address.");

        User branchPrincipal = user(PlatformRole.PRINCIPAL);
        assertThat(json(put(appoint, "{\"email\":\"" + branchPrincipal.email() + "\"}", owner), 200).get("principalAccountId").asString())
                .isEqualTo(branchPrincipal.id());
        // Someone who doesn't own the organization can't appoint (as if it didn't exist), not even the new principal.
        assertThat(put(appoint, "{\"email\":\"" + owner.email() + "\"}", branchPrincipal).statusCode()).isEqualTo(404);

        // The branch principal runs South only.
        String southYears = "/api/v1/schools/" + south + "/academic-years";
        assertThat(post(southYears, "{\"name\":\"2025-2026\",\"startsOn\":\"2025-11-03\",\"endsOn\":\"2026-08-28\"}", branchPrincipal)
                .statusCode()).isEqualTo(201);
        assertThat(get("/api/v1/schools/" + north + "/academic-years", branchPrincipal).statusCode()).isIn(403, 404);
        assertThat(post("/api/v1/organizations/" + organizationId + "/branches", "{\"name\":\"Theirs\"}", branchPrincipal).statusCode())
                .isEqualTo(404);
        JsonNode theirs = json(get("/api/v1/organizations/mine", branchPrincipal), 200);
        assertThat(theirs).hasSize(1);
        assertThat(theirs.get(0).get("owner").asBoolean()).isFalse();
        assertThat(theirs.get(0).get("branches")).extracting(b -> b.get("id").asString()).containsExactly(south);

        // The owner still runs both branches' configuration...
        assertThat(get(southYears, owner).statusCode()).isEqualTo(200);
        assertThat(post("/api/v1/schools/" + south + "/classes", "{\"name\":\"Grade 3A\"}", owner).statusCode()).isEqualTo(201);
        // ...but a branch's children's data stays with the people who run that branch's classes.
        String southClass = json(post("/api/v1/schools/" + south + "/classes", "{\"name\":\"Grade 4A\"}", branchPrincipal), 201)
                .get("id").asString();
        assertThat(get("/api/v1/schools/classes/" + southClass + "/children", owner).statusCode()).isIn(403, 404);
        assertThat(get("/api/v1/schools/classes/" + southClass + "/children", branchPrincipal).statusCode()).isEqualTo(200);

        assertThat(jdbc.queryForObject("select count(*) from audit_events where action = 'SCHOOL_BRANCH_PRINCIPAL_ASSIGNED' "
                + "and resource_id = ?::uuid", Integer.class, south)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ helpers

    private record User(String id, String email, PlatformRole role, String token) {
    }

    private User user(PlatformRole role) throws Exception {
        String email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
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
