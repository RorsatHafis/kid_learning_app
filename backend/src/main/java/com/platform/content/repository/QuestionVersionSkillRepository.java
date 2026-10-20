package com.platform.content.repository;

import com.platform.content.entity.QuestionVersionSkill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for the skills a question version assesses.
 */
public interface QuestionVersionSkillRepository extends JpaRepository<QuestionVersionSkill, UUID> {

    List<QuestionVersionSkill> findByQuestionVersionIdOrderByCreatedAtAscIdAsc(UUID questionVersionId);

    List<QuestionVersionSkill> findByQuestionVersionIdIn(Collection<UUID> questionVersionIds);

    Optional<QuestionVersionSkill> findByQuestionVersionIdAndSkillId(UUID questionVersionId, UUID skillId);
}
