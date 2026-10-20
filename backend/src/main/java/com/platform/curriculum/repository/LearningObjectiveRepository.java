package com.platform.curriculum.repository;

import com.platform.curriculum.entity.LearningObjective;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for learning objectives, by code and by subject.
 */
public interface LearningObjectiveRepository extends JpaRepository<LearningObjective, UUID> {

    Optional<LearningObjective> findByCode(String code);

    List<LearningObjective> findBySubjectId(UUID subjectId);

}