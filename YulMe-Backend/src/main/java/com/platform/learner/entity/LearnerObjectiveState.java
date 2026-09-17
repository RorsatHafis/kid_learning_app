package com.platform.learner.entity;

import com.platform.common.entity.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.springframework.util.Assert;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Mastery rolled up to the learning-objective level (curriculum domain), derived
 * from the underlying skills' {@link LearnerSkillState} rows - see
 * {@code MasteryEngine.recalculateObjectiveState} for how that aggregation works.
 * Maps 1:1 to {@code learner_objective_states} (V15 migration).
 */
@Entity
@Table(name = "learner_objective_states")
public class LearnerObjectiveState extends AuditableEntity {

    @Column(name = "child_id", nullable = false, updatable = false)
    private UUID childId;

    @Column(name = "learning_objective_id", nullable = false, updatable = false)
    private UUID learningObjectiveId;

    @Column(name = "mastery_probability", nullable = false, precision = 5, scale = 4)
    private BigDecimal masteryProbability;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LearnerObjectiveStatus status;

    protected LearnerObjectiveState() {
        // JPA
    }

    private LearnerObjectiveState(UUID childId, UUID learningObjectiveId) {
        this.childId = childId;
        this.learningObjectiveId = learningObjectiveId;
        this.masteryProbability = BigDecimal.ZERO;
        this.status = LearnerObjectiveStatus.NOT_STARTED;
    }

    public static LearnerObjectiveState initial(UUID childId, UUID learningObjectiveId) {
        Assert.notNull(childId, "childId must not be null");
        Assert.notNull(learningObjectiveId, "learningObjectiveId must not be null");
        return new LearnerObjectiveState(childId, learningObjectiveId);
    }

    /**
     * Recomputes status from the new mastery value: NOT_STARTED once evidence
     * exists moves to IN_PROGRESS, and crossing {@code masteryThreshold} moves to
     * MASTERED. {@code masteryThreshold} is caller-supplied (not hardcoded here),
     * consistent with the "make thresholds configurable" principle applied
     * everywhere else in this domain.
     */
    public void updateMastery(BigDecimal newMastery, BigDecimal masteryThreshold) {
        Assert.isTrue(newMastery != null && newMastery.signum() >= 0 && newMastery.compareTo(BigDecimal.ONE) <= 0,
                "newMastery must be between 0 and 1");
        this.masteryProbability = newMastery;
        if (newMastery.compareTo(masteryThreshold) >= 0) {
            this.status = LearnerObjectiveStatus.MASTERED;
        } else if (newMastery.signum() > 0) {
            this.status = LearnerObjectiveStatus.IN_PROGRESS;
        }
        // newMastery == 0 leaves status unchanged (stays NOT_STARTED, or stays wherever
        // it was - mastery dropping back to exactly 0 shouldn't silently revert a child
        // from MASTERED/IN_PROGRESS to NOT_STARTED; that would misrepresent that they'd
        // never engaged with the objective at all).
    }

    public UUID getChildId() {
        return childId;
    }

    public UUID getLearningObjectiveId() {
        return learningObjectiveId;
    }

    public BigDecimal getMasteryProbability() {
        return masteryProbability;
    }

    public LearnerObjectiveStatus getStatus() {
        return status;
    }

}