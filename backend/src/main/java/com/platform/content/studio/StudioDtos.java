package com.platform.content.studio;

import com.platform.common.entity.CatalogStatus;
import com.platform.common.entity.PublicationStatus;
import com.platform.common.entity.PublishableVersion;
import com.platform.content.entity.QuestionType;
import com.platform.content.media.MediaService.MediaRef;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Content Studio contract. Author-facing: unlike learner DTOs these carry answer
 * keys, and every read is limited to content the signed-in author may edit.
 */
public final class StudioDtos {

    private StudioDtos() {
    }

    // ------------------------------------------------------------------ catalog

    public record SubjectDto(UUID id, String code, String name, String description, boolean archived) {
    }

    public record AgeHubDto(UUID id, String code, String name, int minAge, Integer maxAge) {
    }

    public record SkillDto(UUID id, String code, String name, String description, CatalogStatus status,
                           List<UUID> prerequisiteSkillIds) {
    }

    public record ObjectiveDto(UUID id, String code, String title, String description, List<UUID> skillIds, boolean archived) {
    }

    public record CatalogDto(SubjectDto subject, List<SkillDto> skills, List<ObjectiveDto> objectives) {
    }

    private static final String CODE = "^[A-Z0-9_]+$";

    public record CreateSubjectRequest(@NotBlank @Size(max = 50) @Pattern(regexp = CODE) String code,
                                       @NotBlank @Size(max = 120) String name, @Size(max = 2000) String description) {
    }

    public record CreateSkillRequest(@NotBlank @Size(max = 60) @Pattern(regexp = CODE) String code,
                                     @NotBlank @Size(max = 160) String name, @Size(max = 2000) String description) {
    }

    public record CreateObjectiveRequest(@NotBlank @Size(max = 60) @Pattern(regexp = CODE) String code,
                                         @NotBlank @Size(max = 200) String title, @Size(max = 2000) String description) {
    }

    // ------------------------------------------------------------------ versions

    public record VersionSummary(UUID id, int versionNumber, PublicationStatus status, Instant publishedAt) {
        static VersionSummary of(PublishableVersion version) {
            return new VersionSummary(version.getId(), version.getVersionNumber(), version.getStatus(), version.getPublishedAt());
        }
    }

    // ------------------------------------------------------------------ curricula

    public record CurriculumSummary(UUID id, String name, UUID subjectId, UUID ageHubId, List<VersionSummary> versions,
                                    boolean archived) {
    }

    public record CurriculumVersionDetail(UUID id, int versionNumber, PublicationStatus status, Instant publishedAt,
                                          List<UUID> objectiveIds) {
    }

    public record CurriculumDetail(UUID id, String name, UUID subjectId, UUID ageHubId,
                                   List<CurriculumVersionDetail> versions, boolean archived) {
    }

    public record CreateCurriculumRequest(@NotNull UUID subjectId, @NotNull UUID ageHubId,
                                          @NotBlank @Size(max = 160) String name) {
    }

    public record ReplaceObjectivesRequest(@NotNull @Size(max = 200) List<@NotNull UUID> objectiveIds) {
    }

    // ------------------------------------------------------------------ lessons & activities

    /**
     * {@code support} (L8): how much help a child gets in the latest version - so an author sees, across a lesson,
     * where children meet bare questions. Null when the activity has no version.
     */
    public record ActivitySummary(UUID id, String title, String activityType, List<UUID> skillIds,
                                  List<VersionSummary> versions, boolean archived, Support support) {
    }

    /** Questions in a version, how many are read aloud, explained after answering and hinted, and whether it teaches first. */
    public record Support(int questions, int readAloud, int explained, int hinted, boolean introduction) {
    }

    /**
     * A lesson and its activities. When a search found the lesson through some of its activities,
     * {@code activities} holds only those, and {@code activityCount} still counts them all. M5b: {@code visibility}
     * PRIVATE or SCHOOL; {@code copied} when it was copied from a colleague's shared lesson.
     */
    public record LessonSummary(UUID id, String title, UUID subjectId, List<ActivitySummary> activities, int activityCount,
                                boolean archived, String visibility, boolean copied) {
    }

    /**
     * {@code currentVersionId}/{@code currentVersionNumber}: the question's published version now, when it isn't the one
     * this item holds (the item's was retired by a newer one) - so the builder can offer to use it; null otherwise.
     */
    public record ItemDetail(UUID questionVersionId, UUID questionId, QuestionType questionType, String prompt,
                             int questionVersionNumber, PublicationStatus questionVersionStatus, BigDecimal points,
                             List<UUID> questionSkillIds, UUID currentVersionId, Integer currentVersionNumber) {
    }

    /** {@code skillIds}: what this version practises (PD-08); frozen once it is published. */
    /** {@code introduction}: what the version teaches before it asks; null when it has none. */
    public record ActivityVersionDetail(UUID id, int versionNumber, PublicationStatus status, Instant publishedAt,
                                        String instructions, Integer difficultyLevel, Integer estimatedDurationSeconds,
                                        List<ItemDetail> items, List<UUID> skillIds, IntroductionDetail introduction) {
    }

    /** An activity's introduction: text, a picture with its description, a recording. */
    public record IntroductionDetail(String text, com.platform.content.media.MediaService.MediaRef image, String imageDescription,
                                     com.platform.content.media.MediaService.MediaRef audio) {
    }

    /** Sets a draft's introduction; every field optional, blank text is none. */
    public record IntroductionRequest(@Size(max = 1200) String text, UUID imageAssetId, @Size(max = 300) String imageDescription,
                                      UUID audioAssetId) {
    }

    public record ActivityDetail(UUID id, String title, String activityType, UUID lessonId, UUID subjectId,
                                 List<UUID> skillIds, List<ActivityVersionDetail> versions, boolean archived) {
    }

    public record CreateLessonRequest(@NotNull UUID subjectId, @NotBlank @Size(max = 200) String title) {
    }

    public record CreateActivityRequest(@NotBlank @Size(max = 200) String title,
                                        @NotNull @Size(min = 1, max = 20) List<@NotNull UUID> skillIds) {
    }

    public record ReviseActivityDraftRequest(@Size(max = 2000) String instructions,
                                             @Min(1) @Max(10) Integer difficultyLevel,
                                             @Positive @Max(3600) Integer estimatedDurationSeconds) {
    }

    public record ItemRequest(@NotNull UUID questionVersionId, @DecimalMin("0.01") BigDecimal points) {
    }

    public record ReplaceItemsRequest(@NotNull @Size(max = 50) List<@Valid @NotNull ItemRequest> items) {
    }

    // ------------------------------------------------------------------ questions

    public record OptionDetail(UUID id, String label, boolean correct, MediaRef image, MediaRef audio) {
    }

    /** One pair of a MATCHING question, as the author sees it. */
    public record MatchPairDetail(UUID leftId, String left, UUID rightId, String right, MediaRef leftImage, MediaRef rightImage) {
    }

    /** One ORDERING item; a version's items are listed in their correct order. */
    public record OrderItemDetail(UUID id, String label, MediaRef image, MediaRef audio) {
    }

    /** SORTING (X23): a group, as the author sees it; {@code group} is its number. */
    public record SortGroupDetail(UUID id, String label, int group, MediaRef image) {
    }

    /** SORTING (X23): an item and the number of the group it belongs to. */
    public record SortItemDetail(UUID id, String label, int group, MediaRef image, MediaRef audio) {
    }

    /**
     * {@code skillIds}: what answers to this version are evidence for (PD-08); frozen once it is published.
     * {@code promptImage}/{@code promptAudio} (X11): shown with the prompt and the prompt read aloud.
     */
    public record QuestionVersionDetail(UUID id, int versionNumber, PublicationStatus status, Instant publishedAt,
                                        String prompt, String correctAnswer, Integer difficultyLevel,
                                        MediaRef promptImage, String promptImageDescription, MediaRef promptAudio,
                                        List<OptionDetail> options, List<MatchPairDetail> pairs, List<OrderItemDetail> items,
                                        List<UUID> skillIds, List<SortGroupDetail> groups, List<SortItemDetail> sortItems,
                                        String hint, String explanation, KhmerDetail khmer) {
    }

    /**
     * A version's Khmer as written so far (LOCALIZATION.md): any field may be null; {@code options} maps each choice,
     * pair side, item or group (by id) to its label. {@code complete}: a child reading Khmer gets this version in Khmer
     * (otherwise in English, as written). {@code translatable}: false for SHORT_ANSWER, which is scored on the
     * author's own words.
     */
    public record KhmerDetail(String prompt, String hint, String explanation, String promptImageDescription,
                              Map<UUID, String> options, boolean complete, boolean translatable) {
    }

    /**
     * A draft's Khmer, saved with its English in the same request: question-level fields, and one label per row, in
     * the same order as the request's own rows ({@code options}, the pairs' two sides, {@code items}, {@code groups},
     * {@code sortItems}). Blank or missing: not translated yet. Ignored for SHORT_ANSWER.
     */
    public record KhmerDraft(@Size(max = 2000) String prompt, @Size(max = 300) String hint, @Size(max = 600) String explanation,
                             @Size(max = 300) String promptImageDescription,
                             @Size(max = 10) List<@Size(max = 300) String> options,
                             @Size(max = 6) List<@Size(max = 300) String> pairLefts,
                             @Size(max = 6) List<@Size(max = 300) String> pairRights,
                             @Size(max = 8) List<@Size(max = 300) String> items,
                             @Size(max = 4) List<@Size(max = 120) String> groups,
                             @Size(max = 10) List<@Size(max = 300) String> sortItems) {
    }

    /**
     * {@code publishedPrompt}: the published version's own wording - what a child is given when the question is reused
     * (the latest draft may already say something else); null without a published version.
     */
    public record QuestionSummary(UUID id, UUID subjectId, QuestionType questionType, List<UUID> skillIds,
                                  String latestPrompt, UUID publishedVersionId, List<VersionSummary> versions, boolean archived,
                                  String publishedPrompt) {
    }

    /** The question bank's filters; every one is optional. {@code live}: has a published version; {@code archived}: the archived ones instead. */
    public record QuestionFilter(String text, QuestionType type, PublicationStatus status, UUID skillId, boolean live, boolean archived) {
        public QuestionFilter(String text, QuestionType type, PublicationStatus status, UUID skillId, boolean live) {
            this(text, type, status, skillId, live, false);
        }
    }

    public record QuestionDetail(UUID id, UUID subjectId, QuestionType questionType, List<UUID> skillIds,
                                 List<QuestionVersionDetail> versions, boolean archived) {
    }

    /** {@code label} describes the picture when there is one: screen readers read it, and the child sees the picture. */
    public record OptionRequest(@NotBlank @Size(max = 300) String label, boolean correct, UUID imageAssetId, UUID audioAssetId) {
    }

    /** MATCHING: an item and its match, either of which may be a picture. */
    public record MatchPairRequest(@NotBlank @Size(max = 300) String left, @NotBlank @Size(max = 300) String right,
                                   UUID leftImageAssetId, UUID rightImageAssetId) {
    }

    /** ORDERING: one thing to put in order; the request lists them in the correct order. */
    public record OrderItemRequest(@NotBlank @Size(max = 300) String label, UUID imageAssetId, UUID audioAssetId) {
    }

    /** SORTING (X23): a group the child sorts into; its number is its place in the request's groups. */
    public record SortGroupRequest(@NotBlank @Size(max = 120) String label, UUID imageAssetId) {
    }

    /** SORTING (X23): an item and the number (0-based place in the request's groups) of the group it belongs to. */
    public record SortItemRequest(@NotBlank @Size(max = 300) String label, @Min(0) @Max(3) int group, UUID imageAssetId,
                                  UUID audioAssetId) {
    }

    public record CreateQuestionRequest(@NotNull UUID subjectId, @NotNull QuestionType questionType,
                                        @NotBlank @Size(max = 2000) String prompt, @Size(max = 300) String correctAnswer,
                                        @Min(1) @Max(10) Integer difficultyLevel,
                                        @Size(max = 10) List<@Valid @NotNull OptionRequest> options,
                                        @Size(max = 6) List<@Valid @NotNull MatchPairRequest> pairs,
                                        @Size(max = 8) List<@Valid @NotNull OrderItemRequest> items,
                                        UUID promptImageAssetId, @Size(max = 300) String promptImageDescription,
                                        UUID promptAudioAssetId,
                                        @NotNull @Size(max = 20) List<@NotNull UUID> skillIds,
                                        @Size(max = 4) List<@Valid @NotNull SortGroupRequest> groups,
                                        @Size(max = 10) List<@Valid @NotNull SortItemRequest> sortItems,
                                        @Size(max = 300) String hint, @Size(max = 600) String explanation, @Valid KhmerDraft khmer) {
        /** Every type but SORTING. */
        public CreateQuestionRequest(UUID subjectId, QuestionType questionType, String prompt, String correctAnswer,
                                     Integer difficultyLevel, List<OptionRequest> options, List<MatchPairRequest> pairs,
                                     List<OrderItemRequest> items, UUID promptImageAssetId, String promptImageDescription,
                                     UUID promptAudioAssetId, List<UUID> skillIds) {
            this(subjectId, questionType, prompt, correctAnswer, difficultyLevel, options, pairs, items, promptImageAssetId,
                    promptImageDescription, promptAudioAssetId, skillIds, null, null, null, null, null);
        }
    }

    public record ReviseQuestionDraftRequest(@NotBlank @Size(max = 2000) String prompt, @Size(max = 300) String correctAnswer,
                                             @Min(1) @Max(10) Integer difficultyLevel,
                                             @Size(max = 10) List<@Valid @NotNull OptionRequest> options,
                                             @Size(max = 6) List<@Valid @NotNull MatchPairRequest> pairs,
                                             @Size(max = 8) List<@Valid @NotNull OrderItemRequest> items,
                                             UUID promptImageAssetId, @Size(max = 300) String promptImageDescription,
                                             UUID promptAudioAssetId,
                                             @Size(max = 4) List<@Valid @NotNull SortGroupRequest> groups,
                                             @Size(max = 10) List<@Valid @NotNull SortItemRequest> sortItems,
                                             @Size(max = 300) String hint, @Size(max = 600) String explanation,
                                             @Valid KhmerDraft khmer) {
        /** Every type but SORTING. */
        public ReviseQuestionDraftRequest(String prompt, String correctAnswer, Integer difficultyLevel, List<OptionRequest> options,
                                          List<MatchPairRequest> pairs, List<OrderItemRequest> items, UUID promptImageAssetId,
                                          String promptImageDescription, UUID promptAudioAssetId) {
            this(prompt, correctAnswer, difficultyLevel, options, pairs, items, promptImageAssetId, promptImageDescription,
                    promptAudioAssetId, null, null, null, null, null);
        }
    }

}
