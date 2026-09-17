package com.platform.learning.repository;

import com.platform.learning.entity.LearningPath;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LearningPathRepository extends JpaRepository<LearningPath, UUID> {

    // Matches uq_learning_paths_enrollment (V14 migration) - one path per enrollment.
    Optional<LearningPath> findByEnrollmentId(UUID enrollmentId);

}