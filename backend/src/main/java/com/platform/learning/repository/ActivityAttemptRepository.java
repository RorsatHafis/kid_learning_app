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

    /** The completed attempts of one learning path's steps: what a journey shows, without the child's whole history. */
    @org.springframework.data.jpa.repository.Query("""
            select a from ActivityAttempt a
            where a.status = com.platform.learning.entity.ActivityAttemptStatus.COMPLETED
              and a.learningPathItemId in (select i.id from LearningPathItem i where i.learningPathId = :pathId)
            """)
    List<ActivityAttempt> findCompletedOnPath(@org.springframework.data.repository.query.Param("pathId") UUID learningPathId);

    /** A child's completed activities and the time spent on them, counted in the database (TD-25). */
    interface CompletionTotals {
        long getCompleted();

        long getSeconds();
    }

    @org.springframework.data.jpa.repository.Query(nativeQuery = true, value = """
            select count(*) as completed,
                   coalesce(sum(extract(epoch from (completed_at - started_at))), 0)::bigint as seconds
            from activity_attempts
            where child_id = :childId and status = 'COMPLETED'
            """)
    CompletionTotals completionTotals(@org.springframework.data.repository.query.Param("childId") UUID childId);

    /** A child's completed attempts, newest first: the first page of their history. */
    @org.springframework.data.jpa.repository.Query("""
            select a from ActivityAttempt a
            where a.childId = :childId and a.status = com.platform.learning.entity.ActivityAttemptStatus.COMPLETED
            order by a.completedAt desc, a.id desc
            """)
    List<ActivityAttempt> findCompletedNewestFirst(@org.springframework.data.repository.query.Param("childId") UUID childId,
                                                   org.springframework.data.domain.Limit limit);

    /** The page of a child's history after (older than) a given attempt: a keyset cursor, stable while new attempts arrive. */
    @org.springframework.data.jpa.repository.Query("""
            select a from ActivityAttempt a
            where a.childId = :childId and a.status = com.platform.learning.entity.ActivityAttemptStatus.COMPLETED
              and (a.completedAt < :before or (a.completedAt = :before and a.id < :beforeId))
            order by a.completedAt desc, a.id desc
            """)
    List<ActivityAttempt> findCompletedBefore(@org.springframework.data.repository.query.Param("childId") UUID childId,
                                              @org.springframework.data.repository.query.Param("before") Instant before,
                                              @org.springframework.data.repository.query.Param("beforeId") UUID beforeId,
                                              org.springframework.data.domain.Limit limit);

    List<ActivityAttempt> findByChildIdAndActivityVersionId(UUID childId, UUID activityVersionId);

    List<ActivityAttempt> findByLearningPathItemId(UUID learningPathItemId);

    boolean existsByChildIdAndActivityVersionId(UUID childId, UUID activityVersionId);

    long countByChildIdAndActivityVersionIdAndStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThanEqual(
            UUID childId, UUID activityVersionId, com.platform.learning.entity.ActivityAttemptStatus status, Instant from, Instant to);

}