package com.platform.security;

import com.platform.TestAdmin;
import com.platform.TestcontainersConfiguration;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.repository.AccountRepository;
import com.platform.security.stepup.Totp;
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
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M9a over real HTTP against PostgreSQL. An administrator steps up (M8) with the password and a code from an
 * authenticator app: without an app on, refused until one is; each code is accepted once; a recovery code once; wrong
 * codes lock the account at the sign-in limit even though the password with them is right. The secret is kept
 * encrypted and never audited. Families step up with the password alone and can't turn an app on. Turning it off needs
 * the password and a code; another administrator, stepped up, can turn off a colleague's.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class SecondFactorIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Second-Factor-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();
    private long lastStep = Long.MIN_VALUE;

    @Test
    void anAdministratorStepsUpWithACodeThatWorksOnce() throws Exception {
        Account account = admin();
        String admin = login(account.getEmail());

        // No app yet: the step-up says to turn one on (403), even with the right password.
        HttpResponse<String> noApp = stepUp(admin, null);
        assertThat(noApp.statusCode()).isEqualTo(403);
        assertThat(JSON.readTree(noApp.body()).get("secondFactorSetupRequired").asBoolean()).isTrue();

        // Setting up needs the password; the key is shown once and kept only encrypted.
        assertThat(send("POST", "/api/v1/account/second-factor/setup", "{\"password\":\"wrong\"}", admin, null).statusCode()).isEqualTo(422);
        JsonNode setup = json(send("POST", "/api/v1/account/second-factor/setup", "{\"password\":\"" + PASSWORD + "\"}", admin, null), 200);
        String key = setup.get("secret").asString();
        assertThat(key).matches("[A-Z2-7]{32}");
        assertThat(setup.get("otpauthUri").asString()).startsWith("otpauth://totp/Bookeyond%20Kid%3A").contains("secret=" + key)
                .contains("issuer=Bookeyond%20Kid");
        String stored = jdbc.queryForObject("select secret_ciphertext from account_second_factors where account_id = ?", String.class,
                account.getId());
        assertThat(stored).doesNotContain(key);
        byte[] secret = Totp.fromBase32(key);

        // Not in force until a first code confirms it.
        assertThat(stepUp(admin, null).statusCode()).as("still pending").isEqualTo(403);
        assertThat(json(send("GET", "/api/v1/account/second-factor", null, admin, null), 200).get("pending").asBoolean()).isTrue();
        assertThat(activate(admin, Totp.code(secret, Totp.step(Instant.now()) + 20)).statusCode()).as("a wrong code").isEqualTo(422);
        JsonNode codes = json(activate(admin, nextCode(secret)), 200).get("recoveryCodes");
        assertThat(codes).hasSize(8);
        HashSet<String> distinct = new HashSet<>();
        codes.forEach(code -> {
            assertThat(code.asString()).matches("[A-Z2-7]{5}-[A-Z2-7]{5}");
            distinct.add(code.asString());
        });
        assertThat(distinct).hasSize(8);
        assertThat(jdbc.queryForList("select code_hash from account_recovery_codes where account_id = ?", String.class, account.getId()))
                .hasSize(8).allMatch(hash -> hash.matches("[0-9a-f]{64}")).noneMatch(distinct::contains);
        assertThat(activate(admin, nextCode(secret)).statusCode()).as("already on").isEqualTo(409);
        assertThat(send("POST", "/api/v1/account/second-factor/setup", "{\"password\":\"" + PASSWORD + "\"}", admin, null).statusCode())
                .as("a second app replaces nothing while one is on").isEqualTo(409);

        // With it on: the password alone isn't enough, a wrong code isn't, and a code works once.
        HttpResponse<String> passwordOnly = stepUp(admin, null);
        assertThat(passwordOnly.statusCode()).isEqualTo(422);
        assertThat(JSON.readTree(passwordOnly.body()).get("secondFactorRequired").asBoolean()).isTrue();
        assertThat(stepUp(admin, Totp.code(secret, Totp.step(Instant.now()) - 5)).statusCode()).as("an old code").isEqualTo(422);
        String code = nextCode(secret);
        assertThat(stepUp(admin, code).statusCode()).isEqualTo(200);
        assertThat(stepUp(admin, code).statusCode()).as("the same code again").isEqualTo(422);

        // A recovery code works once, written any way.
        String recovery = codes.get(0).asString();
        assertThat(stepUp(admin, " " + recovery.toLowerCase().replace("-", "") + " ").statusCode()).isEqualTo(200);
        assertThat(stepUp(admin, recovery).statusCode()).as("a used recovery code").isEqualTo(422);
        assertThat(json(send("GET", "/api/v1/account/second-factor", null, admin, null), 200).get("recoveryCodesLeft").asInt()).isEqualTo(7);

        List<String> audited = jdbc.queryForList("select action || ' ' || coalesce(metadata::text, '') from audit_events where actor_id = ?",
                String.class, account.getId());
        assertThat(audited).anyMatch(line -> line.startsWith("SECOND_FACTOR_ENABLED")).anyMatch(line -> line.startsWith("RECOVERY_CODE_USED"))
                .noneMatch(line -> line.contains(key) || line.contains(recovery));
    }

    @Test
    void wrongCodesLockTheAccountAtTheSignInLimitThoughThePasswordIsRight() throws Exception {
        Account account = admin();
        String admin = login(account.getEmail());
        TestAdmin.turnOnAuthenticator(port, admin, PASSWORD);
        for (int i = 1; i <= 5; i++) {
            // Shaped like a recovery code, so it can't be this moment's code by chance.
            assertThat(stepUp(admin, "AAAAA-AAAA" + (char) ('A' + i)).statusCode()).as("wrong code " + i).isEqualTo(422);
        }
        assertThat(jdbc.queryForObject("select locked_until is not null from accounts where id = ?", Boolean.class, account.getId())).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action = 'SECOND_FACTOR_LOCKED' and actor_id = ?",
                Integer.class, account.getId())).isEqualTo(1);
        assertThat(stepUp(admin, TestAdmin.code(account.getId().toString())).statusCode()).as("locked").isNotEqualTo(200);
    }

    @Test
    void familiesStepUpWithThePasswordAloneAndCantTurnAnAppOn() throws Exception {
        String email = "sf-parent-" + UUID.randomUUID() + "@example.com";
        String parent = json(send("POST", "/api/v1/auth/register", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null, null), 201).get("accessToken").asString();
        assertThat(stepUp(parent, null).statusCode()).isEqualTo(200);
        assertThat(send("POST", "/api/v1/account/second-factor/setup", "{\"password\":\"" + PASSWORD + "\"}", parent, null).statusCode())
                .isEqualTo(403);
        assertThat(json(send("GET", "/api/v1/account/second-factor", null, parent, null), 200).get("enabled").asBoolean()).isFalse();
    }

    @Test
    void turningItOffNeedsTheCodeAndAnotherAdministratorCanTurnOffAColleagues() throws Exception {
        Account first = admin();
        String a = login(first.getEmail());
        TestAdmin.turnOnAuthenticator(port, a, PASSWORD);
        String id = first.getId().toString();

        assertThat(send("POST", "/api/v1/account/second-factor/off", "{\"password\":\"" + PASSWORD + "\",\"code\":\"12345\"}", a, null)
                .statusCode()).as("a wrong code").isEqualTo(422);
        assertThat(send("POST", "/api/v1/account/second-factor/off", "{\"password\":\"" + PASSWORD + "\",\"code\":\""
                + TestAdmin.code(id) + "\"}", a, null).statusCode()).isEqualTo(204);
        assertThat(stepUp(a, null).statusCode()).as("off: an app is needed again").isEqualTo(403);
        assertThat(jdbc.queryForObject("select count(*) from account_recovery_codes where account_id = ?", Integer.class, first.getId()))
                .isZero();

        // A lost phone: another administrator, stepped up, turns it off; never their own, and never without a step-up.
        TestAdmin.turnOnAuthenticator(port, a, PASSWORD);
        Account second = admin();
        String b = login(second.getEmail());
        String reset = "/api/v1/admin/accounts/" + first.getId() + "/second-factor/reset";
        HttpResponse<String> notSteppedUp = send("POST", reset, null, b, null);
        assertThat(notSteppedUp.statusCode()).isEqualTo(403);
        assertThat(notSteppedUp.body()).contains("stepUpRequired");
        TestAdmin.stepUp(port, b, PASSWORD);
        String proof = TestAdmin.stepUpFor(b);
        assertThat(send("POST", "/api/v1/admin/accounts/" + second.getId() + "/second-factor/reset", null, b, proof).statusCode())
                .as("their own").isEqualTo(403);
        assertThat(send("POST", reset, null, a, TestAdmin.stepUpFor(a)).statusCode()).as("no proof of a's").isEqualTo(403);
        assertThat(send("POST", reset, null, b, proof).statusCode()).isEqualTo(204);
        assertThat(stepUp(a, null).statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action = 'SECOND_FACTOR_RESET' and actor_id = ? "
                + "and resource_id = ?", Integer.class, second.getId(), first.getId())).isEqualTo(1);
    }

    // ------------------------------------------------------------------ helpers

    /** The next code an authenticator app would show that hasn't been used yet. */
    private String nextCode(byte[] secret) throws InterruptedException {
        long step = Math.max(Totp.step(Instant.now()), lastStep + 1);
        while (step > Totp.step(Instant.now()) + 1) {
            Thread.sleep(500);
        }
        lastStep = step;
        return Totp.code(secret, step);
    }

    private HttpResponse<String> stepUp(String token, String code) throws Exception {
        return send("POST", "/api/v1/account/step-up", "{\"password\":\"" + PASSWORD + "\""
                + (code == null ? "" : ",\"code\":\"" + code + "\"") + "}", token, null);
    }

    private HttpResponse<String> activate(String token, String code) throws Exception {
        return send("POST", "/api/v1/account/second-factor/activate", "{\"code\":\"" + code + "\"}", token, null);
    }

    private Account admin() {
        String email = "sf-admin-" + UUID.randomUUID() + "@example.com";
        return accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), PlatformRole.ADMIN));
    }

    private String login(String email) throws Exception {
        return json(send("POST", "/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", null, null), 200)
                .get("accessToken").asString();
    }

    private static JsonNode json(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> send(String method, String path, String body, String bearer, String stepUp) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        if (stepUp != null) {
            request.header("X-Step-Up", stepUp);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

}
