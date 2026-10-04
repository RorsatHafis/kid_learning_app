package com.platform.learning.repository;

import com.platform.learning.entity.LearningPath;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LearningPathRepository extends JpaRepository<LearningPath, UUID> {

    // Matches uq_learning_paths_enrollment (V14 migration) - one path per enrollment.
    Optional<LearningPath> findByEnrollmentId(UUID enrollmentId);

    /** Several enrollments' paths at once (a class's journeys, X20). */
    List<LearningPath> findByEnrollmentIdIn(Collection<UUID> enrollmentIds);

    /**
     * Battle 5A: row-locks the path (SELECT ... FOR UPDATE) until the calling
     * transaction ends. Every path-item insertion takes this lock first, so
     * concurrent inserters (adaptive-next, smart-review-check, retries, other
     * clients) run one at a time per path: each one's "is something already
     * placed?" read and its max(sequence_order)+1 allocation see every earlier
     * inserter's committed items. Uniqueness of (learning_path_id, sequence_order)
     * stays enforced by the V14 constraint as a backstop.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from LearningPath p where p.id = :id")
    Optional<LearningPath> findByIdForUpdate(@Param("id") UUID id);

}
