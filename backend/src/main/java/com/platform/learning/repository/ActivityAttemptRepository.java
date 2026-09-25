package com.platform.learning.repository;

import com.platform.learning.entity.ActivityAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;
import java.time.Instant;

public interface ActivityAttemptRepository extends JpaRepository<ActivityAttempt, UUID> {

    /**
     * Battle 5D: row-locks the attempt until the calling transaction ends. Completion
     * and answer submission both take it first, so a retried or concurrent completion
     * waits and then sees COMPLETED (and runs nothing twice), and no answer can be
     * written after completion has scored the attempt.
     */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from ActivityAttempt a where a.id = :id")
    java.util.Optional<ActivityAttempt> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);


    List<ActivityAttempt> findByChildIdOrderByStartedAtDesc(UUID childId);

    List<ActivityAttempt> findByChildIdAndActivityVersionId(UUID childId, UUID activityVersionId);

    List<ActivityAttempt> findByLearningPathItemId(UUID learningPathItemId);

    long countByChildIdAndActivityVersionIdAndStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThanEqual(
            UUID childId, UUID activityVersionId, com.platform.learning.entity.ActivityAttemptStatus status, Instant from, Instant to);

}