package com.platform.content.repository;

import com.platform.common.entity.PublicationStatus;
import com.platform.content.entity.QuestionVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface QuestionVersionRepository extends JpaRepository<QuestionVersion, UUID> {

    Optional<QuestionVersion> findByQuestionIdAndVersionNumber(UUID questionId, int versionNumber);

    Optional<QuestionVersion> findTopByQuestionIdOrderByVersionNumberDesc(UUID questionId);

    Optional<QuestionVersion> findFirstByQuestionIdAndStatusOrderByVersionNumberDesc(UUID questionId, PublicationStatus status);

}