package com.platform.content.entity;

import com.platform.common.entity.AuditableEntity;
import com.platform.common.entity.CatalogStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.springframework.util.Assert;

import java.util.UUID;

@Entity
@Table(name = "questions")
public class Question extends AuditableEntity {

    @Column(name = "subject_id", nullable = false, updatable = false)
    private UUID subjectId;

    @Column(name = "owner_account_id", updatable = false)
    private UUID ownerAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, updatable = false, length = 30)
    private QuestionType questionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CatalogStatus status;

    protected Question() {
        // JPA
    }

    private Question(UUID subjectId, QuestionType questionType, UUID ownerAccountId) {
        this.subjectId = subjectId;
        this.questionType = questionType;
        this.ownerAccountId = ownerAccountId;
        this.status = CatalogStatus.ACTIVE;
    }

    public static Question create(UUID subjectId, QuestionType questionType) {
        return create(subjectId, questionType, null);
    }

    public static Question create(UUID subjectId, QuestionType questionType, UUID ownerAccountId) {
        Assert.notNull(subjectId, "subjectId must not be null");
        Assert.notNull(questionType, "questionType must not be null");
        return new Question(subjectId, questionType, ownerAccountId);
    }

    public void archive() {
        requireStatus(CatalogStatus.ACTIVE, "archived");
        this.status = CatalogStatus.ARCHIVED;
    }

    public void restore() {
        requireStatus(CatalogStatus.ARCHIVED, "restored");
        this.status = CatalogStatus.ACTIVE;
    }

    private void requireStatus(CatalogStatus required, String attemptedTransition) {
        if (status != required) {
            throw new IllegalStateException("Question %s can only be %s from %s, was %s"
                    .formatted(getId(), attemptedTransition, required, status));
        }
    }

    public UUID getSubjectId() {
        return subjectId;
    }
    public UUID getOwnerAccountId() { return ownerAccountId; }

    public QuestionType getQuestionType() {
        return questionType;
    }

    public CatalogStatus getStatus() {
        return status;
    }

}
