package com.platform.payment.provider;

import com.platform.payment.config.PaymentProperties;
import com.platform.payment.entity.PaymentOrder;
import com.platform.payment.entity.PaymentProviderType;
import com.platform.payment.entity.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * ABA PayWay (Y43): the integration boundary, prepared from the bank's developer documentation and NOT yet
 * proven against its sandbox - it stays off until merchant credentials are configured and it has been tested
 * there. The customer's browser posts a signed form to PayWay's hosted checkout; PayWay later notifies our
 * callback, which is treated only as a hint: the documented notice isn't signed, so the order is settled
 * solely from {@link #check}, a signed check-transaction request this server makes itself. The API key never
 * leaves the backend.
 */
@Component
public class AbaPaywayProvider implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(AbaPaywayProvider.class);
    private static final DateTimeFormatter REQ_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    static final String PURCHASE_PATH = "/api/payment-gateway/v1/payments/purchase";
    static final String CHECK_PATH = "/api/payment-gateway/v1/payments/check-transaction-2";

    private final PaymentProperties.AbaPayway config;
    private final Duration lifetime;
    private final RestClient http;

    public AbaPaywayProvider(PaymentProperties properties) {

        this.config = properties.abaPayway();
        this.lifetime = properties.pendingLifetime();
        this.http = RestClient.builder().build();

    }

    @Override
    public PaymentProviderType type() {

        return PaymentProviderType.ABA_PAYWAY;

    }

    @Override
    public boolean available() {

        return config.enabled();

    }

    @Override
    public Duration lifetime() {

        return lifetime;

    }

    @Override
    public Checkout checkout(PaymentOrder order) {

        Map<String, String> fields = purchaseFields(order, reqTime());
        fields.put("hash", AbaPaywaySigner.sign(AbaPaywaySigner.PURCHASE_FIELDS, fields, config.apiKey()));
        return new Checkout(null, trimmed(config.baseUrl()) + PURCHASE_PATH, fields);

    }

    /** The purchase form for one order (everything but the hash), in the documented field names. */
    Map<String, String> purchaseFields(PaymentOrder order, String reqTime) {

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("req_time", reqTime);
        fields.put("merchant_id", config.merchantId());
        fields.put("tran_id", order.getReference());
        fields.put("amount", amountText(order.getAmountMinor(), order.getCurrency()));
        fields.put("type", "purchase");
        // "URL to receive callbacks upon payment completion, encrypted with Base64" (purchase API).
        fields.put("return_url", Base64.getEncoder().encodeToString(config.returnUrl().getBytes(StandardCharsets.UTF_8)));
        if (config.successUrl() != null && !config.successUrl().isBlank()) {
            fields.put("continue_success_url", config.successUrl());
        }
        fields.put("currency", order.getCurrency());
        fields.put("lifetime", String.valueOf(Math.max(3, lifetime.toMinutes())));
        return fields;

    }

    @Override
    public Optional<TrustedStatus> check(PaymentOrder order) {

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("req_time", reqTime());
        fields.put("merchant_id", config.merchantId());
        fields.put("tran_id", order.getReference());
        fields.put("hash", AbaPaywaySigner.sign(AbaPaywaySigner.CHECK_FIELDS, fields, config.apiKey()));
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = http.post()
                    .uri(trimmed(config.baseUrl()) + CHECK_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(fields)
                    .retrieve()
                    .body(Map.class);
            return interpret(body, order.getCurrency());
        } catch (RestClientException e) {
            // Never the request or the key: only that the check didn't answer.
            log.warn("ABA PayWay check-transaction failed for an order: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }

    }

    /**
     * Reads a check-transaction answer. Only "00" is an answer at all; payment_status_code 0 is approved,
     * 2 pending, 3 declined, 4 refunded, 7 cancelled. Anything else isn't trusted to settle an order.
     */
    @SuppressWarnings("unchecked")
    static Optional<TrustedStatus> interpret(Map<String, Object> body, String orderCurrency) {

        if (body == null || !(body.get("status") instanceof Map<?, ?> status) || !"00".equals(String.valueOf(status.get("code")))
                || !(body.get("data") instanceof Map<?, ?> rawData)) {
            return Optional.empty();
        }

        Map<String, Object> data = (Map<String, Object>) rawData;
        PaymentStatus mapped = switch (String.valueOf(data.get("payment_status_code"))) {
            case "0" -> PaymentStatus.SUCCEEDED;
            case "2" -> PaymentStatus.PENDING;
            case "3" -> PaymentStatus.FAILED;
            case "4" -> PaymentStatus.REFUNDED;
            case "7" -> PaymentStatus.CANCELLED;
            default -> null;
        };
        if (mapped == null) {
            return Optional.empty();
        }

        String currency = data.get("payment_currency") == null ? orderCurrency : String.valueOf(data.get("payment_currency"));
        Object amount = data.get("original_amount") != null ? data.get("original_amount") : data.get("payment_amount");
        long minor = amount == null ? -1 : toMinor(new BigDecimal(String.valueOf(amount)), currency);
        String approval = data.get("apv") == null ? null : String.valueOf(data.get("apv"));
        String eventId = "aba-check:" + data.get("payment_status_code") + ":" + (approval == null ? "" : approval);
        return Optional.of(new PaymentProvider.TrustedStatus(mapped, minor, currency, approval, eventId));

    }

    static String amountText(long minor, String currency) {

        return BigDecimal.valueOf(minor, fractionDigits(currency)).toPlainString();

    }

    static long toMinor(BigDecimal amount, String currency) {

        return amount.movePointRight(fractionDigits(currency)).longValueExact();

    }

    private static int fractionDigits(String currency) {

        int digits = Currency.getInstance(currency).getDefaultFractionDigits();
        return Math.max(digits, 0);

    }

    private static String reqTime() {

        return ZonedDateTime.now(ZoneOffset.UTC).format(REQ_TIME);

    }

    private static String trimmed(String url) {

        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;

    }

}
