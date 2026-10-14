package com.platform.security;

import com.platform.TestcontainersConfiguration;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.repository.AccountRepository;
import com.platform.security.session.AccountSessionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OD-01 over real HTTP: the unauthenticated identity endpoints refuse a client that
 * exceeds its budget with 429 + Retry-After, readable by the browser (CORS), and a
 * refused refresh leaves the refresh token usable.
 *
 * <p>Each test uses its own endpoint budget, so they do not interfere although all
 * requests come from 127.0.0.1. {@code @DirtiesContext} is unnecessary: the budgets
 * here differ from every other test's context, so this context is never shared.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "platform.security.rate-limit.login-per-minute=5",
        "platform.security.rate-limit.register-per-hour=3",
        "platform.security.rate-limit.refresh-per-minute=60",
        "platform.security.rate-limit.recovery-per-hour=3"})
@Tag("integration")
class AuthRateLimitIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String ORIGIN = "http://localhost:5173";

    @Value("${local.server.port}") private int port;
    @Autowired private AccountRepository accountRepository;
    @Autowired private AccountSessionService accountSessionService;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void loginAttemptsBeyondTheBudgetAreRefusedWith429AndRetryAfter() throws Exception {
        String body = "{\"email\":\"nobody-" + UUID.randomUUID() + "@example.com\",\"password\":\"whatever-it-is\"}";
        for (int i = 0; i < 4; i++) {
            assertThat(post("/api/v1/auth/login", body).statusCode()).isEqualTo(401);
        }
        // The same endpoint through an encoded path is the same budget.
        assertThat(post("/api/v1/auth/%6Cogin", body).statusCode()).isEqualTo(401);

        HttpResponse<String> refused = post("/api/v1/auth/login", body);

        assertThat(refused.statusCode()).isEqualTo(429);
        long retryAfter = Long.parseLong(refused.headers().firstValue("Retry-After").orElseThrow());
        assertThat(retryAfter).isBetween(1L, 60L);
        assertThat(refused.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/problem+json");
        JsonNode problem = JSON.readTree(refused.body());
        assertThat(problem.get("status").asInt()).isEqualTo(429);
        assertThat(problem.get("retryAfterSeconds").asLong()).isEqualTo(retryAfter);
        // A browser on the allowed origin can read it.
        assertThat(refused.headers().firstValue("Access-Control-Allow-Origin")).contains(ORIGIN);
    }

    @Test
    void registrationBeyondTheBudgetIsRefusedAndCreatesNoAccount() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(register().statusCode()).isEqualTo(201);
        }
        HttpResponse<String> refused = register();

        assertThat(refused.statusCode()).isEqualTo(429);
        assertThat(refused.body()).doesNotContain("accessToken");
    }

    @Test
    void passwordResetRequestsBeyondTheBudgetAreRefusedWhateverTheAddress() throws Exception {
        // Different addresses each time: the budget is the network's, not the account's.
        for (int i = 0; i < 3; i++) {
            assertThat(post("/api/v1/auth/password-reset",
                    "{\"email\":\"reset-" + UUID.randomUUID() + "@example.com\"}").statusCode()).isEqualTo(202);
        }
        HttpResponse<String> refused = post("/api/v1/auth/password-reset",
                "{\"email\":\"reset-" + UUID.randomUUID() + "@example.com\"}");

        assertThat(refused.statusCode()).isEqualTo(429);
        assertThat(Long.parseLong(refused.headers().firstValue("Retry-After").orElseThrow())).isGreaterThan(60);
        assertThat(JSON.readTree(refused.body()).get("detail").asString())
                .isEqualTo("Too many requests from this network. Please wait and try again.");

        // Said in the request's language, like every other answer.
        HttpResponse<String> khmer = http.send(HttpRequest.newBuilder(uri("/api/v1/auth/password-reset"))
                .header("Content-Type", "application/json").header("Accept-Language", "km-KH,km;q=0.9")
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"reset-" + UUID.randomUUID() + "@example.com\"}")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(khmer.statusCode()).isEqualTo(429);
        assertThat(JSON.readTree(khmer.body()).get("detail").asString())
                .isEqualTo("មានសំណើច្រើនពេកពីបណ្ដាញនេះ។ សូមរង់ចាំបន្តិច ហើយព្យាយាមម្ដងទៀត។");
    }

    @Test
    void aRefusedRefreshDoesNotConsumeTheRefreshToken() throws Exception {
        // Created directly: the registration and login budgets belong to the other tests.
        Account account = accountRepository.saveAndFlush(Account.register(
                "limit-refresh-" + UUID.randomUUID() + "@example.com", "{noop}unused", PlatformRole.PARENT));
        String refreshToken = accountSessionService.issueSession(account.getId(), null, null).rawToken();

        // Spend the refresh budget with tokens that do not exist.
        int status;
        do {
            status = post("/api/v1/auth/refresh", "{\"refreshToken\":\"" + UUID.randomUUID() + "\"}").statusCode();
        } while (status != 429);
        assertThat(post("/api/v1/auth/refresh", "{\"refreshToken\":\"" + refreshToken + "\"}").statusCode()).isEqualTo(429);

        // 60 per minute: a token is back within a second or so, and the real refresh
        // token was never rotated by the refused call.
        Thread.sleep(1_200);
        assertThat(post("/api/v1/auth/refresh", "{\"refreshToken\":\"" + refreshToken + "\"}").statusCode()).isEqualTo(200);
    }

    @Test
    void authenticatedEndpointsAndOtherMethodsAreNotLimitedHere() throws Exception {
        for (int i = 0; i < 20; i++) {
            HttpResponse<String> res = http.send(HttpRequest.newBuilder(uri("/api/v1/account")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(res.statusCode()).isEqualTo(401);
        }
    }

    // ------------------------------------------------------------------ HTTP

    private HttpResponse<String> register() throws Exception {
        return post("/api/v1/auth/register", "{\"email\":\"limit-" + UUID.randomUUID()
                + "@example.com\",\"password\":\"Rate-Limit-Pass-2026\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}");
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return http.send(HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .header("Origin", ORIGIN)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

}
