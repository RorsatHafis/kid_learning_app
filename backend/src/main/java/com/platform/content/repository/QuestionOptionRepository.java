package com.platform.content.repository;

import com.platform.content.entity.QuestionOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QuestionOptionRepository extends JpaRepository<QuestionOption, UUID> {

    List<QuestionOption> findByQuestionVersionIdOrderByDisplayOrderAsc(UUID questionVersionId);

}