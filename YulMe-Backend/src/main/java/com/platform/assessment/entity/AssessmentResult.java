package com.platform.assessment.entity;

import com.platform.common.entity.ImmutableEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.springframework.util.Assert;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The final computed outcome of a completed AssessmentAttempt, written once.
 * Immutable - a scoring correction, if ever needed, is a new row/process, not a
 * mutation of history (Section 7). Maps 1:1 to {@code assessment_results} (V16).
 */
@Entity
@Table(name = "assessment_results")
public class AssessmentResult extends ImmutableEvent {

    @Column(name = "assessment_attempt_id", nullable = false, updatable = false)
    private UUID assessmentAttemptId;

    @Column(name = "total_points", nullable = false, precision = 6, scale = 2)
    private BigDecimal totalPoints;

    @Column(name = "points_earned", nullable = false, precision = 6, scale = 2)
    private BigDecimal pointsEarned;

    @Column(name = "percentage_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal percentageScore;

    protected AssessmentResult() {
        // JPA
    }

    private AssessmentResult(UUID assessmentAttemptId, BigDecimal totalPoints, BigDecimal pointsEarned, BigDecimal percentageScore) {
        this.assessmentAttemptId = assessmentAttemptId;
        this.totalPoints = totalPoints;
        this.pointsEarned = pointsEarned;
        this.percentageScore = percentageScore;
    }

    public static AssessmentResult compute(UUID assessmentAttemptId, BigDecimal totalPoints, BigDecimal pointsEarned) {
        Assert.notNull(assessmentAttemptId, "assessmentAttemptId must not be null");
        Assert.isTrue(totalPoints != null && totalPoints.signum() > 0, "totalPoints must be positive");
        Assert.isTrue(pointsEarned != null && pointsEarned.signum() >= 0 && pointsEarned.compareTo(totalPoints) <= 0,
                "pointsEarned must be between 0 and totalPoints");
        BigDecimal percentage = pointsEarned
                .divide(totalPoints, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, java.math.RoundingMode.HALF_UP);
        return new AssessmentResult(assessmentAttemptId, totalPoints, pointsEarned, percentage);
    }

    public UUID getAssessmentAttemptId() {
        return assessmentAttemptId;
    }

    public BigDecimal getTotalPoints() {
        return totalPoints;
    }

    public BigDecimal getPointsEarned() {
        return pointsEarned;
    }

    public BigDecimal getPercentageScore() {
        return percentageScore;
    }

}