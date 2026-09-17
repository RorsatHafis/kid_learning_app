package com.platform.assessment.entity;

import com.platform.common.entity.AuditableEntity;
import com.platform.common.entity.CatalogStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import org.springframework.util.Assert;

import java.util.UUID;

/**
 * A formal assessment's mutable metadata - kept a distinct concept from
 * {@code Activity} (Section 22: "do not allow assessments to accidentally become
 * generic activity attempts"), even though the shape looks similar. Maps 1:1 to
 * {@code assessments} (V16 migration).
 */
@Entity
@Table(name = "assessments")
public class Assessment extends AuditableEntity {

    @Column(name = "subject_id", nullable = false, updatable = false)
    private UUID subjectId;

    @NotBlank
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "assessment_type", nullable = false, length = 30)
    private AssessmentType assessmentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CatalogStatus status;

    protected Assessment() {
        // JPA
    }

    private Assessment(UUID subjectId, String title, AssessmentType assessmentType) {
        this.subjectId = subjectId;
        this.title = title;
        this.assessmentType = assessmentType;
        this.status = CatalogStatus.ACTIVE;
    }

    public static Assessment create(UUID subjectId, String title, AssessmentType assessmentType) {
        Assert.notNull(subjectId, "subjectId must not be null");
        Assert.hasText(title, "title must not be blank");
        Assert.notNull(assessmentType, "assessmentType must not be null");
        return new Assessment(subjectId, title, assessmentType);
    }

    public void archive() {
        requireStatus(CatalogStatus.ACTIVE, "archived");
        this.status = CatalogStatus.ARCHIVED;
    }

    private void requireStatus(CatalogStatus required, String attemptedTransition) {
        if (status != required) {
            throw new IllegalStateException("Assessment %s can only be %s from %s, was %s"
                    .formatted(getId(), attemptedTransition, required, status));
        }
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public String getTitle() {
        return title;
    }

    public AssessmentType getAssessmentType() {
        return assessmentType;
    }

    public CatalogStatus getStatus() {
        return status;
    }

}