package com.platform.content.repository;

import com.platform.content.entity.LessonSkill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Persistence for the skills a lesson covers.
 */
public interface LessonSkillRepository extends JpaRepository<LessonSkill, UUID> {

    List<LessonSkill> findByLessonId(UUID lessonId);

    List<LessonSkill> findBySkillId(UUID skillId);

}