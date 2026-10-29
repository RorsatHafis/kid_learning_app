package com.platform.subscription;

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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flexible plan management, slice 1 (docs/SUBSCRIPTIONS.md): a plan is changed by duplicating it into a new draft
 * version, never by editing what a subscriber agreed to; and an administrator sees who is on which plan, by standing.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class PlanManagementIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Plan-Management-Test-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ApplicationContext context;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void aDuplicatedPlanIsANewDraftVersionAndTheOriginalsSubscribersKeepWhatTheyAgreedTo() throws Exception {
        String admin = token(PlatformRole.ADMIN);
        String code = "V1_" + suffix();
        String originalId = json(post("/api/v1/admin/plans", "{\"code\":\"" + code + "\",\"name\":\"Family 2026\"}", admin), 201)
                .get("plan").get("id").asString();
        json(put("/api/v1/admin/plans/" + originalId, familyPlan("Family 2026", 2, 1, 500), admin), 200);
        json(post("/api/v1/admin/plans/" + originalId + "/activate", "{}", admin), 200);
        String email = "versioned-" + UUID.randomUUID() + "@example.com";
        registerParent(email);
        String familyId = json(get("/api/v1/admin/families?parentEmail=" + URLEncoder.encode(email, StandardCharsets.UTF_8), admin), 200)
                .get("familyId").asString();
        json(post("/api/v1/admin/families/" + familyId + "/subscription", "{\"planId\":\"" + originalId + "\"}", admin), 200);

        // The copy: a draft with everything the original offers, linked to it.
        String nextCode = "V2_" + suffix();
        JsonNode copy = json(post("/api/v1/admin/plans/" + originalId + "/duplicate",
                "{\"code\":\"" + nextCode.toLowerCase() + "\",\"name\":\"Family 2027\"}", admin), 201);
        String copyId = copy.get("plan").get("id").asString();
        assertThat(copy.get("plan").get("code").asString()).isEqualTo(nextCode);
        assertThat(copy.get("plan").get("name").asString()).isEqualTo("Family 2027");
        assertThat(copy.get("plan").get("status").asString()).isEqualTo("DRAFT");
        assertThat(copy.get("plan").get("audience").asString()).isEqualTo("FAMILY");
        assertThat(copy.get("plan").get("description").asString()).isEqualTo("For tests");
        assertThat(copy.get("plan").get("price").get("amountMinor").asLong()).isEqualTo(500);
        assertThat(copy.get("plan").get("price").get("currency").asString()).isEqualTo("USD");
        assertThat(copy.get("plan").get("price").get("interval").asString()).isEqualTo("MONTH");
        assertThat(copy.get("basedOn").get("id").asString()).isEqualTo(originalId);
        assertThat(copy.get("basedOn").get("code").asString()).isEqualTo(code);
        assertThat(copy.get("subscribers").asLong()).isZero();
        assertThat(limit(copy, "CHILDREN_MAX")).isEqualTo(2);
        assertThat(limit(copy, "JOURNEYS_PER_CHILD_MAX")).isEqualTo(1);

        // The new version changes; the old one is retired. Neither touches the family on the original.
        json(put("/api/v1/admin/plans/" + copyId, familyPlan("Family 2027", 3, 2, 700), admin), 200);
        json(post("/api/v1/admin/plans/" + copyId + "/activate", "{}", admin), 200);
        json(post("/api/v1/admin/plans/" + originalId + "/retire", "{}", admin), 200);

        JsonNode original = planIn(json(get("/api/v1/admin/plans", admin), 200), originalId);
        assertThat(original.get("plan").get("status").asString()).isEqualTo("RETIRED");
        assertThat(original.get("plan").get("price").get("amountMinor").asLong()).isEqualTo(500);
        assertThat(limit(original, "CHILDREN_MAX")).isEqualTo(2);
        assertThat(original.get("subscribers").asLong()).isEqualTo(1);
        assertThat(original.get("basedOn").isNull()).isTrue();
        JsonNode family = json(get("/api/v1/admin/families?parentEmail=" + URLEncoder.encode(email, StandardCharsets.UTF_8), admin), 200);
        assertThat(family.get("plan").get("plan").get("id").asString()).isEqualTo(originalId);
        assertThat(family.get("plan").get("entitlements").get(0).get("limit").asInt()).isEqualTo(2);

        // A retired plan can still be duplicated (to bring an offer back); its copy is again a draft.
        JsonNode again = json(post("/api/v1/admin/plans/" + originalId + "/duplicate",
                "{\"code\":\"V3_" + suffix() + "\",\"name\":\"Family 2026 again\"}", admin), 201);
        assertThat(again.get("plan").get("status").asString()).isEqualTo("DRAFT");

        // A school plan's copy stays a school plan, with the school levers only.
        JsonNode schoolDefault = null;
        for (JsonNode p : json(get("/api/v1/admin/plans", admin), 200)) {
            if (p.get("plan").get("audience").asString().equals("SCHOOL") && p.get("plan").get("isDefault").asBoolean()) {
                schoolDefault = p;
            }
        }
        assertThat(schoolDefault).isNotNull();
        JsonNode schoolCopy = json(post("/api/v1/admin/plans/" + schoolDefault.get("plan").get("id").asString() + "/duplicate",
                "{\"code\":\"SCHOOL_V_" + suffix() + "\",\"name\":\"School copy\"}", admin), 201);
        assertThat(schoolCopy.get("plan").get("audience").asString()).isEqualTo("SCHOOL");
        assertThat(schoolCopy.get("plan").get("isDefault").asBoolean()).isFalse();
        List<String> keys = new ArrayList<>();
        schoolCopy.get("entitlements").forEach(e -> keys.add(e.get("key").asString()));
        assertThat(keys).containsExactlyInAnyOrder("SCHOOL_CHILDREN_MAX", "SCHOOL_TEACHERS_MAX", "SCHOOL_CLASSES_MAX", "TEACHER_STORAGE_MB");

        // Refusals: a code in use, a malformed code, a missing name, someone who isn't an administrator.
        assertThat(json(post("/api/v1/admin/plans/" + originalId + "/duplicate",
                "{\"code\":\"" + nextCode + "\",\"name\":\"Clash\"}", admin), 409).get("detail").asString())
                .isEqualTo("A plan with the code " + nextCode + " already exists");
        json(post("/api/v1/admin/plans/" + originalId + "/duplicate", "{\"code\":\"bad code!\",\"name\":\"Bad\"}", admin), 400);
        json(post("/api/v1/admin/plans/" + originalId + "/duplicate", "{\"code\":\"NO_NAME_" + suffix() + "\",\"name\":\" \"}", admin), 400);
        String parent = registerParent("not-admin-" + UUID.randomUUID() + "@example.com");
        assertThat(post("/api/v1/admin/plans/" + originalId + "/duplicate", "{\"code\":\"X_" + suffix() + "\",\"name\":\"X\"}", parent)
                .statusCode()).isEqualTo(403);
        String teacher = token(PlatformRole.TEACHER);
        assertThat(post("/api/v1/admin/plans/" + originalId + "/duplicate", "{\"code\":\"Y_" + suffix() + "\",\"name\":\"Y\"}", teacher)
                .statusCode()).isEqualTo(403);

        // The schema itself keeps the lineage honest, and the copy is audited with where it came from.
        assertThat(jdbc.queryForObject("select based_on_plan_id from subscription_plans where id = ?::uuid", UUID.class,
                UUID.fromString(copyId))).isEqualTo(UUID.fromString(originalId));
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action = 'PLAN_DUPLICATED' and resource_id = ?::uuid "
                + "and metadata ->> 'from' = ?", Integer.class, UUID.fromString(copyId), code)).isEqualTo(1);
    }

    @Test
    void theSummaryCountsWhoIsOnEachPlanByStandingAndListsSchoolsWhosePaidPeriodEndsSoon() throws Exception {
        String admin = token(PlatformRole.ADMIN);
        long onDefaultBefore = json(get("/api/v1/admin/subscriptions/summary", admin), 200).get("familiesOnDefault").asLong();

        String planId = json(post("/api/v1/admin/plans", "{\"code\":\"SUMMARY_" + suffix() + "\",\"name\":\"Summary plan\"}", admin), 201)
                .get("plan").get("id").asString();
        json(put("/api/v1/admin/plans/" + planId, familyPlan("Summary plan", 2, 2, 300), admin), 200);
        json(post("/api/v1/admin/plans/" + planId + "/activate", "{}", admin), 200);
        String open = familyOf(admin, registerParentEmail());
        String endingSoon = familyOf(admin, registerParentEmail());
        String ended = familyOf(admin, registerParentEmail());
        grant(admin, open, planId, null);
        grant(admin, endingSoon, planId, Instant.now().plus(10, ChronoUnit.DAYS).toString());
        grant(admin, ended, planId, null);
        json(post("/api/v1/admin/families/" + ended + "/subscription/end", "{\"reason\":\"Test\"}", admin), 200);

        JsonNode summary = json(get("/api/v1/admin/subscriptions/summary", admin), 200);
        assertThat(summary.get("endingWithinDays").asInt()).isEqualTo(30);
        JsonNode row = rowOf(summary, planId);
        assertThat(row.get("audience").asString()).isEqualTo("FAMILY");
        assertThat(row.get("status").asString()).isEqualTo("ACTIVE");
        assertThat(row.get("active").asLong()).isEqualTo(2);
        assertThat(row.get("endingSoon").asLong()).isEqualTo(1);
        assertThat(row.get("ended").asLong()).isEqualTo(1);
        assertThat(row.get("trialing").asLong()).isZero();
        assertThat(row.get("pastDue").asLong()).isZero();
        assertThat(row.get("cancelling").asLong()).isZero();
        // Three new families, two of them with a plan in force: one more on the default plan.
        assertThat(summary.get("familiesOnDefault").asLong()).isEqualTo(onDefaultBefore + 1);
        // Families are counted, never listed.
        assertThat(summary.toString()).doesNotContain(open).doesNotContain(endingSoon).doesNotContain("@example.com");

        // A school paid for a year is in force; brought to ten days before its end, it is listed to renew.
        String principal = token(PlatformRole.PRINCIPAL);
        String schoolName = "Summary School " + suffix();
        UUID schoolId = UUID.fromString(json(post("/api/v1/schools", "{\"name\":\"" + schoolName + "\"}", principal), 201)
                .get("id").asString());
        long withoutPaidBefore = json(get("/api/v1/admin/subscriptions/summary", admin), 200).get("schoolsWithoutPaidPlan").asLong();
        UUID orderId = PaidSchools.pay(context, schoolId);
        JsonNode paid = json(get("/api/v1/admin/subscriptions/summary", admin), 200);
        assertThat(paid.get("schoolsWithoutPaidPlan").asLong()).isEqualTo(withoutPaidBefore - 1);
        assertThat(paid.get("schoolsEndingSoon").toString()).doesNotContain(schoolId.toString());

        // The clock moves on (both records that say how long it is paid for, as time passing would).
        Timestamp tenDays = Timestamp.from(Instant.now().plus(10, ChronoUnit.DAYS));
        jdbc.update("update payment_orders set period_end = ? where id = ?", tenDays, orderId);
        jdbc.update("update school_subscriptions set current_period_end = ? where school_id = ?", tenDays, schoolId);
        JsonNode soon = json(get("/api/v1/admin/subscriptions/summary", admin), 200);
        JsonNode listed = null;
        for (JsonNode s : soon.get("schoolsEndingSoon")) {
            if (s.get("schoolId").asString().equals(schoolId.toString())) {
                listed = s;
            }
        }
        assertThat(listed).as(soon.get("schoolsEndingSoon").toString()).isNotNull();
        assertThat(listed.get("schoolName").asString()).isEqualTo(schoolName);
        assertThat(listed.get("planName").asString()).isEqualTo("Paid school (test)");

        // Only a platform administrator reads it.
        assertThat(get("/api/v1/admin/subscriptions/summary", principal).statusCode()).isEqualTo(403);
        assertThat(get("/api/v1/admin/subscriptions/summary", registerParent("summary-parent-" + UUID.randomUUID() + "@example.com")).statusCode()).isEqualTo(403);
        assertThat(get("/api/v1/admin/subscriptions/summary", null).statusCode()).isEqualTo(401);
    }

    /** Slice 2 (V80): offer dates decide who new can take a plan up; nobody already on it is affected. */
    @Test
    void offerDatesDecideWhoNewCanTakeAPlanUpAndNeverTouchWhoeverIsOnIt() throws Exception {
        String admin = token(PlatformRole.ADMIN);
        String code = "OFFER_" + suffix();
        String planId = json(post("/api/v1/admin/plans", "{\"code\":\"" + code + "\",\"name\":\"Offer plan\"}", admin), 201)
                .get("plan").get("id").asString();
        json(put("/api/v1/admin/plans/" + planId, familyPlan("Offer plan", 2, 2, 400).replace("\"selfService\":false",
                "\"selfService\":true"), admin), 200);
        json(post("/api/v1/admin/plans/" + planId + "/activate", "{}", admin), 200);
        String availability = "/api/v1/admin/plans/" + planId + "/availability";
        Instant now = Instant.now();

        // Not offered yet: nobody new gets it, in any of the ways a plan is taken up.
        JsonNode later = json(put(availability, "{\"availableFrom\":\"" + now.plus(5, ChronoUnit.DAYS) + "\"}", admin), 200);
        assertThat(later.get("offeredNow").asBoolean()).isFalse();
        assertThat(later.get("availableFrom").isNull()).isFalse();
        assertThat(later.get("availableUntil").isNull()).isTrue();
        String firstEmail = "offer-first-" + UUID.randomUUID() + "@example.com";
        String firstParent = registerParent(firstEmail);
        String first = familyOf(admin, firstEmail);
        assertThat(json(get("/api/v1/family/plan", firstParent), 200).get("choices").toString()).doesNotContain(planId);
        assertThat(post("/api/v1/family/plan/request", "{\"planId\":\"" + planId + "\"}", firstParent).statusCode()).isEqualTo(404);
        assertThat(json(post("/api/v1/admin/families/" + first + "/subscription", "{\"planId\":\"" + planId + "\"}", admin), 409)
                .get("detail").asString()).isEqualTo("This plan isn't offered now: today is outside its offer dates");
        assertThat(json(post("/api/v1/family/payments", "{\"planId\":\"" + planId + "\",\"method\":\"DEV_SIMULATOR\","
                + "\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", firstParent), 409).get("detail").asString())
                .isEqualTo("This plan isn't offered now: today is outside its offer dates");

        // Offered until a date: the family takes it up; the family's choices show it.
        json(put(availability, "{\"availableUntil\":\"" + now.plus(5, ChronoUnit.DAYS) + "\"}", admin), 200);
        assertThat(json(get("/api/v1/family/plan", firstParent), 200).get("choices").toString()).contains(planId);
        json(post("/api/v1/admin/families/" + first + "/subscription", "{\"planId\":\"" + planId + "\"}", admin), 200);

        // The offer ends: someone new is refused, the family already on it keeps it and can be given it again.
        JsonNode ended = json(put(availability, "{\"availableUntil\":\"" + now.minus(1, ChronoUnit.MINUTES) + "\"}", admin), 200);
        assertThat(ended.get("offeredNow").asBoolean()).isFalse();
        assertThat(ended.get("subscribers").asLong()).isEqualTo(1);
        String secondEmail = "offer-second-" + UUID.randomUUID() + "@example.com";
        registerParent(secondEmail);
        json(post("/api/v1/admin/families/" + familyOf(admin, secondEmail) + "/subscription", "{\"planId\":\"" + planId + "\"}", admin), 409);
        JsonNode stillOn = json(get("/api/v1/family/plan", firstParent), 200);
        assertThat(stillOn.get("plan").get("id").asString()).isEqualTo(planId);
        assertThat(stillOn.get("onDefaultPlan").asBoolean()).isFalse();
        json(post("/api/v1/admin/families/" + first + "/subscription", "{\"planId\":\"" + planId + "\"}", admin), 200);

        // A school plan offered later is not among a principal's choices, can't be started or invoiced.
        String schoolCode = "OFFER_SCHOOL_" + suffix();
        String schoolPlan = json(post("/api/v1/admin/plans", "{\"code\":\"" + schoolCode + "\",\"name\":\"Offer school\",\"audience\":\"SCHOOL\"}",
                admin), 201).get("plan").get("id").asString();
        json(put("/api/v1/admin/plans/" + schoolPlan, "{\"name\":\"Offer school\",\"selfService\":true,\"priceAmountMinor\":9000,"
                + "\"priceCurrency\":\"USD\",\"billingInterval\":\"YEAR\",\"limits\":{}}", admin), 200);
        json(post("/api/v1/admin/plans/" + schoolPlan + "/activate", "{}", admin), 200);
        json(put("/api/v1/admin/plans/" + schoolPlan + "/availability", "{\"availableFrom\":\"" + now.plus(3, ChronoUnit.DAYS) + "\"}", admin), 200);
        String principal = token(PlatformRole.PRINCIPAL);
        String schoolId = json(post("/api/v1/schools", "{\"name\":\"Offer School " + suffix() + "\"}", principal), 201).get("id").asString();
        assertThat(json(get("/api/v1/schools/" + schoolId + "/payment-plans", principal), 200).toString()).doesNotContain(schoolPlan);
        json(post("/api/v1/schools/" + schoolId + "/payments", "{\"planId\":\"" + schoolPlan + "\",\"method\":\"DEV_SIMULATOR\","
                + "\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", principal), 409);
        json(post("/api/v1/admin/schools/" + schoolId + "/invoices", "{\"planId\":\"" + schoolPlan + "\",\"idempotencyKey\":\""
                + UUID.randomUUID() + "\"}", admin), 409);
        json(put("/api/v1/admin/plans/" + schoolPlan + "/availability", "{}", admin), 200);
        assertThat(json(get("/api/v1/schools/" + schoolId + "/payment-plans", principal), 200).toString()).contains(schoolPlan);

        // Rules: an offer ends after it starts; the default plan has no dates and a dated plan can't become it; a retired
        // plan isn't offered; only an administrator sets dates.
        json(put(availability, "{\"availableFrom\":\"" + now.plus(2, ChronoUnit.DAYS) + "\",\"availableUntil\":\""
                + now.plus(1, ChronoUnit.DAYS) + "\"}", admin), 400);
        String familyDefault = null;
        for (JsonNode p : json(get("/api/v1/admin/plans", admin), 200)) {
            if (p.get("plan").get("audience").asString().equals("FAMILY") && p.get("plan").get("isDefault").asBoolean()) {
                familyDefault = p.get("plan").get("id").asString();
                assertThat(p.get("offeredNow").asBoolean()).isTrue();
            }
        }
        assertThat(json(put("/api/v1/admin/plans/" + familyDefault + "/availability", "{\"availableUntil\":\""
                + now.plus(9, ChronoUnit.DAYS) + "\"}", admin), 409).get("detail").asString())
                .isEqualTo("The default plan is always offered, so it can't have offer dates");
        assertThat(json(post("/api/v1/admin/plans/" + planId + "/make-default", "{}", admin), 409).get("detail").asString())
                .isEqualTo("The default plan is always offered: remove this plan's offer dates first");
        json(put(availability, "{}", admin), 200);
        json(post("/api/v1/admin/plans/" + planId + "/retire", "{}", admin), 200);
        json(put(availability, "{\"availableUntil\":\"" + now.plus(9, ChronoUnit.DAYS) + "\"}", admin), 409);
        assertThat(put(availability, "{}", firstParent).statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action = 'PLAN_AVAILABILITY_CHANGED' "
                + "and resource_id = ?::uuid", Integer.class, UUID.fromString(planId))).isEqualTo(4);
        // The database keeps the same rules even if a write bypassed the service.
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(
                "update subscription_plans set available_from = now(), available_until = now() - interval '1 day' where id = ?::uuid",
                UUID.fromString(planId))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    /** An administrator who has found a family lists that family's payments only. */
    @Test
    void anAdministratorListsOneFamilysPaymentsOnly() throws Exception {
        String admin = token(PlatformRole.ADMIN);
        String planId = json(post("/api/v1/admin/plans", "{\"code\":\"FAMPAY_" + suffix() + "\",\"name\":\"Family pay\"}", admin), 201)
                .get("plan").get("id").asString();
        json(put("/api/v1/admin/plans/" + planId, familyPlan("Family pay", 3, 3, 250).replace("\"selfService\":false",
                "\"selfService\":true"), admin), 200);
        json(post("/api/v1/admin/plans/" + planId + "/activate", "{}", admin), 200);
        String payingEmail = registerParentEmail();
        String paying = familyOf(admin, payingEmail);
        String other = familyOf(admin, registerParentEmail());
        String payingParent = json(post("/api/v1/auth/login", "{\"email\":\"" + payingEmail + "\",\"password\":\"" + PASSWORD + "\"}", null),
                200).get("accessToken").asString();
        JsonNode order = json(post("/api/v1/family/payments", "{\"planId\":\"" + planId + "\",\"method\":\"DEV_SIMULATOR\","
                + "\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", payingParent), 201);

        JsonNode theirs = json(get("/api/v1/admin/payments?familyId=" + paying, admin), 200);
        assertThat(theirs.get("total").asLong()).isEqualTo(1);
        assertThat(theirs.get("items").get(0).get("id").asString()).isEqualTo(order.get("id").asString());
        assertThat(json(get("/api/v1/admin/payments?familyId=" + paying + "&status=PENDING", admin), 200).get("total").asLong()).isEqualTo(1);
        assertThat(json(get("/api/v1/admin/payments?familyId=" + paying + "&status=SUCCEEDED", admin), 200).get("total").asLong()).isZero();
        assertThat(json(get("/api/v1/admin/payments?familyId=" + other, admin), 200).get("total").asLong()).isZero();
        assertThat(get("/api/v1/admin/payments?familyId=" + paying, payingParent).statusCode()).isEqualTo(403);
    }

    // ------------------------------------------------------------------ helpers

    private static String familyPlan(String name, int children, int journeys, long price) {
        return "{\"name\":\"" + name + "\",\"description\":\"For tests\",\"selfService\":false,\"priceAmountMinor\":" + price
                + ",\"priceCurrency\":\"USD\",\"billingInterval\":\"MONTH\","
                + "\"limits\":{\"CHILDREN_MAX\":" + children + ",\"JOURNEYS_PER_CHILD_MAX\":" + journeys + "}}";
    }

    private void grant(String admin, String familyId, String planId, String until) throws Exception {
        json(post("/api/v1/admin/families/" + familyId + "/subscription",
                "{\"planId\":\"" + planId + "\"" + (until == null ? "" : ",\"periodEnd\":\"" + until + "\"") + "}", admin), 200);
    }

    private String familyOf(String admin, String email) throws Exception {
        return json(get("/api/v1/admin/families?parentEmail=" + URLEncoder.encode(email, StandardCharsets.UTF_8), admin), 200)
                .get("familyId").asString();
    }

    private String registerParentEmail() throws Exception {
        String email = "summary-" + UUID.randomUUID() + "@example.com";
        registerParent(email);
        return email;
    }

    private static int limit(JsonNode plan, String key) {
        for (JsonNode e : plan.get("entitlements")) {
            if (e.get("key").asString().equals(key)) {
                return e.get("limit").asInt();
            }
        }
        throw new AssertionError("no " + key + " in " + plan);
    }

    private static JsonNode planIn(JsonNode plans, String planId) {
        for (JsonNode p : plans) {
            if (p.get("plan").get("id").asString().equals(planId)) {
                return p;
            }
        }
        throw new AssertionError("plan " + planId + " not listed");
    }

    private static JsonNode rowOf(JsonNode summary, String planId) {
        for (JsonNode p : summary.get("plans")) {
            if (p.get("planId").asString().equals(planId)) {
                return p;
            }
        }
        throw new AssertionError("plan " + planId + " not in the summary");
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String registerParent(String email) throws Exception {
        return json(post("/api/v1/auth/register", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\","
                + "\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null), 201).get("accessToken").asString();
    }

    private String token(PlatformRole role) throws Exception {
        String email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), role));
        String token = json(post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", null), 200)
                .get("accessToken").asString();
        return role == PlatformRole.ADMIN ? com.platform.TestAdmin.stepUp(port, token, PASSWORD) : token;
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
            com.platform.TestAdmin.withStepUp(request, bearer);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
