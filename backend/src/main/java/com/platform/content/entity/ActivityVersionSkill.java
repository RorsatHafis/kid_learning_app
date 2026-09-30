package com.platform.content.entity;

import com.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.springframework.util.Assert;

import java.time.Instant;
import java.util.UUID;

/**
 * A skill an activity version practises (V44, PD-08): part of the version's content. Paths place a
 * version for its own tags; tags are edited only on a DRAFT version and frozen once it is published
 * (a database trigger enforces it too).
 */
@Entity
@Table(name = "activity_version_skills")
public class ActivityVersionSkill extends BaseEntity {

    @Column(name = "activity_version_id", nullable = false, updatable = false)
    private UUID activityVersionId;

    @Column(name = "skill_id", nullable = false, updatable = false)
    private UUID skillId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected ActivityVersionSkill() {
        // JPA
    }

    public static ActivityVersionSkill create(UUID activityVersionId, UUID skillId) {
        Assert.notNull(activityVersionId, "activityVersionId must not be null");
        Assert.notNull(skillId, "skillId must not be null");
        ActivityVersionSkill tag = new ActivityVersionSkill();
        tag.activityVersionId = activityVersionId;
        tag.skillId = skillId;
        return tag;
    }

    public UUID getActivityVersionId() {
        return activityVersionId;
    }

    public UUID getSkillId() {
        return skillId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
