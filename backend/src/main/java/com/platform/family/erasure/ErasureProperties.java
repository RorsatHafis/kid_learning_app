package com.platform.family.erasure;

import com.platform.common.config.ConfigurationContract;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Deleting a child or a family account (PD-24 as adopted in Y3).
 *
 * @param gracePeriod   how long a deletion waits before it is carried out, so a mistake can be
 *                      undone (0 = at the next sweep; at most 90 days)
 * @param sweepInterval how often due deletions are carried out
 * @param sweepEnabled  whether this instance runs the sweep (tests drive it directly)
 */
@ConfigurationProperties(prefix = "platform.erasure")
public record ErasureProperties(Duration gracePeriod, Duration sweepInterval, boolean sweepEnabled) {

    public ErasureProperties {
        ConfigurationContract.require(gracePeriod != null && !gracePeriod.isNegative()
                        && gracePeriod.compareTo(Duration.ofDays(90)) <= 0,
                "ERASURE_GRACE_PERIOD must be between 0s and 90d, was " + gracePeriod);
        ConfigurationContract.require(sweepInterval != null && sweepInterval.compareTo(Duration.ofMinutes(1)) >= 0,
                "ERASURE_SWEEP_INTERVAL must be at least 1m, was " + sweepInterval);
    }

}
