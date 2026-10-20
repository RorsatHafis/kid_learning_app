package com.platform.content.entity;

import com.platform.common.entity.PublishableVersion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import org.springframework.util.Assert;

import java.util.UUID;

/**
 * One version of a question: prompt, correct answer, difficulty, optional picture and sound, and the hint and
 * explanation (V61). A draft is edited freely; once published it never changes, so evidence always points at
 * exactly what the child saw.
 */
@Entity
@Table(name = "question_versions")
public class QuestionVersion extends PublishableVersion {

    @Column(name = "question_id", nullable = false, updatable = false)
    private UUID questionId;

    @NotBlank
    @Column(name = "prompt", nullable = false, columnDefinition = "text")
    private String prompt;

    @Column(name = "correct_answer", columnDefinition = "text")
    private String correctAnswer;

    @Column(name = "difficulty_level")
    private Integer difficultyLevel;

    /** X11: a picture shown with the prompt, and a recording of it read aloud (for children who don't read yet). */
    @Column(name = "prompt_image_asset_id")
    private UUID promptImageAssetId;

    @Column(name = "prompt_audio_asset_id")
    private UUID promptAudioAssetId;

    /** What the prompt's picture shows, read by screen readers (the picture may carry the question). */
    @Column(name = "prompt_image_description", columnDefinition = "text")
    private String promptImageDescription;

    /** Y41: shown before the child answers - guidance, never the answer. */
    @Column(name = "hint", columnDefinition = "text")
    private String hint;

    /** Y41: shown after the child answers, with the right answer: why it is right. */
    @Column(name = "explanation", columnDefinition = "text")
    private String explanation;

    protected QuestionVersion() {
        // JPA
    }

    private QuestionVersion(UUID questionId, int versionNumber, String prompt, String correctAnswer,
                             Integer difficultyLevel) {
        super(versionNumber);
        this.questionId = questionId;
        this.prompt = prompt;
        this.correctAnswer = correctAnswer;
        this.difficultyLevel = difficultyLevel;
    }

    public static QuestionVersion draft(UUID questionId, int versionNumber, String prompt, String correctAnswer,
                                         Integer difficultyLevel) {
        Assert.notNull(questionId, "questionId must not be null");
        Assert.hasText(prompt, "prompt must not be blank");
        Assert.isTrue(difficultyLevel == null || (difficultyLevel >= 1 && difficultyLevel <= 10),
                "difficultyLevel must be between 1 and 10");
        return new QuestionVersion(questionId, versionNumber, prompt, correctAnswer, difficultyLevel);
    }

    /** A draft is the only editable state: once published, learners hold exact references to it. */
    public void reviseDraft(String newPrompt, String newCorrectAnswer, Integer newDifficultyLevel) {
        requireStatus(com.platform.common.entity.PublicationStatus.DRAFT, "edited");
        Assert.hasText(newPrompt, "prompt must not be blank");
        Assert.isTrue(newDifficultyLevel == null || (newDifficultyLevel >= 1 && newDifficultyLevel <= 10),
                "difficultyLevel must be between 1 and 10");
        this.prompt = newPrompt;
        this.correctAnswer = newCorrectAnswer;
        this.difficultyLevel = newDifficultyLevel;
    }

    /** Part of the version like its text: set while it is a draft, carried to the next draft, frozen once published. */
    public void setPromptMedia(UUID imageAssetId, String imageDescription, UUID audioAssetId) {
        requireStatus(com.platform.common.entity.PublicationStatus.DRAFT, "edited");
        this.promptImageAssetId = imageAssetId;
        this.promptImageDescription = imageAssetId == null || imageDescription == null || imageDescription.isBlank()
                ? null : imageDescription.strip();
        this.promptAudioAssetId = audioAssetId;
    }

    /** Y41: the author's hint and explanation; part of the version like its text, so only a draft's can change. Blank removes one. */
    public void setGuidance(String newHint, String newExplanation) {
        requireStatus(com.platform.common.entity.PublicationStatus.DRAFT, "edited");
        this.hint = newHint == null || newHint.isBlank() ? null : newHint.strip();
        this.explanation = newExplanation == null || newExplanation.isBlank() ? null : newExplanation.strip();
    }

    public String getHint() {
        return hint;
    }

    public String getExplanation() {
        return explanation;
    }

    public String getPromptImageDescription() {
        return promptImageDescription;
    }

    public UUID getPromptImageAssetId() {
        return promptImageAssetId;
    }

    public UUID getPromptAudioAssetId() {
        return promptAudioAssetId;
    }

    public UUID getQuestionId() {
        return questionId;
    }

    public String getPrompt() {
        return prompt;
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }

    public Integer getDifficultyLevel() {
        return difficultyLevel;
    }

}