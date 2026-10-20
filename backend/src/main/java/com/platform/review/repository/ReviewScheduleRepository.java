package com.platform.review.repository;

import com.platform.review.entity.ReviewSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for when each review item is next due.
 */
public interface ReviewScheduleRepository extends JpaRepository<ReviewSchedule, UUID> {

    Optional<ReviewSchedule> findByReviewItemId(UUID reviewItemId);

    List<ReviewSchedule> findByReviewItemIdIn(Collection<UUID> reviewItemIds);

}
