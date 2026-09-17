package com.platform.curriculum.entity;

import com.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.springframework.util.Assert;

import java.util.UUID;

@Entity
@Table(name = "curriculum_objectives")
public class CurriculumObjective extends BaseEntity {

    @Column(name = "curriculum_version_id", nullable = false, updatable = false)
    private UUID curriculumVersionId;

    @Column(name = "learning_objective_id", nullable = false, updatable = false)
    private UUID learningObjectiveId;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected CurriculumObjective() {
        // JPA
    }

    private CurriculumObjective(UUID curriculumVersionId, UUID learningObjectiveId, int displayOrder) {
        this.curriculumVersionId = curriculumVersionId;
        this.learningObjectiveId = learningObjectiveId;
        this.displayOrder = displayOrder;
    }

    public static CurriculumObjective create(UUID curriculumVersionId, UUID learningObjectiveId, int displayOrder) {
        Assert.notNull(curriculumVersionId, "curriculumVersionId must not be null");
        Assert.notNull(learningObjectiveId, "learningObjectiveId must not be null");
        Assert.isTrue(displayOrder >= 0, "displayOrder must not be negative");
        return new CurriculumObjective(curriculumVersionId, learningObjectiveId, displayOrder);
    }

    public UUID getCurriculumVersionId() {
        return curriculumVersionId;
    }

    public UUID getLearningObjectiveId() {
        return learningObjectiveId;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

}