package com.platform.assessment.entity;

import com.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.springframework.util.Assert;

import java.math.BigDecimal;
import java.util.UUID;

/** Which question_versions an assessment_version contains, in order, with points. Maps 1:1 to {@code assessment_items} (V16). */
@Entity
@Table(name = "assessment_items")
public class AssessmentItem extends BaseEntity {

    @Column(name = "assessment_version_id", nullable = false, updatable = false)
    private UUID assessmentVersionId;

    @Column(name = "question_version_id", nullable = false, updatable = false)
    private UUID questionVersionId;

    @Column(name = "sequence_order", nullable = false, updatable = false)
    private int sequenceOrder;

    @Column(name = "points", nullable = false, precision = 5, scale = 2)
    private BigDecimal points;

    protected AssessmentItem() {
        // JPA
    }

    private AssessmentItem(UUID assessmentVersionId, UUID questionVersionId, int sequenceOrder, BigDecimal points) {
        this.assessmentVersionId = assessmentVersionId;
        this.questionVersionId = questionVersionId;
        this.sequenceOrder = sequenceOrder;
        this.points = points;
    }

    public static AssessmentItem create(UUID assessmentVersionId, UUID questionVersionId, int sequenceOrder, BigDecimal points) {
        Assert.notNull(assessmentVersionId, "assessmentVersionId must not be null");
        Assert.notNull(questionVersionId, "questionVersionId must not be null");
        Assert.isTrue(points != null && points.signum() > 0, "points must be positive");
        return new AssessmentItem(assessmentVersionId, questionVersionId, sequenceOrder, points);
    }

    public UUID getAssessmentVersionId() {
        return assessmentVersionId;
    }

    public UUID getQuestionVersionId() {
        return questionVersionId;
    }

    public int getSequenceOrder() {
        return sequenceOrder;
    }

    public BigDecimal getPoints() {
        return points;
    }

}