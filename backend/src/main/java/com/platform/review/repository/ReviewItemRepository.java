package com.platform.review.repository;

import com.platform.review.entity.ReviewItem;
import com.platform.review.entity.ReviewItemStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewItemRepository extends JpaRepository<ReviewItem, UUID> {

    Optional<ReviewItem> findByChildIdAndSkillId(UUID childId, UUID skillId);

    List<ReviewItem> findByChildIdAndStatus(UUID childId, ReviewItemStatus status);

    /**
     * Battle 5D (N2): creates the (child, skill) review item unless one already exists,
     * atomically in PostgreSQL. Two concurrent find-then-save calls could both miss the
     * row and both insert, and the loser failed on {@code uq_review_items_child_skill}.
     * Here the database resolves the race on that same constraint and the loser simply
     * inserts nothing. Returns the number of rows inserted (0 or 1).
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO review_items (id, child_id, skill_id, status) VALUES (:id, :childId, :skillId, 'ACTIVE') "
            + "ON CONFLICT (child_id, skill_id) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("childId") UUID childId, @Param("skillId") UUID skillId);

    /**
     * Battle 5D (N2): row-locks the (child, skill) review item until the calling
     * transaction ends, so concurrent evaluations and outcomes for one skill run one at a
     * time and each sees the previous one's committed status and schedule.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReviewItem r where r.childId = :childId and r.skillId = :skillId")
    Optional<ReviewItem> findByChildIdAndSkillIdForUpdate(@Param("childId") UUID childId, @Param("skillId") UUID skillId);

}
