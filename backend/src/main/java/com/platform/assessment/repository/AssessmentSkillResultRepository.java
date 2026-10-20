package com.platform.assessment.repository;

import com.platform.assessment.entity.AssessmentSkillResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Persistence for the per-skill lines of an assessment result.
 */
public interface AssessmentSkillResultRepository extends JpaRepository<AssessmentSkillResult, UUID> {

    List<AssessmentSkillResult> findByAssessmentResultId(UUID assessmentResultId);

}