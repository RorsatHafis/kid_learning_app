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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M8 over real HTTP against PostgreSQL. Actions that move money or access need the password re-entered on this
 * sign-in (a step-up proof, five minutes): without it, with a wrong password, from another sign-in, or used as an access
 * token - refused. A transfer is confirmed only with the receipt's amount and currency equal to the order's, and one
 * receipt settles one payment, even when two confirmations quote it at the same moment.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class StepUpAndReconciliationIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Step-Up-Test-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void moneyAndAccessActionsNeedThePasswordAgainOnThisSignIn() throws Exception {
        String email = "stepup-admin-" + UUID.randomUUID() + "@example.com";
        Account admin = accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), PlatformRole.ADMIN));
        String laptop = login(email);
        String phone = login(email);
        // M9a: an administrator's step-up also needs an authenticator code.
        com.platform.TestAdmin.turnOnAuthenticator(port, laptop, PASSWORD);
        String plan = familyPlan(phone, stepUp(phone));
        String parentEmail = "stepup-parent-" + UUID.randomUUID() + "@example.com";
        json(send("POST", "/api/v1/auth/register", "{\"email\":\"" + parentEmail + "\",\"password\":\"" + PASSWORD
                + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null, null), 201);
        String familyId = jdbc.queryForObject("select m.family_id::text from memberships m join accounts a on a.id = m.account_id "
                + "where a.normalized_email = ?", String.class, parentEmail);
        String grant = "/api/v1/admin/families/" + familyId + "/subscription";

        // Without the password again: refused, and said so.
        HttpResponse<String> refused = send("POST", grant, "{\"planId\":\"" + plan + "\"}", laptop, null);
        assertThat(refused.statusCode()).isEqualTo(403);
        assertThat(JSON.readTree(refused.body()).get("stepUpRequired").asBoolean()).isTrue();

        // A wrong password gives nothing (and counts towards the lockout); the right one, a short proof.
        // (422: the existing answer to a wrong current password, as when changing it.)
        assertThat(send("POST", "/api/v1/account/step-up", "{\"password\":\"wrong-" + PASSWORD + "\"}", laptop, null).statusCode())
                .isEqualTo(422);
        String stepUp = stepUp(laptop);

        // The proof isn't an access token, and it belongs to the sign-in that made it.
        assertThat(send("GET", "/api/v1/account", null, stepUp, null).statusCode()).isEqualTo(401);
        assertThat(send("POST", grant, "{\"planId\":\"" + plan + "\"}", phone, stepUp).statusCode()).as("another sign-in").isEqualTo(403);
        json(send("POST", grant, "{\"planId\":\"" + plan + "\"}", laptop, stepUp), 200);
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action = 'ACCOUNT_STEPPED_UP' and actor_id = ?",
                Integer.class, admin.getId())).isEqualTo(2);
    }

    @Test
    void aTransferIsConfirmedOnlyForItsOwnAmountAndOneReceiptSettlesOnePayment() throws Exception {
        String email = "recon-admin-" + UUID.randomUUID() + "@example.com";
        accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), PlatformRole.ADMIN));
        String admin = login(email);
        com.platform.TestAdmin.turnOnAuthenticator(port, admin, PASSWORD);
        String stepUp = stepUp(admin);
        String plan = familyPlan(admin, stepUp);

        String first = transfer(plan);
        String receipt = "BANK-" + UUID.randomUUID();
        assertThat(confirm(admin, stepUp, first, receipt, 1999, "USD").statusCode()).as("less than the price").isEqualTo(409);
        assertThat(confirm(admin, stepUp, first, receipt, 2000, "KHR").statusCode()).as("another currency").isEqualTo(409);
        assertThat(JSON.readTree(confirm(admin, stepUp, first, receipt, 2001, "USD").body()).get("detail").asString())
                .contains("amount received");
        assertThat(json(confirm(admin, stepUp, first, receipt, 2000, "usd"), 200).get("status").asString()).isEqualTo("SUCCEEDED");

        // The same receipt for another family's payment: refused, case and spaces aside.
        String second = transfer(plan);
        assertThat(confirm(admin, stepUp, second, " " + receipt.toLowerCase() + " ", 2000, "USD").statusCode()).isEqualTo(409);

        // Two confirmations quoting one new receipt at the same moment: one wins.
        String third = transfer(plan);
        String shared = "BANK-" + UUID.randomUUID();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Integer>> results = new ArrayList<>();
            for (String order : List.of(second, third)) {
                results.add(pool.submit(() -> {
                    go.await();
                    return confirm(admin, stepUp, order, shared, 2000, "USD").statusCode();
                }));
            }
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("select count(*) from payment_orders where provider_reference = ? and status = 'SUCCEEDED'",
                Integer.class, shared)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ helpers

    private HttpResponse<String> confirm(String admin, String stepUp, String orderId, String receipt, long amount, String currency)
            throws Exception {
        return send("POST", "/api/v1/admin/payments/" + orderId + "/confirm", "{\"receiptReference\":\"" + receipt
                + "\",\"amountReceivedMinor\":" + amount + ",\"currency\":\"" + currency + "\"}", admin, stepUp);
    }

    /** A new family starts paying for the plan by bank transfer; returns the order. */
    private String transfer(String plan) throws Exception {
        String parent = registerParent();
        return json(send("POST", "/api/v1/family/payments", "{\"planId\":\"" + plan + "\",\"method\":\"MANUAL\",\"idempotencyKey\":\""
                + UUID.randomUUID() + "\"}", parent, null), 201).get("id").asString();
    }

    private String stepUp(String token) throws Exception {
        String accountId = json(send("GET", "/api/v1/account", null, token, null), 200).get("id").asString();
        return json(send("POST", "/api/v1/account/step-up", "{\"password\":\"" + PASSWORD + "\",\"code\":\""
                + com.platform.TestAdmin.code(accountId) + "\"}", token, null), 200).get("stepUpToken").asString();
    }

    private String familyPlan(String admin, String stepUp) throws Exception {
        String id = json(send("POST", "/api/v1/admin/plans", "{\"code\":\"STEPUP_" + UUID.randomUUID().toString().substring(0, 8)
                + "\",\"name\":\"Family Plus\"}", admin, null), 201).get("plan").get("id").asString();
        json(send("PUT", "/api/v1/admin/plans/" + id, "{\"name\":\"Family Plus\",\"selfService\":true,\"priceAmountMinor\":2000,"
                + "\"priceCurrency\":\"USD\",\"billingInterval\":\"MONTH\",\"limits\":{\"CHILDREN_MAX\":4}}", admin, null), 200);
        assertThat(send("POST", "/api/v1/admin/plans/" + id + "/activate", "{}", admin, null).statusCode()).as("activating needs a step-up")
                .isEqualTo(403);
        json(send("POST", "/api/v1/admin/plans/" + id + "/activate", "{}", admin, stepUp), 200);
        return id;
    }

    private String login(String email) throws Exception {
        return json(send("POST", "/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", null, null), 200)
                .get("accessToken").asString();
    }

    private String registerParent() throws Exception {
        return json(send("POST", "/api/v1/auth/register", "{\"email\":\"recon-" + UUID.randomUUID() + "@example.com\",\"password\":\""
                + PASSWORD + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null, null), 201).get("accessToken").asString();
    }

    private static JsonNode json(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> send(String method, String path, String body, String bearer, String stepUp) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        if (stepUp != null) {
            request.header("X-Step-Up", stepUp);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

}
