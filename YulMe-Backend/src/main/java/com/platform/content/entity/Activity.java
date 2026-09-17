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

@Entity
@Table(name = "activities")
public class Activity extends AuditableEntity {

    @Column(name = "subject_id", nullable = false, updatable = false)
    private UUID subjectId;

    /** Nullable: an activity may stand alone or belong to a lesson as its practice component. */
    @Column(name = "lesson_id")
    private UUID lessonId;

    @NotBlank
    @Column(name = "activity_type", nullable = false, length = 30)
    private String activityType;

    @NotBlank
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CatalogStatus status;

    protected Activity() {
        // JPA
    }

    private Activity(UUID subjectId, UUID lessonId, String activityType, String title) {
        this.subjectId = subjectId;
        this.lessonId = lessonId;
        this.activityType = activityType;
        this.title = title;
        this.status = CatalogStatus.ACTIVE;
    }

    public static Activity create(UUID subjectId, UUID lessonId, String activityType, String title) {
        Assert.notNull(subjectId, "subjectId must not be null");
        Assert.hasText(activityType, "activityType must not be blank");
        Assert.hasText(title, "title must not be blank");
        return new Activity(subjectId, lessonId, activityType, title);
    }

    public void retitle(String newTitle) {
        Assert.hasText(newTitle, "newTitle must not be blank");
        this.title = newTitle;
    }

    public void attachToLesson(UUID lessonId) {
        this.lessonId = lessonId;
    }

    public void detachFromLesson() {
        this.lessonId = null;
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
            throw new IllegalStateException("Activity %s can only be %s from %s, was %s"
                    .formatted(getId(), attemptedTransition, required, status));
        }
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public UUID getLessonId() {
        return lessonId;
    }

    public String getActivityType() {
        return activityType;
    }

    public String getTitle() {
        return title;
    }

    public CatalogStatus getStatus() {
        return status;
    }

}