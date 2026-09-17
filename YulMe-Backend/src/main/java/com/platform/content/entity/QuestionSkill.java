package com.platform.content.entity;

import com.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.springframework.util.Assert;

import java.util.UUID;

/** Which skills a question assesses, attached to the container (question_id), not a specific version - see V12's comment. */
@Entity
@Table(name = "question_skills")
public class QuestionSkill extends BaseEntity {

    @Column(name = "question_id", nullable = false, updatable = false)
    private UUID questionId;

    @Column(name = "skill_id", nullable = false, updatable = false)
    private UUID skillId;

    protected QuestionSkill() {
        // JPA
    }

    private QuestionSkill(UUID questionId, UUID skillId) {
        this.questionId = questionId;
        this.skillId = skillId;
    }

    public static QuestionSkill create(UUID questionId, UUID skillId) {
        Assert.notNull(questionId, "questionId must not be null");
        Assert.notNull(skillId, "skillId must not be null");
        return new QuestionSkill(questionId, skillId);
    }

    public UUID getQuestionId() {
        return questionId;
    }

    public UUID getSkillId() {
        return skillId;
    }

}