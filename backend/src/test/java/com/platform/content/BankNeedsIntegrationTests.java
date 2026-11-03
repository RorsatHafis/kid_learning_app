package com.platform.content;

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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The question bank's "still needs" filter (GET /studio/questions?needs=): what each question's latest version lacks -
 * an explanation, a hint, a read-aloud recording, or complete Khmer (by the same rule the bank's Khmer status shows,
 * typed-word questions never needing it) - so a content team can work through a subject's gaps.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class BankNeedsIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Bank-Needs-Pass-2026";
    private static final String OPTIONS = "\"options\":[{\"label\":\"three\",\"correct\":true},{\"label\":\"two\",\"correct\":false}]";

    @Value("${local.server.port}") private int port;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void eachNeedListsTheQuestionsThatLackIt() throws Exception {
        String admin = admin();
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String subjectId = json(post("/api/v1/studio/subjects", "{\"code\":\"BN_" + suffix + "\",\"name\":\"Needs " + suffix + "\"}",
                admin), 201).get("id").asString();
        String skillId = json(post("/api/v1/studio/subjects/" + subjectId + "/skills", "{\"code\":\"BNS_" + suffix
                + "\",\"name\":\"Count\"}", admin), 201).get("id").asString();
        String common = "\"subjectId\":\"" + subjectId + "\",\"difficultyLevel\":1,\"skillIds\":[\"" + skillId + "\"]";

        // Explained, hinted and complete in Khmer: only the recording is missing.
        String complete = question(admin, "{" + common + ",\"questionType\":\"MULTIPLE_CHOICE\",\"prompt\":\"A how many?\"," + OPTIONS
                + ",\"hint\":\"Count them.\",\"explanation\":\"There are three.\",\"khmer\":{\"prompt\":\"ក ប៉ុន្មាន?\",\"hint\":\"រាប់វា។\","
                + "\"explanation\":\"មានបី។\",\"options\":[\"បី\",\"ពីរ\"]}}");
        // Nothing but the question and its answer.
        String bare = question(admin, "{" + common + ",\"questionType\":\"NUMERIC\",\"prompt\":\"B 1 + 1 = ?\",\"correctAnswer\":\"2\"}");
        // Explained, Khmer started but not finished.
        String partly = question(admin, "{" + common + ",\"questionType\":\"MULTIPLE_CHOICE\",\"prompt\":\"C which?\"," + OPTIONS
                + ",\"explanation\":\"Three is right.\",\"khmer\":{\"prompt\":\"គ មួយណា?\"}}");
        // A typed word, hinted: it never needs Khmer.
        String typed = question(admin, "{" + common + ",\"questionType\":\"SHORT_ANSWER\",\"prompt\":\"D spell cat\",\"correctAnswer\":\"cat\","
                + "\"hint\":\"C-A-...\"}");

        String bank = "/api/v1/studio/questions?subjectId=" + subjectId;
        assertThat(ids(bank + "&needs=EXPLANATION", admin)).containsExactly(bare, typed);
        assertThat(ids(bank + "&needs=HINT", admin)).containsExactly(bare, partly);
        assertThat(ids(bank + "&needs=READ_ALOUD", admin)).containsExactly(complete, bare, partly, typed);
        assertThat(ids(bank + "&needs=KHMER", admin)).containsExactly(bare, partly);
        // Combined with the other filters, and paged with the right total.
        assertThat(ids(bank + "&needs=KHMER&type=MULTIPLE_CHOICE", admin)).containsExactly(partly);
        JsonNode second = json(get(bank + "&needs=KHMER&page=1&size=1", admin), 200);
        assertThat(second.get("total").asInt()).isEqualTo(2);
        assertThat(second.get("items").get(0).get("id").asString()).isEqualTo(partly);
        // Anything else is refused, not ignored.
        assertThat(get(bank + "&needs=EVERYTHING", admin).statusCode()).isEqualTo(400);
    }

    private List<String> ids(String path, String bearer) throws Exception {
        List<String> ids = new ArrayList<>();
        json(get(path, bearer), 200).get("items").forEach(q -> ids.add(q.get("id").asString()));
        return ids;
    }

    private String question(String admin, String body) throws Exception {
        return json(post("/api/v1/studio/questions", body, admin), 201).get("id").asString();
    }

    // ------------------------------------------------------------------ helpers

    private String admin() throws Exception {
        String email = "bank-needs-admin-" + UUID.randomUUID() + "@example.com";
        accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), PlatformRole.ADMIN));
        return json(post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", null), 200)
                .get("accessToken").asString();
    }

    private static JsonNode json(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> get(String path, String bearer) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).GET(), bearer);
    }

    private HttpResponse<String> post(String path, String body, String bearer) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)), bearer);
    }

    private HttpResponse<String> send(HttpRequest.Builder request, String bearer) throws Exception {
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
