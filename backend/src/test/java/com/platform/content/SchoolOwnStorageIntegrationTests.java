package com.platform.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.TestcontainersConfiguration;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.repository.AccountRepository;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * M11 (D-06/D-12, owner decisions 2026-11-11) over real HTTP, PostgreSQL and an S3-compatible server (MinIO): a
 * principal connects the school's own storage - checked first, nothing kept if it fails, the access key never returned -
 * and the school's teachers' new files go there, outside Bookeyond's allowance, served back through the same signed
 * links; switching back to Bookeyond-hosted changes only where new files go. Needs Docker (skipped without it).
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class SchoolOwnStorageIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Own-Storage-Test-Pass-2026";
    private static final String BUCKET = "school-media";

    @Container
    static final MinIOContainer MINIO = new MinIOContainer("minio/minio:RELEASE.2023-09-04T19-57-37Z");

    @Value("${local.server.port}") private int port;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeAll
    static void bucket() {
        try (S3Client s3 = s3()) {
            s3.createBucket(request -> request.bucket(BUCKET));
        }
    }

    @Test
    void aSchoolsOwnStorageKeepsItsTeachersNewFilesOutsideTheAllowanceAndSwitchingBackKeepsThemReadable() throws Exception {
        User admin = user(PlatformRole.ADMIN);
        User principal = user(PlatformRole.PRINCIPAL);
        User teacher = user(PlatformRole.TEACHER);
        String schoolId = json(post("/api/v1/schools", "{\"name\":\"Own Storage " + suffix() + "\"}", principal), 201).get("id").asString();
        pay(admin, schoolId, schoolPlan(admin, 1));
        assertThat(post("/api/v1/schools/" + schoolId + "/staff", "{\"email\":\"" + teacher.email() + "\"}", principal).statusCode()).isEqualTo(201);
        for (JsonNode invitation : json(get("/api/v1/schools/invitations/mine", teacher), 200)) {
            post("/api/v1/schools/invitations/" + invitation.get("id").asString() + "/accept", "", teacher);
        }

        // Hosted by default.
        JsonNode before = json(get("/api/v1/schools/" + schoolId + "/storage", principal), 200);
        assertThat(before.get("available").asBoolean()).isTrue();
        assertThat(before.get("current").isNull()).isTrue();

        // A wrong key: checked first, refused with what failed, nothing kept.
        JsonNode refused = json(post("/api/v1/schools/" + schoolId + "/storage/own", connect("not-the-key"), principal), 422);
        assertThat(refused.get("detail").asString()).contains("refused the access key");
        assertThat(jdbc.queryForObject("select count(*) from storage_connections where school_id = ?::uuid", Integer.class, schoolId)).isZero();
        // Only the school's principal (or an administrator) chooses; a teacher can't.
        assertThat(post("/api/v1/schools/" + schoolId + "/storage/own", connect(MINIO.getPassword()), teacher).statusCode()).isEqualTo(403);

        JsonNode connected = json(post("/api/v1/schools/" + schoolId + "/storage/own", connect(MINIO.getPassword()), principal), 200);
        assertThat(connected.get("current").get("bucket").asString()).isEqualTo(BUCKET);
        assertThat(connected.toString()).doesNotContain(MINIO.getPassword()).doesNotContain(MINIO.getUserName());
        assertThat(jdbc.queryForObject("select access_key_sealed from storage_connections where school_id = ?::uuid", String.class, schoolId))
                .doesNotContain(MINIO.getPassword());

        // A teacher's new file goes to the school's bucket - more than the 1 MB allowance, since it isn't Bookeyond's.
        byte[] picture = noise(700);
        assertThat(picture.length).isGreaterThan(1024 * 1024);
        JsonNode uploaded = json(upload(picture, "big.png", teacher), 201);
        String sha = jdbc.queryForObject("select sha256 from content_assets where id = ?::uuid", String.class, uploaded.get("id").asString());
        try (S3Client s3 = s3()) {
            byte[] inBucket = s3.getObjectAsBytes(r -> r.bucket(BUCKET).key("bookeyond/" + sha.substring(0, 2) + "/" + sha.substring(2, 4) + "/" + sha))
                    .asByteArray();
            assertThat(inBucket).isEqualTo(picture);
        }
        assertThat(json(get("/api/v1/studio/storage", teacher), 200).get("usedBytes").asLong()).as("not Bookeyond's allowance").isZero();
        // Served through the same signed link, from the school's storage.
        String url = uploaded.get("url").asString();
        HttpResponse<byte[]> served = http.send(HttpRequest.newBuilder(uri(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(served.statusCode()).isEqualTo(200);
        assertThat(Arrays.equals(served.body(), picture)).isTrue();

        // Check again: fine.
        assertThat(json(post("/api/v1/schools/" + schoolId + "/storage/check", "", principal), 200).get("current").get("lastCheckOk").asBoolean())
                .isTrue();

        // Back to Bookeyond-hosted: new files are hosted (and count), the earlier one still serves from the school's storage.
        JsonNode hosted = json(post("/api/v1/schools/" + schoolId + "/storage/hosted", "", principal), 200);
        assertThat(hosted.get("current").isNull()).isTrue();
        assertThat(hosted.get("earlier").get(0).get("files").asLong()).isEqualTo(1);
        json(upload(noise(40), "small.png", teacher), 201);
        assertThat(json(get("/api/v1/studio/storage", teacher), 200).get("usedBytes").asLong()).isPositive();
        HttpResponse<byte[]> still = http.send(HttpRequest.newBuilder(uri(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(Arrays.equals(still.body(), picture)).isTrue();

        // Another school's principal sees nothing of it.
        User stranger = user(PlatformRole.PRINCIPAL);
        assertThat(get("/api/v1/schools/" + schoolId + "/storage", stranger).statusCode()).isIn(403, 404);
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action = 'SCHOOL_STORAGE_CONNECTED' and resource_id = ?::uuid",
                Integer.class, schoolId)).isEqualTo(1);
    }

    // ---------------------------------------------------------------------------------------------------------------

    private record User(String id, String email, String token) {
    }

    private static S3Client s3() {
        return S3Client.builder().endpointOverride(URI.create(MINIO.getS3URL())).region(Region.US_EAST_1).forcePathStyle(true)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(MINIO.getUserName(), MINIO.getPassword())))
                .httpClient(UrlConnectionHttpClient.create()).build();
    }

    private static String connect(String accessKey) {
        return "{\"endpoint\":\"" + MINIO.getS3URL() + "\",\"region\":\"us-east-1\",\"bucket\":\"" + BUCKET + "\",\"pathStyle\":true,"
                + "\"accessKeyId\":\"" + MINIO.getUserName() + "\",\"secretAccessKey\":\"" + accessKey + "\"}";
    }

    private User user(PlatformRole role) throws Exception {
        String email = "m11-" + role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), role));
        JsonNode body = json(post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", null), 200);
        if (role == PlatformRole.ADMIN) {
            com.platform.TestAdmin.stepUp(port, body.get("accessToken").asString(), PASSWORD);
        }
        return new User(body.get("accountId").asString(), email, body.get("accessToken").asString());
    }

    private String schoolPlan(User admin, int storageMb) throws Exception {
        String planId = json(post("/api/v1/admin/plans", "{\"code\":\"SCHOOL_" + suffix() + "\",\"name\":\"School test plan\","
                + "\"audience\":\"SCHOOL\"}", admin), 201).get("plan").get("id").asString();
        json(put("/api/v1/admin/plans/" + planId, "{\"name\":\"School test plan\",\"selfService\":false,\"priceAmountMinor\":12000,"
                + "\"priceCurrency\":\"USD\",\"billingInterval\":\"YEAR\",\"limits\":{\"TEACHER_STORAGE_MB\":" + storageMb + "}}", admin), 200);
        json(post("/api/v1/admin/plans/" + planId + "/activate", "{}", admin), 200);
        return planId;
    }

    private void pay(User admin, String schoolId, String planId) throws Exception {
        String orderId = json(post("/api/v1/admin/schools/" + schoolId + "/invoices", "{\"planId\":\"" + planId
                + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", admin), 201).get("id").asString();
        json(post("/api/v1/admin/payments/" + orderId + "/confirm", com.platform.TestAdmin.receipt(port, admin.token(), orderId,
                "BANK-" + suffix()), admin), 200);
    }

    private static byte[] noise(int side) throws Exception {
        BufferedImage image = new BufferedImage(side, side, BufferedImage.TYPE_INT_RGB);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int x = 0; x < side; x++) {
            for (int y = 0; y < side; y++) {
                image.setRGB(x, y, random.nextInt(0xFFFFFF));
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private static JsonNode json(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> upload(byte[] content, String filename, User author) throws Exception {
        String boundary = "bk" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + filename
                + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(content);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return http.send(HttpRequest.newBuilder(uri("/api/v1/studio/media")).header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Authorization", "Bearer " + author.token())
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, User user) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).GET(), user);
    }

    private HttpResponse<String> post(String path, String body, User user) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)), user);
    }

    private HttpResponse<String> put(String path, String body, User user) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body)), user);
    }

    private HttpResponse<String> send(HttpRequest.Builder request, User user) throws Exception {
        if (user != null) {
            request.header("Authorization", "Bearer " + user.token());
            com.platform.TestAdmin.withStepUp(request, user.token());
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
