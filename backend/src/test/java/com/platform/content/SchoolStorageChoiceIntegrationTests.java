package com.platform.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.PaidSchools;
import com.platform.TestcontainersConfiguration;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.repository.AccountRepository;
import com.platform.school.entity.School;
import com.platform.school.service.SchoolService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * M11 without an S3 server (runs everywhere): a school starts on Bookeyond-hosted storage; storage that can't be reached
 * is refused with what failed and nothing kept; only the school's principal chooses. The S3 round trip itself is
 * {@link SchoolOwnStorageIntegrationTests} (needs Docker).
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class SchoolStorageChoiceIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Storage-Choice-Test-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SchoolService schoolService;
    @Autowired private ApplicationContext context;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void aSchoolStartsHostedAndStorageThatCantBeReachedIsRefusedWithNothingKept() throws Exception {
        String principal = login(PlatformRole.PRINCIPAL);
        UUID principalId = jdbc.queryForObject("select id from accounts where normalized_email = ?", UUID.class, lastEmail);
        School school = schoolService.createSchool("Storage Choice School", principalId);
        PaidSchools.pay(context, school.getId());
        String path = "/api/v1/schools/" + school.getId() + "/storage";

        JsonNode view = json(get(path, principal), 200);
        assertThat(view.get("available").asBoolean()).isTrue();
        assertThat(view.get("current").isNull()).isTrue();
        assertThat(view.get("earlier")).isEmpty();

        String unreachable = "{\"endpoint\":\"http://127.0.0.1:1\",\"region\":\"us-east-1\",\"bucket\":\"school-media\","
                + "\"accessKeyId\":\"AKIA1234\",\"secretAccessKey\":\"not-real\"}";
        JsonNode refused = json(post(path + "/own", unreachable, principal), 422);
        assertThat(refused.get("detail").asString()).contains("couldn't be reached");
        assertThat(jdbc.queryForObject("select count(*) from storage_connections where school_id = ?", Integer.class, school.getId())).isZero();
        assertThat(json(post(path + "/own", "{\"endpoint\":\"http://127.0.0.1:1\",\"bucket\":\"NO\",\"accessKeyId\":\"a\",\"secretAccessKey\":\"b\"}",
                principal), 400).get("detail").asString()).contains("bucket");
        // Hosted again is a no-op; the view still reads hosted.
        assertThat(json(post(path + "/hosted", "", principal), 200).get("current").isNull()).isTrue();

        // Only the school's principal: a teacher, another principal, a parent - refused.
        assertThat(get(path, login(PlatformRole.TEACHER)).statusCode()).isIn(403, 404);
        assertThat(post(path + "/own", unreachable, login(PlatformRole.PRINCIPAL)).statusCode()).isIn(403, 404);
        assertThat(get(path, login(PlatformRole.PARENT)).statusCode()).isIn(403, 404);
    }

    private String lastEmail;

    private String login(PlatformRole role) throws Exception {
        String email = "m11c-" + role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), role));
        lastEmail = email;
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

    private HttpResponse<String> send(HttpRequest.Builder builder, String bearer) throws Exception {
        if (bearer != null) builder.header("Authorization", "Bearer " + bearer);
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
