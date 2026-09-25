package com.platform.identity;

import com.platform.TestcontainersConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
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
 * The signed-in account managing itself, over real HTTP against PostgreSQL: reading
 * the profile, changing the password (current password required, same lockout as
 * login, every old session ended) and signing out everywhere.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class AccountSettingsIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Account-Test-Pass-2026";
    private static final String NEW_PASSWORD = "Account-Test-New-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void theAccountReadsItsOwnProfileAndNothingSecret() throws Exception {
        Session session = register();

        HttpResponse<String> res = get("/api/v1/account", session.accessToken());
        JsonNode me = json(res, 200);
        assertThat(me.get("email").asString()).isEqualTo(session.email());
        assertThat(me.get("platformRole").asString()).isEqualTo("PARENT");
        assertThat(me.get("status").asString()).isEqualTo("ACTIVE");
        assertThat(res.body()).doesNotContainIgnoringCase("password").doesNotContainIgnoringCase("hash");
        assertThat(get("/api/v1/account", null).statusCode()).isEqualTo(401);
    }

    @Test
    void changingThePasswordEndsEveryOldSessionAndKeepsThisOneSignedIn() throws Exception {
        Session first = register();
        Session second = login(first.email(), PASSWORD);

        JsonNode changed = json(post("/api/v1/account/password",
                "{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}", first.accessToken()), 200);

        // Both earlier sessions are over; the one just issued works.
        assertThat(refreshStatus(first.refreshToken())).isEqualTo(401);
        assertThat(refreshStatus(second.refreshToken())).isEqualTo(401);
        assertThat(refreshStatus(changed.get("refreshToken").asString())).isEqualTo(200);
        assertThat(get("/api/v1/account", changed.get("accessToken").asString()).statusCode()).isEqualTo(200);

        assertThat(loginStatus(first.email(), PASSWORD)).isEqualTo(401);
        assertThat(loginStatus(first.email(), NEW_PASSWORD)).isEqualTo(200);
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action = 'ACCOUNT_PASSWORD_CHANGED' and actor_id = ?::uuid",
                Integer.class, changed.get("accountId").asString())).isEqualTo(1);
    }

    @Test
    void aWrongCurrentPasswordIsRefusedCountsTowardsLockoutAndChangesNothing() throws Exception {
        Session session = register();
        String wrong = "{\"currentPassword\":\"not-the-password\",\"newPassword\":\"" + NEW_PASSWORD + "\"}";

        HttpResponse<String> first = post("/api/v1/account/password", wrong, session.accessToken());
        assertThat(first.statusCode()).isEqualTo(422);
        assertThat(JSON.readTree(first.body()).get("errors").get("currentPassword").asString()).isNotBlank();
        for (int attempt = 2; attempt <= 5; attempt++) {
            assertThat(post("/api/v1/account/password", wrong, session.accessToken()).statusCode()).isEqualTo(422);
        }
        // Five wrong confirmations lock the account, exactly like five wrong logins.
        assertThat(post("/api/v1/account/password",
                "{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}", session.accessToken())
                .statusCode()).isEqualTo(423);
        assertThat(loginStatus(session.email(), PASSWORD)).isEqualTo(423);
        // Nothing changed: the existing session still refreshes.
        assertThat(refreshStatus(session.refreshToken())).isEqualTo(200);
    }

    @Test
    void aWeakOrUnchangedNewPasswordIsRefused() throws Exception {
        Session session = register();

        assertThat(post("/api/v1/account/password",
                "{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"short\"}", session.accessToken()).statusCode())
                .isEqualTo(422);
        assertThat(post("/api/v1/account/password",
                "{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + PASSWORD + "\"}", session.accessToken()).statusCode())
                .isEqualTo(422);
        assertThat(post("/api/v1/account/password", "{\"currentPassword\":\"\",\"newPassword\":\"\"}", session.accessToken())
                .statusCode()).isEqualTo(400);
        assertThat(refreshStatus(session.refreshToken())).isEqualTo(200);
    }

    @Test
    void signingOutEverywhereEndsEverySessionOfThisAccountOnly() throws Exception {
        Session first = register();
        Session second = login(first.email(), PASSWORD);
        Session someoneElse = register();
        // A refresh rotates the first device's token: its chain now has two rows, but it
        // is still one signed-in device.
        String firstRotated = json(post("/api/v1/auth/refresh",
                "{\"refreshToken\":\"" + first.refreshToken() + "\"}", null), 200).get("refreshToken").asString();

        JsonNode result = json(post("/api/v1/account/sign-out-everywhere", "", first.accessToken()), 200);

        assertThat(result.get("sessionsEnded").asInt()).isEqualTo(2);
        assertThat(refreshStatus(firstRotated)).isEqualTo(401);
        assertThat(refreshStatus(second.refreshToken())).isEqualTo(401);
        assertThat(refreshStatus(someoneElse.refreshToken())).isEqualTo(200);
        assertThat(post("/api/v1/account/sign-out-everywhere", "", null).statusCode()).isEqualTo(401);
    }

    // ------------------------------------------------------------------ HTTP

    private record Session(String email, String accessToken, String refreshToken) {
    }

    private Session register() throws Exception {
        String email = "account-" + UUID.randomUUID() + "@example.com";
        JsonNode body = json(post("/api/v1/auth/register", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null), 201);
        return new Session(email, body.get("accessToken").asString(), body.get("refreshToken").asString());
    }

    private Session login(String email, String password) throws Exception {
        JsonNode body = json(post("/api/v1/auth/login",
                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}", null), 200);
        return new Session(email, body.get("accessToken").asString(), body.get("refreshToken").asString());
    }

    private int loginStatus(String email, String password) throws Exception {
        return post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}", null).statusCode();
    }

    private int refreshStatus(String refreshToken) throws Exception {
        return post("/api/v1/auth/refresh", "{\"refreshToken\":\"" + refreshToken + "\"}", null).statusCode();
    }

    private static JsonNode json(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> get(String path, String bearer) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (bearer != null) {
            req.header("Authorization", "Bearer " + bearer);
        }
        return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body, String bearer) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (bearer != null) {
            req.header("Authorization", "Bearer " + bearer);
        }
        return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

}
