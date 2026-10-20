package com.platform.assessment.repository;

import com.platform.assessment.entity.AssessmentItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Persistence for the items of one assessment version, in their authored order.
 */
public interface AssessmentItemRepository extends JpaRepository<AssessmentItem, UUID> {

    List<AssessmentItem> findByAssessmentVersionIdOrderBySequenceOrderAsc(UUID assessmentVersionId);

}