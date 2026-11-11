package com.platform.learner;

import com.platform.common.config.ConfigurationContract;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * D-20 (owner decision 2026-11-11): a right answer on the second try is weaker mastery evidence than a right first
 * try. {@code secondTryCredit} is how much of a right first answer it counts for in the mastery estimate (the
 * observation's outcome: 1 for right first time, this for right second time, 0 for wrong). Default 0.5; the owner may
 * tune it ({@code LEARNING_SECOND_TRY_CREDIT}). It must stay above 0 (a right answer is some evidence) and below 1
 * (it is weaker than a right first try).
 */
@ConfigurationProperties(prefix = "platform.learning.retry")
public record RetryProperties(BigDecimal secondTryCredit) {

    public RetryProperties {
        if (secondTryCredit == null) {
            secondTryCredit = new BigDecimal("0.5");
        }
        ConfigurationContract.require(secondTryCredit.signum() > 0 && secondTryCredit.compareTo(BigDecimal.ONE) < 0,
                "LEARNING_SECOND_TRY_CREDIT must be between 0 and 1 (exclusive), was " + secondTryCredit);
    }
}
