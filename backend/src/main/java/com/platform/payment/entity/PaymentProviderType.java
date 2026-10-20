package com.platform.payment.entity;

/**
 * How an order is paid. DEV_SIMULATOR exists only where development payments are switched on and never in
 * production (PaymentProperties); MANUAL is a transfer or invoice confirmed by an administrator against the
 * real receipt; ABA_PAYWAY is the bank's gateway, confirmed only by its own check-transaction answer.
 */
public enum PaymentProviderType {

    DEV_SIMULATOR,
    MANUAL,
    ABA_PAYWAY

}
