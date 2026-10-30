package com.platform.knowledge.repository;

import com.platform.knowledge.entity.LearningObjectiveSkill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Persistence for the skills each learning objective covers.
 */
public interface LearningObjectiveSkillRepository extends JpaRepository<LearningObjectiveSkill, UUID> {

    List<LearningObjectiveSkill> findByLearningObjectiveId(UUID learningObjectiveId);

    /** The skills of many objectives in one query (the Studio's catalog). */
    List<LearningObjectiveSkill> findByLearningObjectiveIdIn(java.util.Collection<UUID> learningObjectiveIds);

    List<LearningObjectiveSkill> findBySkillId(UUID skillId);

}