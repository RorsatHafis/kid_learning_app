package com.platform.common;

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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A request body other than an upload is at most 1 MB (RequestSizeLimitFilter): a larger one is refused before it is
 * read - even on the unauthenticated sign-in - and one of unknown length stops being read at the limit; ordinary
 * requests and uploads above 1 MB (multipart keeps its own limits) are unaffected.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class RequestSizeLimitIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Size-Limit-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void anOversizedBodyIsRefusedBeforeItIsRead() throws Exception {
        String padding = "x".repeat(2 * 1024 * 1024);
        String big = "{\"email\":\"someone@example.com\",\"password\":\"nope\",\"padding\":\"" + padding + "\"}";

        HttpResponse<String> refused = http.send(HttpRequest.newBuilder(uri("/api/v1/auth/login"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(big)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(refused.statusCode()).isEqualTo(413);
        assertThat(JSON.readTree(refused.body()).get("detail").asString()).isEqualTo("That request is too large to accept.");

        // In the reader's language, like every server message.
        HttpResponse<String> khmer = http.send(HttpRequest.newBuilder(uri("/api/v1/auth/login"))
                .header("Content-Type", "application/json").header("Accept-Language", "km")
                .POST(HttpRequest.BodyPublishers.ofString(big)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(JSON.readTree(khmer.body()).get("detail").asString()).isEqualTo("សំណើនោះធំពេកមិនអាចទទួលបានទេ។");

        // No declared length (chunked): read only up to the limit, then refused - never accepted.
        HttpResponse<String> chunked = http.send(HttpRequest.newBuilder(uri("/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(big.getBytes(StandardCharsets.UTF_8))))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(chunked.statusCode()).isIn(400, 413);

        // An ordinary request is unaffected.
        HttpResponse<String> small = http.send(HttpRequest.newBuilder(uri("/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"someone@example.com\",\"password\":\"nope\"}")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(small.statusCode()).isEqualTo(401);
    }

    @Test
    void anUploadAboveOneMegabyteStillArrives() throws Exception {
        String email = "size-teacher-" + UUID.randomUUID() + "@example.com";
        accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), PlatformRole.TEACHER));
        String token = JSON.readTree(http.send(HttpRequest.newBuilder(uri("/api/v1/auth/login")).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}")).build(),
                HttpResponse.BodyHandlers.ofString()).body()).get("accessToken").asString();

        byte[] picture = noise(720, 720);
        assertThat(picture.length).as("the picture is over the JSON limit").isGreaterThan(1024 * 1024);
        String boundary = "bk" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"big.png\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(picture);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpResponse<String> uploaded = http.send(HttpRequest.newBuilder(uri("/api/v1/studio/media"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary).header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(uploaded.statusCode()).as(uploaded.body()).isEqualTo(201);
        JsonNode item = JSON.readTree(uploaded.body());
        assertThat(item.get("sizeBytes").asLong()).isEqualTo(picture.length);
    }

    /** A picture that doesn't compress: random pixels. */
    private static byte[] noise(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, random.nextInt(0xFFFFFF));
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
