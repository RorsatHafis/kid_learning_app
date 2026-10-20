package com.platform.payment.provider;

import com.platform.payment.config.PaymentProperties;
import com.platform.payment.entity.PaymentOrder;
import com.platform.payment.entity.PaymentProviderType;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Payment by bank transfer or invoice (Y43). The customer is shown the owner's own payment instructions -
 * never invented details - with the order's reference to quote; the order stays pending until an
 * administrator confirms the real receipt (or rejects it). Offered only when instructions are configured.
 */
@Component
public class ManualPaymentProvider implements PaymentProvider {

    private final PaymentProperties properties;

    public ManualPaymentProvider(PaymentProperties properties) {

        this.properties = properties;

    }

    @Override
    public PaymentProviderType type() {

        return PaymentProviderType.MANUAL;

    }

    @Override
    public boolean available() {

        return properties.manual().available();

    }

    @Override
    public Duration lifetime() {

        return properties.manualLifetime();

    }

    @Override
    public Checkout checkout(PaymentOrder order) {

        return Checkout.instructions(properties.manual().instructions().strip());

    }

    @Override
    public Optional<TrustedStatus> check(PaymentOrder order) {

        // Only a person holding the real receipt can confirm a transfer: see PaymentService#confirmManual.
        return Optional.empty();

    }

}
