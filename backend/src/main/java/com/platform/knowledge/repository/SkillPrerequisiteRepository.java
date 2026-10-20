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

    List<SkillPrerequisite> findByPrerequisiteSkillId(UUID prerequisiteSkillId);

}