package com.platform.knowledge.entity;

import com.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.springframework.util.Assert;

import java.util.UUID;

/** Which skills a learning objective (curriculum domain) covers. Structural composition, created_at only. */
@Entity
@Table(name = "learning_objective_skills")
public class LearningObjectiveSkill extends BaseEntity {

    @Column(name = "learning_objective_id", nullable = false, updatable = false)
    private UUID learningObjectiveId;

    @Column(name = "skill_id", nullable = false, updatable = false)
    private UUID skillId;

    protected LearningObjectiveSkill() {
        // JPA
    }

    private LearningObjectiveSkill(UUID learningObjectiveId, UUID skillId) {
        this.learningObjectiveId = learningObjectiveId;
        this.skillId = skillId;
    }

    public static LearningObjectiveSkill create(UUID learningObjectiveId, UUID skillId) {
        Assert.notNull(learningObjectiveId, "learningObjectiveId must not be null");
        Assert.notNull(skillId, "skillId must not be null");
        return new LearningObjectiveSkill(learningObjectiveId, skillId);
    }

    public UUID getLearningObjectiveId() {
        return learningObjectiveId;
    }

    public UUID getSkillId() {
        return skillId;
    }

}