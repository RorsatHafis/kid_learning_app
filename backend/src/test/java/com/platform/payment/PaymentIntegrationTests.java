package com.platform.payment;

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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Y43 over real HTTP against PostgreSQL: the payment lifecycle. A family pays for an eligible, priced plan at
 * the plan's own price; nothing pending gives access; only a confirmed payment does, once, however often the
 * confirmation repeats; failures, cancellations and forged notices leave the plan as it was; a transfer is
 * confirmed only by an administrator, audited; a refund takes back exactly what was paid; the ledger can't
 * be rewritten; and a school is invoiced by an administrator, its principal seeing the invoice.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class PaymentIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Payment-Test-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void aFamilyPaysAtThePlansOwnPriceAndOnlyAConfirmedPaymentGivesThePlanOnce() throws Exception {
        String admin = staff(PlatformRole.ADMIN);
        String planId = pricedFamilyPlan(admin, 500, "USD");
        String parent = registerParent();

        // A request can't name a price: the order carries the plan's, frozen.
        JsonNode order = json(post("/api/v1/family/payments", start(planId, "DEV_SIMULATOR", "k1")
                .replace("}", ",\"amountMinor\":1,\"status\":\"SUCCEEDED\"}"), parent), 201);
        assertThat(order.get("amountMinor").asLong()).isEqualTo(500);
        assertThat(order.get("currency").asString()).isEqualTo("USD");
        assertThat(order.get("status").asString()).isEqualTo("PENDING");
        assertThat(order.get("checkout").get("instructions").asString()).startsWith("TEST PAYMENT");
        String orderId = order.get("id").asString();

        // The same request again is the same order; another checkout can't start alongside it.
        assertThat(json(post("/api/v1/family/payments", start(planId, "DEV_SIMULATOR", "k1"), parent), 201).get("id").asString())
                .isEqualTo(orderId);
        assertThat(post("/api/v1/family/payments", start(planId, "DEV_SIMULATOR", "k2"), parent).statusCode()).isEqualTo(409);

        // Pending gives nothing.
        assertThat(json(get("/api/v1/family/plan", parent), 200).get("plan").get("id").asString()).isNotEqualTo(planId);

        // Confirmed: the plan, for one month.
        JsonNode paid = json(post("/api/v1/payments/" + orderId + "/simulate", "{\"outcome\":\"SUCCEEDED\"}", parent), 200);
        assertThat(paid.get("status").asString()).isEqualTo("SUCCEEDED");
        JsonNode plan = json(get("/api/v1/family/plan", parent), 200);
        assertThat(plan.get("plan").get("id").asString()).isEqualTo(planId);
        Instant end = Instant.parse(paid.get("periodEnd").asString());
        assertThat(end).isBetween(Instant.now().plus(27, ChronoUnit.DAYS), Instant.now().plus(32, ChronoUnit.DAYS));

        // The same confirmation again changes nothing: no second month, no second grant.
        json(post("/api/v1/payments/" + orderId + "/simulate", "{\"outcome\":\"SUCCEEDED\"}", parent), 200);
        assertThat(jdbc.queryForObject("select current_period_end from family_subscriptions where provider_reference = ?",
                java.sql.Timestamp.class, paid.get("reference").asString()).toInstant()).isEqualTo(end);
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action = 'SUBSCRIPTION_PAID' and resource_id = ?::uuid",
                Integer.class, orderId)).isEqualTo(1);

        // Someone else can't see it; nobody can rewrite the ledger.
        assertThat(get("/api/v1/payments/" + orderId, registerParent()).statusCode()).isEqualTo(403);
        assertThatThrownBy(() -> jdbc.update("update payment_events set event_type = 'X' where order_id = ?::uuid", orderId))
                .hasMessageContaining("append-only");

        // A renewal extends the paid month rather than restarting it.
        String renewal = json(post("/api/v1/family/payments", start(planId, "DEV_SIMULATOR", "k3"), parent), 201).get("id").asString();
        JsonNode renewed = json(post("/api/v1/payments/" + renewal + "/simulate", "{\"outcome\":\"SUCCEEDED\"}", parent), 200);
        assertThat(Instant.parse(renewed.get("periodStart").asString())).isEqualTo(end);
    }

    @Test
    void failedCancelledAndForgedPaymentsLeaveThePlanAsItWas() throws Exception {
        String admin = staff(PlatformRole.ADMIN);
        String planId = pricedFamilyPlan(admin, 900, "USD");
        String parent = registerParent();
        String defaultPlan = json(get("/api/v1/family/plan", parent), 200).get("plan").get("id").asString();

        String failed = json(post("/api/v1/family/payments", start(planId, "DEV_SIMULATOR", "f1"), parent), 201).get("id").asString();
        assertThat(json(post("/api/v1/payments/" + failed + "/simulate", "{\"outcome\":\"FAILED\"}", parent), 200)
                .get("status").asString()).isEqualTo("FAILED");
        // A failed attempt can be retried with a new one.
        String cancelled = json(post("/api/v1/family/payments", start(planId, "DEV_SIMULATOR", "f2"), parent), 201).get("id").asString();
        assertThat(json(post("/api/v1/payments/" + cancelled + "/cancel", "", parent), 200).get("status").asString()).isEqualTo("CANCELLED");
        // A success notice for a cancelled test payment still counts (money confirmed late is still money)...
        // ...but a forged PayWay notice for any order changes nothing: we only ever ask PayWay itself.
        String reference = json(get("/api/v1/payments/" + cancelled, parent), 200).get("reference").asString();
        HttpResponse<String> forged = send(HttpRequest.newBuilder(uri("/api/v1/payments/callbacks/aba-payway"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"tran_id\":\"" + reference + "\",\"status\":0}")), null);
        assertThat(forged.statusCode()).isEqualTo(200);
        assertThat(json(get("/api/v1/payments/" + cancelled, parent), 200).get("status").asString()).isEqualTo("CANCELLED");
        assertThat(json(get("/api/v1/family/plan", parent), 200).get("plan").get("id").asString()).isEqualTo(defaultPlan);

        // An unpriced plan can't be bought; a school plan isn't a family's.
        String unpriced = json(post("/api/v1/admin/plans", "{\"code\":\"UNPRICED_" + suffix() + "\",\"name\":\"Unpriced\"}", admin), 201)
                .get("plan").get("id").asString();
        json(put("/api/v1/admin/plans/" + unpriced, "{\"name\":\"Unpriced\",\"selfService\":true,\"limits\":{}}", admin), 200);
        json(post("/api/v1/admin/plans/" + unpriced + "/activate", "", admin), 200);
        assertThat(post("/api/v1/family/payments", start(unpriced, "DEV_SIMULATOR", "f3"), parent).body()).contains("has no price yet");
        String school = pricedSchoolPlan(admin, 10000, "USD");
        assertThat(post("/api/v1/family/payments", start(school, "DEV_SIMULATOR", "f4"), parent).statusCode()).isEqualTo(400);
    }

    @Test
    void aTransferIsConfirmedOnlyByAnAdministratorAuditedAndARefundTakesBackExactlyWhatWasPaid() throws Exception {
        String admin = staff(PlatformRole.ADMIN);
        String planId = pricedFamilyPlan(admin, 1200, "USD");
        String parent = registerParent();

        JsonNode order = json(post("/api/v1/family/payments", start(planId, "MANUAL", "m1"), parent), 201);
        assertThat(order.get("checkout").get("instructions").asString()).contains("TEST INSTRUCTIONS");
        String orderId = order.get("id").asString();
        json(post("/api/v1/payments/" + orderId + "/note", "{\"note\":\"Transfer 4411\"}", parent), 200);

        // Neither the parent nor a test outcome can confirm a transfer.
        assertThat(post("/api/v1/admin/payments/" + orderId + "/confirm", "{\"receiptReference\":\"R-1\"}", parent).statusCode())
                .isEqualTo(403);
        assertThat(post("/api/v1/payments/" + orderId + "/simulate", "{\"outcome\":\"SUCCEEDED\"}", parent).statusCode()).isEqualTo(400);
        assertThat(post("/api/v1/admin/payments/" + orderId + "/confirm", "{\"receiptReference\":\" \"}", admin).statusCode()).isEqualTo(400);

        JsonNode confirmed = json(post("/api/v1/admin/payments/" + orderId + "/confirm",
                "{\"receiptReference\":\"BANK-REF-77\",\"note\":\"Seen on the statement\"}", admin), 200);
        assertThat(confirmed.get("status").asString()).isEqualTo("SUCCEEDED");
        assertThat(confirmed.get("admin").get("ledger").toString()).contains("MANUAL_CONFIRMED").contains("CUSTOMER_NOTE");
        // Each line - including the one just written - carries its time.
        confirmed.get("admin").get("ledger").forEach(line -> assertThat(line.get("at").isNull()).isFalse());
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action = 'PAYMENT_CONFIRMED' and resource_id = ?::uuid",
                Integer.class, orderId)).isEqualTo(1);
        assertThat(json(get("/api/v1/family/plan", parent), 200).get("plan").get("id").asString()).isEqualTo(planId);

        // Confirmed twice: refused, nothing doubled.
        assertThat(post("/api/v1/admin/payments/" + orderId + "/confirm", "{\"receiptReference\":\"BANK-REF-77\"}", admin).statusCode())
                .isEqualTo(409);

        // Refunded: the paid month is taken back - here all of it, so the plan ends now.
        assertThat(post("/api/v1/admin/payments/" + orderId + "/refund", "{\"reason\":\" \"}", admin).statusCode()).isEqualTo(400);
        JsonNode refunded = json(post("/api/v1/admin/payments/" + orderId + "/refund", "{\"reason\":\"Asked within a week\"}", admin), 200);
        assertThat(refunded.get("status").asString()).isEqualTo("REFUNDED");
        assertThat(json(get("/api/v1/family/plan", parent), 200).get("plan").get("id").asString()).isNotEqualTo(planId);
        assertThat(jdbc.queryForObject("select status from family_subscriptions where provider_reference = ?", String.class,
                refunded.get("reference").asString())).isEqualTo("EXPIRED");
    }

    @Test
    void aSchoolIsInvoicedByAnAdministratorAndItsPrincipalSeesTheInvoice() throws Exception {
        String admin = staff(PlatformRole.ADMIN);
        String principal = staff(PlatformRole.PRINCIPAL);
        String schoolId = json(post("/api/v1/schools", "{\"name\":\"Invoice Primary " + suffix() + "\"}", principal), 201)
                .get("id").asString();
        String planId = pricedSchoolPlan(admin, 25000, "USD");

        assertThat(post("/api/v1/admin/schools/" + schoolId + "/invoices", "{\"planId\":\"" + planId + "\",\"idempotencyKey\":\"i1\"}",
                principal).statusCode()).isEqualTo(403);
        String orderId = json(post("/api/v1/admin/schools/" + schoolId + "/invoices",
                "{\"planId\":\"" + planId + "\",\"idempotencyKey\":\"i1\"}", admin), 201).get("id").asString();
        JsonNode invoices = json(get("/api/v1/schools/" + schoolId + "/payments", principal), 200);
        assertThat(invoices.get(0).get("amountMinor").asLong()).isEqualTo(25000);
        assertThat(invoices.get(0).has("admin") && !invoices.get(0).get("admin").isNull()).isFalse();
        assertThat(get("/api/v1/schools/" + schoolId + "/payments", staff(PlatformRole.PRINCIPAL)).statusCode()).isEqualTo(403);

        json(post("/api/v1/admin/payments/" + orderId + "/confirm", "{\"receiptReference\":\"INV-1\"}", admin), 200);
        assertThat(json(get("/api/v1/schools/" + schoolId + "/plan", principal), 200).get("plan").get("id").asString()).isEqualTo(planId);
    }

    // ------------------------------------------------------------------ plans and accounts

    private String pricedFamilyPlan(String admin, long amountMinor, String currency) throws Exception {
        String id = json(post("/api/v1/admin/plans", "{\"code\":\"PAID_" + suffix() + "\",\"name\":\"Paid family\"}", admin), 201)
                .get("plan").get("id").asString();
        json(put("/api/v1/admin/plans/" + id, "{\"name\":\"Paid family\",\"selfService\":true,\"priceAmountMinor\":" + amountMinor
                + ",\"priceCurrency\":\"" + currency + "\",\"billingInterval\":\"MONTH\",\"limits\":{\"CHILDREN_MAX\":5}}", admin), 200);
        json(post("/api/v1/admin/plans/" + id + "/activate", "", admin), 200);
        return id;
    }

    private String pricedSchoolPlan(String admin, long amountMinor, String currency) throws Exception {
        String id = json(post("/api/v1/admin/plans", "{\"code\":\"SCHOOL_" + suffix() + "\",\"name\":\"Paid school\",\"audience\":\"SCHOOL\"}",
                admin), 201).get("plan").get("id").asString();
        json(put("/api/v1/admin/plans/" + id, "{\"name\":\"Paid school\",\"selfService\":false,\"priceAmountMinor\":" + amountMinor
                + ",\"priceCurrency\":\"" + currency + "\",\"billingInterval\":\"YEAR\",\"limits\":{}}", admin), 200);
        json(post("/api/v1/admin/plans/" + id + "/activate", "", admin), 200);
        return id;
    }

    private static String start(String planId, String method, String key) {
        return "{\"planId\":\"" + planId + "\",\"method\":\"" + method + "\",\"idempotencyKey\":\"" + key + "\"}";
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String staff(PlatformRole role) throws Exception {
        String email = "pay-" + role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        Account account = accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), role));
        return json(post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", null), 200)
                .get("accessToken").asString();
    }

    private String registerParent() throws Exception {
        return json(post("/api/v1/auth/register", "{\"email\":\"pay-parent-" + UUID.randomUUID() + "@example.com\",\"password\":\""
                + PASSWORD + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null), 201).get("accessToken").asString();
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
