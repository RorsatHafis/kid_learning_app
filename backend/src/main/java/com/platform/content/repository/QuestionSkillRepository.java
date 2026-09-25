package com.platform.content.repository;

import com.platform.content.entity.QuestionSkill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QuestionSkillRepository extends JpaRepository<QuestionSkill, UUID> {

    List<QuestionSkill> findByQuestionId(UUID questionId);

    List<QuestionSkill> findBySkillId(UUID skillId);

}