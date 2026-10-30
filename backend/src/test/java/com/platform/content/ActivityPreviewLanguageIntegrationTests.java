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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LOCALIZATION.md: an author previews a draft activity as a child reading English or Khmer gets it - the activity's
 * own words and each question in Khmer only where their Khmer is complete, exactly as the child's reader decides.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class ActivityPreviewLanguageIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Preview-Language-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void aDraftActivityIsPreviewedInEachLanguageAsAChildWouldGetIt() throws Exception {
        String admin = admin();
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String subjectId = json(post("/api/v1/studio/subjects", "{\"code\":\"PV_" + suffix + "\",\"name\":\"Preview " + suffix + "\"}",
                admin), 201).get("id").asString();
        String skillId = json(post("/api/v1/studio/subjects/" + subjectId + "/skills", "{\"code\":\"PVS_" + suffix
                + "\",\"name\":\"Compare\"}", admin), 201).get("id").asString();
        // One question with its Khmer complete, one without any.
        String translated = json(post("/api/v1/studio/questions", "{\"subjectId\":\"" + subjectId + "\",\"questionType\":\"MULTIPLE_CHOICE\","
                + "\"prompt\":\"Which is bigger?\",\"difficultyLevel\":1,\"options\":[{\"label\":\"seven\",\"correct\":true},"
                + "{\"label\":\"four\",\"correct\":false}],\"skillIds\":[\"" + skillId + "\"],\"khmer\":{\"prompt\":\"តើលេខមួយណាធំជាង?\","
                + "\"options\":[\"ប្រាំពីរ\",\"បួន\"]}}", admin), 201).get("versions").get(0).get("id").asString();
        String english = json(post("/api/v1/studio/questions", "{\"subjectId\":\"" + subjectId + "\",\"questionType\":\"MULTIPLE_CHOICE\","
                + "\"prompt\":\"Which is smaller?\",\"difficultyLevel\":1,\"options\":[{\"label\":\"two\",\"correct\":true},"
                + "{\"label\":\"nine\",\"correct\":false}],\"skillIds\":[\"" + skillId + "\"]}", admin), 201).get("versions").get(0).get("id").asString();
        String lessonId = json(post("/api/v1/studio/lessons", "{\"subjectId\":\"" + subjectId + "\",\"title\":\"Bigger\"}", admin), 201)
                .get("id").asString();
        String activityVersionId = json(post("/api/v1/studio/lessons/" + lessonId + "/activities",
                "{\"title\":\"Bigger and smaller\",\"skillIds\":[\"" + skillId + "\"]}", admin), 201).get("versions").get(0).get("id").asString();
        json(put("/api/v1/studio/activity-versions/" + activityVersionId + "/items", "{\"items\":[{\"questionVersionId\":\"" + translated
                + "\"},{\"questionVersionId\":\"" + english + "\"}]}", admin), 200);
        json(put("/api/v1/studio/activity-versions/" + activityVersionId + "/introduction",
                "{\"text\":\"Bigger means more.\",\"khmer\":{\"text\":\"ធំជាង មានន័យថាច្រើនជាង។\"}}", admin), 200);

        // The activity's own Khmer isn't complete yet (no Khmer name): its words come in English, even for Khmer.
        JsonNode partly = json(get("/api/v1/studio/activity-versions/" + activityVersionId + "/preview?language=km", admin), 200);
        assertThat(partly.get("language").isNull()).isTrue();
        assertThat(partly.get("title").asString()).isEqualTo("Bigger and smaller");
        assertThat(partly.get("introduction").get("text").asString()).isEqualTo("Bigger means more.");
        // ...while each question still follows its own Khmer.
        assertThat(partly.get("items").get(0).get("language").asString()).isEqualTo("km");
        assertThat(partly.get("items").get(0).get("prompt").asString()).isEqualTo("តើលេខមួយណាធំជាង?");
        assertThat(partly.get("items").get(1).get("language").isNull()).isTrue();
        assertThat(partly.get("items").get(1).get("prompt").asString()).isEqualTo("Which is smaller?");

        // With its name in Khmer, the activity's words are complete: a Khmer reader gets them in Khmer.
        json(put("/api/v1/studio/activity-versions/" + activityVersionId, "{\"difficultyLevel\":1,"
                + "\"khmer\":{\"title\":\"ធំជាង និងតូចជាង\"}}", admin), 200);
        JsonNode km = json(get("/api/v1/studio/activity-versions/" + activityVersionId + "/preview?language=km", admin), 200);
        assertThat(km.get("language").asString()).isEqualTo("km");
        assertThat(km.get("title").asString()).isEqualTo("ធំជាង និងតូចជាង");
        assertThat(km.get("introduction").get("text").asString()).isEqualTo("ធំជាង មានន័យថាច្រើនជាង។");
        assertThat(km.toString()).doesNotContain("\"correct\"");

        // In English, and with no language given (as written), everything is as the author wrote it.
        for (String query : new String[]{"?language=en", ""}) {
            JsonNode en = json(get("/api/v1/studio/activity-versions/" + activityVersionId + "/preview" + query, admin), 200);
            assertThat(en.get("language").isNull()).isTrue();
            assertThat(en.get("title").asString()).isEqualTo("Bigger and smaller");
            assertThat(en.get("items").get(0).get("prompt").asString()).isEqualTo("Which is bigger?");
        }
        // Only a known language is accepted, and only an author of it may preview it.
        assertThat(get("/api/v1/studio/activity-versions/" + activityVersionId + "/preview?language=fr", admin).statusCode()).isEqualTo(400);
        String parent = json(post("/api/v1/auth/register", "{\"email\":\"preview-parent-" + UUID.randomUUID() + "@example.com\",\"password\":\""
                + PASSWORD + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null), 201).get("accessToken").asString();
        assertThat(get("/api/v1/studio/activity-versions/" + activityVersionId + "/preview?language=km", parent).statusCode()).isEqualTo(403);
    }

    // ------------------------------------------------------------------ helpers

    private String admin() throws Exception {
        String email = "preview-admin-" + UUID.randomUUID() + "@example.com";
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
