package com.platform.review.repository;

import com.platform.review.entity.ReviewSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReviewScheduleRepository extends JpaRepository<ReviewSchedule, UUID> {

    Optional<ReviewSchedule> findByReviewItemId(UUID reviewItemId);

}
