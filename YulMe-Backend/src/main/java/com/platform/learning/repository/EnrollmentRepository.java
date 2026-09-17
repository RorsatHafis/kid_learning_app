package com.platform.learning.repository;

import com.platform.learning.entity.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {

    // Matches uq_enrollments_child_curriculum_version (V14 migration).
    Optional<Enrollment> findByChildIdAndCurriculumVersionId(UUID childId, UUID curriculumVersionId);

    List<Enrollment> findByChildId(UUID childId);

}