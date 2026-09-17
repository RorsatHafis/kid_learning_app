package com.platform.learner.entity;

import com.platform.common.entity.ImmutableEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.springframework.util.Assert;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One point-in-time snapshot of {@link LearnerSkillState}, recorded every time it
 * changes - answers the "how did this belief change over time" question
 * learner_skill_states alone can't. Immutable, matching the
 * {@code prevent_row_mutation()} trigger on {@code learner_skill_history} (V15).
 *
 * {@code triggeredById}/{@code triggeredByType} are a polymorphic reference (no FK -
 * see content_localizations' javadoc for the same trade-off) generalized in V22 from
 * an activity_attempts-only FK, specifically so both ActivityAttempts and
 * AssessmentAttempts can be traced as the source of a mastery change (Section 22:
 * assessment results must also contribute evidence to the learner model). Both null
 * together is valid (e.g. a future decay/reinforcement job with no single triggering
 * attempt) - never one without the other.
 */
@Entity
@Table(name = "learner_skill_history")
public class LearnerSkillHistory extends ImmutableEvent {

    @Column(name = "child_id", nullable = false, updatable = false)
    private UUID childId;

    @Column(name = "skill_id", nullable = false, updatable = false)
    private UUID skillId;

    @Column(name = "mastery_probability", nullable = false, precision = 5, scale = 4)
    private BigDecimal masteryProbability;

    @Column(name = "confidence", nullable = false, precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "triggered_by_id")
    private UUID triggeredById;

    @Enumerated(EnumType.STRING)
    @Column(name = "triggered_by_type", length = 20)
    private EvidenceSource triggeredByType;

    protected LearnerSkillHistory() {
        // JPA
    }

    private LearnerSkillHistory(UUID childId, UUID skillId, BigDecimal masteryProbability, BigDecimal confidence,
                                 UUID triggeredById, EvidenceSource triggeredByType) {
        this.childId = childId;
        this.skillId = skillId;
        this.masteryProbability = masteryProbability;
        this.confidence = confidence;
        this.triggeredById = triggeredById;
        this.triggeredByType = triggeredByType;
    }

    public static LearnerSkillHistory snapshot(UUID childId, UUID skillId, BigDecimal masteryProbability,
                                                BigDecimal confidence, UUID triggeredById, EvidenceSource triggeredByType) {
        Assert.notNull(childId, "childId must not be null");
        Assert.notNull(skillId, "skillId must not be null");
        Assert.notNull(masteryProbability, "masteryProbability must not be null");
        Assert.notNull(confidence, "confidence must not be null");
        Assert.isTrue((triggeredById == null) == (triggeredByType == null),
                "triggeredById and triggeredByType must both be null or both be set");
        return new LearnerSkillHistory(childId, skillId, masteryProbability, confidence, triggeredById, triggeredByType);
    }

    public UUID getChildId() {
        return childId;
    }

    public UUID getSkillId() {
        return skillId;
    }

    public BigDecimal getMasteryProbability() {
        return masteryProbability;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public UUID getTriggeredById() {
        return triggeredById;
    }

    public EvidenceSource getTriggeredByType() {
        return triggeredByType;
    }

}