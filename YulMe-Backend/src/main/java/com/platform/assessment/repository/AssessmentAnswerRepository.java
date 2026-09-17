package com.platform.assessment.repository;

import com.platform.assessment.entity.AssessmentAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssessmentAnswerRepository extends JpaRepository<AssessmentAnswer, UUID> {

    List<AssessmentAnswer> findByAssessmentAttemptId(UUID assessmentAttemptId);

    // Used on an idempotent-retry path, same reasoning as AnswerRecordRepository's
    // equivalent method.
    Optional<AssessmentAnswer> findTopByAssessmentAttemptIdAndAssessmentItemIdOrderByOccurredAtDesc(
            UUID assessmentAttemptId, UUID assessmentItemId);

}