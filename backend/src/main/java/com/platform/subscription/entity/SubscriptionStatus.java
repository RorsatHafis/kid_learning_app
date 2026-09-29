package com.platform.subscription.entity;

/**
 * The recorded state of a family's subscription. Whether it grants its plan right now also
 * depends on {@code current_period_end} - see {@code EntitlementService#effectivePlan}.
 */
public enum SubscriptionStatus {
    TRIALING,
    ACTIVE,
    /** A payment failed; access continues while the provider retries (set by a provider only). */
    PAST_DUE,
    /** Ended by the family or an administrator; access continues to the end of the paid period. */
    CANCELED,
    EXPIRED
}
