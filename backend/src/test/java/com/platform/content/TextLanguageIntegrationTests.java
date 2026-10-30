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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V83 (LOCALIZATION.md, screen-reader language): a question says the language of its own text only when that is known -
 * "en" once saved through the bilingual Studio, carried to its next draft - and nothing for older content, which may
 * have Khmer typed into the English fields. Given in Khmer, it says "km" as before.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class TextLanguageIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Text-Language-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void aQuestionSaysItsLanguageOnlyWhenItIsKnown() throws Exception {
        String admin = admin();
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String subjectId = json(post("/api/v1/studio/subjects", "{\"code\":\"TL_" + suffix + "\",\"name\":\"Lang " + suffix + "\"}",
                admin), 201).get("id").asString();
        String skillId = json(post("/api/v1/studio/subjects/" + subjectId + "/skills", "{\"code\":\"TLS_" + suffix
                + "\",\"name\":\"Count\"}", admin), 201).get("id").asString();
        JsonNode created = json(post("/api/v1/studio/questions", "{\"subjectId\":\"" + subjectId + "\",\"questionType\":\"MULTIPLE_CHOICE\","
                + "\"prompt\":\"How many?\",\"difficultyLevel\":1,\"options\":[{\"label\":\"three\",\"correct\":true},"
                + "{\"label\":\"two\",\"correct\":false}],\"skillIds\":[\"" + skillId + "\"],\"khmer\":{\"prompt\":\"ប៉ុន្មាន?\","
                + "\"options\":[\"បី\",\"ពីរ\"]}}", admin), 201);
        String questionId = created.get("id").asString();
        String versionId = created.get("versions").get(0).get("id").asString();

        // Written in the Studio's English fields: as written, it says English; given in Khmer, it says Khmer.
        JsonNode en = json(get("/api/v1/studio/question-versions/" + versionId + "/preview?language=en", admin), 200);
        assertThat(en.get("language").isNull()).isTrue();
        assertThat(en.get("writtenIn").asString()).isEqualTo("en");
        JsonNode km = json(get("/api/v1/studio/question-versions/" + versionId + "/preview?language=km", admin), 200);
        assertThat(km.get("language").asString()).isEqualTo("km");
        assertThat(km.get("writtenIn").isNull()).isTrue();

        // Its next draft is the same text, so the same language.
        json(post("/api/v1/studio/question-versions/" + versionId + "/publish", "", admin), 200);
        String nextId = json(post("/api/v1/studio/questions/" + questionId + "/draft", "", admin), 200).get("versions").get(1).get("id").asString();
        assertThat(json(get("/api/v1/studio/question-versions/" + nextId + "/preview?language=en", admin), 200).get("writtenIn").asString())
                .isEqualTo("en");

        // Content from before (unknown language): nothing is said - never guessed - until a draft of it is saved.
        jdbc.update("update question_versions set text_language = null where id = ?::uuid", UUID.fromString(nextId));
        assertThat(json(get("/api/v1/studio/question-versions/" + nextId + "/preview?language=en", admin), 200).get("writtenIn").isNull())
                .isTrue();
        json(put("/api/v1/studio/question-versions/" + nextId, "{\"prompt\":\"How many ducks?\",\"difficultyLevel\":1,"
                + "\"options\":[{\"label\":\"three\",\"correct\":true},{\"label\":\"two\",\"correct\":false}]}", admin), 200);
        assertThat(json(get("/api/v1/studio/question-versions/" + nextId + "/preview?language=en", admin), 200).get("writtenIn").asString())
                .isEqualTo("en");
    }

    // ------------------------------------------------------------------ helpers

    private String admin() throws Exception {
        String email = "text-language-admin-" + UUID.randomUUID() + "@example.com";
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

    private HttpResponse<String> put(String path, String body, String bearer) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body)), bearer);
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
