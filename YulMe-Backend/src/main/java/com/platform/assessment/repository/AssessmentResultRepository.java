package com.platform.assessment.repository;

import com.platform.assessment.entity.AssessmentResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AssessmentResultRepository extends JpaRepository<AssessmentResult, UUID> {

    // Matches uq_assessment_results_attempt (V16 migration).
    Optional<AssessmentResult> findByAssessmentAttemptId(UUID assessmentAttemptId);

}