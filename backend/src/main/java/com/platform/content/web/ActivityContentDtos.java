package com.platform.content.web;

import com.platform.common.idempotency.service.RequestFingerprint;
import com.platform.content.entity.MatchSide;
import com.platform.content.entity.QuestionOption;
import com.platform.content.entity.QuestionType;

import java.util.ArrayList;
import java.util.Comparator;
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
            int displayOrder,
            MatchSide matchSide
    ) {
        public static QuestionOptionResponse from(QuestionOption option) {
            return new QuestionOptionResponse(option.getId(), option.getLabel(), option.getDisplayOrder(), option.getMatchSide());
        }

        /**
         * A question version's options as a learner may see them. For MATCHING the pairing
         * is the answer, so it never leaves the server: each item carries only its side, and
         * the right column is put in an order derived from the item ids - stable for the
         * version (deterministic, CLAUDE.md §14) but unrelated to the pairs, so an item's
         * position never gives its match away.
         */
        public static List<QuestionOptionResponse> forLearner(UUID questionVersionId, List<QuestionOption> options) {
            if (options.stream().noneMatch(o -> o.getMatchSide() != null)) {
                return options.stream().map(QuestionOptionResponse::from).toList();
            }
            List<QuestionOptionResponse> result = new ArrayList<>();
            List<QuestionOption> lefts = options.stream().filter(o -> o.getMatchSide() == MatchSide.LEFT)
                    .sorted(Comparator.comparingInt(QuestionOption::getDisplayOrder)).toList();
            for (int i = 0; i < lefts.size(); i++) {
                result.add(new QuestionOptionResponse(lefts.get(i).getId(), lefts.get(i).getLabel(), i, MatchSide.LEFT));
            }
            List<QuestionOption> rights = options.stream().filter(o -> o.getMatchSide() == MatchSide.RIGHT)
                    .sorted(Comparator.comparing(o -> RequestFingerprint.sha256Hex(questionVersionId + ":" + o.getId()))).toList();
            for (int i = 0; i < rights.size(); i++) {
                result.add(new QuestionOptionResponse(rights.get(i).getId(), rights.get(i).getLabel(), i, MatchSide.RIGHT));
            }
            return result;
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
