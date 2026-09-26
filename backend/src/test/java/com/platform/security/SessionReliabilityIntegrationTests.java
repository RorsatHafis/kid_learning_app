package com.platform.security;

import com.platform.TestcontainersConfiguration;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Battle 4D, over real HTTP against real PostgreSQL: authentication semantics (401 =
 * not authenticated, 403 = authenticated but not permitted) and the refresh-session
 * state machine - rotation, replay, family revocation, logout, account disablement,
 * and genuinely concurrent refreshes resolved by PostgreSQL row locking.
 *
 * <p>Concurrency policy under test (established by AccountSessionService, preserved):
 * a refresh token is single-use; presenting one twice - even concurrently - is
 * treated as possible theft. At most one refresh ever succeeds, the family is
 * never forked, and the family is revoked. Clients must serialize their refreshes
 * (the frontend does: single-flight + cross-tab Web Lock).
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class SessionReliabilityIntegrationTests {

    private static final String TEST_JWT_SECRET = "test-secret-key-for-automated-tests-only-0123456789ab";
    private static final String TEST_JWT_ISSUER = "yulme-test";
    private static final Pattern FIELD = Pattern.compile("\"(%s)\"\\s*:\\s*\"([^\"]*)\"");

    @Value("${local.server.port}") private int port;
    @Autowired private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    // ------------------------------------------------------------------ 401 vs 403

    @Test
    void noAccessTokenIs401WithABearerChallenge() throws Exception {
        HttpResponse<String> res = get("/api/v1/children", null);

        assertThat(res.statusCode()).isEqualTo(401);
        assertThat(res.headers().firstValue("WWW-Authenticate")).hasValue("Bearer");
        assertThat(res.body()).contains("\"status\":401");
    }

    @Test
    void malformedAccessTokenIs401InvalidToken() throws Exception {
        HttpResponse<String> res = get("/api/v1/children", "not-a-jwt");

        assertThat(res.statusCode()).isEqualTo(401);
        assertThat(res.headers().firstValue("WWW-Authenticate").orElse("")).contains("error=\"invalid_token\"");
    }

    @Test
    void expiredAccessTokenIs401InvalidToken() throws Exception {
        Session session = registerParent();
        String expired = Jwts.builder()
                .subject(session.accountId())
                .claim("role", "PARENT")
                .issuer(TEST_JWT_ISSUER)
                .issuedAt(Date.from(Instant.now().minusSeconds(3600)))
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(TEST_JWT_SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();

        HttpResponse<String> res = get("/api/v1/children", expired);

        assertThat(res.statusCode()).isEqualTo(401);
        assertThat(res.headers().firstValue("WWW-Authenticate").orElse("")).contains("error=\"invalid_token\"");
    }

    @Test
    void validAccessTokenReachesPermittedResources() throws Exception {
        Session session = registerParent();

        assertThat(get("/api/v1/children", session.accessToken()).statusCode()).isEqualTo(200);
    }

    @Test
    void authenticatedButForbiddenStays403AndIsNotAnAuthenticationChallenge() throws Exception {
        Session owner = registerParent();
        String childId = createChild(owner);
        Session stranger = registerParent();

        HttpResponse<String> res = get("/api/v1/children/" + childId + "/progress", stranger.accessToken());

        assertThat(res.statusCode()).isEqualTo(403);
        assertThat(res.headers().firstValue("WWW-Authenticate")).isEmpty();
    }

    @Test
    void publicEndpointsStayPublicEvenWithARejectedBearerHeader() throws Exception {
        assertThat(get("/actuator/health", null).statusCode()).isEqualTo(200);

        Session session = registerParent();
        HttpResponse<String> refresh = post("/api/v1/auth/refresh", "{\"refreshToken\":\"" + session.refreshToken() + "\"}", "garbage");
        assertThat(refresh.statusCode()).isEqualTo(200);
    }

    // ------------------------------------------------------------------ normal refresh

    @Test
    void refreshRotatesTheTokenAndKeepsTheChainConsistent() throws Exception {
        Session first = registerParent();

        Session second = refresh(first.refreshToken());

        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThat(get("/api/v1/children", second.accessToken()).statusCode()).isEqualTo(200);

        Map<String, Object> old = sessionRow(first.refreshToken());
        Map<String, Object> next = sessionRow(second.refreshToken());
        assertThat(old.get("status")).isEqualTo("ROTATED");
        assertThat(old.get("replaced_by_session_id")).isEqualTo(next.get("id"));
        assertThat(next.get("status")).isEqualTo("ACTIVE");
        // stored hashed, never raw
        assertThat(next.get("refresh_token_hash")).isEqualTo(sha256(second.refreshToken()))
                .isNotEqualTo(second.refreshToken());
    }

    @Test
    void refreshWithAnUnknownOrMissingTokenIsRejected() throws Exception {
        assertThat(post("/api/v1/auth/refresh", "{\"refreshToken\":\"" + UUID.randomUUID() + "\"}", null).statusCode())
                .isEqualTo(401);
        assertThat(post("/api/v1/auth/refresh", "{\"refreshToken\":\"\"}", null).statusCode()).isEqualTo(400);
    }

    // ------------------------------------------------------------------ replay / family

    @Test
    void replayingARotatedTokenRevokesTheWholeFamily() throws Exception {
        Session t1 = registerParent();
        Session t2 = refresh(t1.refreshToken());
        Session t3 = refresh(t2.refreshToken());

        assertThat(refreshStatus(t1.refreshToken())).isEqualTo(401);    // replay detected
        assertThat(familyStatuses(t1.accountId())).containsOnly("REVOKED");
        assertThat(refreshStatus(t3.refreshToken())).isEqualTo(401);    // the tip cannot continue
        assertThat(refreshStatus(t1.refreshToken())).isEqualTo(401);    // nor can the replay, ever
    }

    @Test
    void replayIsStillDetectedOnALongLivedSessionChain() throws Exception {
        // Every refresh adds a link, and each extends the 30-day expiry, so a regularly used
        // session's chain grows without bound (~4-8 links a day). Reuse detection walks that chain.
        Session first = registerParent();
        Session current = first;
        for (int i = 0; i < 205; i++) {
            current = refresh(current.refreshToken());
        }

        assertThat(refreshStatus(first.refreshToken())).isEqualTo(401);
        assertThat(familyStatuses(first.accountId())).containsOnly("REVOKED");
        assertThat(refreshStatus(current.refreshToken())).isEqualTo(401);
    }

    // ------------------------------------------------------------------ lost responses (PD-03, N19)

    @Test
    void aRetryAfterALostRefreshResponseKeepsTheSessionAlive() throws Exception {
        Session t1 = registerParent();
        Session lost = refresh(t1.refreshToken()); // succeeded server-side; the response never arrived

        Session recovered = refresh(t1.refreshToken());

        assertThat(get("/api/v1/children", recovered.accessToken()).statusCode()).isEqualTo(200);
        assertThat(familyStatuses(t1.accountId()).stream().filter("ACTIVE"::equals)).hasSize(1);
        assertThat(sessionRow(recovered.refreshToken()).get("status")).isEqualTo("ACTIVE");
        assertThat(sessionRow(lost.refreshToken()).get("status")).isEqualTo("REVOKED");
        assertThat(sessionRow(t1.refreshToken()).get("replaced_by_session_id"))
                .isEqualTo(sessionRow(recovered.refreshToken()).get("id"));
        // The session carries on normally.
        assertThat(refreshStatus(refresh(recovered.refreshToken()).refreshToken())).isEqualTo(200);
        assertThat(auditCount(t1.accountId(), "SESSION_REFRESH_RETRY_RECOVERED")).isEqualTo(1);
    }

    @Test
    void theReplacementThatWasNeverDeliveredCannotBeUsedLater() throws Exception {
        Session t1 = registerParent();
        Session intercepted = refresh(t1.refreshToken()); // a response someone else captured
        Session recovered = refresh(t1.refreshToken());

        assertThat(refreshStatus(intercepted.refreshToken())).isEqualTo(401);
        assertThat(familyStatuses(t1.accountId())).containsOnly("REVOKED"); // reuse detected
        assertThat(refreshStatus(recovered.refreshToken())).isEqualTo(401);
    }

    @Test
    void aRetryFromAnotherBrowserIsReplay() throws Exception {
        Session t1 = registerParent();
        refresh(t1.refreshToken());

        HttpResponse<String> retry = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/refresh"))
                .header("Content-Type", "application/json").header("User-Agent", "Another-Browser/1.0")
                .POST(HttpRequest.BodyPublishers.ofString("{\"refreshToken\":\"" + t1.refreshToken() + "\"}")).build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(retry.statusCode()).isEqualTo(401);
        assertThat(familyStatuses(t1.accountId())).containsOnly("REVOKED");
        assertThat(auditCount(t1.accountId(), "SESSION_REUSE_DETECTED")).isEqualTo(1);
    }

    @Test
    void aRetryAfterTheGraceWindowIsReplay() throws Exception {
        Session t1 = registerParent();
        refresh(t1.refreshToken());
        jdbc.update("update account_sessions set rotated_at = now() - interval '31 seconds' where refresh_token_hash = ?",
                sha256(t1.refreshToken()));

        assertThat(refreshStatus(t1.refreshToken())).isEqualTo(401);
        assertThat(familyStatuses(t1.accountId())).containsOnly("REVOKED");
    }

    @Test
    void aThiefWhoWinsTheGraceWindowIsCutOffAtTheVictimsNextRefresh() throws Exception {
        Session stolen = registerParent();
        Session victim = refresh(stolen.refreshToken());   // the victim refreshed and holds the new token
        Session thief = refresh(stolen.refreshToken());    // a copy of the old token, retried at once

        // The victim's token was revoked in the thief's favour; its next refresh is a replay,
        // which ends the whole family - the thief's session included.
        assertThat(refreshStatus(victim.refreshToken())).isEqualTo(401);
        assertThat(familyStatuses(stolen.accountId())).containsOnly("REVOKED");
        assertThat(refreshStatus(thief.refreshToken())).isEqualTo(401);
    }

    // ------------------------------------------------------------------ concurrency

    @Test
    void twoSimultaneousRefreshesOfOneTokenNeverForkTheFamily() throws Exception {
        assertSimultaneousSameTokenRefresh(2);
    }

    @Test
    void manySimultaneousRefreshesOfOneTokenNeverForkTheFamily() throws Exception {
        assertSimultaneousSameTokenRefresh(8);
    }

    private void assertSimultaneousSameTokenRefresh(int callers) throws Exception {
        Session t1 = registerParent();
        List<HttpResponse<String>> responses = concurrently(callers,
                () -> post("/api/v1/auth/refresh", "{\"refreshToken\":\"" + t1.refreshToken() + "\"}", null));

        // The presented row is locked, so the calls are serialized: the first rotates, each
        // later one is a same-browser retry within the grace window. The family never forks:
        // exactly one live token remains, whichever call issued it.
        assertThat(responses).allMatch(r -> r.statusCode() == 200 || r.statusCode() == 401);
        assertThat(familyStatuses(t1.accountId()).stream().filter("ACTIVE"::equals)).hasSize(1);
        List<String> issued = responses.stream().filter(r -> r.statusCode() == 200)
                .map(r -> extract(r.body(), "refreshToken")).toList();
        List<String> live = issued.stream().filter(token -> "ACTIVE".equals(sessionRow(token).get("status"))).toList();
        assertThat(live).hasSize(1);
        // Every other token handed out is dead, and presenting one is still detected as reuse.
        issued.stream().filter(token -> !token.equals(live.get(0))).findFirst().ifPresent(stale -> {
            try {
                assertThat(refreshStatus(stale)).isEqualTo(401);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
            assertThat(familyStatuses(t1.accountId())).containsOnly("REVOKED");
        });
    }

    @Test
    void serializedRefreshesFromSeveralTabsKeepTheSessionAlive() throws Exception {
        // What the frontend's Web Lock produces: tabs take turns, each using the latest token.
        Session current = registerParent();
        for (int tab = 0; tab < 5; tab++) {
            current = refresh(current.refreshToken());
        }

        assertThat(get("/api/v1/children", current.accessToken()).statusCode()).isEqualTo(200);
        assertThat(familyStatuses(current.accountId())).containsOnly("ROTATED", "ACTIVE");
        assertThat(familyStatuses(current.accountId()).stream().filter("ACTIVE"::equals)).hasSize(1);
    }

    // ------------------------------------------------------------------ logout / account state

    @Test
    void logoutEndsTheSessionAndRefreshAfterLogoutFails() throws Exception {
        Session t1 = registerParent();
        Session t2 = refresh(t1.refreshToken());

        assertThat(post("/api/v1/auth/logout", "{\"refreshToken\":\"" + t2.refreshToken() + "\"}", null).statusCode())
                .isEqualTo(204);
        assertThat(refreshStatus(t2.refreshToken())).isEqualTo(401);
        assertThat(sessionRow(t2.refreshToken()).get("status")).isEqualTo("REVOKED");
    }

    @Test
    void loggingOutAStolenRotatedTokenCannotShieldTheThiefsNewerSession() throws Exception {
        Session stolen = registerParent();
        Session thief = refresh(stolen.refreshToken());     // thief uses the stolen token first

        // Before Battle 4D this revoked only the stolen row and cut the rotation chain, so the
        // victim's later replay of the stolen token could no longer reach the thief's session.
        post("/api/v1/auth/logout", "{\"refreshToken\":\"" + stolen.refreshToken() + "\"}", null);

        assertThat(refreshStatus(thief.refreshToken())).isEqualTo(401);
        assertThat(familyStatuses(stolen.accountId())).containsOnly("REVOKED");
    }

    @Test
    void aDisabledAccountCannotRefresh() throws Exception {
        Session session = registerParent();
        jdbc.update("update accounts set status = 'DISABLED' where id = ?::uuid", session.accountId());

        assertThat(refreshStatus(session.refreshToken())).isEqualTo(401);
        assertThat(sessionRow(session.refreshToken()).get("status")).isEqualTo("REVOKED");
    }

    // ------------------------------------------------------------------ helpers

    private record Session(String accountId, String accessToken, String refreshToken) {
    }

    private Session registerParent() throws Exception {
        String email = "session-" + UUID.randomUUID() + "@example.com";
        HttpResponse<String> res = post("/api/v1/auth/register",
                "{\"email\":\"" + email + "\",\"password\":\"Session-Test-Pass-2026\",\"idempotencyKey\":\""
                        + UUID.randomUUID() + "\"}", null);
        assertThat(res.statusCode()).isEqualTo(201);
        return session(res.body());
    }

    private String createChild(Session parent) throws Exception {
        HttpResponse<String> res = post("/api/v1/children",
                "{\"displayName\":\"Test Child\",\"dateOfBirth\":\"2019-01-01\"}", parent.accessToken());
        assertThat(res.statusCode()).isEqualTo(201);
        return extract(res.body(), "id");
    }

    private Session refresh(String refreshToken) throws Exception {
        HttpResponse<String> res = post("/api/v1/auth/refresh", "{\"refreshToken\":\"" + refreshToken + "\"}", null);
        assertThat(res.statusCode()).isEqualTo(200);
        return session(res.body());
    }

    private int refreshStatus(String refreshToken) throws Exception {
        return post("/api/v1/auth/refresh", "{\"refreshToken\":\"" + refreshToken + "\"}", null).statusCode();
    }

    private static Session session(String body) {
        return new Session(extract(body, "accountId"), extract(body, "accessToken"), extract(body, "refreshToken"));
    }

    private Map<String, Object> sessionRow(String rawRefreshToken) {
        return jdbc.queryForMap("select id, status, replaced_by_session_id, refresh_token_hash "
                + "from account_sessions where refresh_token_hash = ?", sha256(rawRefreshToken));
    }

    private int auditCount(String accountId, String action) {
        return jdbc.queryForObject("select count(*) from audit_events where actor_id = ?::uuid and action = ?",
                Integer.class, accountId, action);
    }

    private List<String> familyStatuses(String accountId) {
        // one login per test account, so the account's sessions are exactly one family
        return jdbc.queryForList("select status from account_sessions where account_id = ?::uuid", String.class, accountId);
    }

    private List<HttpResponse<String>> concurrently(int callers, java.util.concurrent.Callable<HttpResponse<String>> call)
            throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(callers);
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        try {
            List<Future<HttpResponse<String>>> futures = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                futures.add(pool.submit(() -> {
                    barrier.await();
                    return call.call();
                }));
            }
            List<HttpResponse<String>> results = new ArrayList<>();
            for (Future<HttpResponse<String>> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private HttpResponse<String> get(String path, String bearer) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (bearer != null) {
            req.header("Authorization", "Bearer " + bearer);
        }
        return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String json, String bearer) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
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

    private static String sha256(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

}
