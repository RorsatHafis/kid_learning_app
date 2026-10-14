package com.platform.identity;

import com.platform.TestcontainersConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
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
 * The interface language an account keeps (English or Khmer): chosen at sign-up, returned with every
 * sign-in so any device opens in it, changed only by the account itself, and only to a supported value.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class AccountLanguageIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Language-Test-Mango-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void theLanguageChosenAtSignUpIsKeptAndComesBackWithEverySignIn() throws Exception {
        String email = "language-" + UUID.randomUUID() + "@example.com";
        JsonNode registered = json(post("/api/v1/auth/register", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\",\"preferredLanguage\":\"km\"}", null), 201);
        assertThat(registered.get("preferredLanguage").asString()).isEqualTo("km");

        JsonNode login = json(post("/api/v1/auth/login", credentials(email), null), 200);
        assertThat(login.get("preferredLanguage").asString()).as("another device signing in").isEqualTo("km");
        JsonNode refreshed = json(post("/api/v1/auth/refresh", "{\"refreshToken\":\"" + login.get("refreshToken").asString() + "\"}", null), 200);
        assertThat(refreshed.get("preferredLanguage").asString()).as("a page reload").isEqualTo("km");
        assertThat(json(get("/api/v1/account", login.get("accessToken").asString()), 200).get("preferredLanguage").asString()).isEqualTo("km");
    }

    @Test
    void withoutAChoiceAnAccountIsInEnglish() throws Exception {
        String email = "language-" + UUID.randomUUID() + "@example.com";
        JsonNode registered = json(post("/api/v1/auth/register", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null), 201);
        assertThat(registered.get("preferredLanguage").asString()).isEqualTo("en");
        assertThat(post("/api/v1/auth/register", "{\"email\":\"other-" + email + "\",\"password\":\"" + PASSWORD
                + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\",\"preferredLanguage\":\"fr\"}", null).statusCode())
                .as("an unsupported language").isEqualTo(400);
    }

    @Test
    void anAccountChangesOnlyItsOwnLanguageAndOnlyToASupportedOne() throws Exception {
        JsonNode first = register();
        JsonNode second = register();
        String token = first.get("accessToken").asString();

        assertThat(json(put("/api/v1/account/language", "{\"language\":\"km\"}", token), 200).get("preferredLanguage").asString())
                .isEqualTo("km");
        assertThat(json(post("/api/v1/auth/login", credentials(first.get("email").asString()), null), 200)
                .get("preferredLanguage").asString()).as("after signing out and in").isEqualTo("km");
        assertThat(languageOf(second)).as("another account").isEqualTo("en");

        assertThat(put("/api/v1/account/language", "{\"language\":\"fr\"}", token).statusCode()).isEqualTo(400);
        assertThat(put("/api/v1/account/language", "{\"language\":\"\"}", token).statusCode()).isEqualTo(400);
        assertThat(put("/api/v1/account/language", "{\"language\":\"km\"}", null).statusCode()).as("signed out").isEqualTo(401);
        assertThat(languageOf(first)).isEqualTo("km");

        json(put("/api/v1/account/language", "{\"language\":\"en\"}", token), 200);
        assertThat(languageOf(first)).isEqualTo("en");
    }

    /** What the server says to a person comes back in the language the request asks for (Accept-Language). */
    @Test
    void errorsAreSaidInTheRequestsLanguageAndEnglishOtherwise() throws Exception {
        String wrong = "{\"email\":\"nobody-" + UUID.randomUUID() + "@example.com\",\"password\":\"Wrong-Password-Mango\"}";
        JsonNode khmer = json(send(HttpRequest.newBuilder(uri("/api/v1/auth/login")).header("Content-Type", "application/json")
                .header("Accept-Language", "km").POST(HttpRequest.BodyPublishers.ofString(wrong)), null), 401);
        assertThat(khmer.get("detail").asString()).isEqualTo("អ៊ីមែល ឬពាក្យសម្ងាត់មិនត្រឹមត្រូវ");
        JsonNode english = json(post("/api/v1/auth/login", wrong, null), 401);
        assertThat(english.get("detail").asString()).isEqualTo("Invalid email or password");

        JsonNode invalid = json(send(HttpRequest.newBuilder(uri("/api/v1/auth/register")).header("Content-Type", "application/json")
                .header("Accept-Language", "km-KH,km;q=0.9,en;q=0.5")
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"not-an-email\",\"password\":\"x\",\"idempotencyKey\":\"k\"}")), null), 400);
        assertThat(invalid.get("errors").get("email").asString()).isEqualTo("ត្រូវតែជាអាសយដ្ឋានអ៊ីមែលត្រឹមត្រូវ");
    }

    /**
     * A child's space has its own language, kept on the child: it starts as the language the parent is
     * using, then changes on its own - never the parent's, and only by the child's family.
     */
    @Test
    void aChildHasItsOwnLanguageThatOnlyItsFamilyChanges() throws Exception {
        JsonNode parent = register();
        String token = parent.get("accessToken").asString();
        JsonNode child = json(send(HttpRequest.newBuilder(uri("/api/v1/children")).header("Content-Type", "application/json")
                .header("Accept-Language", "km").POST(HttpRequest.BodyPublishers.ofString("{\"displayName\":\"Sokha\",\"dateOfBirth\":\""
                        + java.time.LocalDate.now().minusYears(6) + "\"}")), token), 201);
        String childId = child.get("id").asString();
        assertThat(child.get("preferredLanguage").asString()).as("starts in the parent's current language").isEqualTo("km");

        assertThat(json(put("/api/v1/children/" + childId + "/language", "{\"language\":\"en\"}", token), 200)
                .get("preferredLanguage").asString()).isEqualTo("en");
        assertThat(json(put("/api/v1/children/" + childId + "/language", "{\"language\":\"km\"}", token), 200)
                .get("preferredLanguage").asString()).isEqualTo("km");
        assertThat(languageOf(parent)).as("the parent's own language is untouched").isEqualTo("en");
        json(put("/api/v1/account/language", "{\"language\":\"en\"}", token), 200);
        assertThat(json(get("/api/v1/children/" + childId, token), 200).get("preferredLanguage").asString())
                .as("and the parent's change leaves the child's").isEqualTo("km");

        String stranger = register().get("accessToken").asString();
        assertThat(put("/api/v1/children/" + childId + "/language", "{\"language\":\"en\"}", stranger).statusCode())
                .as("another family").isIn(403, 404);
        assertThat(put("/api/v1/children/" + childId + "/language", "{\"language\":\"fr\"}", token).statusCode()).isEqualTo(400);
        assertThat(put("/api/v1/children/" + childId + "/language", "{\"language\":\"en\"}", null).statusCode()).isEqualTo(401);
        assertThat(jdbc.queryForObject("select preferred_language from children where id = ?::uuid", String.class, childId))
                .isEqualTo("km");
    }

    /** Answers written by the security filters, before any controller, follow the same rule. */
    @Test
    void signedOutAnswersAreSaidInTheRequestsLanguageToo() throws Exception {
        JsonNode khmer = json(send(HttpRequest.newBuilder(uri("/api/v1/account")).header("Accept-Language", "km").GET(), null), 401);
        assertThat(khmer.get("detail").asString()).isEqualTo("សូមចូលគណនីជាមុនសិន");
        JsonNode expired = json(send(HttpRequest.newBuilder(uri("/api/v1/account")).header("Accept-Language", "km").GET(),
                "not-a-real-token"), 401);
        assertThat(expired.get("detail").asString()).isEqualTo("វគ្គចូលរបស់អ្នកផុតកំណត់ហើយ។ សូមចូលម្ដងទៀត");
        // Another language, or none: English, whatever the server machine's own locale is.
        JsonNode other = json(send(HttpRequest.newBuilder(uri("/api/v1/account")).header("Accept-Language", "fr-FR").GET(), null), 401);
        assertThat(other.get("detail").asString()).isEqualTo("Authentication is required");
        assertThat(json(get("/api/v1/account", null), 401).get("detail").asString()).isEqualTo("Authentication is required");
    }

    private String languageOf(JsonNode session) {
        return jdbc.queryForObject("select preferred_language from accounts where id = ?::uuid", String.class,
                session.get("accountId").asString());
    }

    private JsonNode register() throws Exception {
        String email = "language-" + UUID.randomUUID() + "@example.com";
        JsonNode body = json(post("/api/v1/auth/register", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}", null), 201);
        return body;
    }

    private static String credentials(String email) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}";
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
