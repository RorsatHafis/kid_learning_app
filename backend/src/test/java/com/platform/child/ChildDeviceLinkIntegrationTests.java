package com.platform.child;

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
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M10a over real HTTP against PostgreSQL: a parent opens one child's learning space on another device. The code is
 * single use, short-lived and says nothing when wrong; the parent approves the waiting device; the device's sign-in
 * opens only that child's learning (an endpoint sweep: parent pages, settings, time, siblings, billing, the account
 * - all refused) and stays so across refresh; the parent sees it among their devices and signing it out ends it at
 * once; a child has a limited number of devices.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class ChildDeviceLinkIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Child-Device-Pass-2026";
    private static final String TABLET = "Mozilla/5.0 (Linux; Android 14; Tablet) Chrome/130";

    @Value("${local.server.port}") private int port;
    @Autowired private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void aParentOpensOneChildsSpaceOnAnotherDeviceAndThatDeviceCanDoNothingElse() throws Exception {
        String parent = registerParent();
        String child = createChild(parent, "Dara");
        String sibling = createChild(parent, "Sokha");

        // A code nobody made, or a malformed one: the same refusal.
        assertThat(claim("ZZZZZZ").statusCode()).isEqualTo(400);
        assertThat(claim("nope").statusCode()).isEqualTo(400);

        JsonNode code = json(send("POST", "/api/v1/children/" + child + "/device-codes", null, parent, null), 201);
        String codeId = code.get("id").asString();
        String raw = code.get("code").asString();
        assertThat(raw).matches("[A-HJ-KM-NP-Z2-9]{6}");
        // Another family can't see or approve it.
        String stranger = registerParent();
        assertThat(send("GET", "/api/v1/children/" + child + "/device-codes/" + codeId, null, stranger, null).statusCode()).isEqualTo(404);
        assertThat(send("POST", "/api/v1/children/" + child + "/device-codes", null, stranger, null).statusCode()).isEqualTo(404);

        // Typed on the tablet (any case, with a dash): it waits; the code can't be used twice.
        JsonNode claimed = json(claim(raw.substring(0, 3).toLowerCase() + "-" + raw.substring(3)), 200);
        assertThat(claim(raw).statusCode()).as("single use").isEqualTo(400);
        String claimId = claimed.get("claimId").asString();
        String secret = claimed.get("secret").asString();
        assertThat(collect(claimId, "not-the-secret").statusCode()).isEqualTo(400);
        assertThat(json(collect(claimId, secret), 202).get("status").asString()).isEqualTo("WAITING");
        assertThat(send("POST", "/api/v1/children/" + child + "/device-codes/" + codeId + "/approve", null, stranger, null).statusCode())
                .isEqualTo(404);

        // The parent sees which device waits, and approves it.
        JsonNode waiting = json(send("GET", "/api/v1/children/" + child + "/device-codes/" + codeId, null, parent, null), 200);
        assertThat(waiting.get("status").asString()).isEqualTo("CLAIMED");
        assertThat(waiting.get("deviceUserAgent").asString()).isEqualTo(TABLET);
        json(send("POST", "/api/v1/children/" + child + "/device-codes/" + codeId + "/approve", null, parent, null), 200);

        // The tablet collects its sign-in once: for this child only, without the parent's email.
        JsonNode signedIn = json(collect(claimId, secret), 200);
        assertThat(signedIn.get("childId").asString()).isEqualTo(child);
        assertThat(signedIn.get("childName").asString()).isEqualTo("Dara");
        assertThat(collect(claimId, secret).statusCode()).as("given once").isEqualTo(409);
        String device = signedIn.get("accessToken").asString();

        // What the child space needs works - including a whole activity.
        assertThat(send("GET", "/api/v1/children/" + child, null, device, null).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/v1/children/" + child + "/today", null, device, null).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/v1/children/" + child + "/skills", null, device, null).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/v1/children/" + child + "/break-reminder", null, device, null).statusCode()).isEqualTo(200);
        assertThat(send("PUT", "/api/v1/children/" + child + "/language", "{\"language\":\"km\"}", device, null).statusCode()).isEqualTo(200);
        assertThat(send("PUT", "/api/v1/children/" + sibling + "/language", "{\"language\":\"km\"}", device, null).statusCode())
                .isEqualTo(403);
        JsonNode enrollment = json(send("GET", "/api/v1/children/" + child + "/enrollments", null, device, null), 200).get(0);
        String enrollmentId = enrollment.get("enrollmentId").asString();
        assertThat(send("GET", "/api/v1/enrollments/" + enrollmentId + "/journey", null, device, null).statusCode()).isEqualTo(200);
        JsonNode next = json(send("GET", "/api/v1/enrollments/" + enrollmentId + "/next-activity", null, device, null), 200);
        String version = next.get("activityVersionId").asString();
        JsonNode content = json(send("GET", "/api/v1/activity-versions/" + version + "?childId=" + child, null, device, null), 200);
        String attempt = json(send("POST", "/api/v1/children/" + child + "/activity-attempts", "{\"activityVersionId\":\"" + version
                + "\",\"learningPathItemId\":\"" + next.get("id").asString() + "\"}", device, null), 201).get("id").asString();
        for (JsonNode item : content.get("items")) {
            json(send("POST", "/api/v1/activity-attempts/" + attempt + "/answers", "{\"questionVersionId\":\""
                    + item.get("questionVersionId").asString() + "\",\"submittedAnswer\":\"3\",\"idempotencyKey\":\"" + UUID.randomUUID()
                    + "\"}", device, null), 201);
        }
        assertThat(send("GET", "/api/v1/activity-attempts/" + attempt + "/answers", null, device, null).statusCode()).isEqualTo(200);
        json(send("POST", "/api/v1/activity-attempts/" + attempt + "/complete", "", device, null), 200);

        // Everything else is refused: the sibling, the parent's pages and settings, billing, the account.
        JsonNode siblingEnrollment = json(send("GET", "/api/v1/children/" + sibling + "/enrollments", null, parent, null), 200).get(0);
        JsonNode siblingNext = siblingEnrollment.get("nextStep");
        String siblingAttempt = json(send("POST", "/api/v1/children/" + sibling + "/activity-attempts", "{\"activityVersionId\":\""
                + siblingNext.get("activityVersionId").asString() + "\",\"learningPathItemId\":\""
                + siblingNext.get("learningPathItemId").asString() + "\"}", parent, null), 201).get("id").asString();
        String[][] refused = {
                {"GET", "/api/v1/children/" + sibling},
                {"GET", "/api/v1/children/" + sibling + "/enrollments"},
                {"POST", "/api/v1/children/" + sibling + "/activity-attempts"},
                {"GET", "/api/v1/enrollments/" + siblingEnrollment.get("enrollmentId").asString() + "/journey"},
                {"GET", "/api/v1/activity-attempts/" + siblingAttempt},
                {"GET", "/api/v1/activity-attempts/" + siblingAttempt + "/answers"},
                {"POST", "/api/v1/activity-attempts/" + siblingAttempt + "/complete"},
                {"GET", "/api/v1/activity-versions/" + version + "?childId=" + sibling},
                {"GET", "/api/v1/activity-versions/" + version},
                {"GET", "/api/v1/children"},
                {"POST", "/api/v1/children"},
                {"GET", "/api/v1/children/" + child + "/progress"},
                {"GET", "/api/v1/children/" + child + "/daily-limit"},
                {"PUT", "/api/v1/children/" + child + "/daily-limit"},
                {"POST", "/api/v1/children/" + child + "/today/extensions"},
                {"PUT", "/api/v1/children/" + child + "/break-reminder"},
                {"POST", "/api/v1/children/" + child + "/device-codes"},
                {"POST", "/api/v1/children/" + child + "/enrollments"},
                {"GET", "/api/v1/account"},
                {"GET", "/api/v1/account/devices"},
                {"POST", "/api/v1/account/sign-out-everywhere"},
                {"GET", "/api/v1/family/plan"},
                {"GET", "/api/v1/family/payments"},
                {"GET", "/api/v1/account/data-export"},
                {"GET", "/api/v1/curricula"},
                {"GET", "/api/v1/schools/mine"},
        };
        for (String[] call : refused) {
            HttpResponse<String> response = send(call[0], call[1], call[0].equals("GET") ? null : "{}", device, null);
            assertThat(response.statusCode()).as(call[0] + " " + call[1]).isEqualTo(403);
            assertThat(JSON.readTree(response.body()).get("childDeviceScope").asBoolean()).as(call[1]).isTrue();
        }

        // Refreshed, it stays this child's - and still without the parent's email.
        JsonNode refreshed = json(send("POST", "/api/v1/auth/refresh", "{\"refreshToken\":\"" + signedIn.get("refreshToken").asString()
                + "\"}", null, null), 200);
        assertThat(refreshed.get("childId").asString()).isEqualTo(child);
        assertThat(refreshed.get("email").isNull()).isTrue();
        String refreshedDevice = refreshed.get("accessToken").asString();
        assertThat(send("GET", "/api/v1/children/" + sibling, null, refreshedDevice, null).statusCode()).isEqualTo(403);
        assertThat(send("GET", "/api/v1/children/" + child, null, refreshedDevice, null).statusCode()).isEqualTo(200);

        // The parent sees it among their devices, named for the child, and signs it out: it ends at once.
        JsonNode devices = json(send("GET", "/api/v1/account/devices", null, parent, null), 200).get("items");
        JsonNode tablet = devices.valueStream().filter(d -> child.equals(d.get("childId").asString(null))).findFirst().orElseThrow();
        assertThat(send("DELETE", "/api/v1/account/devices/" + tablet.get("id").asString(), null, parent, null).statusCode()).isEqualTo(204);
        assertThat(send("GET", "/api/v1/children/" + child, null, refreshedDevice, null).statusCode()).isEqualTo(401);
        assertThat(send("POST", "/api/v1/auth/refresh", "{\"refreshToken\":\"" + refreshed.get("refreshToken").asString() + "\"}", null, null)
                .statusCode()).isEqualTo(401);

        assertThat(jdbc.queryForList("select action from audit_events where resource_id = ?::uuid and action like 'CHILD_DEVICE_%'",
                String.class, child)).contains("CHILD_DEVICE_CODE_CREATED", "CHILD_DEVICE_APPROVED", "CHILD_DEVICE_SIGNED_IN");
    }

    @Test
    void expiredCancelledOrUnapprovedCodesOpenNothingAndAChildHasAtMostThreeDevices() throws Exception {
        String parent = registerParent();
        String child = createChild(parent, "Mealea");

        // Expired before it was typed.
        JsonNode stale = json(send("POST", "/api/v1/children/" + child + "/device-codes", null, parent, null), 201);
        jdbc.update("update child_device_codes set expires_at = now() - interval '1 minute' where id = ?::uuid", stale.get("id").asString());
        assertThat(claim(stale.get("code").asString()).statusCode()).isEqualTo(400);

        // A new code ends the previous one; a cancelled code's waiting device gets nothing.
        JsonNode first = json(send("POST", "/api/v1/children/" + child + "/device-codes", null, parent, null), 201);
        JsonNode second = json(send("POST", "/api/v1/children/" + child + "/device-codes", null, parent, null), 201);
        assertThat(claim(first.get("code").asString()).statusCode()).as("replaced").isEqualTo(400);
        JsonNode claimed = json(claim(second.get("code").asString()), 200);
        json(send("POST", "/api/v1/children/" + child + "/device-codes/" + second.get("id").asString() + "/cancel", null, parent, null), 200);
        assertThat(collect(claimed.get("claimId").asString(), claimed.get("secret").asString()).statusCode()).isEqualTo(409);
        // Approving a code no device waits on is refused.
        JsonNode unclaimed = json(send("POST", "/api/v1/children/" + child + "/device-codes", null, parent, null), 201);
        assertThat(send("POST", "/api/v1/children/" + child + "/device-codes/" + unclaimed.get("id").asString() + "/approve", null, parent,
                null).statusCode()).isEqualTo(409);

        // Three devices; a fourth is refused until one is signed out.
        for (int i = 0; i < 3; i++) {
            link(parent, child);
        }
        assertThat(send("POST", "/api/v1/children/" + child + "/device-codes", null, parent, null).statusCode()).isEqualTo(409);
        JsonNode one = json(send("GET", "/api/v1/account/devices?size=20", null, parent, null), 200).get("items").valueStream()
                .filter(d -> child.equals(d.get("childId").asString(null))).findFirst().orElseThrow();
        assertThat(send("DELETE", "/api/v1/account/devices/" + one.get("id").asString(), null, parent, null).statusCode()).isEqualTo(204);
        assertThat(send("POST", "/api/v1/children/" + child + "/device-codes", null, parent, null).statusCode()).isEqualTo(201);
    }

    // ------------------------------------------------------------------ helpers

    private String link(String parent, String child) throws Exception {
        JsonNode code = json(send("POST", "/api/v1/children/" + child + "/device-codes", null, parent, null), 201);
        JsonNode claimed = json(claim(code.get("code").asString()), 200);
        json(send("POST", "/api/v1/children/" + child + "/device-codes/" + code.get("id").asString() + "/approve", null, parent, null), 200);
        return json(collect(claimed.get("claimId").asString(), claimed.get("secret").asString()), 200).get("accessToken").asString();
    }

    private HttpResponse<String> claim(String code) throws Exception {
        return send("POST", "/api/v1/auth/child-device/claim", "{\"code\":\"" + code + "\"}", null, TABLET);
    }

    private HttpResponse<String> collect(String claimId, String secret) throws Exception {
        return send("POST", "/api/v1/auth/child-device/collect", "{\"claimId\":\"" + claimId + "\",\"secret\":\"" + secret + "\"}", null, TABLET);
    }

    private String registerParent() throws Exception {
        String email = "device-" + UUID.randomUUID() + "@example.com";
        return json(send("POST", "/api/v1/auth/register", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null, null), 201).get("accessToken").asString();
    }

    private String createChild(String parent, String name) throws Exception {
        return json(send("POST", "/api/v1/children", "{\"displayName\":\"" + name + "\",\"dateOfBirth\":\"" + LocalDate.now().minusYears(6)
                + "\"}", parent, null), 201).get("id").asString();
    }

    private static JsonNode json(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> send(String method, String path, String body, String bearer, String userAgent) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        if (userAgent != null) {
            request.header("User-Agent", userAgent);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

}
