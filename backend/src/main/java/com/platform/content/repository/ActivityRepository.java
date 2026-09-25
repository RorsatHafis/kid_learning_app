package com.platform.content.repository;

import com.platform.common.entity.CatalogStatus;
import com.platform.content.entity.Activity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ActivityRepository extends JpaRepository<Activity, UUID> {

    List<Activity> findBySubjectIdAndStatus(UUID subjectId, CatalogStatus status);

    List<Activity> findByLessonId(UUID lessonId);

}