package com.platform.assessment.entity;

import com.platform.common.entity.PublishableVersion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.springframework.util.Assert;

import java.util.UUID;

/** One publishable snapshot of an {@link Assessment}. Maps 1:1 to {@code assessment_versions} (V16 migration). */
@Entity
@Table(name = "assessment_versions")
public class AssessmentVersion extends PublishableVersion {

    @Column(name = "assessment_id", nullable = false, updatable = false)
    private UUID assessmentId;

    @Column(name = "instructions", columnDefinition = "text")
    private String instructions;

    protected AssessmentVersion() {
        // JPA
    }

    private AssessmentVersion(UUID assessmentId, int versionNumber, String instructions) {
        super(versionNumber);
        this.assessmentId = assessmentId;
        this.instructions = instructions;
    }

    public static AssessmentVersion draft(UUID assessmentId, int versionNumber, String instructions) {
        Assert.notNull(assessmentId, "assessmentId must not be null");
        return new AssessmentVersion(assessmentId, versionNumber, instructions);
    }

    public UUID getAssessmentId() {
        return assessmentId;
    }

    public String getInstructions() {
        return instructions;
    }

}