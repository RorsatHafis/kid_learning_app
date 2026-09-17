package com.platform.curriculum.repository;

import com.platform.common.entity.PublicationStatus;
import com.platform.curriculum.entity.CurriculumVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CurriculumVersionRepository extends JpaRepository<CurriculumVersion, UUID> {

    // Matches uq_curriculum_versions_curriculum_number (V10 migration).
    Optional<CurriculumVersion> findByCurriculumIdAndVersionNumber(UUID curriculumId, int versionNumber);
    
    Optional<CurriculumVersion> findFirstByCurriculumIdAndStatusOrderByVersionNumberDesc(
            UUID curriculumId, PublicationStatus status);

    // Used by createDraftVersion() to compute the next version number regardless of
    // status (draft or published) - distinct from the PUBLISHED-only lookup above.
    Optional<CurriculumVersion> findTopByCurriculumIdOrderByVersionNumberDesc(UUID curriculumId);

}