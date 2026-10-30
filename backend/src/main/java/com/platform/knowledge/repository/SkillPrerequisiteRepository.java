package com.platform.knowledge.repository;

import com.platform.knowledge.entity.SkillPrerequisite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Persistence for skill prerequisites, in both directions.
 */
public interface SkillPrerequisiteRepository extends JpaRepository<SkillPrerequisite, UUID> {

    List<SkillPrerequisite> findBySkillId(UUID skillId);

    /** The prerequisites of many skills in one query (the Studio's catalog). */
    List<SkillPrerequisite> findBySkillIdIn(java.util.Collection<UUID> skillIds);

    List<SkillPrerequisite> findByPrerequisiteSkillId(UUID prerequisiteSkillId);

}