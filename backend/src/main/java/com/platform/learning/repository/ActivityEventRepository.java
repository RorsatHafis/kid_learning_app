package com.platform.learning.repository;

import com.platform.learning.entity.ActivityEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ActivityEventRepository extends JpaRepository<ActivityEvent, UUID> {

    List<ActivityEvent> findByActivityAttemptIdOrderByOccurredAtAsc(UUID activityAttemptId);

}