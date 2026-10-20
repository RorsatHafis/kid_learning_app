package com.platform.content.repository;

import com.platform.common.entity.PublicationStatus;
import com.platform.content.entity.LessonVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for lesson versions: an exact version, the newest, or the newest in a given publication status.
 */
public interface LessonVersionRepository extends JpaRepository<LessonVersion, UUID> {

    Optional<LessonVersion> findByLessonIdAndVersionNumber(UUID lessonId, int versionNumber);

    Optional<LessonVersion> findTopByLessonIdOrderByVersionNumberDesc(UUID lessonId);

    Optional<LessonVersion> findFirstByLessonIdAndStatusOrderByVersionNumberDesc(UUID lessonId, PublicationStatus status);

}