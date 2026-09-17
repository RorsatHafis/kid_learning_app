package com.platform.review.repository;

import com.platform.review.entity.ReviewItem;
import com.platform.review.entity.ReviewItemStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewItemRepository extends JpaRepository<ReviewItem, UUID> {

    Optional<ReviewItem> findByChildIdAndSkillId(UUID childId, UUID skillId);

    List<ReviewItem> findByChildIdAndStatus(UUID childId, ReviewItemStatus status);

}
