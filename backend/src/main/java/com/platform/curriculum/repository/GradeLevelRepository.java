package com.platform.curriculum.repository;

import com.platform.curriculum.entity.GradeLevel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Persistence for an age band's grades, in order.
 */
public interface GradeLevelRepository extends JpaRepository<GradeLevel, UUID> {

    List<GradeLevel> findByAgeHubIdOrderByDisplayOrderAsc(UUID ageHubId);

}