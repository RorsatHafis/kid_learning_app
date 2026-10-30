package com.platform.content.entity;

import com.platform.common.entity.PublishableVersion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.springframework.util.Assert;

import java.util.UUID;

/**
 * One version of an activity: instructions, difficulty and expected duration, with its ordered questions
 * (ActivityItem). A draft is edited freely; once published it never changes.
 */
@Entity
@Table(name = "activity_versions")
public class ActivityVersion extends PublishableVersion {

    @Column(name = "activity_id", nullable = false, updatable = false)
    private UUID activityId;

    @Column(name = "instructions", columnDefinition = "text")
    private String instructions;

    /** V84: "en" when its words (and its activity's name) were written in the Studio's English fields; null: unknown. */
    @Column(name = "text_language", length = 2)
    private String textLanguage;

    /** 1-10 inclusive when present; nullable since not every activity type is meaningfully leveled. */
    @Column(name = "difficulty_level")
    private Integer difficultyLevel;

    @Column(name = "estimated_duration_seconds")
    private Integer estimatedDurationSeconds;

    /** What the activity teaches before it asks: a short explanation or worked example, shown to the child first. */
    @Column(name = "introduction", columnDefinition = "text")
    private String introduction;

    @Column(name = "introduction_image_asset_id")
    private UUID introductionImageAssetId;

    /** What a screen reader says for the introduction's picture. */
    @Column(name = "introduction_image_description", length = 300)
    private String introductionImageDescription;

    /** The introduction read aloud, for children who don't read yet. */
    @Column(name = "introduction_audio_asset_id")
    private UUID introductionAudioAssetId;

    protected ActivityVersion() {
        // JPA
    }

    private ActivityVersion(UUID activityId, int versionNumber, String instructions,
                             Integer difficultyLevel, Integer estimatedDurationSeconds) {
        super(versionNumber);
        this.activityId = activityId;
        this.instructions = instructions;
        this.difficultyLevel = difficultyLevel;
        this.estimatedDurationSeconds = estimatedDurationSeconds;
    }

    public static ActivityVersion draft(UUID activityId, int versionNumber, String instructions,
                                         Integer difficultyLevel, Integer estimatedDurationSeconds) {
        Assert.notNull(activityId, "activityId must not be null");
        Assert.isTrue(difficultyLevel == null || (difficultyLevel >= 1 && difficultyLevel <= 10),
                "difficultyLevel must be between 1 and 10");
        Assert.isTrue(estimatedDurationSeconds == null || estimatedDurationSeconds > 0,
                "estimatedDurationSeconds must be positive");
        return new ActivityVersion(activityId, versionNumber, instructions, difficultyLevel, estimatedDurationSeconds);
    }

    /** A draft is the only editable state: once published, learners hold exact references to it. */
    public void reviseDraft(String newInstructions, Integer newDifficultyLevel, Integer newEstimatedDurationSeconds) {
        requireStatus(com.platform.common.entity.PublicationStatus.DRAFT, "edited");
        Assert.isTrue(newDifficultyLevel == null || (newDifficultyLevel >= 1 && newDifficultyLevel <= 10),
                "difficultyLevel must be between 1 and 10");
        Assert.isTrue(newEstimatedDurationSeconds == null || newEstimatedDurationSeconds > 0,
                "estimatedDurationSeconds must be positive");
        this.instructions = newInstructions;
        this.difficultyLevel = newDifficultyLevel;
        this.estimatedDurationSeconds = newEstimatedDurationSeconds;
    }

    /** The introduction, on a draft only (published versions are what learners hold). Blank text is none. */
    public void setIntroduction(String text, UUID imageAssetId, String imageDescription, UUID audioAssetId) {
        requireStatus(com.platform.common.entity.PublicationStatus.DRAFT, "edited");
        String clean = text == null || text.isBlank() ? null : text.strip();
        Assert.isTrue(clean == null || clean.length() <= 1200, "introduction must be at most 1200 characters");
        this.introduction = clean;
        this.introductionImageAssetId = imageAssetId;
        this.introductionImageDescription = imageAssetId == null || imageDescription == null || imageDescription.isBlank()
                ? null : imageDescription.strip();
        this.introductionAudioAssetId = audioAssetId;
    }

    public String getIntroduction() {
        return introduction;
    }

    public UUID getIntroductionImageAssetId() {
        return introductionImageAssetId;
    }

    public String getIntroductionImageDescription() {
        return introductionImageDescription;
    }

    public UUID getIntroductionAudioAssetId() {
        return introductionAudioAssetId;
    }

    public boolean hasIntroduction() {
        return introduction != null || introductionImageAssetId != null || introductionAudioAssetId != null;
    }

    public UUID getActivityId() {
        return activityId;
    }

    public String getInstructions() {
        return instructions;
    }

    public Integer getDifficultyLevel() {
        return difficultyLevel;
    }

    public Integer getEstimatedDurationSeconds() {
        return estimatedDurationSeconds;
    }


    public String getTextLanguage() {
        return textLanguage;
    }

    /** Its words were written in the Studio's English fields (V84). */
    public void markWrittenInEnglish() {
        this.textLanguage = "en";
    }

    /** A copy's words are the source's, so their language is too (known or unknown). */
    public void sameTextLanguageAs(ActivityVersion source) {
        this.textLanguage = source.textLanguage;
    }
}
