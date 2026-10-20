package com.platform.payment.provider;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * ABA PayWay request signing, as its developer documentation specifies (purchase and check-transaction,
 * read 2026-10-20): Base64 of HMAC-SHA512 over the listed fields concatenated in a fixed order, with no
 * separators, keyed by the merchant's API key. An absent optional field contributes an empty string.
 * This is the documented contract; it is confirmed only once the sandbox accepts a signed request.
 */
public final class AbaPaywaySigner {

    /** The purchase hash's field order (the "hash" section of the purchase API). */
    public static final List<String> PURCHASE_FIELDS = List.of("req_time", "merchant_id", "tran_id", "amount", "items",
            "shipping", "firstname", "lastname", "email", "phone", "type", "payment_option", "return_url", "cancel_url",
            "continue_success_url", "return_deeplink", "currency", "custom_fields", "return_params", "payout", "lifetime",
            "additional_params", "google_pay_token", "skip_success_page");

    /** The check-transaction hash's field order. */
    public static final List<String> CHECK_FIELDS = List.of("req_time", "merchant_id", "tran_id");

    private AbaPaywaySigner() {

    }

    public static String sign(List<String> order, Map<String, String> fields, String apiKey) {

        StringBuilder message = new StringBuilder();
        for (String name : order) {
            message.append(fields.getOrDefault(name, ""));
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(apiKey.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return Base64.getEncoder().encodeToString(mac.doFinal(message.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA512 unavailable", e);
        }

    }

}
