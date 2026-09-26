package com.platform.security.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.platform.common.config.ConfigurationContract;

/**
 * Request budgets for the unauthenticated identity endpoints, per client address
 * (OD-01). They complement the per-account login lockout: the lockout stops guessing
 * one account's password, these stop one client from spraying many accounts,
 * mass-registering, hammering token refresh, or having reset emails sent to many
 * addresses (each account also has its own email limits, AccountRecoveryService).
 *
 * <p>Budgets are generous on purpose: families on mobile networks and schools share
 * public addresses (carrier-grade NAT), and a refused refresh must not look like a
 * lost session to a legitimate user.
 */
@ConfigurationProperties(prefix = "platform.security.rate-limit")
public record RateLimitProperties(int loginPerMinute, int registerPerHour, int refreshPerMinute,
                                  int recoveryPerHour) {

    public RateLimitProperties {
        ConfigurationContract.require(loginPerMinute >= 1,
                "RATE_LIMIT_LOGIN_PER_MINUTE must be at least 1, was " + loginPerMinute);
        ConfigurationContract.require(registerPerHour >= 1,
                "RATE_LIMIT_REGISTER_PER_HOUR must be at least 1, was " + registerPerHour);
        ConfigurationContract.require(refreshPerMinute >= 1,
                "RATE_LIMIT_REFRESH_PER_MINUTE must be at least 1, was " + refreshPerMinute);
        ConfigurationContract.require(recoveryPerHour >= 1,
                "RATE_LIMIT_RECOVERY_PER_HOUR must be at least 1, was " + recoveryPerHour);
    }

}
