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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LOCALIZATION.md: the question bank says, for each question, how far its Khmer has got - so an author sees what is
 * left to translate without opening every question.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class BankKhmerStatusIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Bank-Status-Pass-2026";
    private static final String OPTIONS = "\"options\":[{\"label\":\"three\",\"correct\":true},{\"label\":\"two\",\"correct\":false}]";

    @Value("${local.server.port}") private int port;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void eachQuestionSaysHowFarItsKhmerHasGot() throws Exception {
        String admin = admin();
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String subjectId = json(post("/api/v1/studio/subjects", "{\"code\":\"BK_" + suffix + "\",\"name\":\"Bank " + suffix + "\"}",
                admin), 201).get("id").asString();
        String skillId = json(post("/api/v1/studio/subjects/" + subjectId + "/skills", "{\"code\":\"BKS_" + suffix
                + "\",\"name\":\"Count\"}", admin), 201).get("id").asString();
        String skills = ",\"difficultyLevel\":1,\"skillIds\":[\"" + skillId + "\"]";
        String complete = question(admin, "{\"subjectId\":\"" + subjectId + "\",\"questionType\":\"MULTIPLE_CHOICE\",\"prompt\":\"A complete\","
                + OPTIONS + skills + ",\"khmer\":{\"prompt\":\"ក ពេញលេញ\",\"options\":[\"បី\",\"ពីរ\"]}}");
        String partly = question(admin, "{\"subjectId\":\"" + subjectId + "\",\"questionType\":\"MULTIPLE_CHOICE\",\"prompt\":\"B partly\","
                + OPTIONS + skills + ",\"khmer\":{\"prompt\":\"ខ ខ្លះ\",\"options\":[\"បី\",\" \"]}}");
        String none = question(admin, "{\"subjectId\":\"" + subjectId + "\",\"questionType\":\"MULTIPLE_CHOICE\",\"prompt\":\"C none\","
                + OPTIONS + skills + "}");
        String typed = question(admin, "{\"subjectId\":\"" + subjectId + "\",\"questionType\":\"SHORT_ANSWER\",\"prompt\":\"D typed\","
                + "\"correctAnswer\":\"cat\"" + skills + "}");

        Map<String, String> status = new HashMap<>();
        for (JsonNode q : json(get("/api/v1/studio/questions?subjectId=" + subjectId, admin), 200).get("items")) {
            status.put(q.get("id").asString(), q.get("khmer").asString());
        }
        assertThat(status).containsEntry(complete, "COMPLETE").containsEntry(partly, "PARTLY")
                .containsEntry(none, "NONE").containsEntry(typed, "NOT_TRANSLATABLE");
    }

    private String question(String admin, String body) throws Exception {
        return json(post("/api/v1/studio/questions", body, admin), 201).get("id").asString();
    }

    // ------------------------------------------------------------------ helpers

    private String admin() throws Exception {
        String email = "bank-status-admin-" + UUID.randomUUID() + "@example.com";
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
