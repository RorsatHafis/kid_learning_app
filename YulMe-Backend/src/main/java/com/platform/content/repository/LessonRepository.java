package com.platform.content.repository;

import com.platform.common.entity.CatalogStatus;
import com.platform.content.entity.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LessonRepository extends JpaRepository<Lesson, UUID> {

    List<Lesson> findByOwnerAccountId(UUID ownerAccountId);

    List<Lesson> findBySubjectIdAndStatus(UUID subjectId, CatalogStatus status);

}
