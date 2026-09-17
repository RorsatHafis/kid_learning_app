package com.platform.learning.repository;

import com.platform.learning.entity.ActivityAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;
import java.time.Instant;

public interface ActivityAttemptRepository extends JpaRepository<ActivityAttempt, UUID> {

    List<ActivityAttempt> findByChildIdOrderByStartedAtDesc(UUID childId);

    List<ActivityAttempt> findByChildIdAndActivityVersionId(UUID childId, UUID activityVersionId);

    List<ActivityAttempt> findByLearningPathItemId(UUID learningPathItemId);

    long countByChildIdAndActivityVersionIdAndStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThanEqual(
            UUID childId, UUID activityVersionId, com.platform.learning.entity.ActivityAttemptStatus status, Instant from, Instant to);

}