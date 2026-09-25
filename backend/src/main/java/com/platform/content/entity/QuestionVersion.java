package com.platform.content.entity;

import com.platform.common.entity.PublishableVersion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import org.springframework.util.Assert;

import java.util.UUID;

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