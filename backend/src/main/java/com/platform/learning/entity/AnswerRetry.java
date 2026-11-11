package com.platform.learning.entity;

import com.platform.common.entity.ImmutableEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import org.springframework.util.Assert;

/**
 * D-20 (owner decision 2026-11-11, V93): what followed a retry offered after a wrong first answer - the child's second
 * answer, scored by the server, or the child asking to see the answer (also written when the child moves on, finishes
 * or stops without one). Append-only, one per offered answer; the question's evidence is recorded when it is written.
 */
@Entity
@Table(name = "answer_retries")
public class AnswerRetry extends ImmutableEvent {

    public static final String RETRIED = "RETRIED";
    public static final String DECLINED = "DECLINED";

    @Column(name = "answer_record_id", nullable = false, updatable = false)
    private UUID answerRecordId;

    @Column(name = "outcome", nullable = false, updatable = false)
    private String outcome;

    @Column(name = "submitted_answer", updatable = false, columnDefinition = "text")
    private String submittedAnswer;

    @Column(name = "is_correct", updatable = false)
    private Boolean correct;

    @Column(name = "hint_used", nullable = false, updatable = false)
    private boolean hintUsed;

    @Column(name = "time_spent_seconds", updatable = false)
    private Integer timeSpentSeconds;

    protected AnswerRetry() {
        // JPA
    }

    public static AnswerRetry retried(UUID answerRecordId, String submittedAnswer, boolean correct, boolean hintUsed,
                                      Integer timeSpentSeconds) {
        Assert.notNull(answerRecordId, "answerRecordId must not be null");
        Assert.notNull(submittedAnswer, "submittedAnswer must not be null");
        Assert.isTrue(timeSpentSeconds == null || timeSpentSeconds >= 0, "timeSpentSeconds must not be negative");
        AnswerRetry retry = new AnswerRetry();
        retry.answerRecordId = answerRecordId;
        retry.outcome = RETRIED;
        retry.submittedAnswer = submittedAnswer;
        retry.correct = correct;
        retry.hintUsed = hintUsed;
        retry.timeSpentSeconds = timeSpentSeconds;
        return retry;
    }

    public static AnswerRetry declined(UUID answerRecordId) {
        Assert.notNull(answerRecordId, "answerRecordId must not be null");
        AnswerRetry retry = new AnswerRetry();
        retry.answerRecordId = answerRecordId;
        retry.outcome = DECLINED;
        return retry;
    }

    public UUID getAnswerRecordId() {
        return answerRecordId;
    }

    public boolean isRetried() {
        return RETRIED.equals(outcome);
    }

    public String getSubmittedAnswer() {
        return submittedAnswer;
    }

    /** Right on the second try (false for a declined retry). */
    public boolean isCorrect() {
        return Boolean.TRUE.equals(correct);
    }

    public boolean isHintUsed() {
        return hintUsed;
    }

    public Integer getTimeSpentSeconds() {
        return timeSpentSeconds;
    }
}
