package com.platform.school;

import com.platform.TestcontainersConfiguration;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.repository.AccountRepository;
import com.platform.payment.repository.PaymentOrderRepository;
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
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Y44 over real HTTP against PostgreSQL: a school's services need a subscription backed by a confirmed payment.
 * Unpaid, pending, expired and refunded schools are refused at the backend (402) for their principal and teachers
 * and for families joining; billing stays open so the school can pay; administrators keep invoicing and
 * reconciling; strangers still get 403; a test payment wouldn't count where test payments are off; nothing the
 * school holds is deleted; and a family's own learning is never stopped by a school's payment.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class SchoolPaymentAccessIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "School-Pay-Test-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PaymentOrderRepository orders;
    @Autowired private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void anUnpaidSchoolKeepsItsBillingButNoServiceAndAPendingPaymentGrantsNothing() throws Exception {
        String admin = staff(PlatformRole.ADMIN).token();
        String principal = staff(PlatformRole.PRINCIPAL).token();
        String schoolId = createSchool(principal);
        String plan = schoolPlan(admin);

        // Unpaid: school services are refused - and say so - while billing stays open.
        JsonNode refused = json(post("/api/v1/schools/" + schoolId + "/classes", "{\"name\":\"Grade 1\"}", principal), 402);
        assertThat(refused.get("schoolPaymentRequired").asBoolean()).isTrue();
        assertThat(refused.get("schoolId").asString()).isEqualTo(schoolId);
        assertThat(get("/api/v1/schools/" + schoolId + "/staff", principal).statusCode()).isEqualTo(402);
        assertThat(get("/api/v1/schools/" + schoolId + "/learning", principal).statusCode()).isEqualTo(402);
        assertThat(json(get("/api/v1/schools/" + schoolId + "/plan", principal), 200).get("access").get("paid").asBoolean()).isFalse();
        assertThat(get("/api/v1/schools/" + schoolId + "/payments", principal).statusCode()).isEqualTo(200);
        // An arranged (not self-service) plan isn't offered to the school and can't be paid for by it (M4: only
        // self-service school plans are; other tests' may exist in this database).
        assertThat(json(get("/api/v1/schools/" + schoolId + "/payment-plans", principal), 200).valueStream()
                .map(p -> p.get("id").asString())).doesNotContain(plan);
        assertThat(post("/api/v1/schools/" + schoolId + "/payments", start(plan, "MANUAL", "s0"), principal).statusCode()).isEqualTo(400);
        // Someone else's school: still 403, never a hint about its payment.
        String stranger = staff(PlatformRole.PRINCIPAL).token();
        assertThat(post("/api/v1/schools/" + schoolId + "/classes", "{\"name\":\"X\"}", stranger).statusCode()).isEqualTo(403);
        assertThat(get("/api/v1/schools/" + schoolId + "/plan", stranger).statusCode()).isEqualTo(403);

        // An administrator invoices the school: pending, which grants nothing.
        JsonNode order = json(post("/api/v1/admin/schools/" + schoolId + "/invoices", "{\"planId\":\"" + plan
                + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", admin), 201);
        assertThat(order.get("amountMinor").asLong()).isEqualTo(12000);
        assertThat(order.get("status").asString()).isEqualTo("PENDING");
        JsonNode pending = json(get("/api/v1/schools/" + schoolId + "/plan", principal), 200).get("access");
        assertThat(pending.get("paid").asBoolean()).isFalse();
        assertThat(pending.get("paymentPending").asBoolean()).isTrue();
        assertThat(post("/api/v1/schools/" + schoolId + "/classes", "{\"name\":\"Grade 1\"}", principal).statusCode()).isEqualTo(402);
        // Neither the principal nor a forged provider notice can confirm it.
        assertThat(post("/api/v1/admin/payments/" + order.get("id").asString() + "/confirm", "{\"receiptReference\":\"R\"}", principal)
                .statusCode()).isEqualTo(403);
        assertThat(post("/api/v1/payments/callbacks/aba-payway", "{\"tran_id\":\"" + order.get("reference").asString() + "\",\"status\":0}",
                null).statusCode()).isEqualTo(200);
        assertThat(json(get("/api/v1/schools/" + schoolId + "/plan", principal), 200).get("access").get("paid").asBoolean()).isFalse();

        // An administrator confirms it against the real receipt: audited, and the school's services start.
        String orderId = order.get("id").asString();
        json(post("/api/v1/admin/payments/" + orderId + "/confirm", "{\"receiptReference\":\"BANK-S1\"}", admin), 200);
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action = 'PAYMENT_CONFIRMED' and resource_id = ?::uuid",
                Integer.class, orderId)).isEqualTo(1);
        JsonNode paid = json(get("/api/v1/schools/" + schoolId + "/plan", principal), 200).get("access");
        assertThat(paid.get("paid").asBoolean()).isTrue();
        Instant end = Instant.parse(paid.get("paidUntil").asString());
        assertThat(end).isBetween(Instant.now().plus(360, ChronoUnit.DAYS), Instant.now().plus(370, ChronoUnit.DAYS));
        assertThat(post("/api/v1/schools/" + schoolId + "/classes", "{\"name\":\"Grade 1\"}", principal).statusCode()).isEqualTo(201);

        // Confirmed twice: refused, and the paid year isn't doubled.
        assertThat(post("/api/v1/admin/payments/" + orderId + "/confirm", "{\"receiptReference\":\"BANK-S1\"}", admin).statusCode())
                .isEqualTo(409);
        assertThat(Instant.parse(json(get("/api/v1/schools/" + schoolId + "/plan", principal), 200).get("access").get("paidUntil")
                .asString())).isEqualTo(end);

        // The principal renews the same plan: it follows on from the paid end.
        assertThat(json(get("/api/v1/schools/" + schoolId + "/payment-plans", principal), 200).get(0).get("id").asString()).isEqualTo(plan);
        String renewal = json(post("/api/v1/schools/" + schoolId + "/payments", start(plan, "MANUAL", "s2"), principal), 201)
                .get("id").asString();
        JsonNode renewed = json(post("/api/v1/admin/payments/" + renewal + "/confirm", "{\"receiptReference\":\"BANK-S2\"}", admin), 200);
        assertThat(Instant.parse(renewed.get("periodStart").asString())).isEqualTo(end);
    }

    @Test
    void anExpiredOrRefundedSchoolStopsForItsStaffAndFamiliesButKeepsItsDataAndTheFamilysOwnLearning() throws Exception {
        String admin = staff(PlatformRole.ADMIN).token();
        String principal = staff(PlatformRole.PRINCIPAL).token();
        User teacher = staff(PlatformRole.TEACHER);
        String schoolId = createSchool(principal);
        String plan = schoolPlan(admin);
        String orderId = invoiceAndConfirm(admin, schoolId, plan);

        // A paid school: a class with a teacher, and a family's child in it.
        String classId = json(post("/api/v1/schools/" + schoolId + "/classes", "{\"name\":\"Grade 2\"}", principal), 201).get("id").asString();
        json(post("/api/v1/schools/" + schoolId + "/staff", "{\"email\":\"" + teacher.email() + "\"}", principal), 201);
        String invitation = json(get("/api/v1/schools/invitations/mine", teacher.token()), 200).get(0).get("id").asString();
        assertThat(post("/api/v1/schools/invitations/" + invitation + "/accept", "", teacher.token()).statusCode()).isLessThan(300);
        json(post("/api/v1/schools/classes/" + classId + "/teachers", "{\"teacherAccountId\":\"" + teacher.id() + "\"}", principal), 201);
        String code = json(post("/api/v1/schools/classes/" + classId + "/join-code", "", principal), 201).get("code").asString();
        String parent = registerParent();
        String childId = child(parent);
        assertThat(post("/api/v1/children/" + childId + "/schools/join", "{\"code\":\"" + code + "\"}", parent).statusCode()).isEqualTo(200);
        assertThat(get("/api/v1/schools/classes/" + classId + "/children", teacher.token()).statusCode()).isEqualTo(200);

        // The paid year runs out (moved into the past here): the school's services stop for everyone in it.
        jdbc.update("update payment_orders set period_start = period_start - interval '2 years', period_end = now() - interval '1 day' "
                + "where id = ?::uuid", orderId);
        jdbc.update("update school_subscriptions set current_period_end = now() - interval '1 day' where school_id = ?::uuid", schoolId);
        assertThat(get("/api/v1/schools/classes/" + classId + "/children", teacher.token()).statusCode()).isEqualTo(402);
        assertThat(get("/api/v1/schools/classes/" + classId + "/learning", principal).statusCode()).isEqualTo(402);
        assertThat(post("/api/v1/schools/classes/" + classId + "/join-code", "", principal).statusCode()).isEqualTo(402);
        String secondChild = child(parent);
        assertThat(post("/api/v1/children/" + secondChild + "/schools/join", "{\"code\":\"" + code + "\"}", parent).statusCode())
                .isEqualTo(402);
        // The teacher's home names the school instead of failing, and no class of it is read.
        JsonNode overview = json(get("/api/v1/schools/overview", teacher.token()), 200);
        assertThat(overview.get("classes").size()).isZero();
        assertThat(overview.get("unpaid").get(0).get("schoolId").asString()).isEqualTo(schoolId);
        // An invitation from an unpaid school can be declined, not accepted.
        User another = staff(PlatformRole.TEACHER);
        jdbc.update("insert into school_staff (id, school_id, account_id, status, invited_at) values (gen_random_uuid(), ?::uuid, ?::uuid, "
                + "'INVITED', now())", schoolId, another.id());
        String waiting = json(get("/api/v1/schools/invitations/mine", another.token()), 200).get(0).get("id").asString();
        assertThat(post("/api/v1/schools/invitations/" + waiting + "/accept", "", another.token()).statusCode()).isEqualTo(402);
        assertThat(post("/api/v1/schools/invitations/" + waiting + "/decline", "", another.token()).statusCode()).isLessThan(300);

        // Nothing is deleted, and the family's own learning goes on.
        assertThat(jdbc.queryForObject("select count(*) from class_enrollments where school_class_id = ?::uuid and status = 'ACTIVE'",
                Integer.class, classId)).isEqualTo(1);
        assertThat(get("/api/v1/children/" + childId + "/enrollments", parent).statusCode()).isEqualTo(200);
        assertThat(get("/api/v1/family/plan", parent).statusCode()).isEqualTo(200);
        assertThat(get("/api/v1/children/" + childId + "/schools", parent).statusCode()).isEqualTo(200);
        // Leaving the school is always the family's to do.
        assertThat(delete("/api/v1/children/" + childId + "/schools/" + schoolId, parent).statusCode()).isLessThan(300);

        // Administrators still reach the school to invoice it; paid again, its staff are back.
        assertThat(get("/api/v1/admin/schools/" + schoolId + "/subscription", admin).statusCode()).isEqualTo(200);
        String again = invoiceAndConfirm(admin, schoolId, plan);
        assertThat(get("/api/v1/schools/classes/" + classId + "/children", teacher.token()).statusCode()).isEqualTo(200);

        // Refunded: stopped again at once.
        json(post("/api/v1/admin/payments/" + again + "/refund", "{\"reason\":\"Refund agreed\"}", admin), 200);
        assertThat(get("/api/v1/schools/classes/" + classId + "/children", teacher.token()).statusCode()).isEqualTo(402);
    }

    @Test
    void aTestPaymentCountsOnlyWhereTestPaymentsAreSwitchedOn() throws Exception {
        String admin = staff(PlatformRole.ADMIN).token();
        String principal = staff(PlatformRole.PRINCIPAL).token();
        String schoolId = createSchool(principal);
        String plan = schoolPlan(admin);
        // Paid once, then refunded: the school is unpaid, and its principal may renew its plan.
        String first = invoiceAndConfirm(admin, schoolId, plan);
        json(post("/api/v1/admin/payments/" + first + "/refund", "{\"reason\":\"Refund agreed\"}", admin), 200);
        assertThat(post("/api/v1/schools/" + schoolId + "/classes", "{\"name\":\"Grade 3\"}", principal).statusCode()).isEqualTo(402);

        // Here (the test profile) test payments are on: a simulated success makes the school usable...
        String orderId = json(post("/api/v1/schools/" + schoolId + "/payments", start(plan, "DEV_SIMULATOR", "t1"), principal), 201)
                .get("id").asString();
        json(post("/api/v1/payments/" + orderId + "/simulate", "{\"outcome\":\"SUCCEEDED\"}", principal), 200);
        assertThat(post("/api/v1/schools/" + schoolId + "/classes", "{\"name\":\"Grade 3\"}", principal).statusCode()).isEqualTo(201);

        // ...but the same order is no payment where they are off, as in production (which refuses to start with them on).
        Instant now = Instant.now();
        assertThat(orders.schoolPaidAt(UUID.fromString(schoolId), now, true)).isTrue();
        assertThat(orders.schoolPaidAt(UUID.fromString(schoolId), now, false)).isFalse();
    }

    // ------------------------------------------------------------------ helpers

    private record User(String id, String email, String token) {
    }

    private String invoiceAndConfirm(String admin, String schoolId, String plan) throws Exception {
        String orderId = json(post("/api/v1/admin/schools/" + schoolId + "/invoices", "{\"planId\":\"" + plan + "\",\"idempotencyKey\":\""
                + UUID.randomUUID() + "\"}", admin), 201).get("id").asString();
        json(post("/api/v1/admin/payments/" + orderId + "/confirm", "{\"receiptReference\":\"BANK-" + suffix() + "\"}", admin), 200);
        return orderId;
    }

    private String schoolPlan(String admin) throws Exception {
        String id = json(post("/api/v1/admin/plans", "{\"code\":\"PAYSCHOOL_" + suffix() + "\",\"name\":\"School year\",\"audience\":\"SCHOOL\"}",
                admin), 201).get("plan").get("id").asString();
        json(put("/api/v1/admin/plans/" + id, "{\"name\":\"School year\",\"selfService\":false,\"priceAmountMinor\":12000,"
                + "\"priceCurrency\":\"USD\",\"billingInterval\":\"YEAR\",\"limits\":{}}", admin), 200);
        json(post("/api/v1/admin/plans/" + id + "/activate", "", admin), 200);
        return id;
    }

    private String createSchool(String principal) throws Exception {
        return json(post("/api/v1/schools", "{\"name\":\"Paid Primary " + suffix() + "\"}", principal), 201).get("id").asString();
    }

    private String child(String parent) throws Exception {
        return json(post("/api/v1/children", "{\"displayName\":\"Pay Child\",\"dateOfBirth\":\"" + LocalDate.now().minusYears(7) + "\"}",
                parent), 201).get("id").asString();
    }

    private static String start(String planId, String method, String key) {
        return "{\"planId\":\"" + planId + "\",\"method\":\"" + method + "\",\"idempotencyKey\":\"" + key + "-" + UUID.randomUUID() + "\"}";
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private User staff(PlatformRole role) throws Exception {
        String email = "spay-" + role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        Account account = accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), role));
        String token = json(post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", null), 200)
                .get("accessToken").asString();
        return new User(account.getId().toString(), email, token);
    }

    private String registerParent() throws Exception {
        String email = "spay-parent-" + UUID.randomUUID() + "@example.com";
        String token = json(post("/api/v1/auth/register", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null), 201).get("accessToken").asString();
        jdbc.update("update accounts set email_verified_at = now() where normalized_email = ?", email);
        return token;
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
