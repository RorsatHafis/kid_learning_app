package com.platform.learning.entity;

import com.platform.common.entity.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.springframework.util.Assert;

import java.time.Instant;
import java.util.UUID;

/**
 * A child's enrollment in a specific CurriculumVersion (curriculum domain) - never
 * the mutable Curriculum itself, per V14's migration comment: editing the curriculum
 * later must never retroactively change what this child was actually enrolled in.
 * Maps 1:1 to {@code enrollments} (V14 migration).
 */
@Entity
@Table(name = "enrollments")
public class Enrollment extends AuditableEntity {

    @Column(name = "child_id", nullable = false, updatable = false)
    private UUID childId;

    @Column(name = "curriculum_version_id", nullable = false, updatable = false)
    private UUID curriculumVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EnrollmentStatus status;

    @Column(name = "enrolled_at", nullable = false)
    private Instant enrolledAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected Enrollment() {
        // JPA
    }

    private Enrollment(UUID childId, UUID curriculumVersionId, Instant enrolledAt) {
        this.childId = childId;
        this.curriculumVersionId = curriculumVersionId;
        this.status = EnrollmentStatus.ACTIVE;
        this.enrolledAt = enrolledAt;
    }

    public static Enrollment enroll(UUID childId, UUID curriculumVersionId, Instant enrolledAt) {
        Assert.notNull(childId, "childId must not be null");
        Assert.notNull(curriculumVersionId, "curriculumVersionId must not be null");
        Assert.notNull(enrolledAt, "enrolledAt must not be null");
        return new Enrollment(childId, curriculumVersionId, enrolledAt);
    }

    public void complete(Instant at) {
        requireStatus(EnrollmentStatus.ACTIVE, "completed");
        this.status = EnrollmentStatus.COMPLETED;
        this.completedAt = at;
    }

    public void pause() {
        requireStatus(EnrollmentStatus.ACTIVE, "paused");
        this.status = EnrollmentStatus.PAUSED;
    }

    public void resume() {
        requireStatus(EnrollmentStatus.PAUSED, "resumed");
        this.status = EnrollmentStatus.ACTIVE;
    }

    public void withdraw() {
        if (status == EnrollmentStatus.COMPLETED || status == EnrollmentStatus.WITHDRAWN) {
            throw new IllegalStateException("Enrollment %s can only be withdrawn from ACTIVE or PAUSED, was %s"
                    .formatted(getId(), status));
        }
        this.status = EnrollmentStatus.WITHDRAWN;
    }

    private void requireStatus(EnrollmentStatus required, String attemptedTransition) {
        if (status != required) {
            throw new IllegalStateException("Enrollment %s can only be %s from %s, was %s"
                    .formatted(getId(), attemptedTransition, required, status));
        }
    }

    public UUID getChildId() {
        return childId;
    }

    public UUID getCurriculumVersionId() {
        return curriculumVersionId;
    }

    public EnrollmentStatus getStatus() {
        return status;
    }

    public Instant getEnrolledAt() {
        return enrolledAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

}