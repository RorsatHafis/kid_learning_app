package com.platform.knowledge.entity;

import com.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.springframework.util.Assert;

import java.util.UUID;

@Entity
@Table(name = "skill_prerequisites")
public class SkillPrerequisite extends BaseEntity {

    @Column(name = "skill_id", nullable = false, updatable = false)
    private UUID skillId;

    @Column(name = "prerequisite_skill_id", nullable = false, updatable = false)
    private UUID prerequisiteSkillId;

    protected SkillPrerequisite() {
        // JPA
    }

    private SkillPrerequisite(UUID skillId, UUID prerequisiteSkillId) {
        this.skillId = skillId;
        this.prerequisiteSkillId = prerequisiteSkillId;
    }

    public static SkillPrerequisite create(UUID skillId, UUID prerequisiteSkillId) {
        Assert.notNull(skillId, "skillId must not be null");
        Assert.notNull(prerequisiteSkillId, "prerequisiteSkillId must not be null");
        Assert.isTrue(!skillId.equals(prerequisiteSkillId), "a skill cannot be its own prerequisite");
        return new SkillPrerequisite(skillId, prerequisiteSkillId);
    }

    public UUID getSkillId() {
        return skillId;
    }

    public UUID getPrerequisiteSkillId() {
        return prerequisiteSkillId;
    }

}