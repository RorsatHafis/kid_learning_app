package com.platform.knowledge.entity;

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

/**
 * An atomic unit of knowledge or ability (e.g. "Add two 2-digit numbers"). The
 * central entity the Learner and Adaptive domains key mastery/decisions off of.
 * Maps 1:1 to {@code skills} (V11 migration).
 */
@Entity
@Table(name = "skills")
public class Skill extends AuditableEntity {

    @Column(name = "subject_id", nullable = false, updatable = false)
    private UUID subjectId;

    @NotBlank
    @Column(name = "code", nullable = false, updatable = false, length = 60)
    private String code;

    @NotBlank
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CatalogStatus status;

    protected Skill() {
        // JPA
    }

    private Skill(UUID subjectId, String code, String name, String description) {
        this.subjectId = subjectId;
        this.code = code;
        this.name = name;
        this.description = description;
        this.status = CatalogStatus.ACTIVE;
    }

    public static Skill create(UUID subjectId, String code, String name, String description) {
        Assert.notNull(subjectId, "subjectId must not be null");
        Assert.hasText(code, "code must not be blank");
        Assert.hasText(name, "name must not be blank");
        return new Skill(subjectId, code, name, description);
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
            throw new IllegalStateException("Skill %s can only be %s from %s, was %s"
                    .formatted(getId(), attemptedTransition, required, status));
        }
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public CatalogStatus getStatus() {
        return status;
    }

}