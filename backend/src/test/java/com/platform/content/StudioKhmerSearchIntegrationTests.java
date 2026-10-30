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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LOCALIZATION.md: an author finds content in either language they wrote it in - the question bank by a question's
 * Khmer prompt, the lessons by an activity's Khmer name - and the English search is unchanged.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class StudioKhmerSearchIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Khmer-Search-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void questionsAndLessonsAreFoundByTheirKhmerToo() throws Exception {
        String admin = admin();
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String subjectId = json(post("/api/v1/studio/subjects", "{\"code\":\"SR_" + suffix + "\",\"name\":\"Search " + suffix + "\"}",
                admin), 201).get("id").asString();
        String skillId = json(post("/api/v1/studio/subjects/" + subjectId + "/skills", "{\"code\":\"SRS_" + suffix
                + "\",\"name\":\"Count\"}", admin), 201).get("id").asString();
        String questionId = json(post("/api/v1/studio/questions", "{\"subjectId\":\"" + subjectId + "\",\"questionType\":\"MULTIPLE_CHOICE\","
                + "\"prompt\":\"How many ducks?\",\"difficultyLevel\":1,\"options\":[{\"label\":\"three\",\"correct\":true},"
                + "{\"label\":\"two\",\"correct\":false}],\"skillIds\":[\"" + skillId + "\"],\"khmer\":{\"prompt\":\"តើមានទាប៉ុន្មានក្បាល?\","
                + "\"options\":[\"បី\",\"ពីរ\"]}}", admin), 201).get("id").asString();
        json(post("/api/v1/studio/questions", "{\"subjectId\":\"" + subjectId + "\",\"questionType\":\"MULTIPLE_CHOICE\","
                + "\"prompt\":\"How many hens?\",\"difficultyLevel\":1,\"options\":[{\"label\":\"one\",\"correct\":true},"
                + "{\"label\":\"four\",\"correct\":false}],\"skillIds\":[\"" + skillId + "\"]}", admin), 201);

        // By its Khmer prompt: found, and only it.
        JsonNode byKhmer = json(get("/api/v1/studio/questions?subjectId=" + subjectId + "&q=" + enc("ទា"), admin), 200);
        assertThat(byKhmer.get("total").asLong()).isEqualTo(1);
        assertThat(byKhmer.get("items").get(0).get("id").asString()).isEqualTo(questionId);
        // By its English, as before; a word in neither finds nothing.
        assertThat(json(get("/api/v1/studio/questions?subjectId=" + subjectId + "&q=ducks", admin), 200).get("total").asLong()).isEqualTo(1);
        assertThat(json(get("/api/v1/studio/questions?subjectId=" + subjectId + "&q=How", admin), 200).get("total").asLong()).isEqualTo(2);
        assertThat(json(get("/api/v1/studio/questions?subjectId=" + subjectId + "&q=" + enc("ឆ្កែ"), admin), 200).get("total").asLong()).isZero();

        // A lesson by the Khmer name of one of its activities.
        String lessonId = json(post("/api/v1/studio/lessons", "{\"subjectId\":\"" + subjectId + "\",\"title\":\"Farm animals\"}", admin), 201)
                .get("id").asString();
        String activityVersionId = json(post("/api/v1/studio/lessons/" + lessonId + "/activities",
                "{\"title\":\"Count the ducks\",\"skillIds\":[\"" + skillId + "\"]}", admin), 201).get("versions").get(0).get("id").asString();
        json(put("/api/v1/studio/activity-versions/" + activityVersionId, "{\"difficultyLevel\":1,\"khmer\":{\"title\":\"រាប់ទា\"}}", admin), 200);
        JsonNode lessons = json(get("/api/v1/studio/lessons?subjectId=" + subjectId + "&q=" + enc("រាប់ទា"), admin), 200);
        assertThat(lessons.get("total").asLong()).isEqualTo(1);
        assertThat(lessons.get("items").get(0).get("id").asString()).isEqualTo(lessonId);
        assertThat(json(get("/api/v1/studio/lessons?subjectId=" + subjectId + "&q=ducks", admin), 200).get("total").asLong()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ helpers

    private static String enc(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }

    private String admin() throws Exception {
        String email = "khmer-search-admin-" + UUID.randomUUID() + "@example.com";
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
