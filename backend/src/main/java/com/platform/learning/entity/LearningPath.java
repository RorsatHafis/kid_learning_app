package com.platform.learning.entity;

import com.platform.common.entity.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.springframework.util.Assert;

import java.util.UUID;

/**
 * A child's personalized activity sequence for one Enrollment. Exactly one per
 * enrollment for Phase 1 - {@code uq_learning_paths_enrollment} (V14 migration)
 * enforces that at the DB level. The actual sequence lives in {@link LearningPathItem}.
 */
@Entity
@Table(name = "learning_paths")
public class LearningPath extends AuditableEntity {

    @Column(name = "enrollment_id", nullable = false, updatable = false)
    private UUID enrollmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LearningPathStatus status;

    protected LearningPath() {
        // JPA
    }

    private LearningPath(UUID enrollmentId) {
        this.enrollmentId = enrollmentId;
        this.status = LearningPathStatus.ACTIVE;
    }

    public static LearningPath create(UUID enrollmentId) {
        Assert.notNull(enrollmentId, "enrollmentId must not be null");
        return new LearningPath(enrollmentId);
    }

    public void complete() {
        requireStatus(LearningPathStatus.ACTIVE, "completed");
        this.status = LearningPathStatus.COMPLETED;
    }

    private void requireStatus(LearningPathStatus required, String attemptedTransition) {
        if (status != required) {
            throw new IllegalStateException("LearningPath %s can only be %s from %s, was %s"
                    .formatted(getId(), attemptedTransition, required, status));
        }
    }

    public UUID getEnrollmentId() {
        return enrollmentId;
    }

    public LearningPathStatus getStatus() {
        return status;
    }

}