package com.platform.content.web;

import com.platform.content.entity.QuestionOption;
import com.platform.content.entity.QuestionType;

import java.util.List;
import java.util.UUID;

public final class ActivityContentDtos {

    private ActivityContentDtos() {
    }

    /**
     * Never carries {@code correct} - see {@link QuestionOption#isCorrect()}'s
     * callers elsewhere in the codebase: that flag is server-only, exactly like
     * {@code QuestionVersion.correctAnswer} is never serialized here either.
     */
    public record QuestionOptionResponse(
            UUID id,
            String label,
            int displayOrder
    ) {
        public static QuestionOptionResponse from(QuestionOption option) {
            return new QuestionOptionResponse(option.getId(), option.getLabel(), option.getDisplayOrder());
        }
    }

    public record ActivityItemResponse(
            UUID activityItemId,
            int sequenceOrder,
            UUID questionVersionId,
            QuestionType questionType,
            String prompt,
            List<QuestionOptionResponse> options
    ) {
    }

    public record ActivityVersionDetailResponse(
            UUID activityVersionId,
            UUID activityId,
            String title,
            String instructions,
            Integer difficultyLevel,
            Integer estimatedDurationSeconds,
            List<ActivityItemResponse> items
    ) {
    }

}
