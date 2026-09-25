package com.platform.knowledge.repository;

import com.platform.knowledge.entity.SkillPrerequisite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SkillPrerequisiteRepository extends JpaRepository<SkillPrerequisite, UUID> {

    List<SkillPrerequisite> findBySkillId(UUID skillId);

    List<SkillPrerequisite> findByPrerequisiteSkillId(UUID prerequisiteSkillId);

}