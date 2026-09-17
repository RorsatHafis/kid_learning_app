package com.platform.content.entity;

import com.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.springframework.util.Assert;

import java.util.UUID;

/** Which skills a lesson addresses, attached to the container (lesson_id), not a specific version. */
@Entity
@Table(name = "lesson_skills")
public class LessonSkill extends BaseEntity {

    @Column(name = "lesson_id", nullable = false, updatable = false)
    private UUID lessonId;

    @Column(name = "skill_id", nullable = false, updatable = false)
    private UUID skillId;

    protected LessonSkill() {
        // JPA
    }

    private LessonSkill(UUID lessonId, UUID skillId) {
        this.lessonId = lessonId;
        this.skillId = skillId;
    }

    public static LessonSkill create(UUID lessonId, UUID skillId) {
        Assert.notNull(lessonId, "lessonId must not be null");
        Assert.notNull(skillId, "skillId must not be null");
        return new LessonSkill(lessonId, skillId);
    }

    public UUID getLessonId() {
        return lessonId;
    }

    public UUID getSkillId() {
        return skillId;
    }

}