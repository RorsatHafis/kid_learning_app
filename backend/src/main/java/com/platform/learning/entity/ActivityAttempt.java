package com.platform.learning.entity;

import com.platform.common.entity.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.springframework.util.Assert;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A child's attempt at one ActivityVersion. The central evidence-generating record
 * in the whole platform: Content -&gt; Learner -&gt; Path -&gt; Activity -&gt; Attempt -&gt;
 * Answer -&gt; Evidence. Stays mutable (Auditable) while IN_PROGRESS - the per-answer
 * evidence itself lives in the immutable {@code answer_records} table, not here; this
 * entity is the container/summary, not the evidence. {@code score} is a BigDecimal
 * (matching NUMERIC(5,2)), not a double - deliberately, per the general "don't use
 * floating point carelessly for important learning metrics" principle applied here
 * too, not just to mastery probabilities. Maps 1:1 to {@code activity_attempts}
 * (V14 migration).
 */
@Entity
@Table(name = "activity_attempts")
public class ActivityAttempt extends AuditableEntity {

    @Column(name = "child_id", nullable = false, updatable = false)
    private UUID childId;

    @Column(name = "activity_version_id", nullable = false, updatable = false)
    private UUID activityVersionId;

    /** Nullable: an attempt may happen outside any learning path (free practice). */
    @Column(name = "learning_path_item_id")
    private UUID learningPathItemId;

    /** Nullable: an attempt may not always be tied to a tracked session. */
    @Column(name = "learning_session_id")
    private UUID learningSessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ActivityAttemptStatus status;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "score", precision = 5, scale = 2)
    private BigDecimal score;

    protected ActivityAttempt() {
        // JPA
    }

    private ActivityAttempt(UUID childId, UUID activityVersionId, UUID learningPathItemId, UUID learningSessionId,
                             Instant startedAt) {
        this.childId = childId;
        this.activityVersionId = activityVersionId;
        this.learningPathItemId = learningPathItemId;
        this.learningSessionId = learningSessionId;
        this.status = ActivityAttemptStatus.IN_PROGRESS;
        this.startedAt = startedAt;
    }

    public static ActivityAttempt start(UUID childId, UUID activityVersionId, UUID learningPathItemId,
                                         UUID learningSessionId, Instant startedAt) {
        Assert.notNull(childId, "childId must not be null");
        Assert.notNull(activityVersionId, "activityVersionId must not be null");
        Assert.notNull(startedAt, "startedAt must not be null");
        return new ActivityAttempt(childId, activityVersionId, learningPathItemId, learningSessionId, startedAt);
    }

    public void complete(Instant at, BigDecimal score) {
        requireInProgress("completed");
        Assert.isTrue(score == null || (score.signum() >= 0 && score.compareTo(BigDecimal.valueOf(100)) <= 0),
                "score must be between 0 and 100");
        this.status = ActivityAttemptStatus.COMPLETED;
        this.completedAt = at;
        this.score = score;
    }

    public void abandon(Instant at) {
        requireInProgress("abandoned");
        this.status = ActivityAttemptStatus.ABANDONED;
        this.completedAt = at;
    }

    private void requireInProgress(String attemptedTransition) {
        if (status != ActivityAttemptStatus.IN_PROGRESS) {
            throw new IllegalStateException("ActivityAttempt %s can only be %s from IN_PROGRESS, was %s"
                    .formatted(getId(), attemptedTransition, status));
        }
    }

    public UUID getChildId() {
        return childId;
    }

    public UUID getActivityVersionId() {
        return activityVersionId;
    }

    public UUID getLearningPathItemId() {
        return learningPathItemId;
    }

    public UUID getLearningSessionId() {
        return learningSessionId;
    }

    public ActivityAttemptStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public BigDecimal getScore() {
        return score;
    }

}