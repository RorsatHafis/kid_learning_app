package com.platform.content.entity;

import com.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.springframework.util.Assert;

import java.time.Instant;
import java.util.UUID;

/**
 * A skill a question version records evidence for (V44, PD-08): an answer to that version is evidence
 * for exactly these skills. Edited only on a DRAFT version, frozen once published (enforced by a
 * database trigger as well).
 */
@Entity
@Table(name = "question_version_skills")
public class QuestionVersionSkill extends BaseEntity {

    @Column(name = "question_version_id", nullable = false, updatable = false)
    private UUID questionVersionId;

    @Column(name = "skill_id", nullable = false, updatable = false)
    private UUID skillId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected QuestionVersionSkill() {
        // JPA
    }

    public static QuestionVersionSkill create(UUID questionVersionId, UUID skillId) {
        Assert.notNull(questionVersionId, "questionVersionId must not be null");
        Assert.notNull(skillId, "skillId must not be null");
        QuestionVersionSkill tag = new QuestionVersionSkill();
        tag.questionVersionId = questionVersionId;
        tag.skillId = skillId;
        return tag;
    }

    public UUID getQuestionVersionId() {
        return questionVersionId;
    }

    public UUID getSkillId() {
        return skillId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
