package com.platform;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * M8 test support. Actions that move money or access need the password re-entered (a step-up proof, X-Step-Up): a test
 * steps its administrator up once, and its request helper adds the proof whenever that administrator calls. Confirming a
 * transfer needs the receipt's amount and currency, which a test takes from the order itself.
 *
 * <p>M9a: an administrator's step-up also needs a code from an authenticator app. The first step-up turns one on (as
 * the administrator would on their account page) and keeps its key here, so later step-ups compute the code; a code is
 * accepted once, so a second step-up in the same 30 seconds uses the next one, waiting for it when needed.
 */
public final class TestAdmin {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Map<String, String> STEP_UPS = new ConcurrentHashMap<>();
    private static final Map<String, byte[]> AUTHENTICATORS = new ConcurrentHashMap<>();
    private static final Map<String, Long> LAST_STEPS = new ConcurrentHashMap<>();

    private TestAdmin() {
    }

    /** Re-enters this signed-in account's password (and an administrator's code); its requests then carry the proof. */
    public static String stepUp(int port, String accessToken, String password) throws Exception {
        String accountId = JSON.readTree(call(port, accessToken, "GET", "/api/v1/account", null).body()).get("id").asString();
        HttpResponse<String> response = call(port, accessToken, "POST", "/api/v1/account/step-up", stepUpBody(accountId, password));
        if (response.statusCode() == 403 && response.body().contains("secondFactorSetupRequired")) {
            turnOnAuthenticator(port, accessToken, password);
            response = call(port, accessToken, "POST", "/api/v1/account/step-up", stepUpBody(accountId, password));
        }
        if (response.statusCode() != 200) {
            throw new IllegalStateException("step-up failed: " + response.statusCode() + " " + response.body());
        }
        String proof = JSON.readTree(response.body()).get("stepUpToken").asString();
        STEP_UPS.put(accessToken, proof);
        return accessToken;
    }

    /** Turns on this administrator's authenticator app; returns the recovery codes. */
    public static JsonNode turnOnAuthenticator(int port, String accessToken, String password) throws Exception {
        String accountId = JSON.readTree(call(port, accessToken, "GET", "/api/v1/account", null).body()).get("id").asString();
        HttpResponse<String> setup = call(port, accessToken, "POST", "/api/v1/account/second-factor/setup",
                "{\"password\":\"" + password + "\"}");
        if (setup.statusCode() != 200) {
            throw new IllegalStateException("authenticator setup failed: " + setup.statusCode() + " " + setup.body());
        }
        AUTHENTICATORS.put(accountId, com.platform.security.stepup.Totp.fromBase32(JSON.readTree(setup.body()).get("secret").asString()));
        LAST_STEPS.remove(accountId);
        HttpResponse<String> activated = call(port, accessToken, "POST", "/api/v1/account/second-factor/activate",
                "{\"code\":\"" + code(accountId) + "\"}");
        if (activated.statusCode() != 200) {
            throw new IllegalStateException("authenticator activation failed: " + activated.statusCode() + " " + activated.body());
        }
        return JSON.readTree(activated.body()).get("recoveryCodes");
    }

    /** The next code this account's authenticator app would show that the server hasn't accepted yet. */
    public static String code(String accountId) throws InterruptedException {
        byte[] secret = AUTHENTICATORS.get(accountId);
        if (secret == null) {
            throw new IllegalStateException("no authenticator app was turned on for " + accountId + " in this test run");
        }
        synchronized (secret) {
            long step = Math.max(com.platform.security.stepup.Totp.step(java.time.Instant.now()) - 1, LAST_STEPS.getOrDefault(accountId, Long.MIN_VALUE) + 1);
            while (step > com.platform.security.stepup.Totp.step(java.time.Instant.now()) + 1) {
                Thread.sleep(500);
            }
            LAST_STEPS.put(accountId, step);
            return com.platform.security.stepup.Totp.code(secret, step);
        }
    }

    private static String stepUpBody(String accountId, String password) throws InterruptedException {
        return AUTHENTICATORS.containsKey(accountId)
                ? "{\"password\":\"" + password + "\",\"code\":\"" + code(accountId) + "\"}"
                : "{\"password\":\"" + password + "\"}";
    }

    private static HttpResponse<String> call(int port, String accessToken, String method, String path, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json").header("Authorization", "Bearer " + accessToken);
        request = body == null ? request.method(method, HttpRequest.BodyPublishers.noBody())
                : request.method(method, HttpRequest.BodyPublishers.ofString(body));
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** The step-up proof for this access token, or null when it hasn't stepped up. */
    public static String stepUpFor(String accessToken) {
        return accessToken == null ? null : STEP_UPS.get(accessToken);
    }

    /** Adds the step-up proof to a request when its bearer has one. */
    public static HttpRequest.Builder withStepUp(HttpRequest.Builder request, String accessToken) {
        String proof = stepUpFor(accessToken);
        return proof == null ? request : request.header("X-Step-Up", proof);
    }

    /** A confirmation body quoting the order's own amount and currency, as the real receipt would show them. */
    public static String receipt(int port, String adminToken, String orderId, String reference) throws Exception {
        HttpResponse<String> response = HTTP.send(withStepUp(HttpRequest.newBuilder(URI.create("http://localhost:" + port
                + "/api/v1/admin/payments/" + orderId)).header("Authorization", "Bearer " + adminToken).GET(), adminToken).build(),
                HttpResponse.BodyHandlers.ofString());
        JsonNode order = JSON.readTree(response.body());
        return "{\"receiptReference\":\"" + reference + "\",\"amountReceivedMinor\":" + order.get("amountMinor").asLong()
                + ",\"currency\":\"" + order.get("currency").asString() + "\"}";
    }

}
