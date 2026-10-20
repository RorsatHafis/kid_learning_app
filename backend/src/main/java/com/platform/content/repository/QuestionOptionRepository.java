package com.platform.content.repository;

import com.platform.content.entity.QuestionOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Persistence for a question version's options, in display order.
 */
public interface QuestionOptionRepository extends JpaRepository<QuestionOption, UUID> {

    List<QuestionOption> findByQuestionVersionIdOrderByDisplayOrderAsc(UUID questionVersionId);

    List<QuestionOption> findByQuestionVersionIdInOrderByDisplayOrderAsc(java.util.Collection<UUID> questionVersionIds);

    void deleteByQuestionVersionId(UUID questionVersionId);

}