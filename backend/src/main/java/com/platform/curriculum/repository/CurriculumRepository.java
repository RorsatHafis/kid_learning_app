package com.platform.curriculum.repository;

import com.platform.common.entity.CatalogStatus;
import com.platform.curriculum.entity.Curriculum;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CurriculumRepository extends JpaRepository<Curriculum, UUID> {

    List<Curriculum> findBySubjectIdAndAgeHubIdAndStatus(UUID subjectId, UUID ageHubId, CatalogStatus status);

    /** Every curriculum for a given age hub, across all subjects - used to find auto-enrollment candidates (see EnrollmentService#autoEnrollIfUnambiguous). */
    List<Curriculum> findByAgeHubIdAndStatus(UUID ageHubId, CatalogStatus status);

    List<Curriculum> findByStatus(CatalogStatus status);

    List<Curriculum> findByOwnerAccountIdIsNull();

    List<Curriculum> findByOwnerAccountId(UUID ownerAccountId);

}