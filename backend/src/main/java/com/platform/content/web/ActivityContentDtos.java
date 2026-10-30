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
            return of(option, displayOrder, media, null);
        }

        static QuestionOptionResponse of(QuestionOption option, int displayOrder, Map<UUID, MediaRef> media,
                                         com.platform.content.service.QuestionTranslations.Khmer khmer) {
            String label = khmer == null ? option.getLabel() : khmer.label(option);
            return new QuestionOptionResponse(option.getId(), label == null ? option.getLabel() : label, displayOrder,
                    option.getMatchSide(), option.getSortRole(), media.get(option.getImageAssetId()), media.get(option.getAudioAssetId()));
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
            return forLearner(questionType, questionVersionId, options, media, null);
        }

        /** The same, each label in Khmer when {@code khmer} is given (a complete translation, QuestionTranslations). */
        public static List<QuestionOptionResponse> forLearner(QuestionType questionType, UUID questionVersionId,
                                                              List<QuestionOption> options, Map<UUID, MediaRef> media,
                                                              com.platform.content.service.QuestionTranslations.Khmer khmer) {
            if (questionType == QuestionType.ORDERING) {
                // The display order is the answer: the child gets the items in OrderingItems' order instead.
                List<QuestionOption> given = OrderingItems.learnerOrder(questionVersionId, options);
                List<QuestionOptionResponse> result = new ArrayList<>();
                for (int i = 0; i < given.size(); i++) {
                    result.add(of(given.get(i), i, media, khmer));
                }
                return result;
            }
            if (questionType == QuestionType.SORTING) {
                // Groups in their authored order; items in an order derived from their ids, unrelated to their groups.
                List<QuestionOptionResponse> result = new ArrayList<>();
                List<QuestionOption> groups = options.stream().filter(o -> o.getSortRole() == com.platform.content.entity.SortRole.GROUP)
                        .sorted(Comparator.comparing(QuestionOption::getSortGroup)).toList();
                for (int i = 0; i < groups.size(); i++) {
                    result.add(of(groups.get(i), i, media, khmer));
                }
                List<QuestionOption> items = options.stream().filter(o -> o.getSortRole() == com.platform.content.entity.SortRole.ITEM)
                        .sorted(Comparator.comparing(o -> RequestFingerprint.sha256Hex(questionVersionId + ":sort:" + o.getId()))).toList();
                for (int i = 0; i < items.size(); i++) {
                    result.add(of(items.get(i), i, media, khmer));
                }
                return result;
            }
            if (options.stream().noneMatch(o -> o.getMatchSide() != null)) {
                return options.stream().map(o -> of(o, o.getDisplayOrder(), media, khmer)).toList();
            }
            List<QuestionOptionResponse> result = new ArrayList<>();
            List<QuestionOption> lefts = options.stream().filter(o -> o.getMatchSide() == MatchSide.LEFT)
                    .sorted(Comparator.comparingInt(QuestionOption::getDisplayOrder)).toList();
            for (int i = 0; i < lefts.size(); i++) {
                result.add(of(lefts.get(i), i, media, khmer));
            }
            List<QuestionOption> rights = options.stream().filter(o -> o.getMatchSide() == MatchSide.RIGHT)
                    .sorted(Comparator.comparing(o -> RequestFingerprint.sha256Hex(questionVersionId + ":" + o.getId()))).toList();
            for (int i = 0; i < rights.size(); i++) {
                result.add(of(rights.get(i), i, media, khmer));
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
            String hint,
            /* The language its words are given in: "km" when given in Khmer (LOCALIZATION.md); null when as written. */
            String language,
            /* V83: the language it is written in when given as written and known - "en" - else null (unknown, unmarked). */
            String writtenIn
    ) {
        /** One item as a learner sees it; {@code media} holds the references of every asset it uses ({@link #mediaOf}). */
        public static ActivityItemResponse forLearner(UUID itemId, int sequenceOrder, QuestionType questionType,
                                                      QuestionVersion version, List<QuestionOption> options,
                                                      Map<UUID, MediaRef> media) {
            return forLearner(itemId, sequenceOrder, questionType, version, options, media, null);
        }

        /** The same in Khmer when {@code khmer} - a complete translation (QuestionTranslations) - is given. */
        public static ActivityItemResponse forLearner(UUID itemId, int sequenceOrder, QuestionType questionType,
                                                      QuestionVersion version, List<QuestionOption> options,
                                                      Map<UUID, MediaRef> media,
                                                      com.platform.content.service.QuestionTranslations.Khmer khmer) {
            boolean km = khmer != null;
            return new ActivityItemResponse(itemId, sequenceOrder, version.getId(), questionType, km ? khmer.prompt() : version.getPrompt(),
                    media.get(version.getPromptImageAssetId()),
                    km && khmer.promptImageDescription() != null ? khmer.promptImageDescription() : version.getPromptImageDescription(),
                    media.get(version.getPromptAudioAssetId()),
                    QuestionOptionResponse.forLearner(questionType, version.getId(), options, media, khmer),
                    km && khmer.hint() != null ? khmer.hint() : version.getHint(),
                    km ? com.platform.content.service.QuestionTranslations.KHMER : null,
                    km ? null : version.getTextLanguage());
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

    /**
     * {@code introduction}: what the activity teaches before it asks; null when it has none. {@code language}: the
     * language the activity's own words (name, instructions, introduction) are given in - "km" when given in Khmer
     * (LOCALIZATION.md, rule L-1 for activities); null when as written. Each item says its own.
     */
    public record ActivityVersionDetailResponse(
            UUID activityVersionId,
            UUID activityId,
            String title,
            String instructions,
            Integer difficultyLevel,
            Integer estimatedDurationSeconds,
            List<ActivityItemResponse> items,
            LearnerIntroduction introduction,
            String language,
            /* V84: given as written, its words' language when known ("en"); null when unknown (left unmarked). */
            String writtenIn
    ) {

        /** As written, or - given its complete {@code khmer} - with the activity's own words in Khmer. */
        public static ActivityVersionDetailResponse of(com.platform.content.entity.Activity activity,
                                                       com.platform.content.entity.ActivityVersion version,
                                                       List<ActivityItemResponse> items, LearnerIntroduction introduction,
                                                       com.platform.content.service.ActivityTranslations.Khmer khmer) {
            if (khmer == null) {
                return new ActivityVersionDetailResponse(version.getId(), activity.getId(), activity.getTitle(), version.getInstructions(),
                        version.getDifficultyLevel(), version.getEstimatedDurationSeconds(), items, introduction, null,
                        version.getTextLanguage());
            }
            LearnerIntroduction inKhmer = introduction == null ? null : new LearnerIntroduction(
                    khmer.introduction() != null ? khmer.introduction() : introduction.text(), introduction.image(),
                    khmer.introductionImageDescription() != null ? khmer.introductionImageDescription() : introduction.imageDescription(),
                    introduction.audio());
            return new ActivityVersionDetailResponse(version.getId(), activity.getId(), khmer.title(),
                    khmer.instructions() != null ? khmer.instructions() : version.getInstructions(),
                    version.getDifficultyLevel(), version.getEstimatedDurationSeconds(), items, inKhmer, com.platform.content.service.QuestionTranslations.KHMER,
                    null);
        }
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
