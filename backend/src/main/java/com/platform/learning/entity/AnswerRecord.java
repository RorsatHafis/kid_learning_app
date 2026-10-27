package com.platform.learning.entity;

import com.platform.common.entity.ImmutableEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import org.springframework.util.Assert;

import java.util.UUID;

/**
 * One submitted answer to one question within an ActivityAttempt. Immutable
 * (Section 12/24: "never simply overwrite... evidence") - no setters, matching the
 * {@code prevent_row_mutation()} trigger on {@code answer_records} (V14 migration).
 * {@code correct} is computed and set by the scoring service at creation time, from
 * the server's own comparison against QuestionVersion.correctAnswer - never trusted
 * from client input (Section 25: "scoring must be server-controlled").
 */
@Entity
@Table(name = "answer_records")
public class AnswerRecord extends ImmutableEvent {

    @Column(name = "activity_attempt_id", nullable = false, updatable = false)
    private UUID activityAttemptId;

    @Column(name = "question_version_id", nullable = false, updatable = false)
    private UUID questionVersionId;

    @NotNull
    @Column(name = "submitted_answer", nullable = false, columnDefinition = "text")
    private String submittedAnswer;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    @Column(name = "time_spent_seconds")
    private Integer timeSpentSeconds;

    /**
     * D-16: whether the child opened the hint before answering. Null on answers recorded before V74 (never asked).
     * A hinted answer is practice, not independent evidence: it does not move mastery (see AnswerSubmissionWriter).
     */
    @Column(name = "hint_used", updatable = false)
    private Boolean hintUsed;

    protected AnswerRecord() {
        // JPA
    }

    private AnswerRecord(UUID activityAttemptId, UUID questionVersionId, String submittedAnswer, boolean correct,
                          Integer timeSpentSeconds, boolean hintUsed) {
        this.activityAttemptId = activityAttemptId;
        this.questionVersionId = questionVersionId;
        this.submittedAnswer = submittedAnswer;
        this.correct = correct;
        this.timeSpentSeconds = timeSpentSeconds;
        this.hintUsed = hintUsed;
    }

    public static AnswerRecord record(UUID activityAttemptId, UUID questionVersionId, String submittedAnswer,
                                       boolean correct, Integer timeSpentSeconds) {
        return record(activityAttemptId, questionVersionId, submittedAnswer, correct, timeSpentSeconds, false);
    }

    public static AnswerRecord record(UUID activityAttemptId, UUID questionVersionId, String submittedAnswer,
                                       boolean correct, Integer timeSpentSeconds, boolean hintUsed) {
        Assert.notNull(activityAttemptId, "activityAttemptId must not be null");
        Assert.notNull(questionVersionId, "questionVersionId must not be null");
        Assert.notNull(submittedAnswer, "submittedAnswer must not be null");
        Assert.isTrue(timeSpentSeconds == null || timeSpentSeconds >= 0, "timeSpentSeconds must not be negative");
        return new AnswerRecord(activityAttemptId, questionVersionId, submittedAnswer, correct, timeSpentSeconds, hintUsed);
    }

    public UUID getActivityAttemptId() {
        return activityAttemptId;
    }

    public UUID getQuestionVersionId() {
        return questionVersionId;
    }

    public String getSubmittedAnswer() {
        return submittedAnswer;
    }

    public boolean isCorrect() {
        return correct;
    }

    public Integer getTimeSpentSeconds() {
        return timeSpentSeconds;
    }

    /** True only when the child opened the hint; false for "no hint" and for answers recorded before V74. */
    public boolean isHintUsed() {
        return Boolean.TRUE.equals(hintUsed);
    }

    /** A right answer the child gave without opening the hint (D-16): what counts as showing it on their own. */
    public boolean isCorrectUnaided() {
        return correct && !isHintUsed();
    }

}