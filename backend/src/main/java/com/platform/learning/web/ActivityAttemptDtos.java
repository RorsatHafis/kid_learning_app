package com.platform.learning.web;

import com.platform.learning.entity.ActivityAttempt;
import com.platform.learning.entity.ActivityAttemptStatus;
import com.platform.learning.entity.AnswerRecord;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class ActivityAttemptDtos {

    private ActivityAttemptDtos() {
    }

    public record StartAttemptRequest(
            @NotNull UUID activityVersionId,
            UUID learningPathItemId,
            UUID learningSessionId
    ) {
    }

    public record SubmitAnswerRequest(
            @NotNull UUID questionVersionId,
            @NotBlank String submittedAnswer,
            Integer timeSpentSeconds,
            @NotBlank String idempotencyKey
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

    public record AnswerRecordResponse(
            UUID id,
            UUID questionVersionId,
            boolean correct,
            Integer timeSpentSeconds,
            Instant occurredAt
    ) {
        public static AnswerRecordResponse from(AnswerRecord record) {
            return new AnswerRecordResponse(
                    record.getId(), record.getQuestionVersionId(), record.isCorrect(),
                    record.getTimeSpentSeconds(), record.getOccurredAt());
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
