package com.platform.learning.repository;

import com.platform.learning.entity.LearningSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LearningSessionRepository extends JpaRepository<LearningSession, UUID> {

    List<LearningSession> findByChildIdOrderByStartedAtDesc(UUID childId);

    // "Is there already an open session for this child" - a session with no ended_at yet.
    List<LearningSession> findByChildIdAndEndedAtIsNull(UUID childId);

}