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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** PLATFORM-EXPANSION.md §3: plans, entitlements, grants, and where limits apply. */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class SubscriptionIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Subscription-Test-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void aFamilyWithoutASubscriptionHasTheDefaultPlanWithEverythingIncluded() throws Exception {
        String parent = registerParent("default-" + UUID.randomUUID() + "@example.com");

        JsonNode plan = json(get("/api/v1/family/plan", parent), 200);
        assertThat(plan.get("onDefaultPlan").asBoolean()).isTrue();
        assertThat(plan.get("plan").get("code").asString()).isEqualTo("EARLY_ACCESS");
        assertThat(plan.get("subscription").isNull()).isTrue();
        for (JsonNode entitlement : plan.get("entitlements")) {
            assertThat(entitlement.get("limit").isNull()).as(entitlement.toString()).isTrue();
            assertThat(entitlement.get("used").asInt()).isZero();
        }
    }

    @Test
    void aGrantedPlanLimitsWhatAFamilyAddsAndEndingItRestoresTheDefault() throws Exception {
        String admin = token(PlatformRole.ADMIN);
        String planId = activePlan(admin, 1, 0);
        String email = "granted-" + UUID.randomUUID() + "@example.com";
        String parent = registerParent(email);

        JsonNode family = json(get("/api/v1/admin/families?parentEmail=" + URLEncoder.encode(email, StandardCharsets.UTF_8), admin), 200);
        String familyId = family.get("familyId").asString();
        json(post("/api/v1/admin/families/" + familyId + "/subscription",
                "{\"planId\":\"" + planId + "\",\"note\":\"Pilot family\"}", admin), 200);

        JsonNode plan = json(get("/api/v1/family/plan", parent), 200);
        assertThat(plan.get("onDefaultPlan").asBoolean()).isFalse();
        assertThat(plan.get("plan").get("id").asString()).isEqualTo(planId);
        assertThat(plan.get("subscription").get("status").asString()).isEqualTo("ACTIVE");
        assertThat(plan.get("subscription").get("source").asString()).isEqualTo("ADMIN_GRANT");

        // One child fits; the plan allows no journeys, so automatic enrolment is refused - and the
        // child is still created (a plan never blocks what a family already has).
        String childId = json(post("/api/v1/children", child(), parent), 201).get("id").asString();
        assertThat(json(get("/api/v1/children/" + childId + "/enrollments", parent), 200).size()).isZero();

        JsonNode curricula = json(get("/api/v1/curricula", parent), 200);
        String curriculumId = curricula.get(0).get("id").asString();
        JsonNode journeyRefused = json(post("/api/v1/children/" + childId + "/enrollments",
                "{\"curriculumId\":\"" + curriculumId + "\"}", parent), 409);
        assertThat(journeyRefused.get("entitlement").asString()).isEqualTo("JOURNEYS_PER_CHILD_MAX");
        assertThat(journeyRefused.get("limit").asInt()).isZero();

        JsonNode childRefused = json(post("/api/v1/children", child(), parent), 409);
        assertThat(childRefused.get("entitlement").asString()).isEqualTo("CHILDREN_MAX");
        assertThat(childRefused.get("detail").asString()).isEqualTo("Your family's plan includes one child.");
        assertThat(json(get("/api/v1/family/plan", parent), 200).get("entitlements").get(0).get("used").asInt()).isEqualTo(1);

        // Ending the grant: the default plan applies at once, and the family can go on.
        json(post("/api/v1/admin/families/" + familyId + "/subscription/end", "{\"reason\":\"Pilot over\"}", admin), 200);
        JsonNode after = json(get("/api/v1/family/plan", parent), 200);
        assertThat(after.get("onDefaultPlan").asBoolean()).isTrue();
        assertThat(after.get("subscription").get("status").asString()).isEqualTo("EXPIRED");
        assertThat(after.get("subscription").get("grantsAccess").asBoolean()).isFalse();
        assertThat(post("/api/v1/children/" + childId + "/enrollments", "{\"curriculumId\":\"" + curriculumId + "\"}", parent)
                .statusCode()).isIn(200, 201);

        assertThat(jdbc.queryForObject("select count(*) from audit_events where action in ('SUBSCRIPTION_GRANTED', 'SUBSCRIPTION_ENDED') "
                + "and resource_type = 'FAMILY_SUBSCRIPTION' and resource_id = (select id from family_subscriptions where family_id = ?::uuid)",
                Integer.class, UUID.fromString(familyId))).isEqualTo(2);
    }

    @Test
    void aGrantsEndCanBeMovedAndTheFamilySeesWhatFollowsButNotTheOperatorNote() throws Exception {
        String admin = token(PlatformRole.ADMIN);
        String granted = activePlan(admin, 3, 2);
        String choosable = activePlan(admin, 5, 5, true);
        String email = "period-" + UUID.randomUUID() + "@example.com";
        String parent = registerParent(email);
        String familyId = json(get("/api/v1/admin/families?parentEmail=" + URLEncoder.encode(email, StandardCharsets.UTF_8), admin), 200)
                .get("familyId").asString();

        // No subscription yet: nothing to move.
        assertThat(put(period(familyId), "{\"periodEnd\":\"" + Instant.now().plus(Duration.ofDays(5)) + "\",\"reason\":\"x\"}", admin)
                .statusCode()).isEqualTo(409);

        Instant until = Instant.now().plus(Duration.ofDays(30)).truncatedTo(ChronoUnit.SECONDS);
        JsonNode given = json(post("/api/v1/admin/families/" + familyId + "/subscription",
                "{\"planId\":\"" + granted + "\",\"periodEnd\":\"" + until + "\",\"note\":\"Operator-only remark\"}", admin), 200);
        // As stored (a fresh read): the grant's own response carries the unrounded clock.
        String startedAt = json(get("/api/v1/admin/families?parentEmail=" + URLEncoder.encode(email, StandardCharsets.UTF_8), admin), 200)
                .get("plan").get("subscription").get("startedAt").asString();
        assertThat(given.get("plan").get("subscription").get("note").asString()).isEqualTo("Operator-only remark");

        // The family sees its plan, its end, what follows and plans it could choose - never the note.
        HttpResponse<String> own = get("/api/v1/family/plan", parent);
        assertThat(own.body()).doesNotContain("Operator-only remark");
        JsonNode plan = json(own, 200);
        assertThat(plan.get("subscription").has("note")).isFalse();
        assertThat(plan.get("subscription").has("id")).isFalse();
        assertThat(Instant.parse(plan.get("subscription").get("currentPeriodEnd").asString())).isEqualTo(until);
        assertThat(plan.get("afterwards").get("plan").get("code").asString()).isEqualTo("EARLY_ACCESS");
        List<String> choices = new ArrayList<>();
        plan.get("choices").forEach(choice -> choices.add(choice.get("plan").get("id").asString()));
        assertThat(choices).contains(choosable).doesNotContain(granted);

        // Extended: same plan, same start, a later end, recorded with its reason.
        Instant later = until.plus(Duration.ofDays(60));
        JsonNode extended = json(put(period(familyId), "{\"periodEnd\":\"" + later + "\",\"reason\":\"Pilot extended\"}", admin), 200);
        JsonNode sub = extended.get("plan").get("subscription");
        assertThat(Instant.parse(sub.get("currentPeriodEnd").asString())).isEqualTo(later);
        assertThat(sub.get("startedAt").asString()).isEqualTo(startedAt);
        assertThat(extended.get("plan").get("plan").get("id").asString()).isEqualTo(granted);

        // Refusals: an end in the past, no reason, a parent trying it.
        assertThat(put(period(familyId), "{\"periodEnd\":\"" + Instant.now().minusSeconds(60) + "\",\"reason\":\"x\"}", admin)
                .statusCode()).isEqualTo(400);
        assertThat(put(period(familyId), "{\"periodEnd\":null,\"reason\":\" \"}", admin).statusCode()).isEqualTo(400);
        assertThat(put(period(familyId), "{\"periodEnd\":null,\"reason\":\"x\"}", parent).statusCode()).isEqualTo(403);

        // Open-ended: nothing follows, so the family isn't told about an end.
        json(put(period(familyId), "{\"periodEnd\":null,\"reason\":\"Partner school family\"}", admin), 200);
        assertThat(json(get("/api/v1/family/plan", parent), 200).get("afterwards").isNull()).isTrue();

        // Once ended, a date change can't bring it back: that is a new grant.
        json(post("/api/v1/admin/families/" + familyId + "/subscription/end", "{\"reason\":\"Pilot over\"}", admin), 200);
        assertThat(put(period(familyId), "{\"periodEnd\":\"" + later + "\",\"reason\":\"x\"}", admin).statusCode()).isEqualTo(409);

        assertThat(jdbc.queryForObject("select count(*) from audit_events where action = 'SUBSCRIPTION_PERIOD_CHANGED' "
                + "and resource_id = (select id from family_subscriptions where family_id = ?::uuid)",
                Integer.class, UUID.fromString(familyId))).isEqualTo(2);
    }

    @Test
    void theAdminOverviewListsPlansEndingSoonAndOtherwiseOnlyCounts() throws Exception {
        String admin = token(PlatformRole.ADMIN);
        String planId = activePlan(admin, 3, 3);
        String soonEmail = "soon-" + UUID.randomUUID() + "@example.com";
        String laterEmail = "later-" + UUID.randomUUID() + "@example.com";
        String soonParent = registerParent(soonEmail);
        registerParent(laterEmail);
        json(post("/api/v1/children", "{\"displayName\":\"Overview Child\",\"dateOfBirth\":\"" + LocalDate.now().minusYears(6)
                + "\"}", soonParent), 201);
        String soonFamily = familyOf(admin, soonEmail);
        Instant soon = Instant.now().plus(Duration.ofDays(5)).truncatedTo(ChronoUnit.SECONDS);
        json(post("/api/v1/admin/families/" + soonFamily + "/subscription", "{\"planId\":\"" + planId + "\",\"periodEnd\":\"" + soon
                + "\"}", admin), 200);
        json(post("/api/v1/admin/families/" + familyOf(admin, laterEmail) + "/subscription", "{\"planId\":\"" + planId
                + "\",\"periodEnd\":\"" + Instant.now().plus(Duration.ofDays(40)) + "\"}", admin), 200);

        HttpResponse<String> response = get("/api/v1/admin/overview", admin);
        JsonNode overview = json(response, 200);
        JsonNode ending = null;
        for (JsonNode plan : overview.get("waiting").get("plansEndingSoon")) {
            if (plan.get("familyId").asString().equals(soonFamily)) {
                ending = plan;
            }
            assertThat(plan.get("parentEmail").asString()).isNotEqualTo(laterEmail);
        }
        assertThat(ending).as("the family whose plan ends in 5 days").isNotNull();
        assertThat(ending.get("parentEmail").asString()).isEqualTo(soonEmail);
        assertThat(ending.get("planName").asString()).isEqualTo("Test plan");
        assertThat(Instant.parse(ending.get("endsAt").asString())).isEqualTo(soon);
        assertThat(overview.get("waiting").get("plansEndingSoonTotal").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(overview.get("platform").get("children").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(overview.get("content").get("curriculaPublished").asLong()).isGreaterThanOrEqualTo(0);
        // Counts, not children: no child's name reaches the operator's home.
        assertThat(response.body()).doesNotContain("Overview Child");

        assertThat(get("/api/v1/admin/overview", soonParent).statusCode()).isEqualTo(403);
        assertThat(get("/api/v1/admin/overview", token(PlatformRole.TEACHER)).statusCode()).isEqualTo(403);
        assertThat(get("/api/v1/admin/overview", null).statusCode()).isEqualTo(401);
    }

    private String familyOf(String admin, String parentEmail) throws Exception {
        return json(get("/api/v1/admin/families?parentEmail=" + URLEncoder.encode(parentEmail, StandardCharsets.UTF_8), admin), 200)
                .get("familyId").asString();
    }

    @Test
    void anActivePlanIsFrozenAndTheDefaultCantBeRetired() throws Exception {
        String admin = token(PlatformRole.ADMIN);
        String planId = activePlan(admin, 2, 2);

        assertThat(put("/api/v1/admin/plans/" + planId, revise(3, 3), admin).statusCode()).isEqualTo(409);

        String defaultId = null;
        for (JsonNode plan : json(get("/api/v1/admin/plans", admin), 200)) {
            if (plan.get("plan").get("isDefault").asBoolean()) {
                defaultId = plan.get("plan").get("id").asString();
            }
        }
        assertThat(defaultId).isNotNull();
        assertThat(post("/api/v1/admin/plans/" + defaultId + "/retire", "{}", admin).statusCode()).isEqualTo(409);

        String draft = json(post("/api/v1/admin/plans", "{\"code\":\"DRAFT_" + suffix() + "\",\"name\":\"Draft\"}", admin), 201)
                .get("plan").get("id").asString();
        assertThat(post("/api/v1/admin/plans/" + draft + "/make-default", "{}", admin).statusCode()).isEqualTo(409);
        assertThat(post("/api/v1/admin/families/" + UUID.randomUUID() + "/subscription",
                "{\"planId\":\"" + draft + "\"}", admin).statusCode()).isEqualTo(404);
    }

    @Test
    void onlyAnAdministratorRunsPlansAndGrants() throws Exception {
        String parent = registerParent("not-admin-" + UUID.randomUUID() + "@example.com");
        String teacher = token(PlatformRole.TEACHER);

        for (String bearer : new String[] {parent, teacher}) {
            assertThat(get("/api/v1/admin/plans", bearer).statusCode()).isEqualTo(403);
            assertThat(post("/api/v1/admin/plans", "{\"code\":\"NOPE\",\"name\":\"Nope\"}", bearer).statusCode()).isEqualTo(403);
            assertThat(get("/api/v1/admin/families?parentEmail=x%40example.com", bearer).statusCode()).isEqualTo(403);
        }
        assertThat(get("/api/v1/admin/plans", null).statusCode()).isEqualTo(401);
        assertThat(get("/api/v1/family/plan", null).statusCode()).isEqualTo(401);
    }

    // ------------------------------------------------------------------ helpers

    private String activePlan(String admin, int children, int journeys) throws Exception {
        return activePlan(admin, children, journeys, false);
    }

    private String activePlan(String admin, int children, int journeys, boolean selfService) throws Exception {
        String planId = json(post("/api/v1/admin/plans",
                "{\"code\":\"TEST_" + suffix() + "\",\"name\":\"Test plan\",\"description\":\"For tests\"}", admin), 201)
                .get("plan").get("id").asString();
        json(put("/api/v1/admin/plans/" + planId, revise(children, journeys).replace("\"selfService\":false",
                "\"selfService\":" + selfService), admin), 200);
        json(post("/api/v1/admin/plans/" + planId + "/activate", "{}", admin), 200);
        return planId;
    }

    private static String revise(int children, int journeys) {
        return "{\"name\":\"Test plan\",\"description\":\"For tests\",\"selfService\":false,"
                + "\"limits\":{\"CHILDREN_MAX\":" + children + ",\"JOURNEYS_PER_CHILD_MAX\":" + journeys + "}}";
    }

    private static String period(String familyId) {
        return "/api/v1/admin/families/" + familyId + "/subscription/period";
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private static String child() {
        return "{\"displayName\":\"Plan Child\",\"dateOfBirth\":\"" + LocalDate.now().minusYears(6) + "\"}";
    }

    private String registerParent(String email) throws Exception {
        return json(post("/api/v1/auth/register", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\","
                + "\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null), 201).get("accessToken").asString();
    }

    private String token(PlatformRole role) throws Exception {
        String email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), role));
        return json(post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", null), 200)
                .get("accessToken").asString();
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

    private HttpResponse<String> send(HttpRequest.Builder request, String bearer) throws Exception {
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
