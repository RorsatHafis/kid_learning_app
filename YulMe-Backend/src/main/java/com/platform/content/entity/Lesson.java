package com.platform.content.entity;

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
 * A lesson's mutable metadata. Actual teachable content lives in {@link LessonVersion}
 * - see V10's curriculum_versions comment for the shared versioning rationale. Maps
 * 1:1 to {@code lessons} (V12 migration).
 */
@Entity
@Table(name = "lessons")
public class Lesson extends AuditableEntity {

    @Column(name = "subject_id", nullable = false, updatable = false)
    private UUID subjectId;

    @Column(name = "owner_account_id", updatable = false)
    private UUID ownerAccountId;

    @NotBlank
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CatalogStatus status;

    protected Lesson() {
        // JPA
    }

    private Lesson(UUID subjectId, String title, UUID ownerAccountId) {
        this.subjectId = subjectId;
        this.title = title;
        this.ownerAccountId = ownerAccountId;
        this.status = CatalogStatus.ACTIVE;
    }

    public static Lesson create(UUID subjectId, String title) {
        return create(subjectId, title, null);
    }

    public static Lesson create(UUID subjectId, String title, UUID ownerAccountId) {
        Assert.notNull(subjectId, "subjectId must not be null");
        Assert.hasText(title, "title must not be blank");
        return new Lesson(subjectId, title, ownerAccountId);
    }

    public void retitle(String newTitle) {
        Assert.hasText(newTitle, "newTitle must not be blank");
        this.title = newTitle;
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
            throw new IllegalStateException("Lesson %s can only be %s from %s, was %s"
                    .formatted(getId(), attemptedTransition, required, status));
        }
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public UUID getOwnerAccountId() { return ownerAccountId; }

    public String getTitle() {
        return title;
    }

    public CatalogStatus getStatus() {
        return status;
    }

}
