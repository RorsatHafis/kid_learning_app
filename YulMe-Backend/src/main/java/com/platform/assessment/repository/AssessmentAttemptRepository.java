package com.platform.assessment.repository;

import com.platform.assessment.entity.AssessmentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AssessmentAttemptRepository extends JpaRepository<AssessmentAttempt, UUID> {

    List<AssessmentAttempt> findByChildIdOrderByStartedAtDesc(UUID childId);

}