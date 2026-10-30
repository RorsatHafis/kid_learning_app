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
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L-2 (owner, 2026-10-30): an administrator adds or corrects the Khmer words of a plan already offered - a translation
 * only. The English, price, limits, status and every subscription stay as they are; the change is audited, needs a
 * step-up, and the family on the plan reads the new Khmer.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class PlanTranslationIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Plan-Translation-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void anOfferedPlansKhmerIsCorrectedAndNothingElseChanges() throws Exception {
        String admin = admin(true);
        String code = "TR_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String planId = json(post("/api/v1/admin/plans", "{\"code\":\"" + code + "\",\"name\":\"Family Plus\"}", admin, null), 201)
                .get("plan").get("id").asString();
        json(put("/api/v1/admin/plans/" + planId, "{\"name\":\"Family Plus\",\"description\":\"Three children.\",\"selfService\":false,"
                + "\"priceAmountMinor\":500,\"priceCurrency\":\"USD\",\"billingInterval\":\"MONTH\","
                + "\"limits\":{\"CHILDREN_MAX\":3,\"JOURNEYS_PER_CHILD_MAX\":2}}", admin, null), 200);
        json(post("/api/v1/admin/plans/" + planId + "/activate", "{}", admin, null), 200);
        String email = "plan-translation-" + UUID.randomUUID() + "@example.com";
        String parent = registerParent(email);
        String familyId = json(get("/api/v1/admin/families?parentEmail=" + URLEncoder.encode(email, StandardCharsets.UTF_8), admin, null),
                200).get("familyId").asString();
        json(post("/api/v1/admin/families/" + familyId + "/subscription", "{\"planId\":\"" + planId + "\"}", admin, null), 200);
        Map<String, Object> before = plan(planId);
        String subscriptionBefore = subscription(familyId);
        assertThat(json(get("/api/v1/family/plan", parent, "km"), 200).get("plan").get("name").asString()).isEqualTo("Family Plus");

        // Offered, with no Khmer: the administrator adds it.
        JsonNode added = json(put("/api/v1/admin/plans/" + planId + "/khmer", "{\"name\":\"គ្រួសារ បូក\",\"description\":\"កូនបី។\"}",
                admin, null), 200).get("plan");
        assertThat(added.get("status").asString()).isEqualTo("ACTIVE");
        assertThat(added.get("khmer").get("name").asString()).isEqualTo("គ្រួសារ បូក");
        // ...and corrects it.
        json(put("/api/v1/admin/plans/" + planId + "/khmer", "{\"name\":\"គ្រួសារ បូក\",\"description\":\"កូនបីនាក់។\"}", admin, null), 200);

        // Nothing but the Khmer changed: English, price, limits, status, offer dates, the subscription.
        Map<String, Object> after = plan(planId);
        for (String column : new String[]{"name", "description", "status", "price_amount_minor", "price_currency", "billing_interval",
                "self_service", "available_from", "available_until", "is_default"}) {
            assertThat(after.get(column)).as(column).isEqualTo(before.get(column));
        }
        assertThat(after.get("name_km")).isEqualTo("គ្រួសារ បូក");
        assertThat(after.get("description_km")).isEqualTo("កូនបីនាក់។");
        assertThat(jdbc.queryForObject("select limit_value from plan_entitlements where plan_id = ?::uuid and entitlement_key = 'CHILDREN_MAX'",
                Integer.class, UUID.fromString(planId))).isEqualTo(3);
        assertThat(subscription(familyId)).isEqualTo(subscriptionBefore);

        // The family reading Khmer sees the new words; in English, the same as before.
        JsonNode km = json(get("/api/v1/family/plan", parent, "km"), 200).get("plan");
        assertThat(km.get("name").asString()).isEqualTo("គ្រួសារ បូក");
        assertThat(km.get("description").asString()).isEqualTo("កូនបីនាក់។");
        assertThat(json(get("/api/v1/family/plan", parent, "en"), 200).get("plan").get("description").asString()).isEqualTo("Three children.");

        // Audited, each change with what it was and became.
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action = 'PLAN_TRANSLATED' and resource_id = ?::uuid",
                Integer.class, UUID.fromString(planId))).isEqualTo(2);
        assertThat(jdbc.queryForObject("select metadata::text from audit_events where action = 'PLAN_TRANSLATED' and resource_id = ?::uuid "
                + "order by occurred_at desc limit 1", String.class, UUID.fromString(planId))).contains("កូនបី។").contains("កូនបីនាក់។");

        // Only an administrator, and only with the password just re-entered (step-up).
        assertThat(put("/api/v1/admin/plans/" + planId + "/khmer", "{\"name\":\"x\"}", parent, null).statusCode()).isEqualTo(403);
        HttpResponse<String> noStepUp = put("/api/v1/admin/plans/" + planId + "/khmer", "{\"name\":\"x\"}", admin(false), null);
        assertThat(noStepUp.statusCode()).isEqualTo(403);
        assertThat(noStepUp.body()).contains("stepUpRequired");
        assertThat(plan(planId).get("name_km")).isEqualTo("គ្រួសារ បូក");
    }

    // ------------------------------------------------------------------ helpers

    private Map<String, Object> plan(String planId) {
        return jdbc.queryForMap("select * from subscription_plans where id = ?::uuid", UUID.fromString(planId));
    }

    private String subscription(String familyId) {
        return String.valueOf(jdbc.queryForList("select plan_id, status, current_period_end, updated_at from family_subscriptions "
                + "where family_id = ?::uuid order by created_at", UUID.fromString(familyId)));
    }

    private String registerParent(String email) throws Exception {
        return json(post("/api/v1/auth/register", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\","
                + "\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null, null), 201).get("accessToken").asString();
    }

    private String admin(boolean steppedUp) throws Exception {
        String email = "plan-translation-admin-" + UUID.randomUUID() + "@example.com";
        accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), PlatformRole.ADMIN));
        String token = json(post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", null, null), 200)
                .get("accessToken").asString();
        return steppedUp ? com.platform.TestAdmin.stepUp(port, token, PASSWORD) : token;
    }

    private static JsonNode json(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> get(String path, String bearer, String language) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).GET(), bearer, language);
    }

    private HttpResponse<String> post(String path, String body, String bearer, String language) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)), bearer, language);
    }

    private HttpResponse<String> put(String path, String body, String bearer, String language) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body)), bearer, language);
    }

    private HttpResponse<String> send(HttpRequest.Builder request, String bearer, String language) throws Exception {
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
            com.platform.TestAdmin.withStepUp(request, bearer);
        }
        if (language != null) {
            request.header("Accept-Language", language);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
