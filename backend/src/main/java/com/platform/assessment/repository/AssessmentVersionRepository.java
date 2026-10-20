package com.platform.assessment.repository;

import com.platform.assessment.entity.AssessmentVersion;
import com.platform.common.entity.PublicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for assessment versions: an exact version, the newest, or the newest in a given publication status.
 */
public interface AssessmentVersionRepository extends JpaRepository<AssessmentVersion, UUID> {

    Optional<AssessmentVersion> findByAssessmentIdAndVersionNumber(UUID assessmentId, int versionNumber);

    Optional<AssessmentVersion> findTopByAssessmentIdOrderByVersionNumberDesc(UUID assessmentId);

    Optional<AssessmentVersion> findFirstByAssessmentIdAndStatusOrderByVersionNumberDesc(UUID assessmentId, PublicationStatus status);

}