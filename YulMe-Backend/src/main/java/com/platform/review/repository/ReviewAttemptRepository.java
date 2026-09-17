package com.platform.review.repository;

import com.platform.review.entity.ReviewAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReviewAttemptRepository extends JpaRepository<ReviewAttempt, UUID> {

    Optional<ReviewAttempt> findByActivityAttemptId(UUID activityAttemptId);

}
