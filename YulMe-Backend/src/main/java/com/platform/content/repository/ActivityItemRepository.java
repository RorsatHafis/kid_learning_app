package com.platform.content.repository;

import com.platform.content.entity.ActivityItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActivityItemRepository extends JpaRepository<ActivityItem, UUID> {

    List<ActivityItem> findByActivityVersionIdOrderBySequenceOrderAsc(UUID activityVersionId);

    // The exact check answer submission needs: does this question_version actually
    // belong to this activity_version.
    Optional<ActivityItem> findByActivityVersionIdAndQuestionVersionId(UUID activityVersionId, UUID questionVersionId);

}