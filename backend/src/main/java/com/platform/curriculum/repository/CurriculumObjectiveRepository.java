package com.platform.curriculum.repository;

import com.platform.curriculum.entity.CurriculumObjective;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CurriculumObjectiveRepository extends JpaRepository<CurriculumObjective, UUID> {

    List<CurriculumObjective> findByCurriculumVersionIdOrderByDisplayOrderAsc(UUID curriculumVersionId);

}