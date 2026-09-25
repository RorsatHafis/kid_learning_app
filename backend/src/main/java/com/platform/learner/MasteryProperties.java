package com.platform.learner;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.platform.common.config.ConfigurationContract;

import java.math.BigDecimal;

/**
 * Binds {@code platform.mastery.*} (see application.yaml, which maps each value to its
 * documented MASTERY_* environment variable). Kept as its own small
 * record rather than hardcoded constants inside MasteryEngine, per Section 16 of
 * the master prompt: "make thresholds configurable... do not scatter magic numbers
 * throughout the code."
 *
 * <p>Validated at startup: an unusable value would otherwise only fail (or silently
 * distort mastery) when a child answers a question.
 */
@ConfigurationProperties(prefix = "platform.mastery")
public record MasteryProperties(BigDecimal learningRate, int confidenceSaturationCount, BigDecimal objectiveMasteryThreshold) {

    public MasteryProperties {
        // Same range MasteryEngine enforces per update: the EWMA weight of a new observation.
        ConfigurationContract.require(learningRate != null && learningRate.signum() > 0 && learningRate.compareTo(BigDecimal.ONE) <= 0,
                "MASTERY_LEARNING_RATE must be in (0, 1], was " + learningRate);
        // Confidence is evidenceCount / this value.
        ConfigurationContract.require(confidenceSaturationCount >= 1,
                "MASTERY_CONFIDENCE_SATURATION_COUNT must be at least 1, was " + confidenceSaturationCount);
        // Compared against a mastery probability, which lies in [0, 1].
        ConfigurationContract.require(objectiveMasteryThreshold != null && objectiveMasteryThreshold.signum() > 0
                        && objectiveMasteryThreshold.compareTo(BigDecimal.ONE) <= 0,
                "MASTERY_OBJECTIVE_THRESHOLD must be in (0, 1], was " + objectiveMasteryThreshold);
    }

}
