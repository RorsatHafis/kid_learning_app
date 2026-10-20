package com.platform.payment.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Expires pending payments past their lifetime, so a payment nobody finished doesn't block the customer's next
 * one. Orders are also expired whenever they are read; this catches the ones nobody looks at.
 */
@Component
public class PaymentSweep {

    private final PaymentService payments;

    public PaymentSweep(PaymentService payments) {

        this.payments = payments;

    }

    @Scheduled(fixedDelayString = "${platform.payments.sweep-interval:PT5M}", initialDelayString = "PT1M")
    public void expireDue() {

        payments.expireDue();

    }

}
