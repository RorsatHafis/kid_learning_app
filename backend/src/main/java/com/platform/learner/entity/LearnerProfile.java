package com.platform.learner.entity;

import com.platform.common.entity.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.springframework.util.Assert;

import java.time.Instant;
import java.util.UUID;

/**
 * Aggregate learner stats. {@code totalLearningTimeSeconds}/{@code totalActivitiesCompleted}
 * are a DENORMALIZED CACHE, not a source of truth (see V15's migration comment) -
 * kept in sync explicitly by {@code recordActivityCompletion}, recomputable from
 * activity_attempts if it ever drifts. Maps 1:1 to {@code learner_profiles} (V15).
 */
@Entity
@Table(name = "learner_profiles")
public class LearnerProfile extends AuditableEntity {

    @Column(name = "child_id", nullable = false, updatable = false)
    private UUID childId;

    @Column(name = "total_learning_time_seconds", nullable = false)
    private long totalLearningTimeSeconds;

    @Column(name = "total_activities_completed", nullable = false)
    private int totalActivitiesCompleted;

    @Column(name = "last_activity_at")
    private Instant lastActivityAt;

    protected LearnerProfile() {
        // JPA
    }

    private LearnerProfile(UUID childId) {
        this.childId = childId;
        this.totalLearningTimeSeconds = 0;
        this.totalActivitiesCompleted = 0;
    }

    public static LearnerProfile createFor(UUID childId) {
        Assert.notNull(childId, "childId must not be null");
        return new LearnerProfile(childId);
    }

    public void recordActivityCompletion(long additionalTimeSeconds, Instant at) {
        Assert.isTrue(additionalTimeSeconds >= 0, "additionalTimeSeconds must not be negative");
        this.totalLearningTimeSeconds += additionalTimeSeconds;
        this.totalActivitiesCompleted += 1;
        this.lastActivityAt = at;
    }

    public UUID getChildId() {
        return childId;
    }

    public long getTotalLearningTimeSeconds() {
        return totalLearningTimeSeconds;
    }

    public int getTotalActivitiesCompleted() {
        return totalActivitiesCompleted;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }

}