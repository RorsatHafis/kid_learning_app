package com.platform.learner.repository;

import com.platform.learner.entity.AdaptationDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdaptationDecisionRepository extends JpaRepository<AdaptationDecision, UUID> {

    // Matches uq_adaptation_decisions_activity_attempt (V36): at most one per attempt.
    Optional<AdaptationDecision> findByActivityAttemptId(UUID activityAttemptId);

    List<AdaptationDecision> findByChildIdOrderByOccurredAtAsc(UUID childId);

}
