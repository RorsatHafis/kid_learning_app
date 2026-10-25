package com.platform.identity.phone;

import com.icegreen.greenmail.util.GreenMail;
import com.platform.TestcontainersConfiguration;
import jakarta.mail.Message;
import jakarta.mail.internet.MimeMessage;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M9b over real HTTP against PostgreSQL, the local test sender writing each text to the test mail server. A code needs
 * the password, works once, for 10 minutes and 5 tries, is kept only as a digest; asking is limited per minute and per
 * hour; a number verified on another account is answered the same but gets no text and can't be verified; removing the
 * number needs the password; nothing about the number is audited beyond its last digits.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class PhoneVerificationIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Phone-Verify-Pass-2026";
    private static final Pattern CODE = Pattern.compile("code is (\\d{6})");

    @Value("${local.server.port}") private int port;
    @Autowired private GreenMail greenMail;
    @Autowired private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void aParentAddsAPhoneWithTheCodeSentToIt() throws Exception {
        String parent = registerParent();
        String typed = "012 " + digits(6);
        String phone = "+85512" + typed.substring(4);
        assertThat(json(send("GET", "/api/v1/account/phone", null, parent), 200).get("available").asBoolean()).isTrue();

        assertThat(send("POST", "/api/v1/account/phone/code", body("wrong-password", typed), parent).statusCode()).isEqualTo(422);
        assertThat(send("POST", "/api/v1/account/phone/code", body(PASSWORD, "023 123 456"), parent).statusCode()).as("a landline")
                .isEqualTo(400);
        JsonNode sent = json(send("POST", "/api/v1/account/phone/code", body(PASSWORD, typed), parent), 202);
        assertThat(sent.get("phone").asString()).isEqualTo("+855 ••• ••• " + phone.substring(phone.length() - 3));
        String code = codeSentTo(phone);
        assertThat(jdbc.queryForObject("select code_hash from phone_verifications where phone_e164 = ?", String.class, phone))
                .doesNotContain(code);
        HttpResponse<String> again = send("POST", "/api/v1/account/phone/code", body(PASSWORD, typed), parent);
        assertThat(again.statusCode()).as("once a minute").isEqualTo(429);

        String wrong = code.equals("000000") ? "111111" : "000000";
        HttpResponse<String> refused = send("POST", "/api/v1/account/phone/confirm", "{\"code\":\"" + wrong + "\"}", parent);
        assertThat(refused.statusCode()).isEqualTo(422);
        assertThat(JSON.readTree(refused.body()).get("errors").get("code").asString()).isEqualTo("That code isn't right");
        JsonNode verified = json(send("POST", "/api/v1/account/phone/confirm", "{\"code\":\"" + code + "\"}", parent), 200);
        assertThat(verified.get("phone").asString()).isEqualTo(phone);
        assertThat(verified.get("display").asString()).startsWith("+855 12 ");
        assertThat(json(send("GET", "/api/v1/account", null, parent), 200).get("phone").asString()).isEqualTo(phone);
        assertThat(send("POST", "/api/v1/account/phone/confirm", "{\"code\":\"" + code + "\"}", parent).statusCode())
                .as("a code works once").isEqualTo(409);
        assertThat(json(send("GET", "/api/v1/account/data-export", null, parent), 200).get("account").get("phone").asString())
                .isEqualTo(phone);

        // Another account asking for the same number: answered the same, but no text and no way to verify it.
        String other = registerParent();
        JsonNode same = json(send("POST", "/api/v1/account/phone/code", body(PASSWORD, typed), other), 202);
        assertThat(same.get("phone").asString()).isEqualTo(sent.get("phone").asString());
        assertThat(messagesTo(phone)).as("no second text to that number").hasSize(1);
        assertThat(send("POST", "/api/v1/account/phone/confirm", "{\"code\":\"" + code + "\"}", other).statusCode()).isEqualTo(422);

        // Removing it needs the password; the audit keeps only the last digits.
        assertThat(send("POST", "/api/v1/account/phone/remove", "{\"password\":\"nope\"}", parent).statusCode()).isEqualTo(422);
        assertThat(json(send("POST", "/api/v1/account/phone/remove", "{\"password\":\"" + PASSWORD + "\"}", parent), 200).get("phone").isNull())
                .isTrue();
        List<String> audit = jdbc.queryForList("select action || ' ' || metadata::text from audit_events where action like 'PHONE_%' "
                + "and resource_id = (select id from accounts where id = (select account_id from phone_verifications where phone_e164 = ? "
                + "and sent order by issued_at limit 1))", String.class, phone);
        assertThat(audit).anyMatch(line -> line.startsWith("PHONE_VERIFIED")).anyMatch(line -> line.startsWith("PHONE_REMOVED"))
                .noneMatch(line -> line.contains(phone.substring(1)));
    }

    @Test
    void aCodeAllowsFiveTriesAndTenMinutesAndAnAccountFiveCodesAnHour() throws Exception {
        String parent = registerParent();
        String typed = "+855 12 " + digits(6);
        json(send("POST", "/api/v1/account/phone/code", body(PASSWORD, typed), parent), 202);
        for (int i = 0; i < 5; i++) {
            assertThat(send("POST", "/api/v1/account/phone/confirm", "{\"code\":\"12345x\"}", parent).statusCode()).isEqualTo(422);
        }
        String code = codeSentTo(PhoneNumbers.normalize(typed));
        assertThat(send("POST", "/api/v1/account/phone/confirm", "{\"code\":\"" + code + "\"}", parent).statusCode())
                .as("five wrong tries end the code").isEqualTo(409);

        // A new code (the minute has passed) - expired after ten minutes.
        String account = json(send("GET", "/api/v1/account", null, parent), 200).get("id").asString();
        jdbc.update("update phone_verifications set issued_at = issued_at - interval '2 minutes', expires_at = expires_at - interval '2 minutes' "
                + "where account_id = ?::uuid", account);
        json(send("POST", "/api/v1/account/phone/code", body(PASSWORD, typed), parent), 202);
        jdbc.update("update phone_verifications set issued_at = issued_at - interval '11 minutes', expires_at = expires_at - interval '11 minutes' "
                + "where account_id = ?::uuid and confirmed_at is null and superseded_at is null", account);
        assertThat(send("POST", "/api/v1/account/phone/confirm", "{\"code\":\"" + codeSentTo(PhoneNumbers.normalize(typed)) + "\"}", parent)
                .statusCode()).isEqualTo(409);

        // Five codes an hour per account: the sixth is refused.
        for (int i = 0; i < 3; i++) {
            jdbc.update("update phone_verifications set issued_at = issued_at - interval '61 seconds', expires_at = expires_at - interval '61 seconds' "
                    + "where account_id = ?::uuid", account);
            json(send("POST", "/api/v1/account/phone/code", body(PASSWORD, typed), parent), 202);
        }
        jdbc.update("update phone_verifications set issued_at = issued_at - interval '61 seconds', expires_at = expires_at - interval '61 seconds' "
                + "where account_id = ?::uuid", account);
        HttpResponse<String> capped = send("POST", "/api/v1/account/phone/code", body(PASSWORD, typed), parent);
        assertThat(capped.statusCode()).isEqualTo(429);
        assertThat(JSON.readTree(capped.body()).get("retryAfterSeconds").asLong()).isPositive();
    }

    @Test
    void aVerifiedNumberSignsInAndResetsAForgottenPasswordAndNothingSaysWhoseANumberIs() throws Exception {
        String email = "phone-" + UUID.randomUUID() + "@example.com";
        String parent = json(send("POST", "/api/v1/auth/register", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null), 201).get("accessToken").asString();
        String typed = "012 " + digits(6);
        String phone = PhoneNumbers.normalize(typed);

        // Not verified yet: signing in with it reads like an unknown address.
        json(send("POST", "/api/v1/account/phone/code", body(PASSWORD, typed), parent), 202);
        assertThat(send("POST", "/api/v1/auth/login", login(typed, PASSWORD), null).statusCode()).isEqualTo(401);
        json(send("POST", "/api/v1/account/phone/confirm", "{\"code\":\"" + codeSentTo(phone) + "\"}", parent), 200);

        // Verified: it signs in, written either way; a wrong password, junk or another number don't.
        assertThat(send("POST", "/api/v1/auth/login", login(typed, PASSWORD), null).statusCode()).isEqualTo(200);
        assertThat(send("POST", "/api/v1/auth/login", login(phone, PASSWORD), null).statusCode()).isEqualTo(200);
        assertThat(send("POST", "/api/v1/auth/login", login(typed, "wrong-" + PASSWORD), null).statusCode()).isEqualTo(401);
        assertThat(send("POST", "/api/v1/auth/login", login("not a number", PASSWORD), null).statusCode()).isEqualTo(401);

        // Asking for a reset answers the same for any number; only the real one gets a text.
        String unknown = "+85512" + digits(6);
        assertThat(send("POST", "/api/v1/auth/password-reset/phone", "{\"phone\":\"" + unknown + "\"}", null).statusCode()).isEqualTo(202);
        assertThat(messagesTo(unknown)).isEmpty();
        assertThat(send("POST", "/api/v1/auth/password-reset/phone", "{\"phone\":\"junk\"}", null).statusCode()).isEqualTo(202);
        assertThat(send("POST", "/api/v1/auth/password-reset/phone", "{\"phone\":\"" + typed + "\"}", null).statusCode()).isEqualTo(202);
        String code = resetCodeSentTo(phone);

        // One refusal for any wrong code; a weak password keeps the code usable; the code works once.
        String newPassword = "Phone-Reset-New-2026";
        HttpResponse<String> wrong = send("POST", "/api/v1/auth/password-reset/phone/confirm", reset(typed, code.equals("000000") ? "111111" : "000000", newPassword), null);
        assertThat(wrong.statusCode()).isEqualTo(422);
        HttpResponse<String> nobody = send("POST", "/api/v1/auth/password-reset/phone/confirm", reset(unknown, code, newPassword), null);
        assertThat(JSON.readTree(nobody.body()).get("detail").asString()).isEqualTo(JSON.readTree(wrong.body()).get("detail").asString());
        assertThat(send("POST", "/api/v1/auth/password-reset/phone/confirm", reset(typed, code, "short"), null).statusCode()).isEqualTo(422);
        assertThat(send("POST", "/api/v1/auth/password-reset/phone/confirm", reset(typed, code, newPassword), null).statusCode()).isEqualTo(204);
        assertThat(send("POST", "/api/v1/auth/password-reset/phone/confirm", reset(typed, code, newPassword), null).statusCode()).isEqualTo(422);

        // Every session ended; the new password works; the changed-password email went to the account's address.
        assertThat(send("GET", "/api/v1/account", null, parent).statusCode()).isEqualTo(401);
        assertThat(send("POST", "/api/v1/auth/login", login(email, PASSWORD), null).statusCode()).isEqualTo(401);
        assertThat(send("POST", "/api/v1/auth/login", login(email, newPassword), null).statusCode()).isEqualTo(200);
        long deadline = System.currentTimeMillis() + 10_000;
        boolean noticed = false;
        while (!noticed && System.currentTimeMillis() < deadline) {
            for (MimeMessage message : greenMail.getReceivedMessages()) {
                noticed |= message.getRecipients(Message.RecipientType.TO)[0].toString().equalsIgnoreCase(email)
                        && message.getSubject().contains("password was changed");
            }
            Thread.sleep(50);
        }
        assertThat(noticed).as("changed-password email").isTrue();
    }

    // ------------------------------------------------------------------ helpers

    private static String login(String name, String password) {
        return "{\"email\":\"" + name + "\",\"password\":\"" + password + "\"}";
    }

    private static String reset(String phone, String code, String newPassword) {
        return "{\"phone\":\"" + phone + "\",\"code\":\"" + code + "\",\"newPassword\":\"" + newPassword + "\"}";
    }

    /** The newest password-reset code texted to this number. */
    private String resetCodeSentTo(String phone) throws Exception {
        Pattern reset = Pattern.compile("reset code is (\\d{6})");
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            for (MimeMessage message : messagesTo(phone)) {
                Matcher matcher = reset.matcher(message.getContent().toString());
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
            Thread.sleep(50);
        }
        throw new AssertionError("No reset text to " + phone);
    }


    private static String digits(int n) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < n; i++) {
            out.append(ThreadLocalRandom.current().nextInt(10));
        }
        return out.toString();
    }

    private static String body(String password, String phone) {
        return "{\"password\":\"" + password + "\",\"phone\":\"" + phone + "\"}";
    }

    /** The newest code texted to this number (written to the test mail server by the local test sender). */
    private String codeSentTo(String phone) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            List<MimeMessage> sent = messagesTo(phone);
            if (!sent.isEmpty()) {
                Matcher matcher = CODE.matcher(sent.getLast().getContent().toString());
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
            Thread.sleep(50);
        }
        throw new AssertionError("No text to " + phone);
    }

    private List<MimeMessage> messagesTo(String phone) throws Exception {
        String to = "sms-" + phone.substring(1) + "@sms.test";
        List<MimeMessage> found = new java.util.ArrayList<>();
        for (MimeMessage message : greenMail.getReceivedMessages()) {
            if (message.getRecipients(Message.RecipientType.TO)[0].toString().equalsIgnoreCase(to)) {
                found.add(message);
            }
        }
        return found;
    }

    private String registerParent() throws Exception {
        return json(send("POST", "/api/v1/auth/register", "{\"email\":\"phone-" + UUID.randomUUID() + "@example.com\",\"password\":\""
                + PASSWORD + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null), 201).get("accessToken").asString();
    }

    private static JsonNode json(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> send(String method, String path, String body, String bearer) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

}
