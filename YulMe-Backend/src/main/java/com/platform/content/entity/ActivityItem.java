package com.platform.content.entity;

import com.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.springframework.util.Assert;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Which questions an ActivityVersion contains, in order. Maps 1:1 to
 * {@code activity_items} (V21 migration) - see that migration's comment for why
 * this table exists (a real gap found while building answer submission, not part
 * of the original schema).
 */
@Entity
@Table(name = "activity_items")
public class ActivityItem extends BaseEntity {

    @Column(name = "activity_version_id", nullable = false, updatable = false)
    private UUID activityVersionId;

    @Column(name = "question_version_id", nullable = false, updatable = false)
    private UUID questionVersionId;

    @Column(name = "sequence_order", nullable = false, updatable = false)
    private int sequenceOrder;

    @Column(name = "points", nullable = false, precision = 5, scale = 2)
    private BigDecimal points;

    protected ActivityItem() {
        // JPA
    }

    private ActivityItem(UUID activityVersionId, UUID questionVersionId, int sequenceOrder, BigDecimal points) {
        this.activityVersionId = activityVersionId;
        this.questionVersionId = questionVersionId;
        this.sequenceOrder = sequenceOrder;
        this.points = points;
    }

    public static ActivityItem create(UUID activityVersionId, UUID questionVersionId, int sequenceOrder, BigDecimal points) {
        Assert.notNull(activityVersionId, "activityVersionId must not be null");
        Assert.notNull(questionVersionId, "questionVersionId must not be null");
        Assert.isTrue(points != null && points.signum() > 0, "points must be positive");
        return new ActivityItem(activityVersionId, questionVersionId, sequenceOrder, points);
    }

    public UUID getActivityVersionId() {
        return activityVersionId;
    }

    public UUID getQuestionVersionId() {
        return questionVersionId;
    }

    public int getSequenceOrder() {
        return sequenceOrder;
    }

    public BigDecimal getPoints() {
        return points;
    }

}