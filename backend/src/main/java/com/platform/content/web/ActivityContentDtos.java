package com.platform.content.web;

import com.platform.common.idempotency.service.RequestFingerprint;
import com.platform.content.entity.MatchSide;
import com.platform.content.entity.QuestionOption;
import com.platform.content.entity.QuestionType;
import com.platform.content.entity.QuestionVersion;
import com.platform.content.media.MediaService.MediaRef;
import com.platform.content.service.OrderingItems;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What a learner is sent for an activity: its questions and options in order, without correct answers, authoring
 * notes or moderation details, plus the media they need.
 */
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
            MatchSide matchSide,
            /* X23: SORTING - a group, or an item to sort into one; which group is the answer and is never sent. */
            com.platform.content.entity.SortRole sortRole,
            /* X11: a picture standing for the item (label = its description) and a recording of it. */
            MediaRef image,
            MediaRef audio
    ) {
        static QuestionOptionResponse of(QuestionOption option, int displayOrder, Map<UUID, MediaRef> media) {
            return new QuestionOptionResponse(option.getId(), option.getLabel(), displayOrder, option.getMatchSide(),
                    option.getSortRole(), media.get(option.getImageAssetId()), media.get(option.getAudioAssetId()));
        }

        /**
         * A question version's options as a learner may see them. For MATCHING the pairing
         * is the answer, so it never leaves the server: each item carries only its side, and
         * the right column is put in an order derived from the item ids - stable for the
         * version (deterministic, CLAUDE.md §14) but unrelated to the pairs, so an item's
         * position never gives its match away.
         */
        public static List<QuestionOptionResponse> forLearner(QuestionType questionType, UUID questionVersionId,
                                                              List<QuestionOption> options, Map<UUID, MediaRef> media) {
            if (questionType == QuestionType.ORDERING) {
                // The display order is the answer: the child gets the items in OrderingItems' order instead.
                List<QuestionOption> given = OrderingItems.learnerOrder(questionVersionId, options);
                List<QuestionOptionResponse> result = new ArrayList<>();
                for (int i = 0; i < given.size(); i++) {
                    result.add(of(given.get(i), i, media));
                }
                return result;
            }
            if (questionType == QuestionType.SORTING) {
                // Groups in their authored order; items in an order derived from their ids, unrelated to their groups.
                List<QuestionOptionResponse> result = new ArrayList<>();
                List<QuestionOption> groups = options.stream().filter(o -> o.getSortRole() == com.platform.content.entity.SortRole.GROUP)
                        .sorted(Comparator.comparing(QuestionOption::getSortGroup)).toList();
                for (int i = 0; i < groups.size(); i++) {
                    result.add(of(groups.get(i), i, media));
                }
                List<QuestionOption> items = options.stream().filter(o -> o.getSortRole() == com.platform.content.entity.SortRole.ITEM)
                        .sorted(Comparator.comparing(o -> RequestFingerprint.sha256Hex(questionVersionId + ":sort:" + o.getId()))).toList();
                for (int i = 0; i < items.size(); i++) {
                    result.add(of(items.get(i), i, media));
                }
                return result;
            }
            if (options.stream().noneMatch(o -> o.getMatchSide() != null)) {
                return options.stream().map(o -> of(o, o.getDisplayOrder(), media)).toList();
            }
            List<QuestionOptionResponse> result = new ArrayList<>();
            List<QuestionOption> lefts = options.stream().filter(o -> o.getMatchSide() == MatchSide.LEFT)
                    .sorted(Comparator.comparingInt(QuestionOption::getDisplayOrder)).toList();
            for (int i = 0; i < lefts.size(); i++) {
                result.add(of(lefts.get(i), i, media));
            }
            List<QuestionOption> rights = options.stream().filter(o -> o.getMatchSide() == MatchSide.RIGHT)
                    .sorted(Comparator.comparing(o -> RequestFingerprint.sha256Hex(questionVersionId + ":" + o.getId()))).toList();
            for (int i = 0; i < rights.size(); i++) {
                result.add(of(rights.get(i), i, media));
            }
            return result;
        }
    }

    /**
     * {@code promptImage}/{@code promptAudio} (X11): shown with the prompt, and the prompt read aloud;
     * {@code promptImageDescription}: what the picture shows, for screen readers. {@code hint} (Y41): the
     * author's guidance, offered before answering. The explanation is not here: it comes with the answer
     * ({@code AnswerRecordResponse}), so it is never seen before the child has answered.
     */
    public record ActivityItemResponse(
            UUID activityItemId,
            int sequenceOrder,
            UUID questionVersionId,
            QuestionType questionType,
            String prompt,
            MediaRef promptImage,
            String promptImageDescription,
            MediaRef promptAudio,
            List<QuestionOptionResponse> options,
            String hint
    ) {
        /** One item as a learner sees it; {@code media} holds the references of every asset it uses ({@link #mediaOf}). */
        public static ActivityItemResponse forLearner(UUID itemId, int sequenceOrder, QuestionType questionType,
                                                      QuestionVersion version, List<QuestionOption> options,
                                                      Map<UUID, MediaRef> media) {
            return new ActivityItemResponse(itemId, sequenceOrder, version.getId(), questionType, version.getPrompt(),
                    media.get(version.getPromptImageAssetId()), version.getPromptImageDescription(),
                    media.get(version.getPromptAudioAssetId()),
                    QuestionOptionResponse.forLearner(questionType, version.getId(), options, media), version.getHint());
        }
    }

    /** Every asset a version and its options use, to read their references in one query. */
    public static Set<UUID> mediaOf(QuestionVersion version, List<QuestionOption> options) {
        Set<UUID> ids = new HashSet<>();
        ids.add(version.getPromptImageAssetId());
        ids.add(version.getPromptAudioAssetId());
        options.forEach(option -> {
            ids.add(option.getImageAssetId());
            ids.add(option.getAudioAssetId());
        });
        ids.remove(null);
        return ids;
    }

    /** {@code introduction}: what the activity teaches before it asks; null when it has none. */
    public record ActivityVersionDetailResponse(
            UUID activityVersionId,
            UUID activityId,
            String title,
            String instructions,
            Integer difficultyLevel,
            Integer estimatedDurationSeconds,
            List<ActivityItemResponse> items,
            LearnerIntroduction introduction
    ) {
    }

    /** An activity's introduction as a learner sees it: the text, a picture with its description, a recording. */
    public record LearnerIntroduction(String text, com.platform.content.media.MediaService.MediaRef image, String imageDescription,
                                      com.platform.content.media.MediaService.MediaRef audio) {

        /** Null when the version has none; its media from {@code refs} (already signed). */
        public static LearnerIntroduction of(com.platform.content.entity.ActivityVersion version,
                                             Map<UUID, com.platform.content.media.MediaService.MediaRef> refs) {
            if (!version.hasIntroduction()) {
                return null;
            }
            return new LearnerIntroduction(version.getIntroduction(), refs.get(version.getIntroductionImageAssetId()),
                    version.getIntroductionImageDescription(), refs.get(version.getIntroductionAudioAssetId()));
        }
    }

}
