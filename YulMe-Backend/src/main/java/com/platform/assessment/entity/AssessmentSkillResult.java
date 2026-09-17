package com.platform.assessment.entity;

import com.platform.common.entity.ImmutableEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.springframework.util.Assert;

import java.math.BigDecimal;
import java.util.UUID;

/** Per-skill breakdown within an AssessmentResult. Immutable. Maps 1:1 to {@code assessment_skill_results} (V16). */
@Entity
@Table(name = "assessment_skill_results")
public class AssessmentSkillResult extends ImmutableEvent {

    @Column(name = "assessment_result_id", nullable = false, updatable = false)
    private UUID assessmentResultId;

    @Column(name = "skill_id", nullable = false, updatable = false)
    private UUID skillId;

    @Column(name = "points_possible", nullable = false, precision = 6, scale = 2)
    private BigDecimal pointsPossible;

    @Column(name = "points_earned", nullable = false, precision = 6, scale = 2)
    private BigDecimal pointsEarned;

    protected AssessmentSkillResult() {
        // JPA
    }

    private AssessmentSkillResult(UUID assessmentResultId, UUID skillId, BigDecimal pointsPossible, BigDecimal pointsEarned) {
        this.assessmentResultId = assessmentResultId;
        this.skillId = skillId;
        this.pointsPossible = pointsPossible;
        this.pointsEarned = pointsEarned;
    }

    public static AssessmentSkillResult record(UUID assessmentResultId, UUID skillId, BigDecimal pointsPossible, BigDecimal pointsEarned) {
        Assert.notNull(assessmentResultId, "assessmentResultId must not be null");
        Assert.notNull(skillId, "skillId must not be null");
        Assert.isTrue(pointsPossible != null && pointsPossible.signum() > 0, "pointsPossible must be positive");
        Assert.isTrue(pointsEarned != null && pointsEarned.signum() >= 0 && pointsEarned.compareTo(pointsPossible) <= 0,
                "pointsEarned must be between 0 and pointsPossible");
        return new AssessmentSkillResult(assessmentResultId, skillId, pointsPossible, pointsEarned);
    }

    public UUID getAssessmentResultId() {
        return assessmentResultId;
    }

    public UUID getSkillId() {
        return skillId;
    }

    public BigDecimal getPointsPossible() {
        return pointsPossible;
    }

    public BigDecimal getPointsEarned() {
        return pointsEarned;
    }

}