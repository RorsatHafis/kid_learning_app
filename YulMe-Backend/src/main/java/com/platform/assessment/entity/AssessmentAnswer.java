package com.platform.assessment.entity;

import com.platform.common.entity.ImmutableEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.springframework.util.Assert;

import java.math.BigDecimal;
import java.util.UUID;

/** One submitted answer within an AssessmentAttempt. Immutable, matching answer_records' treatment. Maps 1:1 to {@code assessment_answers} (V16). */
@Entity
@Table(name = "assessment_answers")
public class AssessmentAnswer extends ImmutableEvent {

    @Column(name = "assessment_attempt_id", nullable = false, updatable = false)
    private UUID assessmentAttemptId;

    @Column(name = "assessment_item_id", nullable = false, updatable = false)
    private UUID assessmentItemId;

    @Column(name = "submitted_answer", nullable = false, columnDefinition = "text")
    private String submittedAnswer;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    @Column(name = "points_awarded", nullable = false, precision = 5, scale = 2)
    private BigDecimal pointsAwarded;

    @Column(name = "time_spent_seconds")
    private Integer timeSpentSeconds;

    protected AssessmentAnswer() {
        // JPA
    }

    private AssessmentAnswer(UUID assessmentAttemptId, UUID assessmentItemId, String submittedAnswer, boolean correct,
                              BigDecimal pointsAwarded, Integer timeSpentSeconds) {
        this.assessmentAttemptId = assessmentAttemptId;
        this.assessmentItemId = assessmentItemId;
        this.submittedAnswer = submittedAnswer;
        this.correct = correct;
        this.pointsAwarded = pointsAwarded;
        this.timeSpentSeconds = timeSpentSeconds;
    }

    public static AssessmentAnswer record(UUID assessmentAttemptId, UUID assessmentItemId, String submittedAnswer,
                                           boolean correct, BigDecimal pointsAwarded, Integer timeSpentSeconds) {
        Assert.notNull(assessmentAttemptId, "assessmentAttemptId must not be null");
        Assert.notNull(assessmentItemId, "assessmentItemId must not be null");
        Assert.notNull(submittedAnswer, "submittedAnswer must not be null");
        Assert.isTrue(pointsAwarded != null && pointsAwarded.signum() >= 0, "pointsAwarded must not be negative");
        Assert.isTrue(timeSpentSeconds == null || timeSpentSeconds >= 0, "timeSpentSeconds must not be negative");
        return new AssessmentAnswer(assessmentAttemptId, assessmentItemId, submittedAnswer, correct, pointsAwarded, timeSpentSeconds);
    }

    public UUID getAssessmentAttemptId() {
        return assessmentAttemptId;
    }

    public UUID getAssessmentItemId() {
        return assessmentItemId;
    }

    public String getSubmittedAnswer() {
        return submittedAnswer;
    }

    public boolean isCorrect() {
        return correct;
    }

    public BigDecimal getPointsAwarded() {
        return pointsAwarded;
    }

    public Integer getTimeSpentSeconds() {
        return timeSpentSeconds;
    }

}