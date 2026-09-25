package com.platform.curriculum.entity;

import com.platform.common.entity.AuditableEntity;
import com.platform.common.entity.CatalogStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import org.springframework.util.Assert;

import java.util.UUID;

@Entity
@Table(name = "learning_objectives")
public class LearningObjective extends AuditableEntity {

    @Column(name = "subject_id", nullable = false, updatable = false)
    private UUID subjectId;

    @NotBlank
    @Column(name = "code", nullable = false, updatable = false, length = 60)
    private String code;

    @NotBlank
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CatalogStatus status;

    protected LearningObjective() {
        // JPA
    }

    private LearningObjective(UUID subjectId, String code, String title, String description) {
        this.subjectId = subjectId;
        this.code = code;
        this.title = title;
        this.description = description;
        this.status = CatalogStatus.ACTIVE;
    }

    public static LearningObjective create(UUID subjectId, String code, String title, String description) {
        Assert.notNull(subjectId, "subjectId must not be null");
        Assert.hasText(code, "code must not be blank");
        Assert.hasText(title, "title must not be blank");
        return new LearningObjective(subjectId, code, title, description);
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
            throw new IllegalStateException("LearningObjective %s can only be %s from %s, was %s"
                    .formatted(getId(), attemptedTransition, required, status));
        }
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public CatalogStatus getStatus() {
        return status;
    }

}