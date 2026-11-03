package com.platform.knowledge.repository;

import com.platform.knowledge.entity.SkillPrerequisite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Persistence for skill prerequisites, in both directions.
 */
public interface SkillPrerequisiteRepository extends JpaRepository<SkillPrerequisite, UUID> {

    /**
     * A skill's prerequisites in the order they were recorded (then by id, for links recorded together), so "the first
     * prerequisite" the adaptive engine reinforces is the same on every run (finding AE-1, docs/ADAPTIVE-ENGINE.md).
     */
    @org.springframework.data.jpa.repository.Query(value = "SELECT * FROM skill_prerequisites WHERE skill_id = :skillId "
            + "ORDER BY created_at, id", nativeQuery = true)
    List<SkillPrerequisite> findBySkillId(@org.springframework.data.repository.query.Param("skillId") UUID skillId);

    /** The prerequisites of many skills in one query (the Studio's catalog). */
    List<SkillPrerequisite> findBySkillIdIn(java.util.Collection<UUID> skillIds);

    List<SkillPrerequisite> findByPrerequisiteSkillId(UUID prerequisiteSkillId);

}