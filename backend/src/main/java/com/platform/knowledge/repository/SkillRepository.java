package com.platform.knowledge.repository;

import com.platform.common.entity.CatalogStatus;
import com.platform.knowledge.entity.Skill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for skills, by code and by subject and status.
 */
public interface SkillRepository extends JpaRepository<Skill, UUID> {

    Optional<Skill> findByCode(String code);

    List<Skill> findBySubjectIdAndStatus(UUID subjectId, CatalogStatus status);

}