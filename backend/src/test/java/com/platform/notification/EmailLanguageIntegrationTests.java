package com.platform.notification;

import com.icegreen.greenmail.util.GreenMail;
import com.platform.PaidSchools;
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
import java.time.LocalDate;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every transactional email is written in its recipient's interface language: the account's saved
 * language, or - for someone a school or an administrator has just created an account for - the
 * language the inviter is using. Only the words change: the link is the same page and a working
 * single-use token in either language.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class EmailLanguageIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Email-Language-Mango-2026";
    private static final String NEW_PASSWORD = "Email-Language-Papaya-2026";
    private static final Pattern LINK = Pattern.compile("(http://localhost:5173/[a-z-]+)#token=([A-Za-z0-9_-]+)");

    @Value("${local.server.port}") private int port;
    @Autowired private org.springframework.context.ApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private GreenMail greenMail;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void aKhmerAccountGetsKhmerEmailsWithTheSameWorkingLinks() throws Exception {
        String email = register("km");
        MimeMessage verify = awaitMail(email, "បញ្ជាក់អ៊ីមែល");
        assertThat(verify.getSubject()).isEqualTo("បញ្ជាក់អ៊ីមែលរបស់អ្នកសម្រាប់ Bookeyond Kid");
        assertThat(body(verify)).startsWith("សួស្ដី,").contains("24 ម៉ោង").doesNotContain("Hello", "Please confirm");
        assertThat(link(verify).page()).isEqualTo("http://localhost:5173/verify-email");
        assertThat(post("/api/v1/auth/email-verification/confirm", "{\"token\":\"" + link(verify).token() + "\"}", null, null)
                .statusCode()).as("the Khmer email's link works").isEqualTo(204);

        assertThat(post("/api/v1/auth/password-reset", "{\"email\":\"" + email + "\"}", null, null).statusCode()).isEqualTo(202);
        MimeMessage reset = awaitMail(email, "កំណត់ពាក្យសម្ងាត់");
        assertThat(body(reset)).contains("30 នាទី");
        assertThat(link(reset).page()).isEqualTo("http://localhost:5173/reset-password");
        assertThat(post("/api/v1/auth/password-reset/confirm", "{\"token\":\"" + link(reset).token() + "\",\"newPassword\":\""
                + NEW_PASSWORD + "\"}", null, null).statusCode()).isEqualTo(204);
        assertThat(post("/api/v1/auth/password-reset/confirm", "{\"token\":\"" + link(reset).token() + "\",\"newPassword\":\""
                + PASSWORD + "\"}", null, null).statusCode()).as("still single-use").isEqualTo(400);
        MimeMessage changed = awaitMail(email, "ត្រូវបានផ្លាស់ប្ដូរ");
        assertThat(body(changed)).contains("http://localhost:5173/forgot-password");
    }

    @Test
    void anEnglishAccountKeepsEnglishEmailsWhateverLanguageTheRequestAsksIn() throws Exception {
        String email = register(null);
        MimeMessage verify = awaitMail(email, "Confirm your email");
        assertThat(body(verify)).startsWith("Hello,").contains("expires in 24 hours");

        // The recipient's saved language decides, not the language of whoever triggered the email.
        assertThat(post("/api/v1/auth/password-reset", "{\"email\":\"" + email + "\"}", null, "km").statusCode()).isEqualTo(202);
        MimeMessage reset = awaitMail(email, "Reset your Bookeyond Kid password");
        assertThat(body(reset)).contains("expires in 30 minutes");
        assertThat(link(reset).page()).isEqualTo("http://localhost:5173/reset-password");
    }

    @Test
    void aNewlyInvitedTeacherStartsInTheLanguageTheInviterIsUsing() throws Exception {
        String principal = token(PlatformRole.PRINCIPAL, "en");
        String schoolId = json(post("/api/v1/schools", "{\"name\":\"Sala Sunrise\"}", principal, null), 201).get("id").asString();
        PaidSchools.pay(context, UUID.fromString(schoolId));

        String khmerInvitee = "invitee-km-" + UUID.randomUUID() + "@example.com";
        json(post("/api/v1/schools/" + schoolId + "/staff", "{\"email\":\"" + khmerInvitee + "\"}", principal, "km"), 201);
        MimeMessage setup = awaitMail(khmerInvitee, "អញ្ជើញ");
        assertThat(setup.getSubject()).isEqualTo("អ្នកត្រូវបានអញ្ជើញឱ្យបង្រៀននៅ Sala Sunrise លើ Bookeyond Kid");
        assertThat(body(setup)).contains("Sala Sunrise បានអញ្ជើញ", "7 ថ្ងៃ", "ភ្លេចពាក្យសម្ងាត់?");
        assertThat(languageOf(khmerInvitee)).as("their account starts in it too").isEqualTo("km");
        assertThat(link(setup).page()).isEqualTo("http://localhost:5173/set-up-account");
        assertThat(post("/api/v1/auth/account-setup/confirm", "{\"token\":\"" + link(setup).token() + "\",\"newPassword\":\""
                + PASSWORD + "\"}", null, null).statusCode()).isEqualTo(204);

        String englishInvitee = "invitee-en-" + UUID.randomUUID() + "@example.com";
        json(post("/api/v1/schools/" + schoolId + "/staff", "{\"email\":\"" + englishInvitee + "\"}", principal, null), 201);
        assertThat(awaitMail(englishInvitee, "You're invited to teach at Sala Sunrise").getSubject()).startsWith("You're invited");
        assertThat(languageOf(englishInvitee)).isEqualTo("en");

        // Someone who already has an account is written to in their own language, not the inviter's.
        String khmerTeacher = account(PlatformRole.TEACHER, "km");
        json(post("/api/v1/schools/" + schoolId + "/staff", "{\"email\":\"" + khmerTeacher + "\"}", principal, "en"), 201);
        MimeMessage invite = awaitMail(khmerTeacher, "អញ្ជើញ");
        assertThat(body(invite)).contains("http://localhost:5173/login").doesNotContain("Hello");
    }

    @Test
    void aProvisionedStaffAccountStartsInTheAdministratorsLanguage() throws Exception {
        String admin = token(PlatformRole.ADMIN, "en");
        String email = "provisioned-km-" + UUID.randomUUID() + "@example.com";
        json(post("/api/v1/admin/staff", "{\"email\":\"" + email + "\",\"role\":\"PRINCIPAL\"}", admin, "km"), 201);
        MimeMessage setup = awaitMail(email, "គណនី Bookeyond Kid");
        assertThat(body(setup)).contains("បានបង្កើតគណនីនាយកសាលាសម្រាប់អ្នក");
        assertThat(languageOf(email)).isEqualTo("km");
        assertThat(post("/api/v1/auth/account-setup/confirm", "{\"token\":\"" + link(setup).token() + "\",\"newPassword\":\""
                + PASSWORD + "\"}", null, null).statusCode()).isEqualTo(204);
    }

    @Test
    void anEmailChangeIsToldToEachMailboxInItsOwnersLanguage() throws Exception {
        String englishEmail = register(null);
        String englishToken = login(englishEmail);
        String khmerHolder = account(PlatformRole.PARENT, "km");

        // The address belongs to a Khmer account: that owner is told, in Khmer.
        assertThat(post("/api/v1/account/email", "{\"currentPassword\":\"" + PASSWORD + "\",\"newEmail\":\"" + khmerHolder + "\"}",
                englishToken, null).statusCode()).isEqualTo(202);
        assertThat(body(awaitMail(khmerHolder, "មាននរណាម្នាក់"))).contains("http://localhost:5173/forgot-password");

        // A Khmer account moving its address: the new mailbox confirms in Khmer, the old one is told in Khmer.
        String khmerEmail = register("km");
        String khmerToken = login(khmerEmail);
        String newEmail = "moved-" + UUID.randomUUID() + "@example.com";
        assertThat(post("/api/v1/account/email", "{\"currentPassword\":\"" + PASSWORD + "\",\"newEmail\":\"" + newEmail + "\"}",
                khmerToken, null).statusCode()).isEqualTo(202);
        MimeMessage confirm = awaitMail(newEmail, "អ៊ីមែលថ្មី");
        assertThat(link(confirm).page()).isEqualTo("http://localhost:5173/confirm-email-change");
        assertThat(post("/api/v1/auth/email-change/confirm", "{\"token\":\"" + link(confirm).token() + "\"}", null, null).statusCode())
                .isEqualTo(204);
        assertThat(body(awaitMail(khmerEmail, "ត្រូវបានផ្លាស់ប្ដូរ"))).contains("mo***@example.com").doesNotContain(newEmail);
    }

    @Test
    void aKhmerFamilysErasureAndPlanEmailsAreInKhmerWithKhmerDates() throws Exception {
        String email = register("km");
        String token = login(email);
        String childId = json(post("/api/v1/children", "{\"displayName\":\"Sokha\",\"dateOfBirth\":\""
                + LocalDate.now().minusYears(6) + "\"}", token, null), 201).get("id").asString();
        json(post("/api/v1/children/" + childId + "/deletion", "{\"currentPassword\":\"" + PASSWORD + "\"}", token, null), 202);
        String scheduled = body(awaitMail(email, "Sokha នឹងត្រូវបានលុប"));
        assertThat(scheduled).contains("រក្សា Sokha").containsPattern("នៅថ្ងៃទី \\d{1,2} (មករា|កុម្ភៈ|មីនា|មេសា|ឧសភា|មិថុនា|កក្កដា"
                + "|សីហា|កញ្ញា|តុលា|វិច្ឆិកា|ធ្នូ) \\d{4}");

        String admin = token(PlatformRole.ADMIN, "en");
        String planId = json(post("/api/v1/admin/plans", "{\"code\":\"KM_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()
                + "\",\"name\":\"Family Plus\",\"description\":\"For tests\"}", admin, null), 201).get("plan").get("id").asString();
        json(put("/api/v1/admin/plans/" + planId, "{\"name\":\"Family Plus\",\"description\":\"For tests\",\"selfService\":true,"
                + "\"limits\":{\"CHILDREN_MAX\":5,\"JOURNEYS_PER_CHILD_MAX\":5}}", admin), 200);
        json(post("/api/v1/admin/plans/" + planId + "/activate", "{}", admin, null), 200);
        json(post("/api/v1/family/plan/request", "{\"planId\":\"" + planId + "\"}", token, null), 200);
        // The plan's name is the administrator's wording: quoted as it is.
        assertThat(body(awaitMail(email, "សំណើរបស់អ្នកសម្រាប់កញ្ចប់ Family Plus")))
                .contains("http://localhost:5173/parent/account");
    }

    // ------------------------------------------------------------------ helpers

    private record Link(String page, String token) {
    }

    private static Link link(MimeMessage message) throws Exception {
        Matcher matcher = LINK.matcher(body(message));
        assertThat(matcher.find()).as("a link in the email").isTrue();
        return new Link(matcher.group(1), matcher.group(2));
    }

    private static String body(MimeMessage message) throws Exception {
        return (String) message.getContent();
    }

    /** The newest email to {@code to} whose subject contains {@code subjectPart}. */
    private MimeMessage awaitMail(String to, String subjectPart) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (true) {
            MimeMessage found = null;
            for (MimeMessage message : greenMail.getReceivedMessages()) {
                if (message.getRecipients(Message.RecipientType.TO)[0].toString().equalsIgnoreCase(to)
                        && message.getSubject().contains(subjectPart)) {
                    found = message;
                }
            }
            if (found != null) {
                return found;
            }
            assertThat(System.currentTimeMillis()).as("email '%s' to %s", subjectPart, to).isLessThan(deadline);
            Thread.sleep(50);
        }
    }

    private String languageOf(String email) {
        return jdbc.queryForObject("select preferred_language from accounts where normalized_email = ?", String.class,
                email.toLowerCase());
    }

    /** Signs up through the API, in {@code language} (null: none chosen). */
    private String register(String language) throws Exception {
        String email = "mail-language-" + UUID.randomUUID() + "@example.com";
        json(post("/api/v1/auth/register", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"idempotencyKey\":\""
                + UUID.randomUUID() + "\"" + (language == null ? "" : ",\"preferredLanguage\":\"" + language + "\"") + "}", null, null), 201);
        return email;
    }

    private String account(PlatformRole role, String language) {
        String email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        Account account = Account.register(email, passwordEncoder.encode(PASSWORD), role);
        account.changeLanguage(language);
        accountRepository.saveAndFlush(account);
        return email;
    }

    private String token(PlatformRole role, String language) throws Exception {
        return login(account(role, language));
    }

    private String login(String email) throws Exception {
        return json(post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", null, null), 200)
                .get("accessToken").asString();
    }

    private static JsonNode json(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> post(String path, String body, String bearer, String language) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        if (language != null) {
            request.header("Accept-Language", language);
        }
        return send(request, bearer);
    }

    private HttpResponse<String> put(String path, String body, String bearer) throws Exception {
        return send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json").PUT(HttpRequest.BodyPublishers.ofString(body)), bearer);
    }

    private HttpResponse<String> send(HttpRequest.Builder request, String bearer) throws Exception {
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

}
