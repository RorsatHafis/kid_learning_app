package com.platform.identity;

import com.icegreen.greenmail.util.GreenMail;
import com.platform.TestcontainersConfiguration;
import com.platform.common.idempotency.service.RequestFingerprint;
import com.platform.notification.EmailMessage;
import jakarta.mail.Message;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Email verification and password reset over real HTTP, PostgreSQL and SMTP: every link
 * a test uses was read out of an email actually delivered to an SMTP server. Covers
 * single use, expiry, replacement by a newer link, purpose confusion, concurrent use,
 * enumeration-safe answers, per-account email limits, and account-state boundaries
 * (a pending principal stays pending; a disabled account gets nothing).
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class AccountRecoveryIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Recovery-Test-Pass-2026";
    private static final String NEW_PASSWORD = "Recovery-Test-New-Pass-2026";
    private static final Pattern LINK = Pattern.compile("(http://localhost:5173/[a-z-]+)#token=([A-Za-z0-9_-]+)");

    @Value("${local.server.port}") private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private GreenMail greenMail;
    @Autowired private ApplicationEventPublisher events;

    private final HttpClient http = HttpClient.newHttpClient();

    // ------------------------------------------------------------------ verification

    @Test
    void registeringSendsAConfirmationLinkThatConfirmsTheAddressOnce() throws Exception {
        Session parent = register("PARENT");
        Link link = onlyLink(parent.email(), "Confirm your email");
        assertThat(link.page()).isEqualTo("http://localhost:5173/verify-email");
        assertThat(me(parent).get("emailVerifiedAt").isNull()).isTrue();

        assertThat(confirmEmail(link.token()).statusCode()).isEqualTo(204);

        assertThat(me(parent).get("emailVerifiedAt").isNull()).isFalse();
        assertThat(me(parent).get("status").asString()).isEqualTo("ACTIVE");
        assertThat(auditCount(parent, "ACCOUNT_EMAIL_VERIFIED")).isEqualTo(1);
        // Used once; a second use is refused with the same answer as an unknown link.
        HttpResponse<String> again = confirmEmail(link.token());
        HttpResponse<String> unknown = confirmEmail("never-issued-" + UUID.randomUUID());
        assertThat(again.statusCode()).isEqualTo(400);
        assertThat(unknown.statusCode()).isEqualTo(400);
        assertThat(JSON.readTree(again.body()).get("detail")).isEqualTo(JSON.readTree(unknown.body()).get("detail"));
    }

    @Test
    void onlyTheDigestOfALinkIsStored() throws Exception {
        Session parent = register("PARENT");
        Link link = onlyLink(parent.email(), "Confirm your email");

        List<String> stored = jdbc.queryForList(
                "select token_hash from account_action_tokens where account_id = ?::uuid", String.class, parent.accountId());
        assertThat(stored).containsExactly(RequestFingerprint.sha256Hex(link.token()));
        assertThat(link.token()).hasSizeGreaterThanOrEqualTo(43);
    }

    @Test
    void confirmingTheAddressNeverApprovesAPendingPrincipal() throws Exception {
        String email = "principal-" + UUID.randomUUID() + "@example.com";
        HttpResponse<String> registered = post("/api/v1/auth/register", "{\"email\":\"" + email + "\",\"password\":\""
                + PASSWORD + "\",\"role\":\"PRINCIPAL\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null);
        assertThat(registered.statusCode()).isEqualTo(202);

        assertThat(confirmEmail(onlyLink(email, "Confirm your email").token()).statusCode()).isEqualTo(204);

        assertThat(jdbc.queryForObject("select status from accounts where normalized_email = ?", String.class, email))
                .isEqualTo("PENDING_VERIFICATION");
        assertThat(jdbc.queryForObject("select email_verified_at is not null from accounts where normalized_email = ?",
                Boolean.class, email)).isTrue();
        assertThat(loginStatus(email, PASSWORD)).isEqualTo(401);
    }

    @Test
    void aNewConfirmationLinkWaitsAMinuteAndReplacesTheOldOne() throws Exception {
        Session parent = register("PARENT");
        Link first = onlyLink(parent.email(), "Confirm your email");

        HttpResponse<String> tooSoon = post("/api/v1/account/email-verification", "", parent.accessToken());
        assertThat(tooSoon.statusCode()).isEqualTo(429);
        long retryAfter = Long.parseLong(tooSoon.headers().firstValue("Retry-After").orElseThrow());
        assertThat(retryAfter).isBetween(1L, 60L);

        backdateLinks(parent, 61);
        assertThat(post("/api/v1/account/email-verification", "", parent.accessToken()).statusCode()).isEqualTo(202);
        List<Link> links = links(parent.email(), "Confirm your email", 2);

        assertThat(confirmEmail(first.token()).statusCode()).as("replaced link").isEqualTo(400);
        assertThat(confirmEmail(links.get(1).token()).statusCode()).isEqualTo(204);
        // Nothing left to confirm.
        assertThat(post("/api/v1/account/email-verification", "", parent.accessToken()).statusCode()).isEqualTo(409);
        assertThat(post("/api/v1/account/email-verification", "", null).statusCode()).isEqualTo(401);
    }

    @Test
    void noMoreThanFiveConfirmationEmailsAnHour() throws Exception {
        Session parent = register("PARENT");
        onlyLink(parent.email(), "Confirm your email");
        for (int i = 2; i <= 5; i++) {
            spaceOutLinks(parent);
            assertThat(post("/api/v1/account/email-verification", "", parent.accessToken()).statusCode()).isEqualTo(202);
        }
        spaceOutLinks(parent);

        HttpResponse<String> sixth = post("/api/v1/account/email-verification", "", parent.accessToken());
        assertThat(sixth.statusCode()).isEqualTo(429);
        assertThat(Long.parseLong(sixth.headers().firstValue("Retry-After").orElseThrow())).isGreaterThan(60);
        assertThat(links(parent.email(), "Confirm your email", 5)).hasSize(5);
    }

    // ------------------------------------------------------------------ password reset

    @Test
    void aResetRequestLooksTheSameWhetherOrNotTheAddressHasAnAccount() throws Exception {
        Session parent = register("PARENT");
        String nobody = "nobody-" + UUID.randomUUID() + "@example.com";

        HttpResponse<String> known = requestReset(parent.email());
        HttpResponse<String> unknown = requestReset(nobody);

        assertThat(known.statusCode()).isEqualTo(202);
        assertThat(unknown.statusCode()).isEqualTo(202);
        assertThat(known.body()).isEqualTo(unknown.body());
        assertThat(onlyLink(parent.email(), "Reset your").page()).isEqualTo("http://localhost:5173/reset-password");
        drain();
        assertThat(messagesTo(nobody)).isEmpty();
    }

    @Test
    void aResetSetsTheNewPasswordEndsEverySessionAndWorksOnce() throws Exception {
        Session parent = register("PARENT");
        Session otherDevice = login(parent.email(), PASSWORD);
        requestReset(parent.email());
        Link link = onlyLink(parent.email(), "Reset your");

        // A password the policy refuses does not use up the link.
        assertThat(reset(link.token(), "short").statusCode()).isEqualTo(422);
        assertThat(reset(link.token(), NEW_PASSWORD).statusCode()).isEqualTo(204);

        assertThat(refreshStatus(parent.refreshToken())).isEqualTo(401);
        assertThat(refreshStatus(otherDevice.refreshToken())).isEqualTo(401);
        assertThat(loginStatus(parent.email(), PASSWORD)).isEqualTo(401);
        assertThat(loginStatus(parent.email(), NEW_PASSWORD)).isEqualTo(200);
        assertThat(reset(link.token(), "Another-New-Pass-2026").statusCode()).as("second use").isEqualTo(400);
        assertThat(loginStatus(parent.email(), NEW_PASSWORD)).isEqualTo(200);
        // Using the emailed link also proves the mailbox.
        assertThat(jdbc.queryForObject("select email_verified_at is not null from accounts where id = ?::uuid",
                Boolean.class, parent.accountId())).isTrue();
        assertThat(auditCount(parent, "ACCOUNT_PASSWORD_RESET")).isEqualTo(1);
        drain();
        assertThat(messagesTo(parent.email()).stream().map(AccountRecoveryIntegrationTests::subject))
                .contains("Your Bookeyond Kid password was changed");
    }

    @Test
    void aResetLinkExpiresAfterThirtyMinutes() throws Exception {
        Session parent = register("PARENT");
        requestReset(parent.email());
        Link link = onlyLink(parent.email(), "Reset your");
        jdbc.update("update account_action_tokens set issued_at = issued_at - interval '31 minutes', "
                + "expires_at = expires_at - interval '31 minutes' where account_id = ?::uuid and purpose = 'PASSWORD_RESET'",
                UUID.fromString(parent.accountId()));

        assertThat(reset(link.token(), NEW_PASSWORD).statusCode()).isEqualTo(400);
        assertThat(loginStatus(parent.email(), PASSWORD)).isEqualTo(200);
    }

    @Test
    void aLinkOnlyWorksForItsOwnPurpose() throws Exception {
        Session parent = register("PARENT");
        Link confirm = onlyLink(parent.email(), "Confirm your email");
        requestReset(parent.email());
        Link resetLink = onlyLink(parent.email(), "Reset your");

        assertThat(reset(confirm.token(), NEW_PASSWORD).statusCode()).isEqualTo(400);
        assertThat(confirmEmail(resetLink.token()).statusCode()).isEqualTo(400);
        assertThat(loginStatus(parent.email(), PASSWORD)).isEqualTo(200);
        assertThat(me(parent).get("emailVerifiedAt").isNull()).isTrue();
    }

    @Test
    void aRepeatedRequestSendsNothingNewAndANewerLinkReplacesTheOlder() throws Exception {
        Session parent = register("PARENT");
        requestReset(parent.email());
        requestReset(parent.email());
        drain();
        assertThat(links(parent.email(), "Reset your", 1)).as("second request within a minute").hasSize(1);
        Link older = onlyLink(parent.email(), "Reset your");

        backdateLinks(parent, 61);
        requestReset(parent.email());
        Link newer = links(parent.email(), "Reset your", 2).get(1);

        assertThat(reset(older.token(), NEW_PASSWORD).statusCode()).isEqualTo(400);
        assertThat(reset(newer.token(), NEW_PASSWORD).statusCode()).isEqualTo(204);
    }

    @Test
    void twoSimultaneousUsesOfOneLinkSucceedOnlyOnce() throws Exception {
        Session parent = register("PARENT");
        requestReset(parent.email());
        Link link = onlyLink(parent.email(), "Reset your");

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            String password = "Concurrent-Reset-Pass-" + i;
            Callable<Integer> use = () -> {
                start.await();
                return reset(link.token(), password).statusCode();
            };
            results.add(pool.submit(use));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> result : results) {
            statuses.add(result.get());
        }
        pool.shutdown();

        assertThat(statuses).containsExactlyInAnyOrder(204, 400, 400, 400);
        assertThat(auditCount(parent, "ACCOUNT_PASSWORD_RESET")).isEqualTo(1);
    }

    @Test
    void aResetLiftsALockout() throws Exception {
        Session parent = register("PARENT");
        for (int i = 0; i < 5; i++) {
            loginStatus(parent.email(), "not-the-password");
        }
        assertThat(loginStatus(parent.email(), PASSWORD)).isEqualTo(423);

        requestReset(parent.email());
        assertThat(reset(onlyLink(parent.email(), "Reset your").token(), NEW_PASSWORD).statusCode()).isEqualTo(204);

        assertThat(loginStatus(parent.email(), NEW_PASSWORD)).isEqualTo(200);
    }

    @Test
    void aDisabledAccountIsSentNothingAndItsLinksStopWorking() throws Exception {
        Session parent = register("PARENT");
        requestReset(parent.email());
        Link issuedBefore = onlyLink(parent.email(), "Reset your");
        jdbc.update("update accounts set status = 'DISABLED' where id = ?::uuid", UUID.fromString(parent.accountId()));
        backdateLinks(parent, 61);

        assertThat(requestReset(parent.email()).statusCode()).isEqualTo(202);
        drain();
        assertThat(links(parent.email(), "Reset your", 1)).hasSize(1);
        assertThat(reset(issuedBefore.token(), NEW_PASSWORD).statusCode()).isEqualTo(400);
    }

    @Test
    void aPendingPrincipalCanResetButStillCannotSignInUntilApproved() throws Exception {
        String email = "principal-" + UUID.randomUUID() + "@example.com";
        post("/api/v1/auth/register", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                + "\",\"role\":\"PRINCIPAL\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null);

        requestReset(email);
        assertThat(reset(onlyLink(email, "Reset your").token(), NEW_PASSWORD).statusCode()).isEqualTo(204);

        assertThat(loginStatus(email, NEW_PASSWORD)).isEqualTo(401);
        assertThat(jdbc.queryForObject("select status from accounts where normalized_email = ?", String.class, email))
                .isEqualTo("PENDING_VERIFICATION");
    }

    @Test
    void malformedRequestsAreRefusedWithoutSideEffects() throws Exception {
        assertThat(post("/api/v1/auth/password-reset", "{\"email\":\"not-an-email\"}", null).statusCode()).isEqualTo(400);
        assertThat(post("/api/v1/auth/password-reset/confirm", "{\"token\":\"\",\"newPassword\":\"" + NEW_PASSWORD + "\"}", null)
                .statusCode()).isEqualTo(400);
        assertThat(post("/api/v1/auth/email-verification/confirm", "{\"token\":\"" + "x".repeat(500) + "\"}", null)
                .statusCode()).isEqualTo(400);
    }

    // ------------------------------------------------------------------ email change

    @Test
    void theSignInAddressChangesOnlyWhenTheLinkSentToTheNewAddressIsOpened() throws Exception {
        Session parent = register("PARENT");
        String newEmail = "moved-" + UUID.randomUUID() + "@example.com";

        assertThat(changeEmail(parent, PASSWORD, newEmail).statusCode()).isEqualTo(202);
        assertThat(me(parent).get("email").asString()).as("nothing changes before the link is opened")
                .isEqualTo(parent.email());
        Link link = onlyLink(newEmail, "Confirm your new email");
        assertThat(link.page()).isEqualTo("http://localhost:5173/confirm-email-change");

        assertThat(confirmEmailChange(link.token()).statusCode()).isEqualTo(204);

        JsonNode account = me(parent);
        assertThat(account.get("email").asString()).isEqualTo(newEmail);
        assertThat(account.get("emailVerifiedAt").isNull()).as("the new address was just proven").isFalse();
        assertThat(loginStatus(newEmail, PASSWORD)).isEqualTo(200);
        assertThat(loginStatus(parent.email(), PASSWORD)).isEqualTo(401);
        assertThat(refreshStatus(parent.refreshToken())).as("sessions are kept").isEqualTo(200);
        assertThat(auditCount(parent, "ACCOUNT_EMAIL_CHANGED")).isEqualTo(1);
        // The old address is told, without the new one spelled out in full.
        MimeMessage notice = messagesTo(parent.email()).stream()
                .filter(m -> subject(m).contains("sign-in email was changed")).findFirst().orElseThrow();
        assertThat((String) notice.getContent()).contains("mo***@example.com").doesNotContain(newEmail);
        assertThat(confirmEmailChange(link.token()).statusCode()).as("used once").isEqualTo(400);
    }

    @Test
    void aWrongCurrentPasswordSendsNothingAndCountsTowardsTheLockout() throws Exception {
        Session parent = register("PARENT");
        String newEmail = "moved-" + UUID.randomUUID() + "@example.com";

        assertThat(changeEmail(parent, "Not-The-Password-2026", newEmail).statusCode()).isEqualTo(422);
        assertThat(changeEmail(parent, PASSWORD, "not-an-email").statusCode()).isEqualTo(400);
        assertThat(post("/api/v1/account/email", "{\"currentPassword\":\"" + PASSWORD + "\",\"newEmail\":\"" + newEmail
                + "\"}", null).statusCode()).isEqualTo(401);

        drain();
        assertThat(messagesTo(newEmail)).isEmpty();
        assertThat(jdbc.queryForObject("select failed_login_attempts from accounts where id = ?::uuid", Integer.class,
                UUID.fromString(parent.accountId()))).isEqualTo(1);
        assertThat(changeEmail(parent, PASSWORD, parent.email().toUpperCase()).statusCode())
                .as("already the sign-in address").isEqualTo(409);
    }

    @Test
    void anAddressThatAlreadyHasAnAccountIsToldInsteadOfSentALink() throws Exception {
        Session parent = register("PARENT");
        Session other = register("PARENT");

        assertThat(changeEmail(parent, PASSWORD, other.email()).statusCode()).as("same answer as a free address")
                .isEqualTo(202);

        drain();
        List<MimeMessage> toOther = messagesTo(other.email());
        assertThat(toOther).anyMatch(m -> subject(m).contains("Someone tried to use this address"));
        assertThat(toOther).noneMatch(m -> subject(m).contains("Confirm your new email"));
        assertThat(me(parent).get("email").asString()).isEqualTo(parent.email());
        // It still counts as an email sent: the next request waits like after any other.
        assertThat(changeEmail(parent, PASSWORD, "free-" + UUID.randomUUID() + "@example.com").statusCode()).isEqualTo(429);
    }

    @Test
    void anAddressTakenAfterTheLinkWasSentIsRefusedAndNothingChanges() throws Exception {
        Session parent = register("PARENT");
        String newEmail = "contested-" + UUID.randomUUID() + "@example.com";
        changeEmail(parent, PASSWORD, newEmail);
        Link link = onlyLink(newEmail, "Confirm your new email");
        post("/api/v1/auth/register", "{\"email\":\"" + newEmail + "\",\"password\":\"" + PASSWORD
                + "\",\"role\":\"PARENT\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null);

        assertThat(confirmEmailChange(link.token()).statusCode()).isEqualTo(409);
        assertThat(me(parent).get("email").asString()).isEqualTo(parent.email());
    }

    @Test
    void afterTheMoveLinksSentToTheOldAddressStopWorkingAndANewerChangeLinkReplacesTheOlder() throws Exception {
        Session parent = register("PARENT");
        requestReset(parent.email());
        Link resetToOldAddress = onlyLink(parent.email(), "Reset your");
        String first = "first-" + UUID.randomUUID() + "@example.com";
        String second = "second-" + UUID.randomUUID() + "@example.com";
        changeEmail(parent, PASSWORD, first);
        Link firstLink = onlyLink(first, "Confirm your new email");
        backdateLinks(parent, 61);
        assertThat(changeEmail(parent, PASSWORD, second).statusCode()).isEqualTo(202);

        assertThat(confirmEmailChange(firstLink.token()).statusCode()).as("replaced").isEqualTo(400);
        assertThat(confirmEmailChange(onlyLink(second, "Confirm your new email").token()).statusCode()).isEqualTo(204);
        assertThat(reset(resetToOldAddress.token(), NEW_PASSWORD).statusCode())
                .as("the old mailbox no longer controls the account").isEqualTo(400);
        assertThat(confirmEmail(onlyLink(parent.email(), "Confirm your email").token()).statusCode()).isEqualTo(400);
    }

    @Test
    void aDisabledAccountsChangeLinkStopsWorking() throws Exception {
        Session parent = register("PARENT");
        String newEmail = "moved-" + UUID.randomUUID() + "@example.com";
        changeEmail(parent, PASSWORD, newEmail);
        Link link = onlyLink(newEmail, "Confirm your new email");
        jdbc.update("update accounts set status = 'DISABLED' where id = ?::uuid", UUID.fromString(parent.accountId()));

        assertThat(confirmEmailChange(link.token()).statusCode()).isEqualTo(400);
        assertThat(jdbc.queryForObject("select email from accounts where id = ?::uuid", String.class,
                UUID.fromString(parent.accountId()))).isEqualTo(parent.email());
    }

    // ------------------------------------------------------------------ mail

    private record Link(String page, String token) {
    }

    private Link onlyLink(String to, String subjectPrefix) throws Exception {
        List<Link> links = links(to, subjectPrefix, 1);
        assertThat(links).hasSize(1);
        return links.getFirst();
    }

    /** The links in emails to {@code to} whose subject contains {@code subjectPart}, oldest first, once {@code atLeast} arrived. */
    private List<Link> links(String to, String subjectPart, int atLeast) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (true) {
            List<Link> links = new ArrayList<>();
            for (MimeMessage message : messagesTo(to)) {
                if (subject(message).contains(subjectPart)) {
                    Matcher matcher = LINK.matcher((String) message.getContent());
                    if (matcher.find()) {
                        links.add(new Link(matcher.group(1), matcher.group(2)));
                    }
                }
            }
            if (links.size() >= atLeast || System.currentTimeMillis() > deadline) {
                return links;
            }
            Thread.sleep(50);
        }
    }

    private List<MimeMessage> messagesTo(String to) throws Exception {
        List<MimeMessage> messages = new ArrayList<>();
        for (MimeMessage message : greenMail.getReceivedMessages()) {
            if (message.getRecipients(Message.RecipientType.TO)[0].toString().equalsIgnoreCase(to)) {
                messages.add(message);
            }
        }
        return messages;
    }

    private static String subject(MimeMessage message) {
        try {
            return message.getSubject();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Waits until every email queued so far has been delivered: delivery runs in order,
     * so once a marker sent now arrives, anything that was going to be sent has been.
     */
    private void drain() throws Exception {
        String marker = "drain-" + UUID.randomUUID() + "@example.com";
        events.publishEvent(new EmailMessage(marker, "marker", "marker"));
        long deadline = System.currentTimeMillis() + 10_000;
        while (messagesTo(marker).isEmpty()) {
            assertThat(System.currentTimeMillis()).as("marker email delivered").isLessThan(deadline);
            Thread.sleep(50);
        }
    }

    private void backdateLinks(Session session, int seconds) {
        jdbc.update("update account_action_tokens set issued_at = issued_at - make_interval(secs => ?), "
                + "expires_at = expires_at - make_interval(secs => ?) where account_id = ?::uuid",
                seconds, seconds, UUID.fromString(session.accountId()));
    }

    /** Moves every earlier link back just past the one-minute gap, keeping them inside the hour. */
    private void spaceOutLinks(Session session) {
        backdateLinks(session, 61);
    }

    // ------------------------------------------------------------------ HTTP

    private record Session(String accountId, String email, String accessToken, String refreshToken) {
    }

    private Session register(String role) throws Exception {
        String email = "recovery-" + UUID.randomUUID() + "@example.com";
        JsonNode body = json(post("/api/v1/auth/register", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                + "\",\"role\":\"" + role + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null), 201);
        return new Session(body.get("accountId").asString(), email, body.get("accessToken").asString(),
                body.get("refreshToken").asString());
    }

    private Session login(String email, String password) throws Exception {
        JsonNode body = json(post("/api/v1/auth/login",
                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}", null), 200);
        return new Session(body.get("accountId").asString(), email, body.get("accessToken").asString(),
                body.get("refreshToken").asString());
    }

    private JsonNode me(Session session) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/account"))
                .header("Authorization", "Bearer " + session.accessToken()).GET().build();
        return json(http.send(req, HttpResponse.BodyHandlers.ofString()), 200);
    }

    private HttpResponse<String> requestReset(String email) throws Exception {
        return post("/api/v1/auth/password-reset", "{\"email\":\"" + email + "\"}", null);
    }

    private HttpResponse<String> reset(String token, String newPassword) throws Exception {
        return post("/api/v1/auth/password-reset/confirm",
                "{\"token\":\"" + token + "\",\"newPassword\":\"" + newPassword + "\"}", null);
    }

    private HttpResponse<String> confirmEmail(String token) throws Exception {
        return post("/api/v1/auth/email-verification/confirm", "{\"token\":\"" + token + "\"}", null);
    }

    private HttpResponse<String> changeEmail(Session session, String currentPassword, String newEmail) throws Exception {
        return post("/api/v1/account/email", "{\"currentPassword\":\"" + currentPassword + "\",\"newEmail\":\""
                + newEmail + "\"}", session.accessToken());
    }

    private HttpResponse<String> confirmEmailChange(String token) throws Exception {
        return post("/api/v1/auth/email-change/confirm", "{\"token\":\"" + token + "\"}", null);
    }

    private int loginStatus(String email, String password) throws Exception {
        return post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}", null).statusCode();
    }

    private int refreshStatus(String refreshToken) throws Exception {
        return post("/api/v1/auth/refresh", "{\"refreshToken\":\"" + refreshToken + "\"}", null).statusCode();
    }

    private int auditCount(Session session, String action) {
        return jdbc.queryForObject("select count(*) from audit_events where action = ? and actor_id = ?::uuid",
                Integer.class, action, UUID.fromString(session.accountId()));
    }

    private static JsonNode json(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return JSON.readTree(response.body());
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
