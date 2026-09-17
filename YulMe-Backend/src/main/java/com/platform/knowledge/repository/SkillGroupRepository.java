package com.platform.knowledge.repository;

import com.platform.knowledge.entity.SkillGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SkillGroupRepository extends JpaRepository<SkillGroup, UUID> {

    List<SkillGroup> findBySubjectId(UUID subjectId);

}