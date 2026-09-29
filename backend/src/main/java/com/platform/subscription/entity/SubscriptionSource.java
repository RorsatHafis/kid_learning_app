package com.platform.subscription.entity;

/** How a subscription came about: an administrator's grant, or (once integrated) a payment provider. */
public enum SubscriptionSource {
    ADMIN_GRANT,
    PROVIDER
}
