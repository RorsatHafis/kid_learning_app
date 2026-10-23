package com.platform.payment;

import com.platform.payment.config.PaymentProperties;
import com.platform.payment.config.PaymentSafetyGuard;
import com.platform.payment.entity.PaymentStatus;
import com.platform.payment.provider.AbaPaywaySigner;
import com.platform.payment.provider.DevSimulatorProvider;
import com.platform.payment.provider.ManualPaymentProvider;
import com.platform.payment.provider.PaymentProvider;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Y43 without a server: the production guard against simulated payments, which methods are offered, and the
 * ABA PayWay contract as its documentation specifies it - request signing and reading check-transaction
 * answers. (The PayWay contract is confirmed only against its sandbox, with merchant credentials.)
 */
class PaymentContractTests {

    private static PaymentProperties properties(boolean simulator, boolean manual, String instructions) {
        return new PaymentProperties(Duration.ofMinutes(30), Duration.ofDays(14), new PaymentProperties.DevSimulator(simulator),
                new PaymentProperties.Manual(manual, instructions), null);
    }

    @Test
    void productionRefusesToStartWithTheSimulatorOn() {
        MockEnvironment production = new MockEnvironment();
        production.setActiveProfiles("prod");
        assertThatThrownBy(() -> new PaymentSafetyGuard(properties(true, false, null), production).afterPropertiesSet())
                .hasMessageContaining("PAYMENTS_DEV_SIMULATOR_ENABLED must be false in production");
        assertThatNoException().isThrownBy(() -> new PaymentSafetyGuard(properties(false, false, null), production).afterPropertiesSet());
        MockEnvironment local = new MockEnvironment();
        local.setActiveProfiles("local");
        assertThatNoException().isThrownBy(() -> new PaymentSafetyGuard(properties(true, false, null), local).afterPropertiesSet());
    }

    @Test
    void aMethodIsOfferedOnlyWhenConfigured() {
        assertThat(new DevSimulatorProvider(properties(false, false, null)).available()).isFalse();
        // Manual payment without the owner's own instructions isn't offered: no invented payee details.
        // (M8b: until an administrator saves instructions in the app, the environment's apply.)
        assertThat(manual(properties(false, true, "  ")).available()).isFalse();
        assertThat(manual(properties(false, true, "Pay to: (owner's account)")).available()).isTrue();
        // ABA PayWay can't be switched on without its merchant id, key and return address.
        assertThatThrownBy(() -> new PaymentProperties.AbaPayway(true, "https://checkout-sandbox.payway.com.kh", "", "", "", null))
                .hasMessageContaining("ABA_PAYWAY_MERCHANT_ID");
    }

    /** The manual method with no instructions saved in the app yet. */
    private static ManualPaymentProvider manual(PaymentProperties properties) {
        var instructions = new com.platform.payment.service.PaymentInstructionsService(
                org.mockito.Mockito.mock(org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate.class),
                org.mockito.Mockito.mock(com.platform.identity.repository.AccountRepository.class),
                org.mockito.Mockito.mock(com.platform.content.repository.ContentAssetRepository.class),
                org.mockito.Mockito.mock(com.platform.audit.service.AuditService.class), properties);
        return new ManualPaymentProvider(properties, instructions, org.mockito.Mockito.mock(com.platform.content.media.MediaService.class));
    }

    @Test
    void payWayRequestsAreSignedAsDocumented() throws Exception {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("req_time", "20261020101500");
        fields.put("merchant_id", "test-merchant");
        fields.put("tran_id", "BKABC123");
        fields.put("amount", "5.00");
        fields.put("currency", "USD");
        // Documented order: req_time, merchant_id, tran_id, amount, (absent fields empty) ... currency ...
        String expected = hmac("20261020101500test-merchantBKABC1235.00USD", "test-key");
        assertThat(AbaPaywaySigner.sign(AbaPaywaySigner.PURCHASE_FIELDS, fields, "test-key")).isEqualTo(expected);
        assertThat(AbaPaywaySigner.sign(AbaPaywaySigner.CHECK_FIELDS, fields, "test-key"))
                .isEqualTo(hmac("20261020101500test-merchantBKABC123", "test-key"));
    }

    @Test
    void onlyAnAnsweredCheckSettlesAPayWayPayment() throws Exception {
        java.lang.reflect.Method interpret = com.platform.payment.provider.AbaPaywayProvider.class
                .getDeclaredMethod("interpret", Map.class, String.class);
        interpret.setAccessible(true);
        Map<String, Object> approved = Map.of("status", Map.of("code", "00"),
                "data", Map.of("payment_status_code", 0, "original_amount", 5.00, "payment_currency", "USD", "apv", "A1"));
        @SuppressWarnings("unchecked")
        Optional<PaymentProvider.TrustedStatus> ok = (Optional<PaymentProvider.TrustedStatus>) interpret.invoke(null, approved, "USD");
        assertThat(ok).get().satisfies(status -> {
            assertThat(status.status()).isEqualTo(PaymentStatus.SUCCEEDED);
            assertThat(status.amountMinor()).isEqualTo(500);
        });
        // Not found, a wrong hash or an unknown status settles nothing.
        assertThat(interpret.invoke(null, Map.of("status", Map.of("code", 6)), "USD")).isEqualTo(Optional.empty());
        assertThat(interpret.invoke(null, Map.of("status", Map.of("code", "00"), "data", Map.of("payment_status_code", 9)), "USD"))
                .isEqualTo(Optional.empty());
        Map<String, Object> declined = Map.of("status", Map.of("code", "00"), "data", Map.of("payment_status_code", 3));
        @SuppressWarnings("unchecked")
        Optional<PaymentProvider.TrustedStatus> no = (Optional<PaymentProvider.TrustedStatus>) interpret.invoke(null, declined, "USD");
        assertThat(no).get().extracting(PaymentProvider.TrustedStatus::status).isEqualTo(PaymentStatus.FAILED);
    }

    private static String hmac(String message, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        return Base64.getEncoder().encodeToString(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
    }

}
