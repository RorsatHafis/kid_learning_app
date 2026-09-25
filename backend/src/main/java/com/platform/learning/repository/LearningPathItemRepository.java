package com.platform.learning.repository;

import com.platform.learning.entity.LearningPathItem;
import com.platform.learning.entity.LearningPathItemStatus;
import org.springframework.data.jpa.repository.JpaRepository;

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

}