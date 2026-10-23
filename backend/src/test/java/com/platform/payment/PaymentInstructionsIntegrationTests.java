package com.platform.payment;

import com.platform.TestAdmin;
import com.platform.TestcontainersConfiguration;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.repository.AccountRepository;
import org.junit.jupiter.api.AfterEach;
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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M8b over real HTTP against PostgreSQL: an administrator sets how customers pay by bank transfer - English and Khmer,
 * and the owner's KHQR image from the platform's media library. Until saved, the environment's instructions apply.
 * Saving needs the password again, refuses a stale edit, and grants nothing; an order keeps the instructions it was
 * started with; the QR is shown through a short-lived signed link and can't be deleted while it is part of the record.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class PaymentInstructionsIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Instructions-Test-Pass-2026";
    private static final String PATH = "/api/v1/admin/payment-instructions";

    @Value("${local.server.port}") private int port;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    /** Other tests rely on the environment's instructions: this test's versions don't outlive it. */
    @AfterEach
    void backToTheEnvironment() {
        jdbc.update("update payment_orders set instructions_version_id = null where instructions_version_id is not null");
        jdbc.update("delete from payment_instruction_versions");
    }

    @Test
    void anAdministratorSetsHowCustomersPayAndAnIssuedInvoiceKeepsWhatItWasStartedWith() throws Exception {
        String admin = staff(PlatformRole.ADMIN, false);
        String teacher = staff(PlatformRole.TEACHER, false);

        // Before anything is saved: the environment's instructions, said so.
        JsonNode before = json(send("GET", PATH, null, admin), 200);
        assertThat(before.get("current").get("fromEnvironment").asBoolean()).isTrue();
        assertThat(before.get("current").get("instructionsEn").asString()).startsWith("TEST INSTRUCTIONS");
        assertThat(before.get("history")).isEmpty();
        assertThat(send("GET", PATH, null, teacher).statusCode()).isEqualTo(403);
        assertThat(send("GET", PATH, null, registerParent()).statusCode()).isEqualTo(403);

        // The QR: a picture the platform keeps. A teacher's own picture or a missing one is refused; so is a non-picture.
        String qr = json(upload(png(), "khqr.png", admin), 201).get("id").asString();
        String teachersPicture = json(upload(png(), "mine.png", teacher), 201).get("id").asString();
        assertThat(upload("not an image".getBytes(StandardCharsets.UTF_8), "notes.txt", admin).statusCode()).isIn(400, 415, 422);

        String v1 = save(null, true, "Pay to: TEST BANK 000 (not a real account). Quote your reference.", "បង់ទៅ៖ ធនាគារសាកល្បង", qr);
        assertThat(send("PUT", PATH, v1, admin).statusCode()).as("the password again").isEqualTo(403);
        TestAdmin.stepUp(port, admin, PASSWORD);
        assertThat(send("PUT", PATH, save(null, true, "Pay to X", null, teachersPicture), admin).statusCode()).isEqualTo(400);
        assertThat(send("PUT", PATH, save(null, true, "Pay to X", null, UUID.randomUUID().toString()), admin).statusCode()).isEqualTo(404);
        assertThat(send("PUT", PATH, save(null, true, " ", null, null), admin).statusCode()).as("offered without words").isEqualTo(400);
        assertThat(send("PUT", PATH, save(null, true, "x".repeat(2001), null, null), admin).statusCode()).isEqualTo(400);
        assertThat(send("PUT", PATH, v1, teacher).statusCode()).isEqualTo(403);

        JsonNode saved = json(send("PUT", PATH, v1, admin), 200);
        assertThat(saved.get("current").get("version").asInt()).isEqualTo(1);
        assertThat(saved.get("current").get("fromEnvironment").asBoolean()).isFalse();
        assertThat(saved.get("current").get("qrImageUrl").asString()).contains("/api/v1/media/");
        assertThat(send("PUT", PATH, v1, admin).statusCode()).as("edited from a version that is no longer the latest").isEqualTo(409);

        // A family starts a transfer: it sees these instructions, both languages and the QR - and nothing is granted.
        String family = registerParent();
        String plan = familyPlan(admin);
        JsonNode first = json(send("POST", "/api/v1/family/payments", start(plan), family), 201);
        JsonNode checkout = json(send("GET", "/api/v1/payments/" + first.get("id").asString(), null, family), 200).get("checkout");
        assertThat(checkout.get("instructions").asString()).startsWith("Pay to: TEST BANK 000");
        assertThat(checkout.get("instructionsKm").asString()).isEqualTo("បង់ទៅ៖ ធនាគារសាកល្បង");
        HttpResponse<byte[]> picture = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port
                + checkout.get("qrImageUrl").asString().replaceFirst("^https?://[^/]+", ""))).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(picture.statusCode()).isEqualTo(200);
        assertThat(picture.headers().firstValue("Content-Type").orElse("")).startsWith("image/");
        assertThat(json(send("GET", "/api/v1/payments/" + first.get("id").asString(), null, family), 200).get("status").asString())
                .isEqualTo("PENDING");

        // Changed: the issued invoice keeps its words; a new one gets the new words.
        json(send("PUT", PATH, save(1, true, "Pay to: TEST BANK 111 (not a real account).", null, qr), admin), 200);
        assertThat(json(send("GET", "/api/v1/payments/" + first.get("id").asString(), null, family), 200).get("checkout")
                .get("instructions").asString()).startsWith("Pay to: TEST BANK 000");
        String otherFamily = registerParent();
        JsonNode second = json(send("POST", "/api/v1/family/payments", start(plan), otherFamily), 201);
        assertThat(second.get("checkout").get("instructions").asString()).startsWith("Pay to: TEST BANK 111");
        assertThat(second.get("checkout").get("instructionsKm").isNull()).isTrue();

        // The QR is part of the record: it can't be deleted.
        assertThat(send("DELETE", "/api/v1/studio/media/" + qr, null, admin).statusCode()).isEqualTo(409);

        // Two administrators saving from the same version: one wins.
        List<Integer> statuses = concurrently(save(2, true, "Pay to: A", null, null), save(2, true, "Pay to: B", null, null), admin);
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);

        // Switched off: bank transfer is no longer offered and can't be started.
        json(send("PUT", PATH, save(3, false, null, null, null), admin), 200);
        assertThat(json(send("GET", "/api/v1/payments/methods", null, family), 200).get("methods").toString()).doesNotContain("MANUAL");
        assertThat(send("POST", "/api/v1/family/payments", start(plan), registerParent()).statusCode()).isEqualTo(409);

        // Every change recorded - without the account details themselves.
        List<String> details = jdbc.queryForList("select metadata::text from audit_events where action = 'PAYMENT_INSTRUCTIONS_CHANGED' "
                + "and actor_id = (select id from accounts where id = ?::uuid)", String.class, adminId(admin));
        assertThat(details).hasSize(4);
        assertThat(String.join(" ", details)).doesNotContain("TEST BANK");
    }

    // ------------------------------------------------------------------ helpers

    private final java.util.Map<String, UUID> ids = new java.util.concurrent.ConcurrentHashMap<>();

    private UUID adminId(String token) {
        return ids.get(token);
    }

    private static String save(Integer expected, boolean enabled, String en, String km, String qr) {
        return "{\"expectedVersion\":" + expected + ",\"enabled\":" + enabled + ",\"instructionsEn\":" + quote(en)
                + ",\"instructionsKm\":" + quote(km) + ",\"qrAssetId\":" + quote(qr) + "}";
    }

    private static String quote(String text) {
        return text == null ? "null" : "\"" + text.replace("\"", "\\\"") + "\"";
    }

    private static String start(String plan) {
        return "{\"planId\":\"" + plan + "\",\"method\":\"MANUAL\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}";
    }

    private List<Integer> concurrently(String a, String b, String admin) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Integer>> futures = new ArrayList<>();
            for (String body : List.of(a, b)) {
                futures.add(pool.submit(() -> {
                    go.await();
                    return send("PUT", PATH, body, admin).statusCode();
                }));
            }
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get());
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    private String familyPlan(String admin) throws Exception {
        String id = json(send("POST", "/api/v1/admin/plans", "{\"code\":\"INSTR_" + UUID.randomUUID().toString().substring(0, 8)
                + "\",\"name\":\"Family Plus\"}", admin), 201).get("plan").get("id").asString();
        json(send("PUT", "/api/v1/admin/plans/" + id, "{\"name\":\"Family Plus\",\"selfService\":true,\"priceAmountMinor\":2000,"
                + "\"priceCurrency\":\"USD\",\"billingInterval\":\"MONTH\",\"limits\":{\"CHILDREN_MAX\":4}}", admin), 200);
        json(send("POST", "/api/v1/admin/plans/" + id + "/activate", "{}", admin), 200);
        return id;
    }

    private static byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, java.util.concurrent.ThreadLocalRandom.current().nextInt(1, 0xFFFFFF));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private HttpResponse<String> upload(byte[] content, String filename, String token) throws Exception {
        String boundary = "bk" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + filename
                + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(content);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return http.send(TestAdmin.withStepUp(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/studio/media"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary).header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())), token).build(), HttpResponse.BodyHandlers.ofString());
    }

    private String staff(PlatformRole role, boolean stepUp) throws Exception {
        String email = "instr-" + role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        Account account = accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), role));
        String token = json(send("POST", "/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", null), 200)
                .get("accessToken").asString();
        ids.put(token, account.getId());
        return stepUp ? TestAdmin.stepUp(port, token, PASSWORD) : token;
    }

    private String registerParent() throws Exception {
        return json(send("POST", "/api/v1/auth/register", "{\"email\":\"instr-parent-" + UUID.randomUUID() + "@example.com\",\"password\":\""
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
            TestAdmin.withStepUp(request, bearer);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

}
