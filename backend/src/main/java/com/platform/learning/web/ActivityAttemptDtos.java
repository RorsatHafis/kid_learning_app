package com.platform.learning.web;

import com.platform.learning.service.AnswerFeedbackService;

import com.platform.learning.entity.ActivityAttempt;
import com.platform.learning.entity.ActivityAttemptStatus;
import com.platform.learning.entity.AnswerRecord;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Requests and responses for playing an activity: starting an attempt, submitting an answer (scored by the
 * server), the feedback and explanation after it, and what comes next.
 */
public final class ActivityAttemptDtos {

    private ActivityAttemptDtos() {
    }

    public record StartAttemptRequest(
            @NotNull UUID activityVersionId,
            UUID learningPathItemId,
            UUID learningSessionId
    ) {
    }

    /** {@code hintUsed} (D-16): the child opened the question's hint before answering; absent means no. */
    public record SubmitAnswerRequest(
            @NotNull UUID questionVersionId,
            @NotBlank String submittedAnswer,
            Integer timeSpentSeconds,
            @NotBlank String idempotencyKey,
            Boolean hintUsed
    ) {
    }

    public record AttemptResponse(
            UUID id,
            UUID childId,
            UUID activityVersionId,
            UUID learningPathItemId,
            ActivityAttemptStatus status,
            Instant startedAt,
            Instant completedAt,
            BigDecimal score
    ) {
        public static AttemptResponse from(ActivityAttempt attempt) {
            return new AttemptResponse(attempt.getId(), attempt.getChildId(), attempt.getActivityVersionId(),
                    attempt.getLearningPathItemId(), attempt.getStatus(), attempt.getStartedAt(),
                    attempt.getCompletedAt(), attempt.getScore());
        }
    }

    /**
     * {@code correctAnswer} is feedback on an answer already given (see
     * {@link com.platform.learning.service.AnswerFeedbackService}), so a mistake can be
     * explained. It is only ever returned for a recorded answer. {@code explanation} (Y41): the author's
     * reasoning for the answer, likewise only after answering; null when the question has none.
     * {@code submittedAnswer}: what was recorded as the answer, exactly as it was sent - so a screen that finds the
     * question already answered (another tab, a lost response) shows that answer beside its verdict, not its own.
     */
    public record AnswerRecordResponse(
            UUID id,
            UUID questionVersionId,
            boolean correct,
            Integer timeSpentSeconds,
            Instant occurredAt,
            AnswerFeedbackService.CorrectAnswer correctAnswer,
            String explanation,
            boolean hintUsed,
            String submittedAnswer
    ) {
        public static AnswerRecordResponse from(AnswerRecord record, AnswerFeedbackService.CorrectAnswer correctAnswer,
                                                String explanation) {
            return new AnswerRecordResponse(
                    record.getId(), record.getQuestionVersionId(), record.isCorrect(),
                    record.getTimeSpentSeconds(), record.getOccurredAt(), correctAnswer, explanation, record.isHintUsed(),
                    record.getSubmittedAnswer());
        }
    }

    /** {@code insertedLearningPathItemId} is what the frontend should treat as "what's next" - it's already on the path. */
    public record AdaptiveRecommendationResponse(
            String direction,
            UUID targetSkillId,
            UUID insertedLearningPathItemId,
            UUID activityVersionId
    ) {
    }

    public record SmartReviewCheckResponse(
            boolean reviewOutcomeRecorded,
            boolean flaggedForReview,
            UUID skillId,
            UUID insertedLearningPathItemId,
            UUID activityVersionId
    ) {
    }

}
