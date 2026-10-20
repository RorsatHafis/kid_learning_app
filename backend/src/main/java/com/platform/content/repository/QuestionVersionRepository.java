package com.platform.content.repository;

import com.platform.common.entity.PublicationStatus;
import com.platform.content.entity.QuestionVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for question versions: an exact version, the newest, all of them in order, or the newest in a given
 * publication status.
 */
public interface QuestionVersionRepository extends JpaRepository<QuestionVersion, UUID> {

    Optional<QuestionVersion> findByQuestionIdAndVersionNumber(UUID questionId, int versionNumber);

    Optional<QuestionVersion> findTopByQuestionIdOrderByVersionNumberDesc(UUID questionId);

    List<QuestionVersion> findByQuestionIdOrderByVersionNumberAsc(UUID questionId);

    List<QuestionVersion> findByQuestionIdInOrderByVersionNumberAsc(java.util.Collection<UUID> questionIds);

    Optional<QuestionVersion> findFirstByQuestionIdAndStatusOrderByVersionNumberDesc(UUID questionId, PublicationStatus status);

}