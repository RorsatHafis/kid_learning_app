package com.platform.admin;

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
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Operating the platform: suspending and reinstating accounts, and reading the audit log.
 * Administrators only; suspension ends every session at once and is itself audited;
 * administrators and pending accounts can't be suspended; the log pages without gaps.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class AdminOperationsIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Admin-Ops-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void aSuspendedAccountIsSignedOutEverywhereAndCanBeReinstated() throws Exception {
        User admin = user(PlatformRole.ADMIN);
        User parent = user(PlatformRole.PARENT);
        User secondDevice = login(parent.email());

        JsonNode found = json(get("/api/v1/admin/accounts?email=" + enc(parent.email().toUpperCase()), admin), 200);
        assertThat(found.get("id").asString()).isEqualTo(parent.id());

        JsonNode suspended = json(post("/api/v1/admin/accounts/" + parent.id() + "/suspend",
                "{\"reason\":\"Reported compromised by the account holder\"}", admin), 200);
        assertThat(suspended.get("status").asString()).isEqualTo("DISABLED");
        assertThat(refreshStatus(parent.refreshToken())).isEqualTo(401);
        assertThat(refreshStatus(secondDevice.refreshToken())).isEqualTo(401);
        assertThat(loginStatus(parent.email())).isEqualTo(401);
        // Suspending twice is refused, not repeated.
        assertThat(post("/api/v1/admin/accounts/" + parent.id() + "/suspend", "{\"reason\":\"again\"}", admin).statusCode()).isEqualTo(409);

        json(post("/api/v1/admin/accounts/" + parent.id() + "/reinstate", "{\"reason\":\"Password reset done\"}", admin), 200);
        assertThat(loginStatus(parent.email())).isEqualTo(200);
        assertThat(post("/api/v1/admin/accounts/" + parent.id() + "/reinstate", "{\"reason\":\"again\"}", admin).statusCode()).isEqualTo(409);

        JsonNode log = json(get("/api/v1/admin/audit-events?resourceId=" + parent.id(), admin), 200);
        Set<String> actions = new HashSet<>();
        log.get("entries").forEach(e -> actions.add(e.get("action").asString()));
        assertThat(actions).contains("ACCOUNT_SUSPENDED", "ACCOUNT_REINSTATED");
        JsonNode suspension = null;
        for (JsonNode e : log.get("entries")) {
            if (e.get("action").asString().equals("ACCOUNT_SUSPENDED")) {
                suspension = e;
            }
        }
        assertThat(suspension.get("actorEmail").asString()).isEqualTo(admin.email());
        assertThat(suspension.get("metadata").get("reason").asString()).isEqualTo("Reported compromised by the account holder");
        assertThat(suspension.get("metadata").get("sessionsEnded").asInt()).isEqualTo(2);
    }

    @Test
    void administratorsPendingAccountsAndMissingReasonsAreRefused() throws Exception {
        User admin = user(PlatformRole.ADMIN);
        User otherAdmin = user(PlatformRole.ADMIN);
        String pendingPrincipal = "pending-" + UUID.randomUUID() + "@example.com";
        post("/api/v1/auth/register", "{\"email\":\"" + pendingPrincipal + "\",\"password\":\"" + PASSWORD
                + "\",\"role\":\"PRINCIPAL\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null);
        String pendingId = json(get("/api/v1/admin/accounts?email=" + enc(pendingPrincipal), admin), 200).get("id").asString();
        User teacher = user(PlatformRole.TEACHER);

        assertThat(post("/api/v1/admin/accounts/" + otherAdmin.id() + "/suspend", "{\"reason\":\"x\"}", admin).statusCode()).isEqualTo(409);
        assertThat(post("/api/v1/admin/accounts/" + admin.id() + "/suspend", "{\"reason\":\"x\"}", admin).statusCode()).isEqualTo(409);
        // A principal awaiting approval can't be suspended, so a reinstatement can never approve them.
        assertThat(post("/api/v1/admin/accounts/" + pendingId + "/suspend", "{\"reason\":\"x\"}", admin).statusCode()).isEqualTo(409);
        assertThat(post("/api/v1/admin/accounts/" + teacher.id() + "/suspend", "{\"reason\":\"  \"}", admin).statusCode()).isEqualTo(400);
        assertThat(post("/api/v1/admin/accounts/" + UUID.randomUUID() + "/suspend", "{\"reason\":\"x\"}", admin).statusCode()).isEqualTo(404);
        assertThat(get("/api/v1/admin/accounts?email=" + enc("nobody-" + UUID.randomUUID() + "@example.com"), admin).statusCode()).isEqualTo(404);
        assertThat(get("/api/v1/admin/accounts?email=not-an-email", admin).statusCode()).isEqualTo(400);
        assertThat(loginStatus(teacher.email())).isEqualTo(200);
    }

    @Test
    void onlyAdministratorsOperateThePlatform() throws Exception {
        User teacher = user(PlatformRole.TEACHER);
        for (PlatformRole role : new PlatformRole[] {PlatformRole.PARENT, PlatformRole.TEACHER, PlatformRole.PRINCIPAL}) {
            User user = user(role);
            assertThat(post("/api/v1/admin/accounts/" + teacher.id() + "/suspend", "{\"reason\":\"x\"}", user).statusCode()).as(role.name()).isEqualTo(403);
            assertThat(get("/api/v1/admin/audit-events", user).statusCode()).as(role.name()).isEqualTo(403);
            assertThat(get("/api/v1/admin/accounts?email=" + enc(teacher.email()), user).statusCode()).as(role.name()).isEqualTo(403);
        }
        assertThat(get("/api/v1/admin/audit-events", null).statusCode()).isEqualTo(401);
        assertThat(loginStatus(teacher.email())).isEqualTo(200);
    }

    @Test
    void theAuditLogPagesNewestFirstWithoutGapsOrRepeats() throws Exception {
        User admin = user(PlatformRole.ADMIN);
        User target = user(PlatformRole.TEACHER);
        for (int i = 0; i < 3; i++) {
            post("/api/v1/admin/accounts/" + target.id() + "/suspend", "{\"reason\":\"round " + i + "\"}", admin);
            post("/api/v1/admin/accounts/" + target.id() + "/reinstate", "{\"reason\":\"round " + i + "\"}", admin);
        }

        Set<String> seen = new HashSet<>();
        Instant previous = null;
        String query = "/api/v1/admin/audit-events?resourceId=" + target.id() + "&limit=2";
        String next = query;
        int pages = 0;
        while (next != null) {
            JsonNode page = json(get(next, admin), 200);
            for (JsonNode entry : page.get("entries")) {
                assertThat(seen.add(entry.get("id").asString())).as("no repeats").isTrue();
                Instant at = Instant.parse(entry.get("occurredAt").asString());
                if (previous != null) {
                    assertThat(at).as("newest first").isBeforeOrEqualTo(previous);
                }
                previous = at;
            }
            pages++;
            next = page.get("nextBefore").isNull() ? null
                    : query + "&before=" + enc(page.get("nextBefore").asString()) + "&beforeId=" + page.get("nextBeforeId").asString();
        }
        assertThat(seen).hasSize(6);
        assertThat(pages).isEqualTo(3);
        JsonNode filtered = json(get("/api/v1/admin/audit-events?resourceId=" + target.id() + "&action=ACCOUNT_SUSPENDED", admin), 200);
        assertThat(filtered.get("entries")).hasSize(3);
        assertThat(json(get("/api/v1/admin/audit-events/actions", admin), 200).toString()).contains("ACCOUNT_SUSPENDED");
    }

    // ------------------------------------------------------------------ helpers

    private record User(String id, String email, String accessToken, String refreshToken) {
    }

    private User user(PlatformRole role) throws Exception {
        String email = "ops-" + role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), role));
        return login(email);
    }

    private User login(String email) throws Exception {
        JsonNode body = json(post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", null), 200);
        return new User(body.get("accountId").asString(), email, body.get("accessToken").asString(), body.get("refreshToken").asString());
    }

    private int loginStatus(String email) throws Exception {
        return post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", null).statusCode();
    }

    private int refreshStatus(String refreshToken) throws Exception {
        return post("/api/v1/auth/refresh", "{\"refreshToken\":\"" + refreshToken + "\"}", null).statusCode();
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static JsonNode json(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> get(String path, User user) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (user != null) {
            req.header("Authorization", "Bearer " + user.accessToken());
        }
        return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body, User user) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        if (user != null) {
            req.header("Authorization", "Bearer " + user.accessToken());
        }
        return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

}
