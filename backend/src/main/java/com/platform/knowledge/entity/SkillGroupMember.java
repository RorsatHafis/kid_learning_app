package com.platform.knowledge.entity;

import com.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.springframework.util.Assert;

import java.util.UUID;

/** Structural composition (created_at only, see CurriculumObjective's javadoc for the general pattern). */
@Entity
@Table(name = "skill_group_members")
public class SkillGroupMember extends BaseEntity {

    @Column(name = "skill_group_id", nullable = false, updatable = false)
    private UUID skillGroupId;

    @Column(name = "skill_id", nullable = false, updatable = false)
    private UUID skillId;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected SkillGroupMember() {
        // JPA
    }

    private SkillGroupMember(UUID skillGroupId, UUID skillId, int displayOrder) {
        this.skillGroupId = skillGroupId;
        this.skillId = skillId;
        this.displayOrder = displayOrder;
    }

    public static SkillGroupMember create(UUID skillGroupId, UUID skillId, int displayOrder) {
        Assert.notNull(skillGroupId, "skillGroupId must not be null");
        Assert.notNull(skillId, "skillId must not be null");
        Assert.isTrue(displayOrder >= 0, "displayOrder must not be negative");
        return new SkillGroupMember(skillGroupId, skillId, displayOrder);
    }

    public UUID getSkillGroupId() {
        return skillGroupId;
    }

    public UUID getSkillId() {
        return skillId;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

}