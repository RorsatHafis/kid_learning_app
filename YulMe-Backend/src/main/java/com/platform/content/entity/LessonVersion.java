package com.platform.content.entity;

import com.platform.common.entity.PublishableVersion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.springframework.util.Assert;

import java.util.UUID;

/** One publishable snapshot of a {@link Lesson}'s content. Maps 1:1 to {@code lesson_versions} (V12 migration). */
@Entity
@Table(name = "lesson_versions")
public class LessonVersion extends PublishableVersion {

    @Column(name = "lesson_id", nullable = false, updatable = false)
    private UUID lessonId;

    // Format (markup/plain text/etc) is owned by whatever renders it, not this schema.
    @Column(name = "body", columnDefinition = "text")
    private String body;

    protected LessonVersion() {
        // JPA
    }

    private LessonVersion(UUID lessonId, int versionNumber, String body) {
        super(versionNumber);
        this.lessonId = lessonId;
        this.body = body;
    }

    public static LessonVersion draft(UUID lessonId, int versionNumber, String body) {
        Assert.notNull(lessonId, "lessonId must not be null");
        return new LessonVersion(lessonId, versionNumber, body);
    }

    /** Only meaningful while DRAFT - callers are responsible for that check (see PublishableVersion's javadoc). */
    public void updateBody(String newBody) {
        this.body = newBody;
    }

    public UUID getLessonId() {
        return lessonId;
    }

    public String getBody() {
        return body;
    }

}