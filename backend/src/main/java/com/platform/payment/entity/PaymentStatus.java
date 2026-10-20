package com.platform.payment.entity;

/**
 * Where a payment order stands (V62). PENDING is the only state a customer can act on; SUCCEEDED is the
 * only state that grants access; REFUNDED takes back exactly what SUCCEEDED gave. FAILED, CANCELLED and
 * EXPIRED end an attempt without access - a new attempt is a new order.
 */
public enum PaymentStatus {

    PENDING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    EXPIRED,
    REFUNDED;

    /** Whether money can still be confirmed for this order (a provider or a reviewer saying it arrived). */
    public boolean canStillSucceed() {

        return this == PENDING || this == FAILED || this == CANCELLED || this == EXPIRED;

    }

}
