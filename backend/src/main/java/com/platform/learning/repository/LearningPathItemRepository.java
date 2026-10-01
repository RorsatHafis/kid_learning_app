package com.platform.learning.repository;

import com.platform.learning.entity.LearningPathItem;
import com.platform.learning.entity.LearningPathItemStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LearningPathItemRepository extends JpaRepository<LearningPathItem, UUID> {

    List<LearningPathItem> findByLearningPathIdOrderBySequenceOrderAsc(UUID learningPathId);

    // "What's next" - the first not-yet-completed item in sequence order.
    Optional<LearningPathItem> findFirstByLearningPathIdAndStatusInOrderBySequenceOrderAsc(
            UUID learningPathId, List<LearningPathItemStatus> statuses);

    // "What sequence number comes next" - used when appending a new item (e.g. an
    // adaptive insertion).
    Optional<LearningPathItem> findTopByLearningPathIdOrderBySequenceOrderDesc(UUID learningPathId);

    /** One piece of practice a class's teacher placed, with the child whose path it is on (X10). */
    record ClassPractice(UUID childId, UUID itemId, UUID activityVersionId, UUID skillId, LearningPathItemStatus status,
                         Instant addedAt, Instant completedAt) {
    }

    /** Everything a class placed, newest first - one query for the class's follow-up view. */
    @Query("""
            select new com.platform.learning.repository.LearningPathItemRepository$ClassPractice(
                e.childId, i.id, i.activityVersionId, i.assignedSkillId, i.status, i.addedAt, i.completedAt)
            from LearningPathItem i, LearningPath p, Enrollment e
            where i.assignedClassId = :classId and p.id = i.learningPathId and e.id = p.enrollmentId
            order by i.addedAt desc, i.id desc
            """)
    List<ClassPractice> findClassPractice(@Param("classId") UUID classId);

    /**
     * Practice these classes placed for a child that hasn't been started - withdrawn when the
     * class's access to the child ends. Started practice (an attempt is open) is left to finish.
     */
    @Query("""
            select i from LearningPathItem i, LearningPath p, Enrollment e
            where i.assignedClassId in :classIds and p.id = i.learningPathId and e.id = p.enrollmentId
              and e.childId = :childId and i.status = com.platform.learning.entity.LearningPathItemStatus.PENDING
            """)
    List<LearningPathItem> findWaitingClassPractice(@Param("classIds") Collection<UUID> classIds, @Param("childId") UUID childId);

}
