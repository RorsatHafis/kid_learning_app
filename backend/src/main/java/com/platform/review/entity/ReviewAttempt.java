package com.platform.review.entity;

import com.platform.common.entity.ImmutableEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.springframework.util.Assert;

import java.util.UUID;

/**
 * Immutable link between an existing {@code activity_attempts} row and the
 * ReviewItem it was answering (V18's migration comment: reviews deliberately don't
 * duplicate attempt-tracking - an attempt is still just an attempt, recorded once).
 * One row per activity_attempt_id ({@code uq_review_attempts_activity_attempt}).
 */
@Entity
@Table(name = "review_attempts")
public class ReviewAttempt extends ImmutableEvent {

    @Column(name = "review_item_id", nullable = false, updatable = false)
    private UUID reviewItemId;

    @Column(name = "activity_attempt_id", nullable = false, updatable = false)
    private UUID activityAttemptId;

    @Column(name = "was_successful", nullable = false)
    private boolean wasSuccessful;

    protected ReviewAttempt() {
        // JPA
    }

    private ReviewAttempt(UUID reviewItemId, UUID activityAttemptId, boolean wasSuccessful) {
        this.reviewItemId = reviewItemId;
        this.activityAttemptId = activityAttemptId;
        this.wasSuccessful = wasSuccessful;
    }

    public static ReviewAttempt record(UUID reviewItemId, UUID activityAttemptId, boolean wasSuccessful) {
        Assert.notNull(reviewItemId, "reviewItemId must not be null");
        Assert.notNull(activityAttemptId, "activityAttemptId must not be null");
        return new ReviewAttempt(reviewItemId, activityAttemptId, wasSuccessful);
    }

    public UUID getReviewItemId() {
        return reviewItemId;
    }

    public UUID getActivityAttemptId() {
        return activityAttemptId;
    }

    public boolean isWasSuccessful() {
        return wasSuccessful;
    }

}
