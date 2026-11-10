package com.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.icegreen.greenmail.util.GreenMail;
import com.platform.TestcontainersConfiguration;
import com.platform.VerifiedRegistrationTestFilter;
import jakarta.mail.Message;
import jakarta.mail.internet.MimeMessage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Owner requirement 2026-11-10 - the email is verified BEFORE the account exists - over real HTTP, PostgreSQL and SMTP.
 * Every request here opts out of the test registration helper ({@link VerifiedRegistrationTestFilter#OPT_OUT_HEADER}),
 * so the API answers exactly as in production.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class RegistrationVerificationIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Registration-Test-Pass-2026";
    private static final Pattern LINK = Pattern.compile("/register/continue#token=([A-Za-z0-9_-]+)");

    @Value("${local.server.port}") private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private GreenMail greenMail;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void anUnverifiedEmailCannotRegisterNoAccountIsCreated() throws Exception {
        String email = unique("unverified");
        HttpResponse<String> refused = register(email, null, null);
        assertThat(refused.statusCode()).isEqualTo(400);
        assertThat(refused.body()).contains("Confirm your email before creating an account");
        assertThat(accounts(email)).isZero();

        // a made-up grant is refused the same way
        assertThat(register(email, "made-up-" + UUID.randomUUID(), null).statusCode()).isEqualTo(400);
        assertThat(accounts(email)).isZero();
    }

    @Test
    void aVerifiedEmailRegistersOnceStartsVerifiedAndGetsNoConfirmationEmailAfterwards() throws Exception {
        String email = unique("verified");
        assertThat(start(email).statusCode()).isEqualTo(202);
        assertThat(accounts(email)).as("nothing is created before the link is opened").isZero();
        String token = onlyLinkToken(email);

        HttpResponse<String> verified = verify(token);
        assertThat(verified.statusCode()).isEqualTo(200);
        JsonNode grant = JSON.readTree(verified.body());
        assertThat(grant.get("email").asString()).isEqualTo(email);

        // the link works once
        assertThat(verify(token).statusCode()).isEqualTo(400);

        HttpResponse<String> created = register(email, grant.get("registrationGrant").asString(), null);
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(jdbc.queryForObject("select email_verified_at is not null from accounts where normalized_email = ?",
                Boolean.class, email)).isTrue();
        Thread.sleep(500);
        assertThat(messagesTo(email)).as("only the verification email - no 'confirm your email' afterwards").hasSize(1);

        // the grant works once: a second registration with it is refused (the address now has an account: 409)
        assertThat(register(email, grant.get("registrationGrant").asString(), null).statusCode()).isEqualTo(409);
    }

    @Test
    void aGrantProvesOnlyItsOwnAddress() throws Exception {
        String email = unique("owner");
        start(email);
        String grant = JSON.readTree(verify(onlyLinkToken(email)).body()).get("registrationGrant").asString();
        String other = unique("other");
        assertThat(register(other, grant, null).statusCode()).isEqualTo(400);
        assertThat(accounts(other)).isZero();
        // still usable for its own address
        assertThat(register(email, grant, null).statusCode()).isEqualTo(201);
    }

    @Test
    void anExpiredLinkOrGrantIsRefused() throws Exception {
        String email = unique("expired");
        start(email);
        String token = onlyLinkToken(email);
        jdbc.update("update registration_verifications set link_expires_at = now() - interval '1 hour', "
                + "created_at = now() - interval '2 hours' where normalized_email = ?", email);
        assertThat(verify(token).statusCode()).isEqualTo(400);

        String email2 = unique("grant-expired");
        start(email2);
        String grant = JSON.readTree(verify(onlyLinkToken(email2)).body()).get("registrationGrant").asString();
        jdbc.update("update registration_verifications set grant_expires_at = now() - interval '1 second' "
                + "where normalized_email = ?", email2);
        assertThat(register(email2, grant, null).statusCode()).isEqualTo(400);
        assertThat(accounts(email2)).isZero();
    }

    @Test
    void theAnswerNeverRevealsAnAccountAndAnExistingOwnerIsToldBySignInEmail() throws Exception {
        String email = unique("existing");
        start(email);
        String grant = JSON.readTree(verify(onlyLinkToken(email)).body()).get("registrationGrant").asString();
        assertThat(register(email, grant, null).statusCode()).isEqualTo(201);
        jdbc.update("update registration_verifications set created_at = now() - interval '5 minutes' where normalized_email = ?", email);

        HttpResponse<String> again = start(email);
        assertThat(again.statusCode()).isEqualTo(202);
        assertThat(again.body()).isEmpty();
        List<MimeMessage> mail = awaitMessages(email, 2);
        MimeMessage latest = mail.getLast();
        assertThat(latest.getSubject()).isEqualTo("You already have a Bookeyond Kid account");
        assertThat(String.valueOf(latest.getContent())).doesNotContain("#token=");
    }

    @Test
    void sendingAgainWithinAMinuteSendsNothingMore() throws Exception {
        String email = unique("cooldown");
        assertThat(start(email).statusCode()).isEqualTo(202);
        assertThat(start(email).statusCode()).isEqualTo(202);
        Thread.sleep(700);
        assertThat(messagesTo(email)).hasSize(1);
    }

    @Test
    void aVerifiedPrincipalStartsActiveWithASession() throws Exception {
        String email = unique("principal");
        start(email);
        String grant = JSON.readTree(verify(onlyLinkToken(email)).body()).get("registrationGrant").asString();
        HttpResponse<String> created = register(email, grant, "PRINCIPAL");
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(JSON.readTree(created.body()).get("accessToken").isNull()).isFalse();
    }

    // ---------------------------------------------------------------------------------------------------------------

    private static String unique(String what) {
        return "reg-" + what + "-" + UUID.randomUUID() + "@example.com";
    }

    private Integer accounts(String email) {
        return jdbc.queryForObject("select count(*) from accounts where normalized_email = ?", Integer.class, email);
    }

    private HttpResponse<String> start(String email) throws Exception {
        return post("/api/v1/auth/registration/start", "{\"email\":\"" + email + "\",\"preferredLanguage\":\"en\"}");
    }

    private HttpResponse<String> verify(String token) throws Exception {
        return post("/api/v1/auth/registration/verify", "{\"token\":\"" + token + "\"}");
    }

    private HttpResponse<String> register(String email, String grant, String role) throws Exception {
        return post("/api/v1/auth/register", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\""
                + (grant == null ? "" : ",\"registrationGrant\":\"" + grant + "\"")
                + (role == null ? "" : ",\"role\":\"" + role + "\"") + "}");
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .header(VerifiedRegistrationTestFilter.OPT_OUT_HEADER, "off")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private String onlyLinkToken(String email) throws Exception {
        MimeMessage message = awaitMessages(email, 1).getLast();
        Matcher link = LINK.matcher(String.valueOf(message.getContent()));
        assertThat(link.find()).as("a verification link in the email").isTrue();
        return link.group(1);
    }

    private List<MimeMessage> awaitMessages(String email, int atLeast) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        List<MimeMessage> found = messagesTo(email);
        while (found.size() < atLeast && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
            found = messagesTo(email);
        }
        assertThat(found).hasSizeGreaterThanOrEqualTo(atLeast);
        return found;
    }

    private List<MimeMessage> messagesTo(String email) throws Exception {
        List<MimeMessage> messages = new ArrayList<>();
        for (MimeMessage message : greenMail.getReceivedMessages()) {
            if (message.getRecipients(Message.RecipientType.TO)[0].toString().equalsIgnoreCase(email)) {
                messages.add(message);
            }
        }
        return messages;
    }
}
