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

/** One activity_version placed at a position on a LearningPath. Maps 1:1 to {@code learning_path_items} (V14 migration). */
@Entity
@Table(name = "learning_path_items")
public class LearningPathItem extends AuditableEntity {

    @Column(name = "learning_path_id", nullable = false, updatable = false)
    private UUID learningPathId;

    @Column(name = "activity_version_id", nullable = false, updatable = false)
    private UUID activityVersionId;

    @Column(name = "sequence_order", nullable = false, updatable = false)
    private int sequenceOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LearningPathItemStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 30)
    private LearningPathItemSource source;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected LearningPathItem() {
        // JPA
    }

    private LearningPathItem(UUID learningPathId, UUID activityVersionId, int sequenceOrder,
                              LearningPathItemSource source, Instant addedAt) {
        this.learningPathId = learningPathId;
        this.activityVersionId = activityVersionId;
        this.sequenceOrder = sequenceOrder;
        this.status = LearningPathItemStatus.PENDING;
        this.source = source;
        this.addedAt = addedAt;
    }

    public static LearningPathItem place(UUID learningPathId, UUID activityVersionId, int sequenceOrder,
                                          LearningPathItemSource source, Instant addedAt) {
        Assert.notNull(learningPathId, "learningPathId must not be null");
        Assert.notNull(activityVersionId, "activityVersionId must not be null");
        Assert.notNull(source, "source must not be null");
        Assert.notNull(addedAt, "addedAt must not be null");
        return new LearningPathItem(learningPathId, activityVersionId, sequenceOrder, source, addedAt);
    }

    public void start() {
        requireStatus(LearningPathItemStatus.PENDING, "started");
        this.status = LearningPathItemStatus.IN_PROGRESS;
    }

    public void complete(Instant at) {
        if (status != LearningPathItemStatus.PENDING && status != LearningPathItemStatus.IN_PROGRESS) {
            throw new IllegalStateException("LearningPathItem %s can only be completed from PENDING or IN_PROGRESS, was %s"
                    .formatted(getId(), status));
        }
        this.status = LearningPathItemStatus.COMPLETED;
        this.completedAt = at;
    }

    public void skip() {
        if (status != LearningPathItemStatus.PENDING && status != LearningPathItemStatus.IN_PROGRESS) {
            throw new IllegalStateException("LearningPathItem %s can only be skipped from PENDING or IN_PROGRESS, was %s"
                    .formatted(getId(), status));
        }
        this.status = LearningPathItemStatus.SKIPPED;
    }

    private void requireStatus(LearningPathItemStatus required, String attemptedTransition) {
        if (status != required) {
            throw new IllegalStateException("LearningPathItem %s can only be %s from %s, was %s"
                    .formatted(getId(), attemptedTransition, required, status));
        }
    }

    public UUID getLearningPathId() {
        return learningPathId;
    }

    public UUID getActivityVersionId() {
        return activityVersionId;
    }

    public int getSequenceOrder() {
        return sequenceOrder;
    }

    public LearningPathItemStatus getStatus() {
        return status;
    }

    public LearningPathItemSource getSource() {
        return source;
    }

    public Instant getAddedAt() {
        return addedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

}