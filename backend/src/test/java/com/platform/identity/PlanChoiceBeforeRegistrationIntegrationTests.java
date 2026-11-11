package com.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.icegreen.greenmail.util.GreenMail;
import com.platform.TestcontainersConfiguration;
import com.platform.VerifiedRegistrationTestFilter;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.repository.AccountRepository;
import jakarta.mail.Message;
import jakarta.mail.internet.MimeMessage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

/**
 * Owner requirement 2026-11-11 over real HTTP, PostgreSQL and SMTP: a visitor chooses a plan before registering. The
 * choice is checked against the plan records and kept by the server through the email verification; a paid choice
 * waits on the new account and its plan starts only from a confirmed payment; the free plan needs no checkout; a school
 * plan registers a school's principal; a choice can be set aside for Free, and a failed payment leaves it to resume.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class PlanChoiceBeforeRegistrationIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Plan-Choice-Test-Pass-2026";
    private static final Pattern LINK = Pattern.compile("/register/continue#token=([A-Za-z0-9_-]+)");

    @Value("${local.server.port}") private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private GreenMail greenMail;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void aPaidPlanChosenFirstWaitsForItsPaymentAndStartsOnlyWhenPaid() throws Exception {
        String admin = admin();
        String code = "CHOSEN_" + suffix();
        String planId = plan(admin, code, "FAMILY", true, 700);

        String email = unique("paid");
        // A price or entitlement sent alongside is never read: only the code names the plan.
        assertThat(post("/api/v1/auth/registration/start", "{\"email\":\"" + email + "\",\"preferredLanguage\":\"en\",\"planCode\":\""
                + code + "\"}", null).statusCode()).isEqualTo(202);
        JsonNode verified = json(post("/api/v1/auth/registration/verify", "{\"token\":\"" + linkToken(email) + "\"}", null), 200);
        assertThat(verified.get("plan").get("code").asString()).isEqualTo(code);
        assertThat(verified.get("plan").get("amountMinor").asLong()).isEqualTo(700);
        assertThat(verified.get("plan").get("audience").asString()).isEqualTo("FAMILY");

        String parent = json(register(email, verified.get("registrationGrant").asString(), null), 201).get("accessToken").asString();
        JsonNode chosen = json(get("/api/v1/account/plan-choice", parent), 200);
        assertThat(chosen.get("id").asString()).isEqualTo(planId);
        assertThat(chosen.get("offered").asBoolean()).isTrue();
        // Registered, not paid: the family is on the free default plan.
        assertThat(json(get("/api/v1/family/plan", parent), 200).get("plan").get("id").asString()).isNotEqualTo(planId);

        // A failed payment leaves the choice to resume.
        String failed = json(post("/api/v1/family/payments", start(planId, "f1"), parent), 201).get("id").asString();
        json(post("/api/v1/payments/" + failed + "/simulate", "{\"outcome\":\"FAILED\"}", parent), 200);
        assertThat(get("/api/v1/account/plan-choice", parent).statusCode()).isEqualTo(200);
        assertThat(json(get("/api/v1/family/plan", parent), 200).get("plan").get("id").asString()).isNotEqualTo(planId);

        // Confirmed: the plan starts, and the choice is settled.
        String paid = json(post("/api/v1/family/payments", start(planId, "f2"), parent), 201).get("id").asString();
        json(post("/api/v1/payments/" + paid + "/simulate", "{\"outcome\":\"SUCCEEDED\"}", parent), 200);
        assertThat(json(get("/api/v1/family/plan", parent), 200).get("plan").get("id").asString()).isEqualTo(planId);
        assertThat(get("/api/v1/account/plan-choice", parent).statusCode()).isEqualTo(204);
    }

    @Test
    void theFreePlanNeedsNoCheckoutAndAPaidChoiceCanBeSetAsideForFree() throws Exception {
        JsonNode free = null;
        for (JsonNode plan : json(get("/api/v1/site/plans", null), 200)) {
            if (plan.get("audience").asString().equals("FAMILY") && plan.get("isDefault").asBoolean()) free = plan;
        }
        assertThat(free).isNotNull();
        assertThat(free.get("chooseable").asBoolean()).isTrue();
        String email = unique("free");
        String parent = registerWith(email, free.get("code").asString(), null);
        assertThat(get("/api/v1/account/plan-choice", parent).statusCode()).isEqualTo(204);
        assertThat(jdbc.queryForObject("select count(*) from plan_choices c join accounts a on a.id = c.account_id "
                + "where a.normalized_email = ?", Integer.class, email)).isZero();

        String code = "ASIDE_" + suffix();
        plan(admin(), code, "FAMILY", true, 300);
        String other = registerWith(unique("aside"), code, null);
        assertThat(get("/api/v1/account/plan-choice", other).statusCode()).isEqualTo(200);
        assertThat(delete("/api/v1/account/plan-choice", other).statusCode()).isEqualTo(204);
        assertThat(get("/api/v1/account/plan-choice", other).statusCode()).isEqualTo(204);
    }

    @Test
    void onlyAPlanOnOfferToChooseCanBeChosen() throws Exception {
        String admin = admin();
        String email = unique("refused");
        assertThat(post("/api/v1/auth/registration/start", startBody(email, "NO_SUCH_PLAN_" + suffix()), null).statusCode()).isEqualTo(400);
        // A plan families can't choose themselves (given by an administrator only) isn't offered here either.
        String closed = "CLOSED_" + suffix();
        plan(admin, closed, "FAMILY", false, 900);
        assertThat(post("/api/v1/auth/registration/start", startBody(email, closed), null).statusCode()).isEqualTo(400);
        Thread.sleep(300);
        assertThat(messagesTo(email)).as("nothing is sent for a refused choice").isZero();
        assertThat(jdbc.queryForObject("select count(*) from registration_verifications where normalized_email = ?",
                Integer.class, email)).isZero();
    }

    @Test
    void aSchoolPlanRegistersASchoolsPrincipalNotAParent() throws Exception {
        String code = "SCHOOLCHOICE_" + suffix();
        String planId = plan(admin(), code, "SCHOOL", true, 12000);
        String email = unique("school");
        post("/api/v1/auth/registration/start", startBody(email, code), null);
        JsonNode verified = json(post("/api/v1/auth/registration/verify", "{\"token\":\"" + linkToken(email) + "\"}", null), 200);
        String grant = verified.get("registrationGrant").asString();

        HttpResponse<String> asParent = register(email, grant, null);
        assertThat(asParent.statusCode()).isEqualTo(400);
        assertThat(asParent.body()).contains("The plan you chose is for schools");
        assertThat(jdbc.queryForObject("select count(*) from accounts where normalized_email = ?", Integer.class, email)).isZero();

        // The refusal used nothing up: the same grant registers the principal, who keeps the choice.
        String principal = json(register(email, grant, "PRINCIPAL"), 201).get("accessToken").asString();
        assertThat(json(get("/api/v1/account/plan-choice", principal), 200).get("id").asString()).isEqualTo(planId);
    }

    // ---------------------------------------------------------------------------------------------------------------

    private String registerWith(String email, String code, String role) throws Exception {
        assertThat(post("/api/v1/auth/registration/start", startBody(email, code), null).statusCode()).isEqualTo(202);
        String grant = json(post("/api/v1/auth/registration/verify", "{\"token\":\"" + linkToken(email) + "\"}", null), 200)
                .get("registrationGrant").asString();
        return json(register(email, grant, role), 201).get("accessToken").asString();
    }

    private String plan(String admin, String code, String audience, boolean selfService, long amountMinor) throws Exception {
        String id = json(post("/api/v1/admin/plans", "{\"code\":\"" + code + "\",\"name\":\"Chosen " + code + "\",\"audience\":\""
                + audience + "\"}", admin), 201).get("plan").get("id").asString();
        json(put("/api/v1/admin/plans/" + id, "{\"name\":\"Chosen " + code + "\",\"selfService\":" + selfService
                + ",\"priceAmountMinor\":" + amountMinor + ",\"priceCurrency\":\"USD\",\"billingInterval\":\""
                + (audience.equals("SCHOOL") ? "YEAR" : "MONTH") + "\",\"limits\":{}}", admin), 200);
        json(post("/api/v1/admin/plans/" + id + "/activate", "", admin), 200);
        return id;
    }

    private String admin() throws Exception {
        String email = "choice-admin-" + UUID.randomUUID() + "@example.com";
        accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), PlatformRole.ADMIN));
        String token = json(post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", null), 200)
                .get("accessToken").asString();
        return com.platform.TestAdmin.stepUp(port, token, PASSWORD);
    }

    private static String startBody(String email, String code) {
        return "{\"email\":\"" + email + "\",\"preferredLanguage\":\"en\",\"planCode\":\"" + code + "\"}";
    }

    private static String start(String planId, String key) {
        return "{\"planId\":\"" + planId + "\",\"method\":\"DEV_SIMULATOR\",\"idempotencyKey\":\"" + key + "\"}";
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private static String unique(String what) {
        return "choice-" + what + "-" + UUID.randomUUID() + "@example.com";
    }

    private HttpResponse<String> register(String email, String grant, String role) throws Exception {
        return post("/api/v1/auth/register", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\",\"registrationGrant\":\"" + grant + "\""
                + (role == null ? "" : ",\"role\":\"" + role + "\"") + "}", null);
    }

    private String linkToken(String email) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            for (MimeMessage message : greenMail.getReceivedMessages()) {
                if (message.getRecipients(Message.RecipientType.TO)[0].toString().equalsIgnoreCase(email)) {
                    Matcher link = LINK.matcher(String.valueOf(message.getContent()));
                    if (link.find()) return link.group(1);
                }
            }
            Thread.sleep(50);
        }
        throw new AssertionError("no verification link for " + email);
    }

    private long messagesTo(String email) throws Exception {
        long count = 0;
        for (MimeMessage message : greenMail.getReceivedMessages()) {
            if (message.getRecipients(Message.RecipientType.TO)[0].toString().equalsIgnoreCase(email)) count++;
        }
        return count;
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
        request.header(VerifiedRegistrationTestFilter.OPT_OUT_HEADER, "off");
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
