package com.platform.learner.entity;

import com.platform.common.entity.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.springframework.util.Assert;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The platform's current belief about a child's mastery of one skill. The actual
 * update math lives in {@link com.platform.learner.service.MasteryEngine} (kept
 * separate and pure/testable, per Section 16's "use a documented algorithm... make
 * thresholds configurable" - this entity just applies an already-computed result
 * and enforces its own invariants (0-1 bounds, count consistency), it doesn't decide
 * the numbers itself. Maps 1:1 to {@code learner_skill_states} (V15 migration).
 */
@Entity
@Table(name = "learner_skill_states")
public class LearnerSkillState extends AuditableEntity {

    @Column(name = "child_id", nullable = false, updatable = false)
    private UUID childId;

    @Column(name = "skill_id", nullable = false, updatable = false)
    private UUID skillId;

    @Column(name = "mastery_probability", nullable = false, precision = 5, scale = 4)
    private BigDecimal masteryProbability;

    @Column(name = "confidence", nullable = false, precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "evidence_count", nullable = false)
    private int evidenceCount;

    @Column(name = "correct_count", nullable = false)
    private int correctCount;

    @Column(name = "incorrect_count", nullable = false)
    private int incorrectCount;

    @Column(name = "last_evidence_at")
    private Instant lastEvidenceAt;

    protected LearnerSkillState() {
        // JPA
    }

    private LearnerSkillState(UUID childId, UUID skillId) {
        this.childId = childId;
        this.skillId = skillId;
        this.masteryProbability = BigDecimal.ZERO;
        this.confidence = BigDecimal.ZERO;
        this.evidenceCount = 0;
        this.correctCount = 0;
        this.incorrectCount = 0;
    }

    public static LearnerSkillState initial(UUID childId, UUID skillId) {
        Assert.notNull(childId, "childId must not be null");
        Assert.notNull(skillId, "skillId must not be null");
        return new LearnerSkillState(childId, skillId);
    }

    /**
     * Applies an evidence-driven update. {@code newMastery}/{@code newConfidence}
     * are the already-computed results of {@link com.platform.learner.service.MasteryEngine}'s
     * algorithm - this method's job is applying them safely (bounds-checked) and
     * keeping the evidence counters consistent, not deciding the numbers.
     */
    public void applyEvidence(BigDecimal newMastery, BigDecimal newConfidence, boolean correct, Instant at) {
        Assert.isTrue(inUnitRange(newMastery), "newMastery must be between 0 and 1");
        Assert.isTrue(inUnitRange(newConfidence), "newConfidence must be between 0 and 1");
        this.masteryProbability = newMastery;
        this.confidence = newConfidence;
        this.evidenceCount += 1;
        if (correct) {
            this.correctCount += 1;
        } else {
            this.incorrectCount += 1;
        }
        this.lastEvidenceAt = at;
    }

    private static boolean inUnitRange(BigDecimal value) {
        return value != null && value.signum() >= 0 && value.compareTo(BigDecimal.ONE) <= 0;
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

    public int getEvidenceCount() {
        return evidenceCount;
    }

    public int getCorrectCount() {
        return correctCount;
    }

    public int getIncorrectCount() {
        return incorrectCount;
    }

    public Instant getLastEvidenceAt() {
        return lastEvidenceAt;
    }

}