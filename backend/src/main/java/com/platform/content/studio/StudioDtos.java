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

    public record ActivitySummary(UUID id, String title, String activityType, List<UUID> skillIds,
                                  List<VersionSummary> versions, boolean archived) {
    }

    /**
     * A lesson and its activities. When a search found the lesson through some of its activities,
     * {@code activities} holds only those, and {@code activityCount} still counts them all.
     */
    public record LessonSummary(UUID id, String title, UUID subjectId, List<ActivitySummary> activities, int activityCount,
                                boolean archived) {
    }

    public record ItemDetail(UUID questionVersionId, UUID questionId, QuestionType questionType, String prompt,
                             int questionVersionNumber, PublicationStatus questionVersionStatus, BigDecimal points,
                             List<UUID> questionSkillIds) {
    }

    /** {@code skillIds}: what this version practises (PD-08); frozen once it is published. */
    public record ActivityVersionDetail(UUID id, int versionNumber, PublicationStatus status, Instant publishedAt,
                                        String instructions, Integer difficultyLevel, Integer estimatedDurationSeconds,
                                        List<ItemDetail> items, List<UUID> skillIds) {
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

    /**
     * {@code skillIds}: what answers to this version are evidence for (PD-08); frozen once it is published.
     * {@code promptImage}/{@code promptAudio} (X11): shown with the prompt and the prompt read aloud.
     */
    public record QuestionVersionDetail(UUID id, int versionNumber, PublicationStatus status, Instant publishedAt,
                                        String prompt, String correctAnswer, Integer difficultyLevel,
                                        MediaRef promptImage, String promptImageDescription, MediaRef promptAudio,
                                        List<OptionDetail> options, List<MatchPairDetail> pairs, List<OrderItemDetail> items,
                                        List<UUID> skillIds) {
    }

    public record QuestionSummary(UUID id, UUID subjectId, QuestionType questionType, List<UUID> skillIds,
                                  String latestPrompt, UUID publishedVersionId, List<VersionSummary> versions, boolean archived) {
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

    public record CreateQuestionRequest(@NotNull UUID subjectId, @NotNull QuestionType questionType,
                                        @NotBlank @Size(max = 2000) String prompt, @Size(max = 300) String correctAnswer,
                                        @Min(1) @Max(10) Integer difficultyLevel,
                                        @Size(max = 10) List<@Valid @NotNull OptionRequest> options,
                                        @Size(max = 6) List<@Valid @NotNull MatchPairRequest> pairs,
                                        @Size(max = 8) List<@Valid @NotNull OrderItemRequest> items,
                                        UUID promptImageAssetId, @Size(max = 300) String promptImageDescription,
                                        UUID promptAudioAssetId,
                                        @NotNull @Size(max = 20) List<@NotNull UUID> skillIds) {
    }

    public record ReviseQuestionDraftRequest(@NotBlank @Size(max = 2000) String prompt, @Size(max = 300) String correctAnswer,
                                             @Min(1) @Max(10) Integer difficultyLevel,
                                             @Size(max = 10) List<@Valid @NotNull OptionRequest> options,
                                             @Size(max = 6) List<@Valid @NotNull MatchPairRequest> pairs,
                                             @Size(max = 8) List<@Valid @NotNull OrderItemRequest> items,
                                             UUID promptImageAssetId, @Size(max = 300) String promptImageDescription,
                                             UUID promptAudioAssetId) {
    }

}
