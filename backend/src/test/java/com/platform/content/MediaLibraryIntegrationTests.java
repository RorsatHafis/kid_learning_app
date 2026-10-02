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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * X13 over real HTTP against PostgreSQL: the media library shows an author what their content may use
 * (platform media and their own, never another author's), finds it by title or description, shows
 * where each file is used, lets its owner describe it, archive it (no new uses; drafts already using
 * it keep it) and restore it, and deletes only what nothing uses - refusals leave everything as it was.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class MediaLibraryIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Library-Test-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void anAuthorFindsDescribesReusesArchivesAndDeletesTheirMediaAndNoOneElsesIsTouched() throws Exception {
        Staff admin = staff(PlatformRole.ADMIN);
        Staff teacher = staff(PlatformRole.TEACHER);
        Staff otherTeacher = staff(PlatformRole.TEACHER);
        String tag = UUID.randomUUID().toString().substring(0, 6);

        JsonNode apples = json(upload(png(11, 7), "apples_on-a plate " + tag + ".png", teacher), 201);
        assertThat(apples.get("title").asString()).isEqualTo("apples on a plate " + tag);
        assertThat(apples.get("width").asInt()).isEqualTo(11);
        assertThat(apples.get("height").asInt()).isEqualTo(7);
        assertThat(apples.get("editable").asBoolean()).isTrue();
        byte[] catBytes = png(13, 9);
        JsonNode cat = json(upload(catBytes, "cat " + tag + ".png", teacher), 201);
        JsonNode word = json(upload(wav(3), "word cat " + tag + ".wav", teacher), 201);
        JsonNode platform = json(upload(png(17, 5), "platform star " + tag + ".png", admin), 201);

        // What each author's content may use: platform media and their own - never another author's.
        assertThat(titles(library(teacher, "q=" + tag))).containsExactlyInAnyOrder("apples on a plate " + tag, "cat " + tag,
                "word cat " + tag, "platform star " + tag);
        assertThat(titles(library(teacher, "q=" + tag + "&type=AUDIO"))).containsExactly("word cat " + tag);
        assertThat(titles(library(otherTeacher, "q=" + tag))).containsExactly("platform star " + tag);
        assertThat(get("/api/v1/studio/media/" + cat.get("id").asString(), otherTeacher.token()).statusCode()).isEqualTo(404);
        assertThat(put("/api/v1/studio/media/" + cat.get("id").asString(), "{\"title\":\"mine now\"}", otherTeacher.token()).statusCode())
                .isEqualTo(404);
        assertThat(library(teacher, "q=" + tag).get("items").valueStream()
                .filter(i -> i.get("title").asString().startsWith("platform")).findFirst().orElseThrow().get("editable").asBoolean()).isFalse();
        assertThat(put("/api/v1/studio/media/" + platform.get("id").asString(), "{\"title\":\"renamed\"}", teacher.token()).statusCode())
                .isEqualTo(403);
        assertThat(get("/api/v1/studio/media", registerParent()).statusCode()).isEqualTo(403);

        // Described once: found by its description too.
        String catId = cat.get("id").asString();
        json(put("/api/v1/studio/media/" + catId, "{\"title\":\"Ginger cat " + tag + "\",\"description\":\"A ginger cat asleep in the sun\"}",
                teacher.token()), 200);
        assertThat(titles(library(teacher, "q=asleep%20in%20the%20sun"))).contains("Ginger cat " + tag);

        // Used by a question: its usage says where, and "unused" leaves it out.
        String subjectId = jdbc.queryForObject("select id from subjects order by code limit 1", String.class);
        JsonNode question = json(post("/api/v1/studio/questions", "{\"subjectId\":\"" + subjectId + "\",\"questionType\":\"MULTIPLE_CHOICE\","
                + "\"prompt\":\"Which one is the cat? " + tag + "\",\"promptImageAssetId\":\"" + catId + "\",\"promptImageDescription\":\"A cat\","
                + "\"options\":[{\"label\":\"Cat\",\"correct\":true},{\"label\":\"Dog\",\"correct\":false}],\"skillIds\":[]}", teacher.token()), 201);
        String draftId = question.get("versions").get(0).get("id").asString();
        JsonNode detail = json(get("/api/v1/studio/media/" + catId, teacher.token()), 200);
        assertThat(detail.get("item").get("usedBy").asInt()).isEqualTo(1);
        assertThat(detail.get("uses").get(0).get("field").asString()).isEqualTo("PROMPT_IMAGE");
        assertThat(detail.get("uses").get(0).get("prompt").asString()).isEqualTo("Which one is the cat? " + tag);
        assertThat(detail.get("uses").get(0).get("status").asString()).isEqualTo("DRAFT");
        assertThat(titles(library(teacher, "q=" + tag + "&unused=true"))).doesNotContain("Ginger cat " + tag);

        // Archived: out of the library and no new uses - but the draft that uses it can still be saved.
        json(post("/api/v1/studio/media/" + catId + "/archive", "", teacher.token()), 200);
        assertThat(titles(library(teacher, "q=" + tag))).doesNotContain("Ginger cat " + tag);
        assertThat(titles(library(teacher, "q=" + tag + "&archived=true"))).containsExactly("Ginger cat " + tag);
        int questionsBefore = jdbc.queryForObject("select count(*) from questions", Integer.class);
        assertThat(post("/api/v1/studio/questions", "{\"subjectId\":\"" + subjectId + "\",\"questionType\":\"MULTIPLE_CHOICE\","
                + "\"prompt\":\"Another\",\"promptImageAssetId\":\"" + catId + "\",\"options\":[{\"label\":\"A\",\"correct\":true},"
                + "{\"label\":\"B\",\"correct\":false}],\"skillIds\":[]}", teacher.token()).statusCode()).isEqualTo(409);
        assertThat(jdbc.queryForObject("select count(*) from questions", Integer.class)).isEqualTo(questionsBefore);
        json(put("/api/v1/studio/question-versions/" + draftId, "{\"prompt\":\"Which one is the cat? " + tag + " (edited)\","
                + "\"promptImageAssetId\":\"" + catId + "\",\"promptImageDescription\":\"A cat\",\"options\":[{\"label\":\"Cat\",\"correct\":true},"
                + "{\"label\":\"Dog\",\"correct\":false}]}", teacher.token()), 200);
        // Uploading the same file again brings it back.
        assertThat(json(upload(catBytes, "again.png", teacher), 201).get("id").asString()).isEqualTo(catId);
        assertThat(titles(library(teacher, "q=" + tag))).contains("Ginger cat " + tag);

        // Deleted only when nothing uses it; the file goes with it.
        assertThat(delete("/api/v1/studio/media/" + catId, teacher.token()).statusCode()).isEqualTo(409);
        assertThat(delete("/api/v1/studio/media/" + platform.get("id").asString(), teacher.token()).statusCode()).isEqualTo(403);
        String appleUrl = apples.get("url").asString();
        assertThat(delete("/api/v1/studio/media/" + apples.get("id").asString(), teacher.token()).statusCode()).isEqualTo(204);
        assertThat(get(appleUrl, null).statusCode()).isEqualTo(404);
        assertThat(get("/api/v1/studio/media/" + apples.get("id").asString(), teacher.token()).statusCode()).isEqualTo(404);
        assertThat(get(word.get("url").asString(), null).statusCode()).isEqualTo(200);

        assertThat(jdbc.queryForObject("select count(*) from audit_events where action in ('MEDIA_ARCHIVED','MEDIA_DELETED') "
                + "and actor_id = ?", Integer.class, teacher.id())).isEqualTo(2);
    }

    private JsonNode library(Staff author, String query) throws Exception {
        return json(get("/api/v1/studio/media?" + query, author.token()), 200);
    }

    private static List<String> titles(JsonNode page) {
        List<String> titles = new ArrayList<>();
        page.get("items").forEach(item -> titles.add(item.get("title").asString()));
        return titles;
    }

    private static byte[] png(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        // Unique bytes per run: the library deduplicates identical files per owner.
        image.setRGB(0, 0, java.util.concurrent.ThreadLocalRandom.current().nextInt(1, 0xFFFFFF));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static byte[] wav(int seed) {
        byte[] b = new byte[44 + 600];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, b, 0, 4);
        System.arraycopy("WAVEfmt ".getBytes(StandardCharsets.US_ASCII), 0, b, 8, 8);
        for (int i = 44; i < b.length; i++) {
            b[i] = (byte) (i * seed + java.util.concurrent.ThreadLocalRandom.current().nextInt(256));
        }
        return b;
    }

    // ------------------------------------------------------------------ accounts and HTTP

    private record Staff(UUID id, String token) {
    }

    private Staff staff(PlatformRole role) throws Exception {
        String email = "library-" + role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        Account account = accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), role));
        return new Staff(account.getId(), json(post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}",
                null), 200).get("accessToken").asString());
    }

    private String registerParent() throws Exception {
        return json(post("/api/v1/auth/register", "{\"email\":\"library-parent-" + UUID.randomUUID() + "@example.com\",\"password\":\""
                + PASSWORD + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null), 201).get("accessToken").asString();
    }

    private static JsonNode json(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> upload(byte[] content, String filename, Staff author) throws Exception {
        String boundary = "bk" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + filename
                + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(content);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return send(HttpRequest.newBuilder(uri("/api/v1/studio/media")).header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())), author.token());
    }

    private HttpResponse<String> get(String path, String bearer) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).GET(), bearer);
    }

    private HttpResponse<String> post(String path, String body, String bearer) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)),
                bearer);
    }

    private HttpResponse<String> put(String path, String body, String bearer) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json").PUT(HttpRequest.BodyPublishers.ofString(body)),
                bearer);
    }

    private HttpResponse<String> delete(String path, String bearer) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).DELETE(), bearer);
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
