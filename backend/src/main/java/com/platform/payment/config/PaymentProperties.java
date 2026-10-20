package com.platform.payment.config;

import com.platform.common.config.ConfigurationContract;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * How this deployment takes payments (Y43). Every method is off unless configured; with none on, no plan
 * can be bought and the product says so. Prices come from the plans themselves (set by an administrator),
 * never from here.
 *
 * @param pendingLifetime how long an online payment (simulator, ABA PayWay) may stay pending before it expires
 * @param manualLifetime  how long a bank transfer or invoice may stay pending awaiting its receipt
 * @param devSimulator    the development payment simulator - refused at startup in production
 * @param manual          payment by transfer or invoice, confirmed by an administrator against the real receipt
 * @param abaPayway       ABA PayWay; merchant secrets live only in the backend's environment
 */
@ConfigurationProperties(prefix = "platform.payments")
public record PaymentProperties(Duration pendingLifetime, Duration manualLifetime, DevSimulator devSimulator, Manual manual,
                                AbaPayway abaPayway) {

    public PaymentProperties {

        ConfigurationContract.requirePositive(pendingLifetime, "PAYMENTS_PENDING_LIFETIME");
        ConfigurationContract.requirePositive(manualLifetime, "PAYMENTS_MANUAL_LIFETIME");
        devSimulator = devSimulator == null ? new DevSimulator(false) : devSimulator;
        manual = manual == null ? new Manual(false, null) : manual;
        abaPayway = abaPayway == null ? new AbaPayway(false, null, null, null, null, null) : abaPayway;

    }

    /** A clearly labelled simulation for development and tests: it never moves money. */
    public record DevSimulator(boolean enabled) {
    }

    /**
     * @param instructions what the customer is told to do - the owner's own payee and account details, as they
     *                     wrote them. Blank means manual payment isn't offered, even when enabled.
     */
    public record Manual(boolean enabled, String instructions) {

        public boolean available() {

            return enabled && instructions != null && !instructions.isBlank();

        }

    }

    /**
     * @param baseUrl    the gateway (the sandbox until production is approved)
     * @param merchantId the merchant id ABA Bank issued
     * @param apiKey     the signing key ABA Bank issued - a secret: environment only, never logged or returned
     * @param returnUrl  where PayWay sends its notice after a payment (this API's callback address)
     * @param successUrl where the customer's browser goes after paying (the family's plan page)
     */
    public record AbaPayway(boolean enabled, String baseUrl, String merchantId, String apiKey, String returnUrl,
                            String successUrl) {

        public AbaPayway {

            if (enabled) {
                ConfigurationContract.requireText(baseUrl, "ABA_PAYWAY_BASE_URL");
                ConfigurationContract.requireText(merchantId, "ABA_PAYWAY_MERCHANT_ID");
                ConfigurationContract.requireText(apiKey, "ABA_PAYWAY_API_KEY");
                ConfigurationContract.requireText(returnUrl, "ABA_PAYWAY_RETURN_URL");
            }

        }

    }

}
