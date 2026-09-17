package com.platform.learner;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Binds {@code platform.mastery.*} (see application.yml). Kept as its own small
 * record rather than hardcoded constants inside MasteryEngine, per Section 16 of
 * the master prompt: "make thresholds configurable... do not scatter magic numbers
 * throughout the code."
 */
@ConfigurationProperties(prefix = "platform.mastery")
public record MasteryProperties(BigDecimal learningRate, int confidenceSaturationCount, BigDecimal objectiveMasteryThreshold) {
}