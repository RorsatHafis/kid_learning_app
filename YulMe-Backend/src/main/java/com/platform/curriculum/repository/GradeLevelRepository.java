package com.platform.curriculum.repository;

import com.platform.curriculum.entity.GradeLevel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GradeLevelRepository extends JpaRepository<GradeLevel, UUID> {

    List<GradeLevel> findByAgeHubIdOrderByDisplayOrderAsc(UUID ageHubId);

}