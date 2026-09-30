package com.platform.school.repository;

import com.platform.school.entity.ClassCurriculumAssignment;
import com.platform.school.entity.ClassCurriculumAssignmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClassCurriculumAssignmentRepository extends JpaRepository<ClassCurriculumAssignment, UUID> {

    List<ClassCurriculumAssignment> findBySchoolClassIdAndStatusOrderByCreatedAtAsc(UUID schoolClassId,
                                                                                   ClassCurriculumAssignmentStatus status);

    Optional<ClassCurriculumAssignment> findBySchoolClassIdAndCurriculumId(UUID schoolClassId, UUID curriculumId);
}
