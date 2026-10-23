package com.platform.school;

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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M4 over real HTTP against PostgreSQL: a principal sets up a school and pays for its first plan themselves. Setting
 * the school up twice (or twice at once) makes one school; only a priced school plan can be offered to schools; the
 * principal sees and starts paying for it; two "pay" clicks at once make one order; a pending payment grants nothing;
 * only a confirmed one opens the school, with the plan's limits; while it runs, switching plans is refused.
 * (A new principal opening their account by confirming their address is in AccountRecoveryIntegrationTests.)
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class SelfServiceSchoolOnboardingIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Self-Service-School-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void aPrincipalSetsUpASchoolOnceAndPaysForItsFirstPlanWhichOpensOnlyWhenConfirmed() throws Exception {
        String admin = staff(PlatformRole.ADMIN);
        String principal = staff(PlatformRole.PRINCIPAL);

        // Set up twice (a double submit, a retry): one school, the second answer says it already existed.
        String name = "Sunrise Primary " + suffix();
        HttpResponse<String> first = post("/api/v1/schools", "{\"name\":\"" + name + "\"}", principal);
        assertThat(first.statusCode()).isEqualTo(201);
        String schoolId = JSON.readTree(first.body()).get("id").asString();
        HttpResponse<String> again = post("/api/v1/schools", "{\"name\":\"  " + name.toLowerCase() + " \"}", principal);
        assertThat(again.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(again.body()).get("id").asString()).isEqualTo(schoolId);
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action = 'SCHOOL_CREATED' and resource_id = ?::uuid",
                Integer.class, schoolId)).isEqualTo(1);

        // An unpriced school plan can't be offered to schools; a priced one can.
        String unpriced = draftSchoolPlan(admin);
        assertThat(put("/api/v1/admin/plans/" + unpriced, "{\"name\":\"Unpriced\",\"selfService\":true,\"limits\":{}}", admin).statusCode())
                .isEqualTo(400);
        String plan = activeSchoolPlan(admin, true, 1);
        String arranged = activeSchoolPlan(admin, false, 5);

        // The principal sees the self-service plan (not the arranged one), and the school is still closed.
        List<String> choices = ids(json(get("/api/v1/schools/" + schoolId + "/payment-plans", principal), 200));
        assertThat(choices).contains(plan).doesNotContain(arranged);
        assertThat(post("/api/v1/schools/" + schoolId + "/payments", start(arranged, "pay-arranged"), principal).statusCode()).isEqualTo(400);
        assertThat(post("/api/v1/schools/" + schoolId + "/classes", "{\"name\":\"Grade 1\"}", principal).statusCode()).isEqualTo(402);

        // Two "pay" clicks at once (different keys): one order.
        List<Integer> statuses = concurrently(2, i -> post("/api/v1/schools/" + schoolId + "/payments", start(plan, "click" + i), principal)
                .statusCode());
        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(jdbc.queryForObject("select count(*) from payment_orders where school_id = ?::uuid", Integer.class, schoolId)).isEqualTo(1);
        String orderId = jdbc.queryForObject("select id::text from payment_orders where school_id = ?::uuid", String.class, schoolId);

        // Pending grants nothing; a stranger can't pay for it or see it.
        JsonNode pending = json(get("/api/v1/schools/" + schoolId + "/plan", principal), 200).get("access");
        assertThat(pending.get("paid").asBoolean()).isFalse();
        assertThat(pending.get("paymentPending").asBoolean()).isTrue();
        assertThat(post("/api/v1/schools/" + schoolId + "/classes", "{\"name\":\"Grade 1\"}", principal).statusCode()).isEqualTo(402);
        String stranger = staff(PlatformRole.PRINCIPAL);
        assertThat(post("/api/v1/schools/" + schoolId + "/payments", start(plan, "stranger"), stranger).statusCode()).isEqualTo(403);

        // Confirmed against the receipt: the school opens, with the plan's limit (one class).
        json(post("/api/v1/admin/payments/" + orderId + "/confirm", com.platform.TestAdmin.receipt(port, admin, orderId, "BANK-" + suffix()),
                admin), 200);
        assertThat(json(get("/api/v1/schools/" + schoolId + "/plan", principal), 200).get("access").get("paid").asBoolean()).isTrue();
        assertThat(post("/api/v1/schools/" + schoolId + "/classes", "{\"name\":\"Grade 1\"}", principal).statusCode()).isEqualTo(201);
        assertThat(post("/api/v1/schools/" + schoolId + "/classes", "{\"name\":\"Grade 2\"}", principal).statusCode())
                .as("the plan's limit").isEqualTo(409);

        // While the paid plan runs: renewing it is offered; switching to another is not.
        String other = activeSchoolPlan(admin, true, 3);
        assertThat(ids(json(get("/api/v1/schools/" + schoolId + "/payment-plans", principal), 200))).containsExactly(plan);
        assertThat(post("/api/v1/schools/" + schoolId + "/payments", start(other, "switch"), principal).statusCode()).isEqualTo(409);
    }

    @Test
    void settingUpTheSameSchoolTwiceAtOnceMakesOne() throws Exception {
        String principal = staff(PlatformRole.PRINCIPAL);
        String name = "Twin Hill School " + suffix();
        List<Integer> statuses = concurrently(3, i -> post("/api/v1/schools", "{\"name\":\"" + name + "\"}", principal).statusCode());
        assertThat(statuses).containsOnlyOnce(201).contains(200);
        assertThat(jdbc.queryForObject("select count(*) from schools where name = ?", Integer.class, name)).isEqualTo(1);
        // Another principal may have a school of the same name: it is theirs.
        assertThat(post("/api/v1/schools", "{\"name\":\"" + name + "\"}", staff(PlatformRole.PRINCIPAL)).statusCode()).isEqualTo(201);
    }

    // ------------------------------------------------------------------ helpers

    private interface Call {
        int run(int i) throws Exception;
    }

    private static List<Integer> concurrently(int n, Call call) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                int index = i;
                futures.add(pool.submit((Callable<Integer>) () -> {
                    go.await();
                    return call.run(index);
                }));
            }
            go.countDown();
            List<Integer> results = new ArrayList<>();
            for (Future<Integer> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private String draftSchoolPlan(String admin) throws Exception {
        return json(post("/api/v1/admin/plans", "{\"code\":\"SELFSCHOOL_" + suffix() + "\",\"name\":\"School plan\",\"audience\":\"SCHOOL\"}",
                admin), 201).get("plan").get("id").asString();
    }

    private String activeSchoolPlan(String admin, boolean selfService, int classes) throws Exception {
        String id = draftSchoolPlan(admin);
        json(put("/api/v1/admin/plans/" + id, "{\"name\":\"School plan\",\"selfService\":" + selfService + ",\"priceAmountMinor\":9000,"
                + "\"priceCurrency\":\"USD\",\"billingInterval\":\"YEAR\",\"limits\":{\"SCHOOL_CLASSES_MAX\":" + classes + "}}", admin), 200);
        json(post("/api/v1/admin/plans/" + id + "/activate", "", admin), 200);
        return id;
    }

    private static List<String> ids(JsonNode plans) {
        return plans.valueStream().map(p -> p.get("id").asString()).toList();
    }

    private static String start(String planId, String key) {
        return "{\"planId\":\"" + planId + "\",\"method\":\"MANUAL\",\"idempotencyKey\":\"" + key + "-" + UUID.randomUUID() + "\"}";
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String staff(PlatformRole role) throws Exception {
        String email = "selfschool-" + role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        Account account = Account.register(email, passwordEncoder.encode(PASSWORD), role);
        account.markEmailVerified(java.time.Instant.now());
        accountRepository.saveAndFlush(account);
        String token = json(post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", null), 200)
                .get("accessToken").asString();
        // M8: an administrator's money and access actions need the password re-entered.
        return role == PlatformRole.ADMIN ? com.platform.TestAdmin.stepUp(port, token, PASSWORD) : token;
    }

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

    private HttpResponse<String> put(String path, String body, String bearer) throws Exception {
        return send("PUT", path, body, bearer);
    }

    private HttpResponse<String> send(String method, String path, String body, String bearer) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
            com.platform.TestAdmin.withStepUp(request, bearer);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

}
