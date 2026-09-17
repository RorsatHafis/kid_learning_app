package com.platform.review.entity;

import com.platform.common.entity.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.springframework.util.Assert;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * SM-2-inspired spaced-repetition schedule for one {@link ReviewItem} -
 * {@code uq_review_schedules_review_item} (V18 migration) enforces the 1:1. Default
 * {@code ease_factor} of 2.5 matches SM-2's own standard starting value (V18's
 * migration comment). Since evidence here is boolean (correct/incorrect on the
 * review attempt), not SM-2's original 0-5 quality scale, {@link #recordOutcome}
 * is a deliberately simplified two-branch version of the same idea: succeed and the
 * interval grows (multiplied by ease, which itself nudges up slightly); fail and the
 * interval resets to 1 day with ease nudged down - a documented simplification, not
 * an attempt at a literal SM-2 port, matching V18's own comment that a more
 * sophisticated retention model can come later.
 */
@Entity
@Table(name = "review_schedules")
public class ReviewSchedule extends AuditableEntity {

    private static final BigDecimal MIN_EASE_FACTOR = BigDecimal.valueOf(1.3);
    private static final BigDecimal MAX_EASE_FACTOR = BigDecimal.valueOf(3.0);
    private static final BigDecimal EASE_STEP = BigDecimal.valueOf(0.1);
    private static final BigDecimal EASE_PENALTY = BigDecimal.valueOf(0.2);

    @Column(name = "review_item_id", nullable = false, updatable = false)
    private UUID reviewItemId;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "interval_days", nullable = false)
    private int intervalDays;

    @Column(name = "ease_factor", nullable = false, precision = 4, scale = 2)
    private BigDecimal easeFactor;

    protected ReviewSchedule() {
        // JPA
    }

    private ReviewSchedule(UUID reviewItemId, Instant dueAt, int intervalDays, BigDecimal easeFactor) {
        this.reviewItemId = reviewItemId;
        this.dueAt = dueAt;
        this.intervalDays = intervalDays;
        this.easeFactor = easeFactor;
    }

    /** Due immediately - a freshly-flagged skill should show up for review right away, not a week from now. */
    public static ReviewSchedule initial(UUID reviewItemId, Instant now) {
        Assert.notNull(reviewItemId, "reviewItemId must not be null");
        Assert.notNull(now, "now must not be null");
        return new ReviewSchedule(reviewItemId, now, 1, BigDecimal.valueOf(2.5));
    }

    public void recordOutcome(boolean successful, Instant now) {
        if (successful) {
            this.intervalDays = Math.max(1, BigDecimal.valueOf(intervalDays).multiply(easeFactor)
                    .setScale(0, RoundingMode.HALF_UP).intValueExact());
            this.easeFactor = easeFactor.add(EASE_STEP).min(MAX_EASE_FACTOR);
        } else {
            this.intervalDays = 1;
            this.easeFactor = easeFactor.subtract(EASE_PENALTY).max(MIN_EASE_FACTOR);
        }
        this.dueAt = now.plus(intervalDays, ChronoUnit.DAYS);
    }

    public UUID getReviewItemId() {
        return reviewItemId;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public int getIntervalDays() {
        return intervalDays;
    }

    public BigDecimal getEaseFactor() {
        return easeFactor;
    }

}
