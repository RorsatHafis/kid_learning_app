package com.platform.subscription;

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
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LOCALIZATION.md "Content in two languages", plans (V82): a plan's name and description in English and Khmer, saved
 * apart on a draft, kept through a duplicate, fixed once offered like the English, and read by a family in its
 * language - the English wherever no Khmer is written.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class PlanKhmerIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Plan-Khmer-Test-Pass-2026";
    private static final String LIMITS = ",\"selfService\":false,\"priceAmountMinor\":500,\"priceCurrency\":\"USD\","
            + "\"billingInterval\":\"MONTH\",\"limits\":{\"CHILDREN_MAX\":3,\"JOURNEYS_PER_CHILD_MAX\":2}";

    @Value("${local.server.port}") private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void aPlansWordsAreWrittenInBothLanguagesOnADraftAndAFamilyReadsThemInItsOwn() throws Exception {
        String admin = admin();
        String code = "KM_" + suffix();

        // Created with a Khmer name; the English is its own.
        JsonNode created = json(post("/api/v1/admin/plans", "{\"code\":\"" + code + "\",\"name\":\"Family Plus\","
                + "\"khmer\":{\"name\":\"គ្រួសារ បូក\"}}", admin, null), 201).get("plan");
        String planId = created.get("id").asString();
        assertThat(created.get("name").asString()).isEqualTo("Family Plus");
        assertThat(created.get("khmer").get("name").asString()).isEqualTo("គ្រួសារ បូក");
        assertThat(created.get("khmer").get("description").isNull()).isTrue();

        // A save that leaves the Khmer out keeps it; one that has it replaces it; a blank removes a part.
        JsonNode kept = json(put("/api/v1/admin/plans/" + planId, "{\"name\":\"Family Plus\",\"description\":\"Three children.\""
                + LIMITS + "}", admin, null), 200).get("plan");
        assertThat(kept.get("description").asString()).isEqualTo("Three children.");
        assertThat(kept.get("khmer").get("name").asString()).isEqualTo("គ្រួសារ បូក");
        JsonNode both = json(put("/api/v1/admin/plans/" + planId, "{\"name\":\"Family Plus\",\"description\":\"Three children.\""
                + LIMITS + ",\"khmer\":{\"name\":\"គ្រួសារ បូក\",\"description\":\"កូនបីនាក់។\"}}", admin, null), 200).get("plan");
        assertThat(both.get("name").asString()).as("the Khmer never replaces the English").isEqualTo("Family Plus");
        assertThat(both.get("description").asString()).isEqualTo("Three children.");
        assertThat(both.get("khmer").get("description").asString()).isEqualTo("កូនបីនាក់។");
        Map<String, Object> row = jdbc.queryForMap("select name, name_km, description, description_km from subscription_plans "
                + "where id = ?::uuid", UUID.fromString(planId));
        assertThat(row).containsEntry("name", "Family Plus").containsEntry("name_km", "គ្រួសារ បូក")
                .containsEntry("description", "Three children.").containsEntry("description_km", "កូនបីនាក់។");
        // Reopened: both are there.
        JsonNode reopened = planIn(json(get("/api/v1/admin/plans", admin, null), 200), planId).get("plan");
        assertThat(reopened.get("khmer").get("name").asString()).isEqualTo("គ្រួសារ បូក");
        assertThat(reopened.get("name").asString()).isEqualTo("Family Plus");
        // An administrator reading in Khmer still manages the English as English.
        assertThat(planIn(json(get("/api/v1/admin/plans", admin, "km"), 200), planId).get("plan").get("name").asString())
                .isEqualTo("Family Plus");

        // Offered: its words, in both languages, are fixed.
        json(post("/api/v1/admin/plans/" + planId + "/activate", "{}", admin, null), 200);
        json(put("/api/v1/admin/plans/" + planId, "{\"name\":\"Family Plus\",\"description\":\"Three children.\"" + LIMITS
                + ",\"khmer\":{\"name\":\"ផ្សេង\"}}", admin, null), 409);
        assertThat(jdbc.queryForObject("select name_km from subscription_plans where id = ?::uuid", String.class,
                UUID.fromString(planId))).isEqualTo("គ្រួសារ បូក");

        // A duplicate - the way an offered plan changes - carries both languages to its draft.
        JsonNode copy = json(post("/api/v1/admin/plans/" + planId + "/duplicate", "{\"code\":\"KM2_" + suffix()
                + "\",\"name\":\"Family Plus 2027\"}", admin, null), 201).get("plan");
        assertThat(copy.get("status").asString()).isEqualTo("DRAFT");
        assertThat(copy.get("khmer").get("name").asString()).isEqualTo("គ្រួសារ បូក");
        assertThat(copy.get("khmer").get("description").asString()).isEqualTo("កូនបីនាក់។");

        // A family on it reads its words in its language; the Khmer is not sent to it separately.
        String email = "plan-khmer-" + UUID.randomUUID() + "@example.com";
        String parent = registerParent(email);
        String familyId = json(get("/api/v1/admin/families?parentEmail=" + URLEncoder.encode(email, StandardCharsets.UTF_8), admin, null),
                200).get("familyId").asString();
        // Before: the default plan, which has no Khmer, reads in English for a Khmer reader.
        JsonNode defaultKm = json(get("/api/v1/family/plan", parent, "km"), 200).get("plan");
        JsonNode defaultEn = json(get("/api/v1/family/plan", parent, "en"), 200).get("plan");
        assertThat(defaultKm.get("name").asString()).isEqualTo(defaultEn.get("name").asString());
        json(post("/api/v1/admin/families/" + familyId + "/subscription", "{\"planId\":\"" + planId + "\"}", admin, null), 200);
        JsonNode km = json(get("/api/v1/family/plan", parent, "km"), 200).get("plan");
        assertThat(km.get("name").asString()).isEqualTo("គ្រួសារ បូក");
        assertThat(km.get("description").asString()).isEqualTo("កូនបីនាក់។");
        assertThat(km.get("khmer").isNull()).isTrue();
        JsonNode en = json(get("/api/v1/family/plan", parent, "en"), 200).get("plan");
        assertThat(en.get("name").asString()).isEqualTo("Family Plus");
        assertThat(en.get("description").asString()).isEqualTo("Three children.");
    }

    // ------------------------------------------------------------------ helpers

    private static JsonNode planIn(JsonNode plans, String planId) {
        for (JsonNode p : plans) {
            if (p.get("plan").get("id").asString().equals(planId)) {
                return p;
            }
        }
        throw new AssertionError("plan " + planId + " not listed");
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String registerParent(String email) throws Exception {
        return json(post("/api/v1/auth/register", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\","
                + "\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null, null), 201).get("accessToken").asString();
    }

    private String admin() throws Exception {
        String email = "plan-khmer-admin-" + UUID.randomUUID() + "@example.com";
        accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), PlatformRole.ADMIN));
        String token = json(post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", null, null), 200)
                .get("accessToken").asString();
        return com.platform.TestAdmin.stepUp(port, token, PASSWORD);
    }

    private static JsonNode json(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> get(String path, String bearer, String language) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).GET(), bearer, language);
    }

    private HttpResponse<String> post(String path, String body, String bearer, String language) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)), bearer, language);
    }

    private HttpResponse<String> put(String path, String body, String bearer, String language) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body)), bearer, language);
    }

    private HttpResponse<String> send(HttpRequest.Builder request, String bearer, String language) throws Exception {
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
            com.platform.TestAdmin.withStepUp(request, bearer);
        }
        if (language != null) {
            request.header("Accept-Language", language);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
