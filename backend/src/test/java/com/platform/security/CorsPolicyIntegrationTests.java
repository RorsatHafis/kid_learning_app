package com.platform.security;

import com.platform.TestcontainersConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Battle N5, over real HTTP against real PostgreSQL: the browser-origin policy.
 * Configured like a deployment - the local Vite origin plus a production-style
 * origin - to show that exactly the configured origins can use the API from a
 * browser, that anything else is refused before the request is processed, and
 * that CORS changes nothing about authentication (401) or authorization (403).
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "platform.security.cors.allowed-origins="
                + CorsPolicyIntegrationTests.DEV_ORIGIN + "," + CorsPolicyIntegrationTests.PROD_ORIGIN)
/**
 * CORS: configured origins may call the API with the headers it needs; any other origin, or an unlisted header, is
 * refused before the request is processed.
 */
@Tag("integration")
class CorsPolicyIntegrationTests {

    static final String DEV_ORIGIN = "http://localhost:5173";
    static final String PROD_ORIGIN = "https://app.bk-test.invalid";
    private static final String UNTRUSTED_ORIGIN = "https://evil.example";
    private static final Pattern FIELD = Pattern.compile("\"(%s)\"\\s*:\\s*\"([^\"]*)\"");

    @Value("${local.server.port}") private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    // ------------------------------------------------------------------ preflight

    @Test
    void preflightFromTheDevelopmentOriginAllowsTheAuthorizationHeader() throws Exception {
        HttpResponse<String> res = preflight("/api/v1/children", DEV_ORIGIN, "GET", "authorization");

        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.headers().firstValue("Access-Control-Allow-Origin")).hasValue(DEV_ORIGIN);
        assertThat(res.headers().firstValue("Access-Control-Allow-Headers").orElse("")).containsIgnoringCase("authorization");
        assertThat(res.headers().firstValue("Access-Control-Allow-Credentials")).isEmpty();
    }

    @Test
    void preflightFromTheProductionOriginAllowsAJsonPost() throws Exception {
        HttpResponse<String> res = preflight("/api/v1/auth/login", PROD_ORIGIN, "POST", "content-type");

        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.headers().firstValue("Access-Control-Allow-Origin")).hasValue(PROD_ORIGIN);
        assertThat(res.headers().firstValue("Access-Control-Allow-Methods").orElse("")).contains("POST");
        assertThat(res.headers().firstValue("Access-Control-Allow-Headers").orElse("")).containsIgnoringCase("content-type");
        assertThat(res.headers().firstValue("Access-Control-Allow-Credentials")).isEmpty();
    }

    @Test
    void preflightFromAnUntrustedOriginIsRefused() throws Exception {
        for (String origin : new String[] {UNTRUSTED_ORIGIN, "null", "http://localhost:5174",
                "http://app.bk-test.invalid", "https://app.bk-test.invalid.evil.example"}) {
            HttpResponse<String> res = preflight("/api/v1/children", origin, "GET", "authorization");

            assertThat(res.statusCode()).as(origin).isEqualTo(403);
            assertThat(res.headers().firstValue("Access-Control-Allow-Origin")).as(origin).isEmpty();
        }
    }

    @Test
    void unlistedRequestHeadersAreNeverGrantedEvenToAnAllowedOrigin() throws Exception {
        HttpResponse<String> onlyUnlisted = preflight("/api/v1/children", PROD_ORIGIN, "GET", "x-unlisted");
        HttpResponse<String> mixed = preflight("/api/v1/children", PROD_ORIGIN, "GET", "authorization,x-unlisted");

        assertThat(onlyUnlisted.statusCode()).isEqualTo(403);
        assertThat(onlyUnlisted.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
        // Spring answers with the permitted subset; the browser then refuses to send
        // the request because x-unlisted is missing from the grant.
        assertThat(mixed.headers().firstValue("Access-Control-Allow-Headers").orElse(""))
                .containsIgnoringCase("authorization")
                .doesNotContainIgnoringCase("x-unlisted");
    }

    // ------------------------------------------------------------------ actual requests

    @Test
    void authenticatedRequestFromAnAllowedOriginSucceedsAndIsReadable() throws Exception {
        String token = registerParent(PROD_ORIGIN);

        HttpResponse<String> res = send("GET", "/api/v1/children", PROD_ORIGIN, token, null);

        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.headers().firstValue("Access-Control-Allow-Origin")).hasValue(PROD_ORIGIN);
        assertThat(res.headers().allValues("Vary").toString()).contains("Origin");
    }

    @Test
    void authenticatedRequestFromAnUntrustedOriginIsRefusedBeforeItIsProcessed() throws Exception {
        String token = registerParent(null);

        HttpResponse<String> write = send("POST", "/api/v1/children", UNTRUSTED_ORIGIN, token,
                "{\"displayName\":\"Cross Origin\",\"dateOfBirth\":\"2019-01-01\"}");
        HttpResponse<String> read = send("GET", "/api/v1/children", UNTRUSTED_ORIGIN, token, null);

        assertThat(write.statusCode()).isEqualTo(403);
        assertThat(read.statusCode()).isEqualTo(403);
        assertThat(read.body()).isEqualTo("Invalid CORS request");
        assertThat(read.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
        // The refused write never reached the controller: the account still has no children.
        assertThat(send("GET", "/api/v1/children", null, token, null).body()).isEqualTo("[]");
    }

    @Test
    void refreshWorksFromAnAllowedOriginAndIsRefusedFromAnUntrustedOne() throws Exception {
        HttpResponse<String> registered = register(PROD_ORIGIN);
        String refreshToken = extract(registered.body(), "refreshToken");
        String body = "{\"refreshToken\":\"" + refreshToken + "\"}";

        HttpResponse<String> untrusted = send("POST", "/api/v1/auth/refresh", UNTRUSTED_ORIGIN, null, body);
        HttpResponse<String> allowed = send("POST", "/api/v1/auth/refresh", PROD_ORIGIN, null, body);

        assertThat(untrusted.statusCode()).isEqualTo(403);
        // The refused refresh never consumed the single-use token, so the real frontend's refresh succeeds.
        assertThat(allowed.statusCode()).isEqualTo(200);
        assertThat(allowed.headers().firstValue("Access-Control-Allow-Origin")).hasValue(PROD_ORIGIN);
    }

    // ------------------------------------------------------------------ 401 / 403 unchanged

    @Test
    void unauthenticatedRequestFromAnAllowedOriginIsAReadable401Challenge() throws Exception {
        HttpResponse<String> missing = send("GET", "/api/v1/children", DEV_ORIGIN, null, null);
        HttpResponse<String> invalid = send("GET", "/api/v1/children", DEV_ORIGIN, "not-a-jwt", null);

        assertThat(missing.statusCode()).isEqualTo(401);
        assertThat(missing.headers().firstValue("WWW-Authenticate")).hasValue("Bearer");
        assertThat(missing.headers().firstValue("Access-Control-Allow-Origin")).hasValue(DEV_ORIGIN);
        assertThat(invalid.statusCode()).isEqualTo(401);
        assertThat(invalid.headers().firstValue("WWW-Authenticate").orElse("")).contains("error=\"invalid_token\"");
        // Readable by the page, so the frontend can see the 401 and refresh.
        assertThat(invalid.headers().firstValue("Access-Control-Allow-Origin")).hasValue(DEV_ORIGIN);
    }

    @Test
    void forbiddenFromAnAllowedOriginStaysAnAuthorization403() throws Exception {
        // A parent asking for an administrators-only resource: authenticated, not permitted.
        String parentToken = registerParent(null);

        HttpResponse<String> res = send("GET", "/api/v1/admin/audit-events", PROD_ORIGIN, parentToken, null);

        assertThat(res.statusCode()).isEqualTo(403);
        assertThat(res.headers().firstValue("WWW-Authenticate")).isEmpty();
        assertThat(res.headers().firstValue("Access-Control-Allow-Origin")).hasValue(PROD_ORIGIN);
        assertThat(res.body()).contains("\"status\":403");
    }

    @Test
    void requestsWithoutAnOriginAreNotSubjectToCors() throws Exception {
        String token = registerParent(null);

        HttpResponse<String> res = send("GET", "/api/v1/children", null, token, null);

        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
    }

    // ------------------------------------------------------------------ helpers

    private HttpResponse<String> register(String origin) throws Exception {
        HttpResponse<String> res = send("POST", "/api/v1/auth/register",
                origin, null, "{\"email\":\"cors-" + UUID.randomUUID() + "@example.com\",\"password\":\"Cors-Test-Pass-2026\","
                        + "\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}");
        assertThat(res.statusCode()).isEqualTo(201);
        return res;
    }

    private String registerParent(String origin) throws Exception {
        return extract(register(origin).body(), "accessToken");
    }

    private HttpResponse<String> preflight(String path, String origin, String method, String headers) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", origin)
                .header("Access-Control-Request-Method", method)
                .header("Access-Control-Request-Headers", headers)
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> send(String method, String path, String origin, String bearer, String json) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
        if (json != null) {
            req.header("Content-Type", "application/json");
        }
        if (origin != null) {
            req.header("Origin", origin);
        }
        if (bearer != null) {
            req.header("Authorization", "Bearer " + bearer);
        }
        return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String extract(String json, String field) {
        Matcher m = Pattern.compile(String.format(FIELD.pattern(), Pattern.quote(field))).matcher(json);
        assertThat(m.find()).as("field %s in %s", field, json).isTrue();
        return m.group(2);
    }

}
