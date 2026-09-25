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
     * Battle 5C: applies an objective rollup computed from the skills that actually
     * have evidence. Only called once at least one mapped skill has evidence, so the
     * objective is never NOT_STARTED afterwards - even when every observed answer was
     * wrong (observedMastery 0 is evidence of difficulty, not absence of evidence;
     * the previous rule kept such a child NOT_STARTED, hiding it from Parent Insight).
     *
     * <p>MASTERED is a claim about the whole objective, so it additionally requires
     * every mapped skill to have evidence: strong performance on the skills observed
     * so far is not mastery of skills never observed. {@code masteryThreshold} is
     * caller-supplied (configurable, see MasteryProperties).
     *
     * @param observedMastery    mean mastery over the mapped skills that have evidence
     * @param everySkillObserved whether every mapped skill has evidence
     */
    public void applyEvidenceRollup(BigDecimal observedMastery, boolean everySkillObserved, BigDecimal masteryThreshold) {
        Assert.isTrue(observedMastery != null && observedMastery.signum() >= 0
                && observedMastery.compareTo(BigDecimal.ONE) <= 0, "observedMastery must be between 0 and 1");
        this.masteryProbability = observedMastery;
        this.status = everySkillObserved && observedMastery.compareTo(masteryThreshold) >= 0
                ? LearnerObjectiveStatus.MASTERED
                : LearnerObjectiveStatus.IN_PROGRESS;
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