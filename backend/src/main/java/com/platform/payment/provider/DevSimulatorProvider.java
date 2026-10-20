package com.platform.payment.provider;

import com.platform.payment.config.PaymentProperties;
import com.platform.payment.entity.PaymentOrder;
import com.platform.payment.entity.PaymentProviderType;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Development and test payments (Y43): a clearly labelled simulation that exercises the whole lifecycle -
 * success, failure, cancellation, repeated notices - without moving money. Available only when switched on,
 * and production refuses to start with it switched on (PaymentSafetyGuard). Outcomes are chosen through
 * PaymentService#simulate, never through a callback anyone could send.
 */
@Component
public class DevSimulatorProvider implements PaymentProvider {

    private final PaymentProperties properties;

    public DevSimulatorProvider(PaymentProperties properties) {

        this.properties = properties;

    }

    @Override
    public PaymentProviderType type() {

        return PaymentProviderType.DEV_SIMULATOR;

    }

    @Override
    public boolean available() {

        return properties.devSimulator().enabled();

    }

    @Override
    public Duration lifetime() {

        return properties.pendingLifetime();

    }

    @Override
    public Checkout checkout(PaymentOrder order) {

        return Checkout.instructions("TEST PAYMENT - a simulation for development and testing. No money moves.");

    }

    @Override
    public Optional<TrustedStatus> check(PaymentOrder order) {

        return Optional.empty();

    }

}
