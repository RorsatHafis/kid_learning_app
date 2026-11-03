package com.platform.knowledge.repository;

import com.platform.knowledge.entity.LearningObjectiveSkill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Persistence for the skills each learning objective covers.
 */
public interface LearningObjectiveSkillRepository extends JpaRepository<LearningObjectiveSkill, UUID> {

    /**
     * An objective's skills in the order they were linked (then by id, for links made together): the order of the steps
     * within an objective on a child's starting path and in the journey builder (finding PP-1, docs/ADAPTIVE-ENGINE.md).
     */
    @org.springframework.data.jpa.repository.Query(value = "SELECT * FROM learning_objective_skills "
            + "WHERE learning_objective_id = :learningObjectiveId ORDER BY created_at, id", nativeQuery = true)
    List<LearningObjectiveSkill> findByLearningObjectiveId(
            @org.springframework.data.repository.query.Param("learningObjectiveId") UUID learningObjectiveId);

    /** The skills of many objectives in one query (the Studio's catalog). */
    List<LearningObjectiveSkill> findByLearningObjectiveIdIn(java.util.Collection<UUID> learningObjectiveIds);

    List<LearningObjectiveSkill> findBySkillId(UUID skillId);

}