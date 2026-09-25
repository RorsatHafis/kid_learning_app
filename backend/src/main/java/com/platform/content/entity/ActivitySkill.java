package com.platform.content.entity;

import com.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.springframework.util.Assert;

import java.util.UUID;

/** Which skills an activity addresses, attached to the container (activity_id), not a specific version. */
@Entity
@Table(name = "activity_skills")
public class ActivitySkill extends BaseEntity {

    @Column(name = "activity_id", nullable = false, updatable = false)
    private UUID activityId;

    @Column(name = "skill_id", nullable = false, updatable = false)
    private UUID skillId;

    protected ActivitySkill() {
        // JPA
    }

    private ActivitySkill(UUID activityId, UUID skillId) {
        this.activityId = activityId;
        this.skillId = skillId;
    }

    public static ActivitySkill create(UUID activityId, UUID skillId) {
        Assert.notNull(activityId, "activityId must not be null");
        Assert.notNull(skillId, "skillId must not be null");
        return new ActivitySkill(activityId, skillId);
    }

    public UUID getActivityId() {
        return activityId;
    }

    public UUID getSkillId() {
        return skillId;
    }

}