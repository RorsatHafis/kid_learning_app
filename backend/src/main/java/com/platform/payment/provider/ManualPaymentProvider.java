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
    private final com.platform.payment.service.PaymentInstructionsService instructions;
    private final com.platform.content.media.MediaService media;

    public ManualPaymentProvider(PaymentProperties properties, com.platform.payment.service.PaymentInstructionsService instructions,
                                 com.platform.content.media.MediaService media) {

        this.properties = properties;
        this.instructions = instructions;
        this.media = media;

    }

    @Override
    public PaymentProviderType type() {

        return PaymentProviderType.MANUAL;

    }

    @Override
    public boolean available() {

        // M8b: the administrator's latest saved instructions, or the environment's until one is saved.
        return instructions.current().available();

    }

    @Override
    public Duration lifetime() {

        return properties.manualLifetime();

    }

    @Override
    public Checkout checkout(PaymentOrder order) {

        // M8b: the instructions this order was started with - a later change never rewrites an issued invoice.
        var used = instructions.forOrder(order.getInstructionsVersionId());
        String qr = used.qrAssetId() == null ? null
                : java.util.Optional.ofNullable(media.refs(java.util.Set.of(used.qrAssetId())).get(used.qrAssetId()))
                .map(com.platform.content.media.MediaService.MediaRef::url).orElse(null);
        return new Checkout(used.instructionsEn(), null, java.util.Map.of(), used.instructionsKm(), qr);

    }

    @Override
    public Optional<TrustedStatus> check(PaymentOrder order) {

        // Only a person holding the real receipt can confirm a transfer: see PaymentService#confirmManual.
        return Optional.empty();

    }

}
