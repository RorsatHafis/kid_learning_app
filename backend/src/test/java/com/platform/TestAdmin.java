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
 */
public final class TestAdmin {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Map<String, String> STEP_UPS = new ConcurrentHashMap<>();

    private TestAdmin() {
    }

    /** Re-enters this signed-in account's password; its requests then carry the proof (see {@link #stepUpFor}). */
    public static String stepUp(int port, String accessToken, String password) throws Exception {
        HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/account/step-up"))
                .header("Content-Type", "application/json").header("Authorization", "Bearer " + accessToken)
                .POST(HttpRequest.BodyPublishers.ofString("{\"password\":\"" + password + "\"}")).build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("step-up failed: " + response.statusCode() + " " + response.body());
        }
        String proof = JSON.readTree(response.body()).get("stepUpToken").asString();
        STEP_UPS.put(accessToken, proof);
        return accessToken;
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
