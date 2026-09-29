package com.platform.admin;

import com.icegreen.greenmail.util.GreenMail;
import com.platform.TestcontainersConfiguration;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.repository.AccountRepository;
import jakarta.mail.Message;
import jakarta.mail.internet.MimeMessage;
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
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TD-22: an administrator creates a teacher or principal account without ever choosing
 * or knowing its password - the new staff member chooses it from an emailed link.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class AdminProvisioningIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Admin-Provision-Pass-2026";
    private static final Pattern SETUP_LINK = Pattern.compile("http://localhost:5173/set-up-account#token=([A-Za-z0-9_-]+)");

    @Value("${local.server.port}") private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private GreenMail greenMail;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void aProvisionedPrincipalChoosesTheirOwnPassword() throws Exception {
        String admin = token(PlatformRole.ADMIN);
        String email = "provisioned-" + UUID.randomUUID() + "@example.com";

        // A password in the request is not accepted as the account's password.
        JsonNode created = json(post("/api/v1/admin/staff",
                "{\"email\":\"" + email + "\",\"role\":\"PRINCIPAL\",\"password\":\"" + PASSWORD + "\"}", admin), 201);
        assertThat(created.get("platformRole").asString()).isEqualTo("PRINCIPAL");
        assertThat(loginStatus(email, PASSWORD)).isEqualTo(401);

        MimeMessage mail = awaitMail(email);
        String body = (String) mail.getContent();
        assertThat(mail.getSubject()).isEqualTo("Your Bookeyond Kid account is ready to set up");
        assertThat(body).contains("created a principal account for you").contains("Forgot your password?");
        assertThat(body.lines()).as("no collapsed line continuations").noneMatch(line -> line.matches(".*\\S {2,}\\S.*"));
        Matcher link = SETUP_LINK.matcher(body);
        assertThat(link.find()).isTrue();

        assertThat(post("/api/v1/auth/account-setup/confirm",
                "{\"token\":\"" + link.group(1) + "\",\"newPassword\":\"" + PASSWORD + "\"}", null).statusCode()).isEqualTo(204);
        assertThat(loginStatus(email, PASSWORD)).isEqualTo(200);
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action = 'ACCOUNT_PROVISIONED' and resource_id = ?::uuid",
                Integer.class, UUID.fromString(created.get("id").asString()))).isEqualTo(1);
    }

    @Test
    void provisioningIsForAdminsOnlyAndNeverReplacesAnAccount() throws Exception {
        String admin = token(PlatformRole.ADMIN);
        String existing = "existing-" + UUID.randomUUID() + "@example.com";
        accountRepository.saveAndFlush(Account.register(existing, passwordEncoder.encode(PASSWORD), PlatformRole.PARENT));

        assertThat(post("/api/v1/admin/staff", "{\"email\":\"" + existing + "\",\"role\":\"TEACHER\"}", admin).statusCode()).isEqualTo(409);
        assertThat(loginStatus(existing, PASSWORD)).isEqualTo(200);
        assertThat(post("/api/v1/admin/staff", "{\"email\":\"x-" + UUID.randomUUID() + "@example.com\",\"role\":\"ADMIN\"}", admin)
                .statusCode()).isEqualTo(400);
        for (PlatformRole role : new PlatformRole[] {PlatformRole.PRINCIPAL, PlatformRole.TEACHER, PlatformRole.PARENT}) {
            assertThat(post("/api/v1/admin/staff", "{\"email\":\"y-" + UUID.randomUUID() + "@example.com\",\"role\":\"TEACHER\"}",
                    token(role)).statusCode()).as(role.name()).isEqualTo(403);
        }
    }

    @Test
    void staffAreSearchedByEmailAndRoleAPageAtATimeWithoutFamilies() throws Exception {
        String admin = token(PlatformRole.ADMIN);
        String tag = "staffsearch-" + UUID.randomUUID().toString().substring(0, 8);
        accountRepository.saveAndFlush(Account.register(tag + "-a@example.com", passwordEncoder.encode(PASSWORD), PlatformRole.TEACHER));
        accountRepository.saveAndFlush(Account.register(tag + "-b@example.com", passwordEncoder.encode(PASSWORD), PlatformRole.PRINCIPAL));
        accountRepository.saveAndFlush(Account.register(tag + "-c@example.com", passwordEncoder.encode(PASSWORD), PlatformRole.PARENT));

        // A search matches part of an email, whatever its case, and never lists a family's account.
        JsonNode found = json(get("/api/v1/admin/staff?q=" + tag.toUpperCase(), admin), 200);
        assertThat(found.get("total").asLong()).isEqualTo(2);
        assertThat(found.get("items").get(0).get("email").asString()).isEqualTo(tag + "-a@example.com");
        assertThat(found.get("items").get(1).get("email").asString()).isEqualTo(tag + "-b@example.com");

        JsonNode principals = json(get("/api/v1/admin/staff?q=" + tag + "&role=PRINCIPAL", admin), 200);
        assertThat(principals.get("total").asLong()).isEqualTo(1);
        assertThat(principals.get("items").get(0).get("platformRole").asString()).isEqualTo("PRINCIPAL");

        JsonNode second = json(get("/api/v1/admin/staff?q=" + tag + "&size=1&page=1", admin), 200);
        assertThat(second.get("items")).hasSize(1);
        assertThat(second.get("items").get(0).get("email").asString()).isEqualTo(tag + "-b@example.com");

        // A wildcard typed into the search is only a character.
        assertThat(json(get("/api/v1/admin/staff?q=" + tag.replace("-", "_"), admin), 200).get("total").asLong()).isZero();

        assertThat(get("/api/v1/admin/staff?role=PARENT", admin).statusCode()).isEqualTo(400);
        assertThat(get("/api/v1/admin/staff?size=500", admin).statusCode()).isEqualTo(400);
        assertThat(get("/api/v1/admin/staff", token(PlatformRole.PRINCIPAL)).statusCode()).isEqualTo(403);
    }

    // ------------------------------------------------------------------ helpers

    private String token(PlatformRole role) throws Exception {
        String email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), role));
        return json(post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", null), 200)
                .get("accessToken").asString();
    }

    private MimeMessage awaitMail(String to) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            for (MimeMessage message : greenMail.getReceivedMessages()) {
                if (message.getRecipients(Message.RecipientType.TO)[0].toString().equalsIgnoreCase(to)) {
                    return message;
                }
            }
            Thread.sleep(50);
        }
        throw new AssertionError("No email to " + to);
    }

    private int loginStatus(String email, String password) throws Exception {
        return post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}", null).statusCode();
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
