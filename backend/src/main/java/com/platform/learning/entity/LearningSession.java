package com.platform.learning.entity;

import com.platform.common.entity.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.springframework.util.Assert;

import java.time.Instant;
import java.util.UUID;

/** A bounded window of a child's activity. Maps 1:1 to {@code learning_sessions} (V14 migration). */
@Entity
@Table(name = "learning_sessions")
public class LearningSession extends AuditableEntity {

    @Column(name = "child_id", nullable = false, updatable = false)
    private UUID childId;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    protected LearningSession() {
        // JPA
    }

    private LearningSession(UUID childId, Instant startedAt) {
        this.childId = childId;
        this.startedAt = startedAt;
    }

    public static LearningSession start(UUID childId, Instant startedAt) {
        Assert.notNull(childId, "childId must not be null");
        Assert.notNull(startedAt, "startedAt must not be null");
        return new LearningSession(childId, startedAt);
    }

    public void end(Instant at) {
        if (endedAt != null) {
            throw new IllegalStateException("LearningSession %s has already ended".formatted(getId()));
        }
        Assert.isTrue(!at.isBefore(startedAt), "ended_at must not be before started_at");
        this.endedAt = at;
    }

    public boolean isEnded() {
        return endedAt != null;
    }

    public UUID getChildId() {
        return childId;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

}