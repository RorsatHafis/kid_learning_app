package com.platform.content.entity;

import com.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import org.springframework.util.Assert;

import java.util.UUID;

@Entity
@Table(name = "question_options")
public class QuestionOption extends BaseEntity {

    @Column(name = "question_version_id", nullable = false, updatable = false)
    private UUID questionVersionId;

    @NotBlank
    @Column(name = "label", nullable = false, columnDefinition = "text")
    private String label;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected QuestionOption() {
        // JPA
    }

    private QuestionOption(UUID questionVersionId, String label, boolean correct, int displayOrder) {
        this.questionVersionId = questionVersionId;
        this.label = label;
        this.correct = correct;
        this.displayOrder = displayOrder;
    }

    public static QuestionOption create(UUID questionVersionId, String label, boolean correct, int displayOrder) {
        Assert.notNull(questionVersionId, "questionVersionId must not be null");
        Assert.hasText(label, "label must not be blank");
        Assert.isTrue(displayOrder >= 0, "displayOrder must not be negative");
        return new QuestionOption(questionVersionId, label, correct, displayOrder);
    }

    public void relabel(String newLabel) {
        Assert.hasText(newLabel, "newLabel must not be blank");
        this.label = newLabel;
    }

    public void setCorrect(boolean correct) {
        this.correct = correct;
    }

    public UUID getQuestionVersionId() {
        return questionVersionId;
    }

    public String getLabel() {
        return label;
    }

    public boolean isCorrect() {
        return correct;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

}