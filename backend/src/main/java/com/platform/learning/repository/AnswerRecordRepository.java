package com.platform.learning.repository;

import com.platform.learning.entity.AnswerRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for answers given in attempts; one answer per question per attempt.
 */
public interface AnswerRecordRepository extends JpaRepository<AnswerRecord, UUID> {

    List<AnswerRecord> findByActivityAttemptId(UUID activityAttemptId);

    /** Every answer of several attempts in one query (history pages, exports). */
    List<AnswerRecord> findByActivityAttemptIdIn(Collection<UUID> activityAttemptIds);

    Optional<AnswerRecord> findTopByActivityAttemptIdAndQuestionVersionIdOrderByOccurredAtDesc(
            UUID activityAttemptId, UUID questionVersionId);

    // Known Backend Fix B (duplicate answer integrity): the application-level guard
    // used by AnswerSubmissionWriter before it writes a new AnswerRecord - a second
    // answer to the same question within the same attempt, submitted under a
    // different idempotency key, must be rejected rather than silently accepted as a
    // second row. Backed by the V30 unique index at the database level too.
    boolean existsByActivityAttemptIdAndQuestionVersionId(UUID activityAttemptId, UUID questionVersionId);

}