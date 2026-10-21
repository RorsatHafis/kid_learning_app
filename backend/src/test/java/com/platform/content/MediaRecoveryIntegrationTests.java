package com.platform.content;

import com.platform.TestcontainersConfiguration;
import com.platform.content.media.MediaStorage;
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
import javax.sql.DataSource;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TD-26 over real HTTP against PostgreSQL: uploading the same file again reuses its asset as before, and also
 * repairs a stored file that went missing or was damaged - same asset, same references, nothing charged twice.
 * A repair that can't be written fails and changes nothing; uploads of the same bytes at once make one asset
 * and one good file; and deleting one owner's copy never takes the file from another owner's upload racing it.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class MediaRecoveryIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Recovery-Test-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MediaStorage storage;
    @Autowired private DataSource dataSource;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void anIntactFileIsReusedAndALostOrDamagedOneIsRepairedByUploadingItAgain() throws Exception {
        Staff teacher = staff(PlatformRole.TEACHER);
        byte[] bytes = png(19, 11);
        String sha = sha256(bytes);
        JsonNode first = json(upload(bytes, "boat.png", teacher), 201);
        String id = first.get("id").asString();
        Path file = storage.resolve(MediaStorage.keyFor(sha));
        long used = usedBytes(teacher);

        // Intact: reused as it was - one asset, the file not rewritten.
        FileTime stamp = FileTime.from(Instant.parse("2020-01-01T00:00:00Z"));
        Files.setLastModifiedTime(file, stamp);
        assertThat(json(upload(bytes, "boat again.png", teacher), 201).get("id").asString()).isEqualTo(id);
        assertThat(Files.getLastModifiedTime(file)).isEqualTo(stamp);
        assertThat(rows(sha)).isEqualTo(1);

        // Lost: its link finds nothing, until the same file is uploaded again.
        Files.delete(file);
        assertThat(get(first.get("url").asString(), null).statusCode()).isEqualTo(404);
        JsonNode repaired = json(upload(bytes, "boat.png", teacher), 201);
        assertThat(repaired.get("id").asString()).isEqualTo(id);
        assertThat(served(first.get("url").asString())).isEqualTo(bytes); // the link issued before still works
        assertThat(rows(sha)).isEqualTo(1);
        assertThat(usedBytes(teacher)).isEqualTo(used); // not charged twice

        // Damaged (same size, other bytes): repaired the same way.
        byte[] damaged = bytes.clone();
        damaged[damaged.length / 2] ^= 0x55;
        Files.write(file, damaged);
        json(upload(bytes, "boat.png", teacher), 201);
        assertThat(served(first.get("url").asString())).isEqualTo(bytes);
        assertThat(Files.readAllBytes(storage.resolve(MediaStorage.keyFor(sha)))).isEqualTo(bytes);
    }

    @Test
    void aRepairThatCantBeWrittenFailsAndChangesNothing() throws Exception {
        Staff teacher = staff(PlatformRole.TEACHER);
        byte[] bytes = png(23, 13);
        String sha = sha256(bytes);
        String id = json(upload(bytes, "kite.png", teacher), 201).get("id").asString();
        json(post("/api/v1/studio/media/" + id + "/archive", teacher.token()), 200);
        Path file = storage.resolve(MediaStorage.keyFor(sha));
        Files.delete(file);
        // Nothing can be written where the file belongs: a folder with something in it stands there.
        Path blocker = Files.createDirectories(file).resolve("in-the-way");
        Files.writeString(blocker, "in the way");
        try {
            HttpResponse<String> failed = upload(bytes, "kite.png", teacher);
            assertThat(failed.statusCode()).isGreaterThanOrEqualTo(500); // not a success
            assertThat(jdbc.queryForObject("select archived_at is not null from content_assets where id = ?::uuid", Boolean.class, id))
                    .as("the upload's restore of the archived asset rolled back").isTrue();
            assertThat(rows(sha)).isEqualTo(1);
        } finally {
            Files.delete(blocker);
            Files.delete(file);
        }
        // Once storage can be written again, the same upload repairs it.
        assertThat(json(upload(bytes, "kite.png", teacher), 201).get("id").asString()).isEqualTo(id);
        assertThat(Files.readAllBytes(storage.resolve(MediaStorage.keyFor(sha)))).isEqualTo(bytes);
    }

    @Test
    void theSameFileUploadedAtOnceMakesOneAssetAndOneGoodFile() throws Exception {
        Staff admin = staff(PlatformRole.ADMIN); // platform media: no owner row to queue on, only the bytes' lock
        byte[] bytes = png(29, 17);
        String sha = sha256(bytes);
        List<HttpResponse<String>> responses = atOnce(8, () -> upload(bytes, "sun.png", admin));
        assertThat(responses).allSatisfy(r -> assertThat(r.statusCode()).as(r.body()).isEqualTo(201));
        assertThat(responses.stream().map(r -> JSON.readTree(r.body()).get("id").asString()).distinct()).hasSize(1);
        assertThat(rows(sha)).isEqualTo(1);
        assertThat(Files.readAllBytes(storage.resolve(MediaStorage.keyFor(sha)))).isEqualTo(bytes);
    }

    @Test
    void deletingTheLastCopyNeverTakesTheFileFromAnUploadThatIsStillCommitting() throws Exception {
        Staff deleting = staff(PlatformRole.TEACHER);
        Staff uploading = staff(PlatformRole.TEACHER);
        byte[] bytes = png(31, 7);
        String sha = sha256(bytes);
        String doomed = json(upload(bytes, "leaf.png", deleting), 201).get("id").asString();
        Path file = storage.resolve(MediaStorage.keyFor(sha));

        // Another owner's upload of the same bytes is in flight: it holds the bytes' lock, found the file there,
        // and hasn't committed its row yet. The deletion commits meanwhile and must wait before touching the file.
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (Connection upload = dataSource.getConnection()) {
            upload.setAutoCommit(false);
            try (var lock = upload.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                lock.setString(1, sha);
                lock.execute();
            }
            Future<HttpResponse<String>> deletion = pool.submit(() -> delete("/api/v1/studio/media/" + doomed, deleting.token()));
            for (int i = 0; i < 100 && rows(sha) > 0; i++) {
                Thread.sleep(50); // the deletion's own transaction has committed
            }
            assertThat(rows(sha)).isZero();
            assertThat(deletion.isDone()).as("the deletion waits for the bytes' lock before removing the file").isFalse();
            try (var row = upload.prepareStatement("INSERT INTO content_assets (id, asset_type, storage_key, mime_type, size_bytes, sha256,"
                    + " owner_account_id, uploaded_by_account_id) VALUES (?, 'IMAGE', ?, 'image/png', ?, ?, ?, ?)")) {
                row.setObject(1, UUID.randomUUID());
                row.setString(2, MediaStorage.keyFor(sha));
                row.setLong(3, bytes.length);
                row.setString(4, sha);
                row.setObject(5, uploading.id());
                row.setObject(6, uploading.id());
                row.executeUpdate();
            }
            upload.commit();
            assertThat(deletion.get().statusCode()).isEqualTo(204);
        } finally {
            pool.shutdownNow();
        }
        assertThat(rows(sha)).isEqualTo(1);
        assertThat(Files.readAllBytes(file)).as("the other owner's upload keeps its file").isEqualTo(bytes);
    }

    // ------------------------------------------------------------------ helpers

    private int rows(String sha) {
        return jdbc.queryForObject("select count(*) from content_assets where sha256 = ?", Integer.class, sha);
    }

    private long usedBytes(Staff owner) {
        return jdbc.queryForObject("select coalesce(sum(size_bytes), 0) from content_assets where sha256 is not null and owner_account_id = ?",
                Long.class, owner.id());
    }

    private byte[] served(String url) throws Exception {
        HttpResponse<byte[]> response = http.send(HttpRequest.newBuilder(uri(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(200);
        return response.body();
    }

    private static <T> List<T> atOnce(int n, Callable<T> task) throws Exception {
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                futures.add(pool.submit(() -> {
                    go.await();
                    return task.call();
                }));
            }
            go.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> f : futures) {
                results.add(f.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private static byte[] png(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        // Unique bytes per run: media is deduplicated by content.
        image.setRGB(0, 0, java.util.concurrent.ThreadLocalRandom.current().nextInt(1, 0xFFFFFF));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    // ------------------------------------------------------------------ accounts and HTTP

    private record Staff(UUID id, String token) {
    }

    private Staff staff(PlatformRole role) throws Exception {
        String email = "recovery-" + role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        Account account = accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), role));
        return new Staff(account.getId(), json(send(HttpRequest.newBuilder(uri("/api/v1/auth/login")).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}")), null), 200)
                .get("accessToken").asString());
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

    private HttpResponse<String> post(String path, String bearer) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("")),
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
