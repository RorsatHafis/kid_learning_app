package com.platform.assessment.entity;

import com.platform.common.entity.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.springframework.util.Assert;

import java.time.Instant;
import java.util.UUID;

/** A child's attempt at an AssessmentVersion. Maps 1:1 to {@code assessment_attempts} (V16 migration). */
@Entity
@Table(name = "assessment_attempts")
public class AssessmentAttempt extends AuditableEntity {

    @Column(name = "child_id", nullable = false, updatable = false)
    private UUID childId;

    @Column(name = "assessment_version_id", nullable = false, updatable = false)
    private UUID assessmentVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AssessmentAttemptStatus status;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected AssessmentAttempt() {
        // JPA
    }

    private AssessmentAttempt(UUID childId, UUID assessmentVersionId, Instant startedAt) {
        this.childId = childId;
        this.assessmentVersionId = assessmentVersionId;
        this.status = AssessmentAttemptStatus.IN_PROGRESS;
        this.startedAt = startedAt;
    }

    public static AssessmentAttempt start(UUID childId, UUID assessmentVersionId, Instant startedAt) {
        Assert.notNull(childId, "childId must not be null");
        Assert.notNull(assessmentVersionId, "assessmentVersionId must not be null");
        Assert.notNull(startedAt, "startedAt must not be null");
        return new AssessmentAttempt(childId, assessmentVersionId, startedAt);
    }

    public void complete(Instant at) {
        requireInProgress("completed");
        this.status = AssessmentAttemptStatus.COMPLETED;
        this.completedAt = at;
    }

    public void abandon(Instant at) {
        requireInProgress("abandoned");
        this.status = AssessmentAttemptStatus.ABANDONED;
        this.completedAt = at;
    }

    private void requireInProgress(String attemptedTransition) {
        if (status != AssessmentAttemptStatus.IN_PROGRESS) {
            throw new IllegalStateException("AssessmentAttempt %s can only be %s from IN_PROGRESS, was %s"
                    .formatted(getId(), attemptedTransition, status));
        }
    }

    public UUID getChildId() {
        return childId;
    }

    public UUID getAssessmentVersionId() {
        return assessmentVersionId;
    }

    public AssessmentAttemptStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

}