package com.platform.learner.service;

import com.platform.learner.MasteryProperties;
import com.platform.learner.entity.LearnerSkillState;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * How much evidence stands behind a skill, and what it lets us say about the child.
 * Used by every parent- and child-facing read of learning state so they all make the
 * same claims from the same evidence (Battle 2C / 5C).
 *
 * <p>Nothing here is a new number:
 * <ul>
 *   <li>{@link #SUFFICIENT_EVIDENCE} (3 scored answers) is the evidence the platform
 *       already requires before a performance or mastery claim - {@code ParentInsightService}
 *       and the Adaptive Engine's challenge gate.</li>
 *   <li>STRONG is the point at which {@code MasteryEngine}'s confidence saturates
 *       ({@code MASTERY_CONFIDENCE_SATURATION_COUNT}): more evidence no longer raises
 *       confidence.</li>
 *   <li>{@link #GOOD_PROGRESS} (0.6) is {@code ParentInsightService}'s "good progress"
 *       line; SECURE is the configured objective mastery threshold
 *       ({@code MASTERY_OBJECTIVE_THRESHOLD}).</li>
 * </ul>
 */
@Component
public class EvidenceStandard {

    public static final int SUFFICIENT_EVIDENCE = 3;
    public static final BigDecimal GOOD_PROGRESS = BigDecimal.valueOf(0.6);

    /** Evidence strength, kept separate from performance: "not assessed" is never "weak". */
    public enum EvidenceLevel {
        /** No scored answer yet. */
        NONE,
        /** Some answers, fewer than {@link #SUFFICIENT_EVIDENCE}: too few to say anything about performance. */
        PARTIAL,
        /** Enough answers to describe performance. */
        SUFFICIENT,
        /** Enough answers that confidence has saturated. */
        STRONG
    }

    /** What the evidence supports saying about the child's performance on a skill. */
    public enum SkillStanding {
        NOT_STARTED,
        TOO_EARLY_TO_TELL,
        NEEDS_PRACTICE,
        PROGRESSING,
        SECURE
    }

    private final MasteryProperties masteryProperties;

    public EvidenceStandard(MasteryProperties masteryProperties) {
        this.masteryProperties = masteryProperties;
    }

    public EvidenceLevel levelFor(int evidenceCount) {
        if (evidenceCount <= 0) {
            return EvidenceLevel.NONE;
        }
        if (evidenceCount < SUFFICIENT_EVIDENCE) {
            return EvidenceLevel.PARTIAL;
        }
        return evidenceCount >= Math.max(SUFFICIENT_EVIDENCE, masteryProperties.confidenceSaturationCount())
                ? EvidenceLevel.STRONG
                : EvidenceLevel.SUFFICIENT;
    }

    /** {@code state} may be null: a skill the child has never been scored on. */
    public SkillStanding standingFor(LearnerSkillState state) {
        return state == null ? SkillStanding.NOT_STARTED : standingFor(state.getEvidenceCount(), state.getMasteryProbability());
    }

    /**
     * The same rule for evidence as it stood at any moment (StandingHistory): {@code mastery} is the
     * estimate after the last of {@code evidence} answers, and is not read when there are too few.
     */
    public SkillStanding standingFor(int evidence, BigDecimal mastery) {
        EvidenceLevel level = levelFor(evidence);
        if (level == EvidenceLevel.NONE) {
            return SkillStanding.NOT_STARTED;
        }
        if (level == EvidenceLevel.PARTIAL) {
            return SkillStanding.TOO_EARLY_TO_TELL;
        }
        if (mastery.compareTo(masteryProperties.objectiveMasteryThreshold()) >= 0) {
            return SkillStanding.SECURE;
        }
        return mastery.compareTo(GOOD_PROGRESS) >= 0 ? SkillStanding.PROGRESSING : SkillStanding.NEEDS_PRACTICE;
    }

}
