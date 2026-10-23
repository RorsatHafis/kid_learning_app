package com.platform.subscription;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M6 over real HTTP against PostgreSQL: a refusal from a plan says why in a machine-readable way, that nothing already
 * there is lost, and - for a family - which real plans include more (only ones a family may choose, with a price only
 * when set). A school's refusal never lists plans (a family can reach a school's limit; a school's plans are its
 * principal's). An unpaid school says whether it never paid or its paid period ended.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class EntitlementNoticeIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Entitlement-Notice-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void aFamilysLimitSaysWhichRealPlansIncludeMoreAndThatNothingIsLost() throws Exception {
        String admin = token(PlatformRole.ADMIN);
        String small = familyPlan(admin, "Small", "1", false, null);
        familyPlan(admin, "Plus", "4", true, 500L);
        familyPlan(admin, "Open", "null", true, null);
        String arranged = familyPlan(admin, "Arranged", "10", false, 900L);
        String same = familyPlan(admin, "Same", "1", true, 100L);

        String email = "notice-" + UUID.randomUUID() + "@example.com";
        String parent = registerParent(email);
        String familyId = json(send("GET", "/api/v1/admin/families?parentEmail=" + URLEncoder.encode(email, StandardCharsets.UTF_8), null,
                admin), 200).get("familyId").asString();
        json(send("POST", "/api/v1/admin/families/" + familyId + "/subscription", "{\"planId\":\"" + small + "\"}", admin), 200);
        json(send("POST", "/api/v1/children", child(), parent), 201);

        HttpResponse<String> refused = send("POST", "/api/v1/children", child(), parent);
        JsonNode problem = json(refused, 409);
        assertThat(problem.get("reason").asString()).isEqualTo("PLAN_LIMIT");
        assertThat(problem.get("audience").asString()).isEqualTo("FAMILY");
        assertThat(problem.get("entitlement").asString()).isEqualTo("CHILDREN_MAX");
        assertThat(problem.get("limit").asInt()).isEqualTo(1);
        assertThat(problem.get("dataKept").asBoolean()).isTrue();

        // Other tests' plans share this database, so which offers come back isn't fixed here (PlanOffersTests pins the
        // choice and order): a short list, each including more, never one families can't choose or no better.
        JsonNode offers = problem.get("unlockedBy");
        assertThat(offers.isArray()).isTrue();
        assertThat(offers.size()).isBetween(1, 3);
        offers.valueStream().forEach(o -> assertThat(o.get("limit").isNull() || o.get("limit").asInt() > 1).as(o.toString()).isTrue());
        assertThat(offers.valueStream().map(o -> o.get("planId").asString()))
                .as("not choosable by families, or no more than now").doesNotContain(arranged, same, small);
    }

    @Test
    void aSchoolsRefusalsSayWhyAndNeverListPlans() throws Exception {
        String principal = token(PlatformRole.PRINCIPAL);
        String schoolId = json(send("POST", "/api/v1/schools", "{\"name\":\"Notice School " + suffix() + "\"}", principal), 201)
                .get("id").asString();
        JsonNode unpaid = json(send("POST", "/api/v1/schools/" + schoolId + "/classes", "{\"name\":\"Grade 1\"}", principal), 402);
        assertThat(unpaid.get("reason").asString()).isEqualTo("SCHOOL_UNPAID");
        assertThat(unpaid.get("audience").asString()).isEqualTo("SCHOOL");
        assertThat(unpaid.get("dataKept").asBoolean()).isTrue();
        assertThat(unpaid.has("unlockedBy")).isFalse();
    }

    // ------------------------------------------------------------------ helpers

    private String familyPlan(String admin, String name, String children, boolean selfService, Long priceMinor) throws Exception {
        String planId = json(send("POST", "/api/v1/admin/plans", "{\"code\":\"NOTICE_" + suffix() + "\",\"name\":\"" + name + "\"}", admin), 201)
                .get("plan").get("id").asString();
        String price = priceMinor == null ? "" : ",\"priceAmountMinor\":" + priceMinor + ",\"priceCurrency\":\"USD\",\"billingInterval\":\"MONTH\"";
        json(send("PUT", "/api/v1/admin/plans/" + planId, "{\"name\":\"" + name + "\",\"selfService\":" + selfService + price
                + ",\"limits\":{\"CHILDREN_MAX\":" + children + "}}", admin), 200);
        json(send("POST", "/api/v1/admin/plans/" + planId + "/activate", "{}", admin), 200);
        return planId;
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private static String child() {
        return "{\"displayName\":\"Notice Child\",\"dateOfBirth\":\"" + LocalDate.now().minusYears(6) + "\"}";
    }

    private String registerParent(String email) throws Exception {
        return json(send("POST", "/api/v1/auth/register", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\","
                + "\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null), 201).get("accessToken").asString();
    }

    private String token(PlatformRole role) throws Exception {
        String email = "notice-" + role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), role));
        String token = json(send("POST", "/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", null), 200)
                .get("accessToken").asString();
        // M8: an administrator's money and access actions need the password re-entered.
        return role == PlatformRole.ADMIN ? com.platform.TestAdmin.stepUp(port, token, PASSWORD) : token;
    }

    private static JsonNode json(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return JSON.readTree(response.body());
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
