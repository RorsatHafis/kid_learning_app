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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Questions pasted from a spreadsheet (POST /studio/questions/import): every row a draft with its publishing checks,
 * all of them or none, in one subject - never published, never another author's.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class QuestionImportIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Question-Import-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void everyRowBecomesADraftWithItsChecks() throws Exception {
        String author = account(PlatformRole.ADMIN);
        String[] subject = subjectWithSkill(author);
        String rows = "[" + String.join(",",
                row(subject, "MULTIPLE_CHOICE", "2 + 3 = ?", null,
                        ",\"options\":[{\"label\":\"5\",\"correct\":true},{\"label\":\"6\",\"correct\":false}]"
                                + ",\"hint\":\"Count on from 3.\",\"explanation\":\"3 and 2 more is 5.\""
                                + ",\"khmer\":{\"prompt\":\"២ + ៣ = ?\",\"hint\":\"រាប់បន្តពី ៣។\",\"explanation\":\"៣ និង ២ ទៀតគឺ ៥។\",\"options\":[\"៥\",\"៦\"]}"),
                row(subject, "TRUE_FALSE", "4 is more than 7.", "maybe", ""),
                row(subject, "NUMERIC", "1 + 1 = ?", "2", "")) + "]";

        JsonNode imported = json(post("/api/v1/studio/questions/import", "{\"questions\":" + rows + "}", author), 201);

        assertThat(imported).hasSize(3);
        assertThat(imported.get(0).get("prompt").asString()).isEqualTo("2 + 3 = ?");
        assertThat(imported.get(0).get("validation").get("errors")).as("a complete choice question").isEmpty();
        assertThat(imported.get(1).get("validation").get("errors")).as("'maybe' is not true or false").isNotEmpty();
        assertThat(imported.get(2).get("validation").get("errors")).isEmpty();
        // Drafts, with what the row said: the choice question's hint and its Khmer.
        JsonNode first = json(get("/api/v1/studio/questions/" + imported.get(0).get("questionId").asString(), author), 200);
        JsonNode version = first.get("versions").get(0);
        assertThat(version.get("status").asString()).isEqualTo("DRAFT");
        assertThat(version.get("hint").asString()).isEqualTo("Count on from 3.");
        assertThat(version.get("khmer").get("complete").asBoolean()).isTrue();
        assertThat(first.get("skillIds").get(0).asString()).isEqualTo(subject[1]);
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action = 'QUESTIONS_IMPORTED'"
                + " and resource_id = ?::uuid", Integer.class, subject[0])).isEqualTo(1);
    }

    @Test
    void oneRefusedRowLeavesNoneBehind() throws Exception {
        String author = account(PlatformRole.ADMIN);
        String[] subject = subjectWithSkill(author);
        String[] other = subjectWithSkill(author);
        String foreignSkill = "{\"subjectId\":\"" + subject[0] + "\",\"questionType\":\"NUMERIC\",\"prompt\":\"3 + 3 = ?\","
                + "\"correctAnswer\":\"6\",\"skillIds\":[\"" + other[1] + "\"]}";
        String body = "{\"questions\":[" + row(subject, "NUMERIC", "2 + 2 = ?", "4", "") + "," + foreignSkill + "]}";

        assertThat(post("/api/v1/studio/questions/import", body, author).statusCode()).isEqualTo(400);
        assertThat(bankSize(subject[0], author)).as("the good row was not kept either").isZero();

        // Two subjects in one paste: refused, nothing kept.
        String mixed = "{\"questions\":[" + row(subject, "NUMERIC", "2 + 2 = ?", "4", "") + ","
                + row(other, "NUMERIC", "5 + 5 = ?", "10", "") + "]}";
        assertThat(post("/api/v1/studio/questions/import", mixed, author).statusCode()).isEqualTo(400);
        assertThat(bankSize(subject[0], author) + bankSize(other[0], author)).isZero();

        // Nothing, or more than 50 at once: refused by the request's own limits.
        assertThat(post("/api/v1/studio/questions/import", "{\"questions\":[]}", author).statusCode()).isEqualTo(400);
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            many.add(row(subject, "NUMERIC", i + " + 1 = ?", String.valueOf(i + 1), ""));
        }
        HttpResponse<String> tooMany = post("/api/v1/studio/questions/import", "{\"questions\":[" + String.join(",", many) + "]}", author);
        assertThat(tooMany.statusCode()).isEqualTo(400);
        assertThat(bankSize(subject[0], author)).isZero();
    }

    @Test
    void onlyAuthorsImportAndATeachersDraftsAreTheirOwn() throws Exception {
        String admin = account(PlatformRole.ADMIN);
        String[] subject = subjectWithSkill(admin);
        String body = "{\"questions\":[" + row(subject, "SHORT_ANSWER", "Spell cat.", "cat", "") + "]}";

        assertThat(post("/api/v1/studio/questions/import", body, account(PlatformRole.PARENT)).statusCode()).isEqualTo(403);

        String teacher = account(PlatformRole.TEACHER);
        String questionId = json(post("/api/v1/studio/questions/import", body, teacher), 201).get(0).get("questionId").asString();
        assertThat(get("/api/v1/studio/questions/" + questionId, teacher).statusCode()).isEqualTo(200);
        assertThat(get("/api/v1/studio/questions/" + questionId, account(PlatformRole.TEACHER)).statusCode())
                .as("another teacher's draft").isIn(403, 404);
    }

    // ------------------------------------------------------------------ helpers

    private static String row(String[] subject, String type, String prompt, String answer, String more) {
        return "{\"subjectId\":\"" + subject[0] + "\",\"questionType\":\"" + type + "\",\"prompt\":\"" + prompt + "\""
                + (answer == null ? "" : ",\"correctAnswer\":\"" + answer + "\"") + ",\"skillIds\":[\"" + subject[1] + "\"]" + more + "}";
    }

    /** A subject and one skill of it: {subjectId, skillId}. */
    private String[] subjectWithSkill(String admin) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String subjectId = json(post("/api/v1/studio/subjects", "{\"code\":\"QI_" + suffix + "\",\"name\":\"Import " + suffix + "\"}",
                admin), 201).get("id").asString();
        String skillId = json(post("/api/v1/studio/subjects/" + subjectId + "/skills", "{\"code\":\"QIS_" + suffix
                + "\",\"name\":\"Add\"}", admin), 201).get("id").asString();
        return new String[]{subjectId, skillId};
    }

    private int bankSize(String subjectId, String bearer) throws Exception {
        return json(get("/api/v1/studio/questions?subjectId=" + subjectId, bearer), 200).get("total").asInt();
    }

    private String account(PlatformRole role) throws Exception {
        String email = "question-import-" + role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), role));
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
