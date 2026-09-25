package com.platform.knowledge.repository;

import com.platform.knowledge.entity.LearningObjectiveSkill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LearningObjectiveSkillRepository extends JpaRepository<LearningObjectiveSkill, UUID> {

    List<LearningObjectiveSkill> findByLearningObjectiveId(UUID learningObjectiveId);

    List<LearningObjectiveSkill> findBySkillId(UUID skillId);

}